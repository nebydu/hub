package com.monitoring.hub.domain.command;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.json.JsonTest;
import org.springframework.boot.test.json.JacksonTester;

import com.monitoring.hub.domain.job.JobType;

/**
 * spec §5.1.1 / §5.1.2 commands 페이로드를 Spring Boot의 ObjectMapper로 직렬화해
 * 글로벌 SNAKE_CASE + JavaTimeModule 정책 적용을 검증.
 *
 * <p>Agent(Go) 측이 그대로 디코딩할 수 있는 JSON이 나오는지 보는 게 핵심.
 */
@JsonTest
class CommandJsonTest {

    @Autowired
    private JacksonTester<Command> json;

    @Test
    void serializesScriptCommandPerSpec() throws Exception {
        Command command = new Command(
                "8f4b1c9e-0000-0000-0000-000000000000",
                "3a7d2b5f-0000-0000-0000-000000000000",
                "9c1e8a4d-0000-0000-0000-000000000000",
                "agent-001",
                JobType.SCRIPT_JOB,
                Instant.parse("2026-05-19T14:00:00Z"),
                Instant.parse("2026-05-19T14:04:30Z"),
                Map.of(
                        "script_path", "/opt/scripts/check_disk.sh",
                        "args", List.of("--threshold", "80"),
                        "timeout_seconds", 30,
                        "output_cap_bytes", 65536
                )
        );

        String out = json.write(command).getJson();

        assertThat(out)
                .contains("\"execution_id\":\"8f4b1c9e-0000-0000-0000-000000000000\"")
                .contains("\"target_agent_id\":\"agent-001\"")
                .contains("\"job_type\":\"SCRIPT_JOB\"")
                .contains("\"issued_at\":\"2026-05-19T14:00:00Z\"")
                .contains("\"valid_until\":\"2026-05-19T14:04:30Z\"")
                .contains("\"script_path\":\"/opt/scripts/check_disk.sh\"");
    }

    @Test
    void serializesLogCommandPerSpec() throws Exception {
        Command command = new Command(
                "8f4b1c9e-0000-0000-0000-000000000000",
                "3a7d2b5f-0000-0000-0000-000000000000",
                "9c1e8a4d-0000-0000-0000-000000000000",
                "agent-001",
                JobType.LOG_JOB,
                Instant.parse("2026-05-19T14:00:00Z"),
                Instant.parse("2026-05-19T14:04:30Z"),
                Map.of(
                        "log_path", "/var/log/app/error.log",
                        "pattern", "ERROR|FATAL",
                        "encoding", "UTF-8"
                )
        );

        String out = json.write(command).getJson();

        assertThat(out)
                .contains("\"job_type\":\"LOG_JOB\"")
                .contains("\"log_path\":\"/var/log/app/error.log\"")
                .contains("\"pattern\":\"ERROR|FATAL\"")
                .contains("\"encoding\":\"UTF-8\"");
    }
}
