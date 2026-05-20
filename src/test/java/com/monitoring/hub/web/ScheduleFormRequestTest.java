package com.monitoring.hub.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.monitoring.hub.domain.job.JobType;

class ScheduleFormRequestTest {

    @Test
    void scriptSpecBuildsAllFieldsWhenPresent() {
        ScheduleFormRequest form = new ScheduleFormRequest(
                JobType.SCRIPT_JOB,
                "0 0/5 * * * ?",
                "agent-001",
                "/opt/scripts/check_disk.sh",
                "--threshold,80",
                30,
                65536,
                null, null, null
        );

        Map<String, Object> spec = form.toSpec();

        assertThat(spec).containsEntry("script_path", "/opt/scripts/check_disk.sh");
        assertThat(spec).containsEntry("args", List.of("--threshold", "80"));
        assertThat(spec).containsEntry("timeout_seconds", 30);
        assertThat(spec).containsEntry("output_cap_bytes", 65536);
    }

    @Test
    void scriptSpecOmitsBlankFields() {
        ScheduleFormRequest form = new ScheduleFormRequest(
                JobType.SCRIPT_JOB,
                "0 0/5 * * * ?",
                "agent-001",
                "  ", "", null, null,
                null, null, null
        );

        Map<String, Object> spec = form.toSpec();

        assertThat(spec).doesNotContainKeys("script_path", "args", "timeout_seconds", "output_cap_bytes");
        assertThat(spec).isEmpty();
    }

    @Test
    void scriptSpecArgsCommaSplitTrimsAndFiltersEmpty() {
        ScheduleFormRequest form = new ScheduleFormRequest(
                JobType.SCRIPT_JOB,
                "0 0/5 * * * ?",
                "agent-001",
                "/x", " --a , b ,, ,", null, null,
                null, null, null
        );

        Map<String, Object> spec = form.toSpec();

        assertThat(spec).containsEntry("args", List.of("--a", "b"));
    }

    @Test
    void logSpecBuildsAllFieldsWhenPresent() {
        ScheduleFormRequest form = new ScheduleFormRequest(
                JobType.LOG_JOB,
                "0 0/10 * * * ?",
                "agent-002",
                null, null, null, null,
                "/var/log/app/error.log", "ERROR|FATAL", "UTF-8"
        );

        Map<String, Object> spec = form.toSpec();

        assertThat(spec)
                .containsEntry("log_path", "/var/log/app/error.log")
                .containsEntry("pattern", "ERROR|FATAL")
                .containsEntry("encoding", "UTF-8");
    }
}
