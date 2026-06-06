package com.monitoring.hub.ingest.audit;

import java.util.Map;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import com.monitoring.hub.config.KafkaConfig;
import com.monitoring.hub.domain.audit.AuditEvent;
import com.monitoring.hub.messaging.EnvelopeHeaders;
import com.monitoring.hub.store.AgentRegistry;
import com.monitoring.hub.store.AuditRingBuffer;

/**
 * audit-topic consumer. spec §5.3 / §3.2.
 *
 * <p>책임:
 * <ul>
 *   <li>수신한 모든 audit event를 {@link AuditRingBuffer}에 적재</li>
 *   <li>{@code AGENT_STARTED} → {@link AgentRegistry#register} 호출 (등록 역할 겸함)</li>
 *   <li>{@code AGENT_STOPPED} → {@link AgentRegistry#markOffline} 호출</li>
 *   <li>{@code JOB_EXECUTED} → ring buffer 적재만 (Job/Schedule 매칭은 다음 단계)</li>
 * </ul>
 *
 * <p>spec §2.2 envelope 헤더 검사는 데모 단계 범위 외 (ADR #15).
 * 깨진 메시지가 와도 전체 처리가 막히지 않도록 단일 메시지 처리에서 발생하는
 * 예외는 잡아 WARN 로깅만 하고 다음 메시지로 진행 — 본격 dead-letter 정책은
 * 본개발 영역.
 */
@Component
public class AuditConsumer {

    private static final Logger log = LoggerFactory.getLogger(AuditConsumer.class);

    private final AuditRingBuffer buffer;
    private final AgentRegistry registry;

    public AuditConsumer(AuditRingBuffer buffer, AgentRegistry registry) {
        this.buffer = buffer;
        this.registry = registry;
    }

    @KafkaListener(
            topics = KafkaConfig.Topics.AUDIT_EVENTS,
            containerFactory = "auditEventListenerFactory"
    )
    public void consume(ConsumerRecord<String, AuditEvent> record) {
        // envelope §2.3 x-source 가드: 헤더를 읽되 부재/미지값이어도 처리는 그대로
        // 계속한다(미지값에 안 깨지는 것이 의도된 동작). 미지값일 때만 debug 로깅.
        EnvelopeHeaders.inspectSource(record.headers(), KafkaConfig.Topics.AUDIT_EVENTS);

        AuditEvent event = record.value();
        if (event == null) {
            log.warn("skipping null audit event payload at offset={}", record.offset());
            return;
        }
        try {
            buffer.add(event);
            switch (event.action()) {
                case AGENT_STARTED -> handleAgentStarted(event);
                case AGENT_STOPPED -> handleAgentStopped(event);
                case JOB_EXECUTED -> log.debug("JOB_EXECUTED received: event_id={}", event.eventId());
            }
        } catch (RuntimeException ex) {
            log.warn("failed to handle audit event: event_id={} action={}",
                    event.eventId(), event.action(), ex);
        }
    }

    private void handleAgentStarted(AuditEvent event) {
        String agentId = event.actor().id();
        Map<String, Object> md = event.metadata();
        String hostname = stringOrNull(md, "hostname");
        String os = stringOrNull(md, "os");
        String agentVersion = stringOrNull(md, "agent_version");

        registry.register(agentId, hostname, os, agentVersion);

        log.info("AGENT_STARTED received: agent_id={} hostname={} os={} agent_version={}",
                agentId, hostname, os, agentVersion);
    }

    private void handleAgentStopped(AuditEvent event) {
        String agentId = event.actor().id();
        String reason = stringOrNull(event.metadata(), "reason");

        registry.markOffline(agentId);

        log.info("AGENT_STOPPED received: agent_id={} reason={}", agentId, reason);
    }

    private static String stringOrNull(Map<String, Object> map, String key) {
        if (map == null) {
            return null;
        }
        Object v = map.get(key);
        return v instanceof String s ? s : null;
    }
}
