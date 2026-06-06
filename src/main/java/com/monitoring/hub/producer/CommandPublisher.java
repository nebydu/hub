package com.monitoring.hub.producer;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import com.monitoring.hub.config.KafkaConfig;
import com.monitoring.hub.domain.command.Command;
import com.monitoring.hub.messaging.EnvelopeHeaders;
import com.monitoring.hub.store.CommandRingBuffer;

/**
 * command-topic publisher. spec §5.1.
 *
 * <p>책임:
 * <ul>
 *   <li>spec §2.2 envelope 헤더 4종(x-message-id / x-message-version / x-source /
 *       x-trace-id) 첨부. x-trace-id는 데모 단계에서 발행만, 검사 없음 (ADR #15).</li>
 *   <li>spec §2.3 메시지 키 = {@code target_agent_id} (Agent 단위 ordering 보장).</li>
 *   <li>{@link CommandRingBuffer}에 발행 이력 적재 — UI 노출용.</li>
 * </ul>
 *
 * <p>send는 fire-and-forget: 결과 콜백에서 성공/실패 로깅만. send 자체 예외는
 * Quartz Job이 그대로 잡아 다음 트리거에 영향 주지 않도록 한다.
 */
@Component
public class CommandPublisher {

    private static final Logger log = LoggerFactory.getLogger(CommandPublisher.class);

    /** spec §2.2 — payload 스키마 버전. 데모는 {@code 1} 고정. */
    private static final String MESSAGE_VERSION = "1";
    /** spec §2.2 — 발행자 식별자. BE는 monitoring-be로 고정. */
    private static final String SOURCE = "monitoring-be";

    // 헤더 키 문자열의 단일 진실 지점은 EnvelopeHeaders. 여기서는 기존 참조
    // (CommandPublisherTest 포함) 호환을 위해 별칭으로 위임만 한다 — 값 불변.
    static final String HEADER_MESSAGE_ID = EnvelopeHeaders.X_MESSAGE_ID;
    static final String HEADER_MESSAGE_VERSION = EnvelopeHeaders.X_MESSAGE_VERSION;
    static final String HEADER_SOURCE = EnvelopeHeaders.X_SOURCE;
    static final String HEADER_TRACE_ID = EnvelopeHeaders.X_TRACE_ID;

    private final KafkaTemplate<String, Command> kafkaTemplate;
    private final CommandRingBuffer ringBuffer;

    public CommandPublisher(
            KafkaTemplate<String, Command> commandKafkaTemplate,
            CommandRingBuffer ringBuffer) {
        this.kafkaTemplate = commandKafkaTemplate;
        this.ringBuffer = ringBuffer;
    }

    /**
     * {@link Command}를 command-topic에 발행한다.
     *
     * <p>{@code traceId}는 OTel 통합 시 propagation을 위한 자리 — null이면 헤더
     * 추가를 생략한다. 데모는 호출 측에서 null로 둔다.
     */
    public void publish(Command command, String traceIdOrNull) {
        String key = command.targetAgentId();
        ProducerRecord<String, Command> record = new ProducerRecord<>(
                KafkaConfig.Topics.COMMANDS, key, command);

        String messageId = UUID.randomUUID().toString();
        addHeader(record, HEADER_MESSAGE_ID, messageId);
        addHeader(record, HEADER_MESSAGE_VERSION, MESSAGE_VERSION);
        addHeader(record, HEADER_SOURCE, SOURCE);
        if (traceIdOrNull != null && !traceIdOrNull.isBlank()) {
            addHeader(record, HEADER_TRACE_ID, traceIdOrNull);
        }

        ringBuffer.add(command);

        kafkaTemplate.send(record).whenComplete((result, ex) -> {
            if (ex != null) {
                log.warn("failed to send command: execution_id={} target_agent={}",
                        command.executionId(), command.targetAgentId(), ex);
            } else {
                log.info(
                        "COMMAND sent: execution_id={} target_agent={} job_type={} "
                                + "valid_until={} message_id={} partition={} offset={}",
                        command.executionId(), command.targetAgentId(), command.jobType(),
                        command.validUntil(), messageId,
                        result.getRecordMetadata().partition(),
                        result.getRecordMetadata().offset());
            }
        });
    }

    private static void addHeader(ProducerRecord<String, Command> record, String name, String value) {
        record.headers().add(new RecordHeader(name, value.getBytes(StandardCharsets.UTF_8)));
    }
}
