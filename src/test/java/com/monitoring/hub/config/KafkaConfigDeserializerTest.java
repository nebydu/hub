package com.monitoring.hub.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;

import org.apache.kafka.common.serialization.Deserializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.kafka.core.ConsumerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.monitoring.hub.domain.audit.ActorType;
import com.monitoring.hub.domain.audit.AuditAction;
import com.monitoring.hub.domain.audit.AuditEvent;
import com.monitoring.hub.domain.audit.EventResult;
import com.monitoring.hub.domain.audit.TargetType;
import com.monitoring.hub.domain.job.JobResult;
import com.monitoring.hub.domain.job.JobStatus;
import com.monitoring.hub.domain.job.JobType;

/**
 * {@link KafkaConfig}의 audit-topic / result-topic-job·result-topic-log 전용
 * ConsumerFactory가 글로벌 {@link ObjectMapper}(snake_case 정책 + JavaTimeModule)를
 * 사용해 {@link Deserializer}를 구성하는지 검증한다.
 *
 * <p>회귀 방지: 과거 자체 ObjectMapper(camelCase 디폴트)를 만드는 생성자
 * {@code new JsonDeserializer<>(Class, false)}로 회귀하면 snake_case 페이로드의
 * camelCase 필드(executionId / agentId / jobType / eventId / occurredAt 등)가
 * 모두 {@code null}로 매핑되어 종단 흐름이 깨진다. 이 테스트는 ConsumerFactory에서
 * value deserializer를 꺼내 raw bytes를 흘리고 모든 필드가 채워지는지 단정해
 * 그 회귀를 즉시 잡는다.
 */
class KafkaConfigDeserializerTest {

    private static final String DUMMY_BROKERS = "localhost:9092";

    private KafkaConfig kafkaConfig;
    private AppProperties appProperties;
    private KafkaProperties kafkaProperties;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        kafkaConfig = new KafkaConfig();
        appProperties = new AppProperties(
                new AppProperties.Kafka(DUMMY_BROKERS),
                new AppProperties.Audit(200),
                new AppProperties.Job(100),
                new AppProperties.Command(50),
                new AppProperties.Agent(30)
        );
        kafkaProperties = new KafkaProperties();
        // 글로벌 자동 구성(`spring.jackson.property-naming-strategy=SNAKE_CASE`,
        // `JavaTimeModule` 자동 등록)과 동일한 정책의 ObjectMapper를 손으로
        // 만들어 주입한다 — KafkaConfig가 이 매퍼를 그대로 deserializer로
        // 흘려보내는지가 회귀 포인트.
        objectMapper = new ObjectMapper()
                .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
                .registerModule(new JavaTimeModule());
    }

    @Test
    void auditEventConsumerFactoryMapsSnakeCasePayloadFully() {
        ConsumerFactory<String, AuditEvent> factory =
                kafkaConfig.auditEventConsumerFactory(appProperties, kafkaProperties, objectMapper);

        // 실제 production raw 페이로드 형태를 그대로 가져옴 (script-agent가 발행).
        String payload = """
                {
                  "event_id":    "b7c5a4b1-a0dc-4ddd-9e8b-a434d9a55ec0",
                  "actor":       {"type": "AGENT", "id": "agent-001"},
                  "action":      "AGENT_STARTED",
                  "target":      {"type": "AGENT", "id": "agent-001"},
                  "result":      "SUCCESS",
                  "occurred_at": "2026-05-19T14:00:00Z",
                  "metadata":    {"hostname": "host-1", "agent_version": "0.1.0"}
                }
                """;

        try (Deserializer<AuditEvent> deserializer = factory.getValueDeserializer()) {
            AuditEvent event = deserializer.deserialize(
                    KafkaConfig.Topics.AUDIT_EVENTS,
                    payload.getBytes(StandardCharsets.UTF_8));

            // camelCase Java 필드 ↔ snake_case JSON 키 매핑이 적용되어야 한다.
            assertThat(event.eventId()).isEqualTo("b7c5a4b1-a0dc-4ddd-9e8b-a434d9a55ec0");
            assertThat(event.action()).isEqualTo(AuditAction.AGENT_STARTED);
            assertThat(event.result()).isEqualTo(EventResult.SUCCESS);
            // JavaTimeModule 등록 확인 — Instant 파싱.
            assertThat(event.occurredAt()).isEqualTo(Instant.parse("2026-05-19T14:00:00Z"));
            assertThat(event.actor()).isNotNull();
            assertThat(event.actor().type()).isEqualTo(ActorType.AGENT);
            assertThat(event.actor().id()).isEqualTo("agent-001");
            assertThat(event.target().type()).isEqualTo(TargetType.AGENT);
            // metadata는 Map<String,Object>이므로 snake_case key 원형 유지.
            Map<String, Object> md = event.metadata();
            assertThat(md).containsEntry("hostname", "host-1")
                          .containsEntry("agent_version", "0.1.0");
        }
    }

    @Test
    void jobResultConsumerFactoryMapsSnakeCasePayloadFully() {
        ConsumerFactory<String, JobResult> factory =
                kafkaConfig.jobResultConsumerFactory(appProperties, kafkaProperties, objectMapper);

        // SCRIPT_JOB 결과 — 중첩 record(ScriptResult)의 snake_case 매핑까지 검증.
        String payload = """
                {
                  "execution_id": "8f4b1c9e-0000-0000-0000-000000000000",
                  "schedule_id":  "3a7d2b5f-0000-0000-0000-000000000000",
                  "job_id":       "9c1e8a4d-0000-0000-0000-000000000000",
                  "agent_id":     "agent-001",
                  "job_type":     "SCRIPT_JOB",
                  "status":       "SUCCESS",
                  "started_at":   "2026-05-19T14:00:01Z",
                  "finished_at":  "2026-05-19T14:00:03Z",
                  "script": {
                    "exit_code":  0,
                    "stdout_cap": "ok",
                    "stderr_cap": "",
                    "truncated":  false
                  },
                  "log": null
                }
                """;

        // T4-2: SCRIPT_JOB 결과는 result-topic-job으로 수신 — 동일 factory가 처리.
        try (Deserializer<JobResult> deserializer = factory.getValueDeserializer()) {
            JobResult result = deserializer.deserialize(
                    KafkaConfig.Topics.RESULT_JOB,
                    payload.getBytes(StandardCharsets.UTF_8));

            assertThat(result.executionId()).isEqualTo("8f4b1c9e-0000-0000-0000-000000000000");
            assertThat(result.scheduleId()).isEqualTo("3a7d2b5f-0000-0000-0000-000000000000");
            assertThat(result.jobId()).isEqualTo("9c1e8a4d-0000-0000-0000-000000000000");
            assertThat(result.agentId()).isEqualTo("agent-001");
            assertThat(result.jobType()).isEqualTo(JobType.SCRIPT_JOB);
            assertThat(result.status()).isEqualTo(JobStatus.SUCCESS);
            assertThat(result.startedAt()).isEqualTo(Instant.parse("2026-05-19T14:00:01Z"));
            assertThat(result.finishedAt()).isEqualTo(Instant.parse("2026-05-19T14:00:03Z"));
            assertThat(result.log()).isNull();
            assertThat(result.script()).isNotNull();
            // ScriptResult 중첩 record 필드도 snake_case → camelCase 매핑 적용되어야 한다.
            assertThat(result.script().exitCode()).isZero();
            assertThat(result.script().stdoutCap()).isEqualTo("ok");
            assertThat(result.script().stderrCap()).isEmpty();
            assertThat(result.script().truncated()).isFalse();
        }
    }

    @Test
    void jobResultConsumerFactoryMapsLogPayloadFromResultLogTopic() {
        // T4-2 회귀: result-topic-job/result-topic-log 두 토픽이 동일 factory(payload
        // class JobResult 공통, 옵션 A)를 통과함을 LOG_JOB payload로 못 박는다.
        ConsumerFactory<String, JobResult> factory =
                kafkaConfig.jobResultConsumerFactory(appProperties, kafkaProperties, objectMapper);

        // LOG_JOB 결과 — 중첩 record(LogResult)의 snake_case 매핑까지 검증.
        String payload = """
                {
                  "execution_id": "1a2b3c4d-0000-0000-0000-000000000000",
                  "schedule_id":  "5e6f7a8b-0000-0000-0000-000000000000",
                  "job_id":       "9c0d1e2f-0000-0000-0000-000000000000",
                  "agent_id":     "agent-002",
                  "job_type":     "LOG_JOB",
                  "status":       "SUCCESS",
                  "started_at":   "2026-05-19T15:00:01Z",
                  "finished_at":  "2026-05-19T15:00:02Z",
                  "script": null,
                  "log": {
                    "matched_lines_count": 2,
                    "sample_lines": ["ERROR boom", "ERROR again"]
                  }
                }
                """;

        try (Deserializer<JobResult> deserializer = factory.getValueDeserializer()) {
            JobResult result = deserializer.deserialize(
                    KafkaConfig.Topics.RESULT_LOG,
                    payload.getBytes(StandardCharsets.UTF_8));

            assertThat(result.executionId()).isEqualTo("1a2b3c4d-0000-0000-0000-000000000000");
            assertThat(result.agentId()).isEqualTo("agent-002");
            assertThat(result.jobType()).isEqualTo(JobType.LOG_JOB);
            assertThat(result.status()).isEqualTo(JobStatus.SUCCESS);
            assertThat(result.script()).isNull();
            assertThat(result.log()).isNotNull();
            // LogResult 중첩 record 필드도 snake_case → camelCase 매핑 적용되어야 한다.
            assertThat(result.log().matchedLinesCount()).isEqualTo(2);
            assertThat(result.log().sampleLines()).containsExactly("ERROR boom", "ERROR again");
        }
    }
}
