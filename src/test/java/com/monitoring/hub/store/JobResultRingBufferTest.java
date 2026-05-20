package com.monitoring.hub.store;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.monitoring.hub.config.AppProperties;
import com.monitoring.hub.domain.job.JobResult;
import com.monitoring.hub.domain.job.JobStatus;
import com.monitoring.hub.domain.job.JobType;
import com.monitoring.hub.domain.job.ScriptResult;

class JobResultRingBufferTest {

    private static JobResultRingBuffer newBuffer(int capacity) {
        AppProperties props = new AppProperties(
                new AppProperties.Kafka("localhost:9092"),
                new AppProperties.Audit(200),
                new AppProperties.Job(capacity),
                new AppProperties.Command(50),
                new AppProperties.Agent(30)
        );
        return new JobResultRingBuffer(props);
    }

    private static JobResult dummyResult(String executionId) {
        return new JobResult(
                executionId,
                "schedule-1",
                "job-1",
                "agent-test",
                JobType.SCRIPT_JOB,
                JobStatus.SUCCESS,
                Instant.parse("2026-05-19T14:00:01Z"),
                Instant.parse("2026-05-19T14:00:03Z"),
                new ScriptResult(0, "ok", "", false),
                null
        );
    }

    @Test
    void evictsOldestWhenOverCapacity() {
        JobResultRingBuffer buffer = newBuffer(3);
        buffer.add(dummyResult("e1"));
        buffer.add(dummyResult("e2"));
        buffer.add(dummyResult("e3"));
        buffer.add(dummyResult("e4"));

        List<JobResult> snapshot = buffer.snapshot();
        assertThat(snapshot).hasSize(3);
        assertThat(snapshot).extracting(JobResult::executionId)
                .containsExactly("e2", "e3", "e4");
        assertThat(buffer.size()).isEqualTo(3);
        assertThat(buffer.capacity()).isEqualTo(3);
    }

    @Test
    void snapshotIsImmutable() {
        JobResultRingBuffer buffer = newBuffer(2);
        buffer.add(dummyResult("e1"));

        List<JobResult> snapshot = buffer.snapshot();
        assertThatThrownBy(() -> snapshot.add(dummyResult("e2")))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
