package com.monitoring.hub.ingest.heartbeat;

import java.time.Instant;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.monitoring.hub.config.KafkaConfig;
import com.monitoring.hub.domain.heartbeat.HeartbeatState;
import com.monitoring.hub.store.AgentRegistry;
import com.monitoring.hub.store.HeartbeatLatestMap;

/**
 * heartbeats 토픽 consumer. spec §5.4.
 *
 * <p>OTel Collector가 {@code otlp_json} exporter로 발행한 OTLP JSON 메시지에서
 * spec §5.4.2의 추출 규약을 따라 agent_id와 last_seen을 뽑아낸다:
 * <ul>
 *   <li>경로: {@code resourceMetrics[].scopeMetrics[].metrics[]}</li>
 *   <li>metric name이 {@code agent.heartbeat}인 항목만 처리</li>
 *   <li>각 dataPoint의 attribute에서 {@code agent_id}의 {@code stringValue} 추출</li>
 *   <li>dataPoint의 {@code timeUnixNano}를 {@link Instant}로 변환해 last_seen 갱신</li>
 * </ul>
 *
 * <p>{@code timeUnixNano}는 OTLP JSON 명세상 64-bit 정수를 안전하게 표현하기 위해
 * 문자열로 인코딩된다 — long 변환 후 {@link Instant#ofEpochSecond(long, long)}로 풀어준다.
 *
 * <p>매 heartbeat마다 {@link HeartbeatLatestMap}을 upsert하고 동시에
 * {@link AgentRegistry#updateLastSeen(String)}을 호출 — 데모 단계에서는 Agent
 * 목록 맵의 last_seen이 heartbeat에서도 갱신되어야 화면에서 한 곳을 보고
 * 살아있음을 판단할 수 있다 (spec §3.2 두 번째 단락).
 *
 * <p>OTLP 표준 헤더 envelope이므로 spec §2.2의 4종 헤더 검사는 적용하지 않는다.
 * 깨진 메시지는 WARN 로깅만 하고 다음 메시지로 진행.
 */
@Component
public class HeartbeatConsumer {

    private static final Logger log = LoggerFactory.getLogger(HeartbeatConsumer.class);
    private static final String METRIC_NAME = "agent.heartbeat";
    private static final String ATTR_AGENT_ID = "agent_id";

    private final ObjectMapper objectMapper;
    private final HeartbeatLatestMap latestMap;
    private final AgentRegistry registry;

    public HeartbeatConsumer(
            ObjectMapper objectMapper,
            HeartbeatLatestMap latestMap,
            AgentRegistry registry) {
        this.objectMapper = objectMapper;
        this.latestMap = latestMap;
        this.registry = registry;
    }

    @KafkaListener(
            topics = KafkaConfig.Topics.HEARTBEATS,
            containerFactory = "heartbeatListenerFactory",
            groupId = "hub-heartbeat-consumer"
    )
    public void consume(ConsumerRecord<String, String> record) {
        String payload = record.value();
        if (payload == null || payload.isBlank()) {
            log.warn("skipping empty heartbeat payload at offset={}", record.offset());
            return;
        }
        try {
            JsonNode root = objectMapper.readTree(payload);
            int applied = extractAndApply(root);
            if (applied == 0) {
                log.debug("heartbeat payload contained no agent.heartbeat metrics");
            }
        } catch (Exception ex) {
            log.warn("failed to parse heartbeat payload at offset={}", record.offset(), ex);
        }
    }

    /**
     * OTLP JSON 트리에서 {@code agent.heartbeat} dataPoint를 모두 찾아 last_seen을
     * 갱신한다. 처리한 dataPoint 수를 반환 (테스트와 진단용).
     */
    int extractAndApply(JsonNode root) {
        int applied = 0;
        for (JsonNode rm : optArray(root.path("resourceMetrics"))) {
            for (JsonNode sm : optArray(rm.path("scopeMetrics"))) {
                for (JsonNode metric : optArray(sm.path("metrics"))) {
                    if (!METRIC_NAME.equals(metric.path("name").asText())) {
                        continue;
                    }
                    applied += applyMetric(metric);
                }
            }
        }
        return applied;
    }

    /**
     * 단일 metric의 dataPoints를 순회. OTLP 표준상 metric type별로 dataPoints가
     * 다른 컨테이너에 들어간다 ({@code gauge}/{@code sum} 등) — spec §5.4.1은 Gauge로
     * 정의하지만 송신 측 변경에도 깨지지 않도록 후보를 모두 본다.
     */
    private int applyMetric(JsonNode metric) {
        int applied = 0;
        for (String container : new String[]{"gauge", "sum"}) {
            JsonNode points = metric.path(container).path("dataPoints");
            for (JsonNode dp : optArray(points)) {
                if (applyDataPoint(dp)) {
                    applied++;
                }
            }
        }
        return applied;
    }

    private boolean applyDataPoint(JsonNode dp) {
        String agentId = extractAgentId(dp);
        if (agentId == null) {
            log.debug("skipping heartbeat dataPoint without agent_id attribute");
            return false;
        }
        Instant lastSeen = extractTimeUnixNano(dp);
        if (lastSeen == null) {
            log.debug("skipping heartbeat dataPoint without timeUnixNano: agent_id={}", agentId);
            return false;
        }
        latestMap.upsert(new HeartbeatState(agentId, lastSeen));
        registry.updateLastSeen(agentId);
        return true;
    }

    private static String extractAgentId(JsonNode dp) {
        for (JsonNode attr : optArray(dp.path("attributes"))) {
            if (ATTR_AGENT_ID.equals(attr.path("key").asText())) {
                String value = attr.path("value").path("stringValue").asText(null);
                if (value != null && !value.isBlank()) {
                    return value;
                }
            }
        }
        return null;
    }

    /**
     * {@code timeUnixNano}는 OTLP JSON 명세상 64-bit 정수를 문자열로 인코딩한다.
     * 빈 문자열·파싱 실패는 null 반환.
     */
    private static Instant extractTimeUnixNano(JsonNode dp) {
        String nanoStr = dp.path("timeUnixNano").asText(null);
        if (nanoStr == null || nanoStr.isBlank()) {
            return null;
        }
        try {
            long nano = Long.parseLong(nanoStr);
            return Instant.ofEpochSecond(nano / 1_000_000_000L, nano % 1_000_000_000L);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static Iterable<JsonNode> optArray(JsonNode node) {
        return node.isArray() ? node : java.util.Collections.emptyList();
    }
}
