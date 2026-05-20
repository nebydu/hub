package com.monitoring.hub.store;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.monitoring.hub.config.AppProperties;
import com.monitoring.hub.domain.audit.Actor;
import com.monitoring.hub.domain.audit.ActorType;
import com.monitoring.hub.domain.audit.AuditAction;
import com.monitoring.hub.domain.audit.AuditEvent;
import com.monitoring.hub.domain.audit.EventResult;
import com.monitoring.hub.domain.audit.Target;
import com.monitoring.hub.domain.audit.TargetType;

class AuditRingBufferTest {

    private static AuditRingBuffer newBuffer(int capacity) {
        AppProperties props = new AppProperties(
                new AppProperties.Kafka("localhost:9092"),
                new AppProperties.Audit(capacity),
                new AppProperties.Job(100),
                new AppProperties.Command(50),
                new AppProperties.Agent(30)
        );
        return new AuditRingBuffer(props);
    }

    private static AuditEvent dummyEvent(String eventId) {
        return new AuditEvent(
                eventId,
                new Actor(ActorType.AGENT, "agent-test"),
                AuditAction.AGENT_STARTED,
                new Target(TargetType.AGENT, "agent-test"),
                EventResult.SUCCESS,
                Instant.parse("2026-05-19T13:55:00Z"),
                Map.of("hostname", "h", "os", "linux/amd64", "agent_version", "0.1.0")
        );
    }

    @Test
    void evictsOldestWhenOverCapacity() {
        AuditRingBuffer buffer = newBuffer(3);
        buffer.add(dummyEvent("e1"));
        buffer.add(dummyEvent("e2"));
        buffer.add(dummyEvent("e3"));
        buffer.add(dummyEvent("e4"));

        List<AuditEvent> snapshot = buffer.snapshot();
        assertThat(snapshot).hasSize(3);
        assertThat(snapshot).extracting(AuditEvent::eventId)
                .containsExactly("e2", "e3", "e4");
        assertThat(buffer.size()).isEqualTo(3);
        assertThat(buffer.capacity()).isEqualTo(3);
    }

    @Test
    void snapshotIsImmutable() {
        AuditRingBuffer buffer = newBuffer(2);
        buffer.add(dummyEvent("e1"));

        List<AuditEvent> snapshot = buffer.snapshot();
        assertThatThrownBy(() -> snapshot.add(dummyEvent("e2")))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void snapshotIsDecoupledFromSubsequentAdds() {
        AuditRingBuffer buffer = newBuffer(5);
        buffer.add(dummyEvent("e1"));
        List<AuditEvent> snapshot = buffer.snapshot();

        buffer.add(dummyEvent("e2"));

        // 스냅샷은 호출 시점의 상태를 유지해야 한다.
        assertThat(snapshot).hasSize(1);
        assertThat(buffer.size()).isEqualTo(2);
    }
}
