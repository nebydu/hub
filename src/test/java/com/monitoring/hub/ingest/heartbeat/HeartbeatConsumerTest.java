package com.monitoring.hub.ingest.heartbeat;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.monitoring.hub.config.KafkaConfig;
import com.monitoring.hub.store.AgentRegistry;
import com.monitoring.hub.store.HeartbeatLatestMap;

/**
 * spec §5.4.2 추출 규약을 OTLP JSON 샘플로 검증.
 *
 * <p>샘플 timeUnixNano = 1747734000000000000은 {@code 2025-05-20T07:00:00Z}.
 */
class HeartbeatConsumerTest {

    private static final String AGENT_ID = "agent-001";
    private static final long NANOS = 1_747_734_000_000_000_000L;
    private static final Instant EXPECTED_INSTANT = Instant.ofEpochSecond(1_747_734_000L);

    private HeartbeatLatestMap latestMap;
    private AgentRegistry registry;
    private HeartbeatConsumer consumer;
    private ObjectMapper mapper;

    @BeforeEach
    void setUp() {
        latestMap = new HeartbeatLatestMap();
        registry = new AgentRegistry();
        mapper = new ObjectMapper();
        consumer = new HeartbeatConsumer(mapper, latestMap, registry);
    }

    @Test
    void parsesAgentHeartbeatGaugeAndUpdatesLatestMap() throws Exception {
        String payload = """
                {
                  "resourceMetrics": [{
                    "resource": { "attributes": [] },
                    "scopeMetrics": [{
                      "scope": { "name": "monitoring-agent", "version": "0.1.0" },
                      "metrics": [{
                        "name": "agent.heartbeat",
                        "gauge": {
                          "dataPoints": [{
                            "attributes": [
                              { "key": "agent_id", "value": { "stringValue": "%s" } }
                            ],
                            "timeUnixNano": "%d",
                            "asDouble": 1.0
                          }]
                        }
                      }]
                    }]
                  }]
                }
                """.formatted(AGENT_ID, NANOS);

        JsonNode root = mapper.readTree(payload);
        int applied = consumer.extractAndApply(root);

        assertThat(applied).isEqualTo(1);
        assertThat(latestMap.find(AGENT_ID))
                .hasValueSatisfying(s -> assertThat(s.lastSeen()).isEqualTo(EXPECTED_INSTANT));
    }

    @Test
    void ignoresUnknownMetricNames() throws Exception {
        // agent.heartbeat이 아니면 무시 — Collector가 다른 metric을 함께 보낼 수 있다.
        String payload = """
                {
                  "resourceMetrics": [{
                    "scopeMetrics": [{
                      "metrics": [{
                        "name": "process.cpu.utilization",
                        "gauge": {
                          "dataPoints": [{
                            "attributes": [
                              { "key": "agent_id", "value": { "stringValue": "agent-001" } }
                            ],
                            "timeUnixNano": "1747734000000000000",
                            "asDouble": 0.42
                          }]
                        }
                      }]
                    }]
                  }]
                }
                """;

        int applied = consumer.extractAndApply(mapper.readTree(payload));

        assertThat(applied).isZero();
        assertThat(latestMap.size()).isZero();
    }

    @Test
    void skipsDataPointWithoutAgentId() throws Exception {
        String payload = """
                {
                  "resourceMetrics": [{
                    "scopeMetrics": [{
                      "metrics": [{
                        "name": "agent.heartbeat",
                        "gauge": {
                          "dataPoints": [{
                            "attributes": [
                              { "key": "service.name", "value": { "stringValue": "x" } }
                            ],
                            "timeUnixNano": "1747734000000000000"
                          }]
                        }
                      }]
                    }]
                  }]
                }
                """;

        int applied = consumer.extractAndApply(mapper.readTree(payload));

        assertThat(applied).isZero();
        assertThat(latestMap.size()).isZero();
    }

    @Test
    void updatesAgentRegistryLastSeenForKnownAgent() throws Exception {
        // 사전 등록된 Agent의 last_seen은 heartbeat에서도 갱신되어야 한다 (spec §3.2).
        registry.register(AGENT_ID, "host-1", "linux/amd64", "0.1.0");
        Instant registeredAt = registry.find(AGENT_ID).orElseThrow().lastSeen();

        // 등록 직후의 last_seen보다 더 미래의 timeUnixNano로 heartbeat 발송.
        long futureNanos = (registeredAt.getEpochSecond() + 60) * 1_000_000_000L;
        String payload = """
                {
                  "resourceMetrics": [{
                    "scopeMetrics": [{
                      "metrics": [{
                        "name": "agent.heartbeat",
                        "gauge": {
                          "dataPoints": [{
                            "attributes": [
                              { "key": "agent_id", "value": { "stringValue": "%s" } }
                            ],
                            "timeUnixNano": "%d"
                          }]
                        }
                      }]
                    }]
                  }]
                }
                """.formatted(AGENT_ID, futureNanos);

        consumer.extractAndApply(mapper.readTree(payload));

        Instant afterHeartbeat = registry.find(AGENT_ID).orElseThrow().lastSeen();
        assertThat(afterHeartbeat).isAfter(registeredAt);
    }

    @Test
    void handlesMultipleDataPointsAcrossAgents() throws Exception {
        String payload = """
                {
                  "resourceMetrics": [{
                    "scopeMetrics": [{
                      "metrics": [{
                        "name": "agent.heartbeat",
                        "gauge": {
                          "dataPoints": [
                            {
                              "attributes": [{ "key": "agent_id", "value": { "stringValue": "agent-A" } }],
                              "timeUnixNano": "1747734000000000000"
                            },
                            {
                              "attributes": [{ "key": "agent_id", "value": { "stringValue": "agent-B" } }],
                              "timeUnixNano": "1747734010000000000"
                            }
                          ]
                        }
                      }]
                    }]
                  }]
                }
                """;

        int applied = consumer.extractAndApply(mapper.readTree(payload));

        assertThat(applied).isEqualTo(2);
        assertThat(latestMap.snapshot()).containsKeys("agent-A", "agent-B");
    }

    @Test
    void consumeHandlesNullPayloadWithoutThrowing() {
        // 깨진 메시지가 와도 다음 처리가 막히면 안 된다 (AuditConsumer와 동일 정책).
        consumer.consume(new ConsumerRecord<>(KafkaConfig.Topics.HEARTBEATS, 0, 0L, "key", null));

        assertThat(latestMap.size()).isZero();
    }
}
