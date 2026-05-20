package com.monitoring.hub.domain.audit;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.json.JsonTest;
import org.springframework.boot.test.json.JacksonTester;

/**
 * spec §5.3.1 AGENT_STARTED 페이로드를 Spring Boot의 ObjectMapper로 역직렬화해
 * 글로벌 {@code SNAKE_CASE} 정책과 {@code JavaTimeModule} 등록을 동시에 검증.
 *
 * <p>{@link JsonTest}는 Jackson slice를 띄우면서 {@code application.yml}의
 * {@code spring.jackson.property-naming-strategy} 설정을 그대로 반영한다.
 */
@JsonTest
class AuditEventJsonTest {

    @Autowired
    private JacksonTester<AuditEvent> json;

    @Test
    void deserializesAgentStartedFromSpecExample() throws Exception {
        // spec §5.3.1 그대로 (문서의 "..." 자리에 실제 UUID 값으로 대체).
        String payload = """
                {
                  "event_id": "0a5e1d5b-0001-4f1e-b1a1-aaaaaaaaaaaa",
                  "actor":  { "type": "AGENT", "id": "agent-001" },
                  "action": "AGENT_STARTED",
                  "target": { "type": "AGENT", "id": "agent-001" },
                  "result": "SUCCESS",
                  "occurred_at": "2026-05-19T13:55:00Z",
                  "metadata": {
                    "hostname": "demo-host-01",
                    "os": "linux/amd64",
                    "agent_version": "0.1.0",
                    "started_at": "2026-05-19T13:55:00Z"
                  }
                }
                """;

        AuditEvent event = json.parse(payload).getObject();

        assertThat(event.eventId()).isEqualTo("0a5e1d5b-0001-4f1e-b1a1-aaaaaaaaaaaa");
        assertThat(event.action()).isEqualTo(AuditAction.AGENT_STARTED);
        assertThat(event.result()).isEqualTo(EventResult.SUCCESS);
        assertThat(event.occurredAt()).isEqualTo(Instant.parse("2026-05-19T13:55:00Z"));

        assertThat(event.actor()).isEqualTo(new Actor(ActorType.AGENT, "agent-001"));
        assertThat(event.target()).isEqualTo(new Target(TargetType.AGENT, "agent-001"));

        // metadata는 action별 자유 형식이므로 Map의 raw key (snake_case)로 접근한다.
        assertThat(event.metadata())
                .containsEntry("hostname", "demo-host-01")
                .containsEntry("os", "linux/amd64")
                .containsEntry("agent_version", "0.1.0")
                .containsEntry("started_at", "2026-05-19T13:55:00Z");
    }

    @Test
    void deserializesAgentStoppedWithReason() throws Exception {
        // spec §5.3.2.
        String payload = """
                {
                  "event_id": "0a5e1d5b-0002-4f1e-b1a1-bbbbbbbbbbbb",
                  "actor":  { "type": "AGENT", "id": "agent-001" },
                  "action": "AGENT_STOPPED",
                  "target": { "type": "AGENT", "id": "agent-001" },
                  "result": "SUCCESS",
                  "occurred_at": "2026-05-19T18:30:00Z",
                  "metadata": { "reason": "interrupt" }
                }
                """;

        AuditEvent event = json.parse(payload).getObject();

        assertThat(event.action()).isEqualTo(AuditAction.AGENT_STOPPED);
        assertThat(event.metadata()).containsEntry("reason", "interrupt");
    }

    @Test
    void deserializesJobExecutedScriptResult() throws Exception {
        // spec §5.3.3.
        String payload = """
                {
                  "event_id": "0a5e1d5b-0003-4f1e-b1a1-cccccccccccc",
                  "actor":  { "type": "AGENT", "id": "agent-001" },
                  "action": "JOB_EXECUTED",
                  "target": { "type": "SCRIPT", "id": "/opt/scripts/check_disk.sh" },
                  "result": "SUCCESS",
                  "occurred_at": "2026-05-19T14:00:03Z",
                  "metadata": {
                    "execution_id": "8f4b1c9e-0000-0000-0000-000000000000",
                    "schedule_id":  "3a7d2b5f-0000-0000-0000-000000000000",
                    "job_id":       "9c1e8a4d-0000-0000-0000-000000000000",
                    "job_type":     "SCRIPT_JOB",
                    "exit_code":    0
                  }
                }
                """;

        AuditEvent event = json.parse(payload).getObject();

        assertThat(event.action()).isEqualTo(AuditAction.JOB_EXECUTED);
        assertThat(event.target()).isEqualTo(new Target(TargetType.SCRIPT, "/opt/scripts/check_disk.sh"));
        assertThat(event.metadata())
                .containsEntry("job_type", "SCRIPT_JOB")
                .containsEntry("exit_code", 0);
    }
}
