package com.monitoring.hub.ingest.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.monitoring.hub.config.AppProperties;
import com.monitoring.hub.domain.audit.Actor;
import com.monitoring.hub.domain.audit.ActorType;
import com.monitoring.hub.domain.audit.AuditAction;
import com.monitoring.hub.domain.audit.AuditEvent;
import com.monitoring.hub.domain.audit.EventResult;
import com.monitoring.hub.domain.audit.Target;
import com.monitoring.hub.domain.audit.TargetType;
import com.monitoring.hub.messaging.EnvelopeHeaders;
import com.monitoring.hub.store.AgentRegistry;
import com.monitoring.hub.store.AuditRingBuffer;

/**
 * AuditConsumer 단위 테스트.
 *
 * <p>envelope §2.3 "알 수 없는 x-source 가드" 적용 후에도 payload가 ring buffer에
 * 정상 적재되고 AGENT_STARTED 시 AgentRegistry.register가 정상 호출됨을 보장한다.
 * 가드는 처리 흐름에 절대 영향을 주지 않는다.
 *
 * <p>검증 항목:
 * <ol>
 *   <li>미지 x-source 헤더가 달린 레코드 → ring buffer 정상 적재</li>
 *   <li>x-source 헤더 부재 레코드 → ring buffer 정상 적재</li>
 *   <li>알려진 x-source 헤더가 달린 레코드 → ring buffer 정상 적재</li>
 *   <li>AGENT_STARTED + 미지 x-source → AgentRegistry.register가 정상 호출됨
 *       (가드가 흐름을 막지 않음)</li>
 * </ol>
 *
 * <p>Spring Context를 띄우지 않는 순수 단위 테스트다 — 실제 인스턴스/mock을 직접 구성.
 */
class AuditConsumerTest {

    private static final String TOPIC = "audit-topic";

    private AuditRingBuffer buffer;
    private AgentRegistry registry;
    private AuditConsumer consumer;

    @BeforeEach
    void setUp() {
        AppProperties props = new AppProperties(
                new AppProperties.Kafka("localhost:9092"),
                new AppProperties.Audit(200),
                new AppProperties.Job(100),
                new AppProperties.Command(50),
                new AppProperties.Agent(30)
        );
        buffer = new AuditRingBuffer(props);
        // AgentRegistry는 mock으로 두어 register 호출 여부 검증 가능.
        registry = mock(AgentRegistry.class);
        consumer = new AuditConsumer(buffer, registry);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 샘플 이벤트 생성 헬퍼
    // ──────────────────────────────────────────────────────────────────────────

    private static AuditEvent agentStartedEvent(String eventId, String agentId) {
        return new AuditEvent(
                eventId,
                new Actor(ActorType.AGENT, agentId),
                AuditAction.AGENT_STARTED,
                new Target(TargetType.AGENT, agentId),
                EventResult.SUCCESS,
                Instant.parse("2026-05-19T13:55:00Z"),
                Map.of("hostname", "demo-host-01", "os", "linux/amd64", "agent_version", "0.1.0")
        );
    }

    private static AuditEvent agentStoppedEvent(String eventId, String agentId) {
        return new AuditEvent(
                eventId,
                new Actor(ActorType.AGENT, agentId),
                AuditAction.AGENT_STOPPED,
                new Target(TargetType.AGENT, agentId),
                EventResult.SUCCESS,
                Instant.parse("2026-05-19T18:30:00Z"),
                Map.of("reason", "interrupt")
        );
    }

    private static AuditEvent jobExecutedEvent(String eventId) {
        return new AuditEvent(
                eventId,
                new Actor(ActorType.AGENT, "agent-test"),
                AuditAction.JOB_EXECUTED,
                new Target(TargetType.SCRIPT, "/opt/scripts/check.sh"),
                EventResult.SUCCESS,
                Instant.parse("2026-05-19T14:00:03Z"),
                Map.of("execution_id", "exec-001")
        );
    }

    // ──────────────────────────────────────────────────────────────────────────
    // x-source 가드 — ring buffer 적재 검증
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * 미지 x-source 헤더가 달린 레코드를 consume해도 ring buffer에 정상 적재된다.
     * 가드가 reject/throw하지 않음을 검증.
     */
    @Test
    void consume_withUnknownXSource_addsToRingBuffer() {
        ConsumerRecord<String, AuditEvent> record = new ConsumerRecord<>(
                TOPIC, 0, 0L, "agent-test", agentStoppedEvent("evt-001", "agent-test"));
        // 미지값 x-source 헤더 부착
        record.headers().add(new RecordHeader(
                EnvelopeHeaders.X_SOURCE,
                "unknown-future-service".getBytes(StandardCharsets.UTF_8)));

        assertThatNoException().isThrownBy(() -> consumer.consume(record));
        assertThat(buffer.size()).isEqualTo(1);
        assertThat(buffer.snapshot().get(0).eventId()).isEqualTo("evt-001");
    }

    /**
     * x-source 헤더가 아예 없는 레코드 — 헤더 부재는 정상이며 ring buffer에 적재된다.
     */
    @Test
    void consume_withNoXSourceHeader_addsToRingBuffer() {
        ConsumerRecord<String, AuditEvent> record = new ConsumerRecord<>(
                TOPIC, 0, 1L, "agent-test", jobExecutedEvent("evt-002"));
        // 헤더를 추가하지 않음

        assertThatNoException().isThrownBy(() -> consumer.consume(record));
        assertThat(buffer.size()).isEqualTo(1);
        assertThat(buffer.snapshot().get(0).eventId()).isEqualTo("evt-002");
    }

    /**
     * 알려진 x-source(script-agent)가 달린 레코드 — 정상 적재되어야 한다.
     */
    @Test
    void consume_withKnownXSource_addsToRingBuffer() {
        ConsumerRecord<String, AuditEvent> record = new ConsumerRecord<>(
                TOPIC, 0, 2L, "agent-test", jobExecutedEvent("evt-003"));
        record.headers().add(new RecordHeader(
                EnvelopeHeaders.X_SOURCE,
                "script-agent".getBytes(StandardCharsets.UTF_8)));

        assertThatNoException().isThrownBy(() -> consumer.consume(record));
        assertThat(buffer.size()).isEqualTo(1);
        assertThat(buffer.snapshot().get(0).eventId()).isEqualTo("evt-003");
    }

    // ──────────────────────────────────────────────────────────────────────────
    // AGENT_STARTED + 미지 x-source → AgentRegistry.register 호출 검증
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * AGENT_STARTED 이벤트에 미지 x-source가 달려 있어도
     * AgentRegistry.register가 정상 호출된다 (가드가 흐름을 막지 않음).
     */
    @Test
    void consume_agentStarted_withUnknownXSource_callsRegistryRegister() {
        AuditEvent event = agentStartedEvent("evt-010", "agent-001");
        ConsumerRecord<String, AuditEvent> record = new ConsumerRecord<>(
                TOPIC, 0, 10L, "agent-001", event);
        record.headers().add(new RecordHeader(
                EnvelopeHeaders.X_SOURCE,
                "unknown-future-service".getBytes(StandardCharsets.UTF_8)));

        consumer.consume(record);

        // ring buffer 적재 확인
        assertThat(buffer.size()).isEqualTo(1);
        // AgentRegistry.register가 호출됐는지 확인 — 가드로 흐름이 막히면 미호출
        verify(registry).register("agent-001", "demo-host-01", "linux/amd64", "0.1.0");
    }

    /**
     * AGENT_STARTED 이벤트에 x-source 헤더 부재 시도 AgentRegistry.register가 정상 호출된다.
     */
    @Test
    void consume_agentStarted_withNoXSourceHeader_callsRegistryRegister() {
        AuditEvent event = agentStartedEvent("evt-011", "agent-002");
        ConsumerRecord<String, AuditEvent> record = new ConsumerRecord<>(
                TOPIC, 0, 11L, "agent-002", event);
        // 헤더 없음

        consumer.consume(record);

        assertThat(buffer.size()).isEqualTo(1);
        verify(registry).register("agent-002", "demo-host-01", "linux/amd64", "0.1.0");
    }

    /**
     * AGENT_STARTED 이벤트에 알려진 x-source가 달려 있어도 동일하게 동작한다.
     */
    @Test
    void consume_agentStarted_withKnownXSource_callsRegistryRegister() {
        AuditEvent event = agentStartedEvent("evt-012", "agent-003");
        ConsumerRecord<String, AuditEvent> record = new ConsumerRecord<>(
                TOPIC, 0, 12L, "agent-003", event);
        record.headers().add(new RecordHeader(
                EnvelopeHeaders.X_SOURCE,
                "script-agent".getBytes(StandardCharsets.UTF_8)));

        consumer.consume(record);

        assertThat(buffer.size()).isEqualTo(1);
        verify(registry).register("agent-003", "demo-host-01", "linux/amd64", "0.1.0");
    }

    /**
     * null payload는 ring buffer에 적재되지 않고 warn 로깅만 한다.
     * 가드와 무관한 방어적 동작도 유지됨 확인.
     */
    @Test
    void consume_withNullPayload_doesNotAddToRingBuffer() {
        ConsumerRecord<String, AuditEvent> record = new ConsumerRecord<>(
                TOPIC, 0, 99L, "agent-test", null);

        assertThatNoException().isThrownBy(() -> consumer.consume(record));
        assertThat(buffer.size()).isEqualTo(0);
    }
}
