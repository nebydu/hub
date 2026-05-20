package com.monitoring.hub.store;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;

import com.monitoring.hub.domain.job.JobDefinition;
import com.monitoring.hub.domain.job.JobType;
import com.monitoring.hub.domain.job.ScheduleDefinition;

/**
 * spec §4.3 — Map&lt;JobId, JobDefinition&gt; / Map&lt;ScheduleId, ScheduleDefinition&gt;
 * 기본 CRUD 동작.
 */
class JobAndScheduleRegistryTest {

    @Test
    void jobRegistryRoundTrip() {
        JobRegistry registry = new JobRegistry();
        JobDefinition job = new JobDefinition(
                "job-1", JobType.SCRIPT_JOB, Map.of("script_path", "/x"));

        registry.register(job);

        assertThat(registry.size()).isEqualTo(1);
        assertThat(registry.find("job-1")).hasValue(job);
        assertThat(registry.findAll()).containsExactly(job);
    }

    @Test
    void scheduleRegistryRoundTrip() {
        ScheduleRegistry registry = new ScheduleRegistry();
        ScheduleDefinition schedule = new ScheduleDefinition(
                "schedule-1", "job-1", "agent-001", "0 0/5 * * * ?", true);

        registry.register(schedule);

        assertThat(registry.size()).isEqualTo(1);
        assertThat(registry.find("schedule-1")).hasValue(schedule);
    }

    @Test
    void reregisterOverwrites() {
        JobRegistry registry = new JobRegistry();
        JobDefinition first = new JobDefinition("job-1", JobType.SCRIPT_JOB, Map.of("v", 1));
        JobDefinition second = new JobDefinition("job-1", JobType.SCRIPT_JOB, Map.of("v", 2));

        registry.register(first);
        registry.register(second);

        assertThat(registry.find("job-1")).hasValueSatisfying(
                job -> assertThat(job.spec()).containsEntry("v", 2));
    }
}
