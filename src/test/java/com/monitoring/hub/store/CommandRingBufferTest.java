package com.monitoring.hub.store;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.monitoring.hub.config.AppProperties;
import com.monitoring.hub.domain.command.Command;
import com.monitoring.hub.domain.job.JobType;

class CommandRingBufferTest {

    private static CommandRingBuffer newBuffer(int capacity) {
        AppProperties props = new AppProperties(
                new AppProperties.Kafka("localhost:9092"),
                new AppProperties.Audit(200),
                new AppProperties.Job(100),
                new AppProperties.Command(capacity),
                new AppProperties.Agent(30)
        );
        return new CommandRingBuffer(props);
    }

    private static Command dummy(String executionId) {
        return new Command(
                executionId, "schedule-1", "job-1", "agent-001",
                JobType.SCRIPT_JOB,
                Instant.parse("2026-05-19T14:00:00Z"),
                Instant.parse("2026-05-19T14:04:30Z"),
                Map.of("script_path", "/x"));
    }

    @Test
    void evictsOldestWhenOverCapacity() {
        CommandRingBuffer buffer = newBuffer(3);
        buffer.add(dummy("e1"));
        buffer.add(dummy("e2"));
        buffer.add(dummy("e3"));
        buffer.add(dummy("e4"));

        List<Command> snapshot = buffer.snapshot();
        assertThat(snapshot).extracting(Command::executionId)
                .containsExactly("e2", "e3", "e4");
    }
}
