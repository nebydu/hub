package com.monitoring.hub.domain.job;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.json.JsonTest;
import org.springframework.boot.test.json.JacksonTester;

/**
 * spec §5.2.1 / §5.2.2 페이로드를 Spring Boot의 ObjectMapper로 역직렬화해
 * 글로벌 {@code SNAKE_CASE} 정책과 {@code JavaTimeModule} 등록을 동시에 검증.
 */
@JsonTest
class JobResultJsonTest {

    @Autowired
    private JacksonTester<JobResult> json;

    @Test
    void deserializesScriptResultFromSpecExample() throws Exception {
        // spec §5.2.1 그대로.
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
                    "exit_code":   0,
                    "stdout_cap":  "Disk usage: 42%",
                    "stderr_cap":  "",
                    "truncated":   false
                  },
                  "log": null
                }
                """;

        JobResult result = json.parse(payload).getObject();

        assertThat(result.executionId()).isEqualTo("8f4b1c9e-0000-0000-0000-000000000000");
        assertThat(result.agentId()).isEqualTo("agent-001");
        assertThat(result.jobType()).isEqualTo(JobType.SCRIPT_JOB);
        assertThat(result.status()).isEqualTo(JobStatus.SUCCESS);
        assertThat(result.startedAt()).isEqualTo(Instant.parse("2026-05-19T14:00:01Z"));
        assertThat(result.finishedAt()).isEqualTo(Instant.parse("2026-05-19T14:00:03Z"));

        assertThat(result.log()).isNull();
        assertThat(result.script()).isNotNull();
        assertThat(result.script().exitCode()).isZero();
        assertThat(result.script().stdoutCap()).isEqualTo("Disk usage: 42%");
        assertThat(result.script().stderrCap()).isEmpty();
        assertThat(result.script().truncated()).isFalse();
    }

    @Test
    void deserializesLogResultFromSpecExample() throws Exception {
        // spec §5.2.2 그대로.
        String payload = """
                {
                  "execution_id": "8f4b1c9e-0000-0000-0000-000000000000",
                  "schedule_id":  "3a7d2b5f-0000-0000-0000-000000000000",
                  "job_id":       "9c1e8a4d-0000-0000-0000-000000000000",
                  "agent_id":     "agent-001",
                  "job_type":     "LOG_JOB",
                  "status":       "SUCCESS",
                  "started_at":   "2026-05-19T14:00:01Z",
                  "finished_at":  "2026-05-19T14:00:02Z",
                  "script":       null,
                  "log": {
                    "matched_lines_count": 3,
                    "sample_lines": [
                      "[2026-05-19 13:59:42] ERROR Failed to connect to DB",
                      "[2026-05-19 13:59:55] ERROR Retry exceeded"
                    ]
                  }
                }
                """;

        JobResult result = json.parse(payload).getObject();

        assertThat(result.jobType()).isEqualTo(JobType.LOG_JOB);
        assertThat(result.script()).isNull();
        assertThat(result.log()).isNotNull();
        assertThat(result.log().matchedLinesCount()).isEqualTo(3);
        assertThat(result.log().sampleLines()).hasSize(2)
                .first().asString().contains("ERROR Failed to connect to DB");
    }

    @Test
    void deserializesTimeoutStatusForScriptJob() throws Exception {
        // spec §5.2.3 — TIMEOUT은 Agent가 timeout 초과 시 발행. exit_code는 Agent의 선택.
        String payload = """
                {
                  "execution_id": "8f4b1c9e-0000-0000-0000-000000000000",
                  "schedule_id":  "3a7d2b5f-0000-0000-0000-000000000000",
                  "job_id":       "9c1e8a4d-0000-0000-0000-000000000000",
                  "agent_id":     "agent-001",
                  "job_type":     "SCRIPT_JOB",
                  "status":       "TIMEOUT",
                  "started_at":   "2026-05-19T14:00:01Z",
                  "finished_at":  "2026-05-19T14:00:31Z",
                  "script": {
                    "exit_code":   -1,
                    "stdout_cap":  "",
                    "stderr_cap":  "killed: timeout",
                    "truncated":   false
                  },
                  "log": null
                }
                """;

        JobResult result = json.parse(payload).getObject();

        assertThat(result.status()).isEqualTo(JobStatus.TIMEOUT);
        assertThat(result.script().exitCode()).isEqualTo(-1);
    }
}
