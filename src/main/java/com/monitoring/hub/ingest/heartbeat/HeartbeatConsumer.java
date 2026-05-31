package com.monitoring.hub.ingest.heartbeat;

import java.util.List;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import com.monitoring.hub.config.KafkaConfig;
import com.monitoring.hub.domain.heartbeat.HeartbeatState;
import com.monitoring.hub.ingest.heartbeat.HeartbeatOtlpDecoder.DecodedHeartbeat;
import com.monitoring.hub.store.AgentRegistry;
import com.monitoring.hub.store.HeartbeatLatestMap;

/**
 * heartbeats 토픽 consumer. spec §5.4 / ADR #2.
 *
 * <p>OTel Collector가 {@code otlp_proto} exporter로 발행한 OTLP MetricsData
 * protobuf(byte[])를 {@link HeartbeatOtlpDecoder}로 디코드해 agent_id와 last_seen을
 * 추출하고, 매 heartbeat마다 last_seen을 갱신한다. byte[] → 도메인 추출 로직은
 * 디코더에 분리되어 있고, 이 consumer는 Kafka 수신/예외 정책/스토어 반영만 책임진다.
 *
 * <p>매 heartbeat마다 {@link HeartbeatLatestMap}을 upsert하고 동시에
 * {@link AgentRegistry#updateLastSeen(String)}을 호출 — 데모 단계에서는 Agent
 * 목록 맵의 last_seen이 heartbeat에서도 갱신되어야 화면에서 한 곳을 보고
 * 살아있음을 판단할 수 있다 (spec §3.2 두 번째 단락).
 *
 * <p>OTLP 표준 메시지(envelope 예외군)이므로 spec §2.2의 4종 헤더 검사는 적용하지
 * 않는다. null/빈 payload는 skip, 디코드 예외 시 WARN 로깅 후 다음 메시지로 진행한다.
 */
@Component
public class HeartbeatConsumer {

    private static final Logger log = LoggerFactory.getLogger(HeartbeatConsumer.class);

    private final HeartbeatOtlpDecoder decoder;
    private final HeartbeatLatestMap latestMap;
    private final AgentRegistry registry;

    public HeartbeatConsumer(
            HeartbeatOtlpDecoder decoder,
            HeartbeatLatestMap latestMap,
            AgentRegistry registry) {
        this.decoder = decoder;
        this.latestMap = latestMap;
        this.registry = registry;
    }

    @KafkaListener(
            topics = KafkaConfig.Topics.HEARTBEATS,
            containerFactory = "heartbeatListenerFactory",
            groupId = "hub-heartbeat-consumer"
    )
    public void consume(ConsumerRecord<String, byte[]> record) {
        byte[] payload = record.value();
        if (payload == null || payload.length == 0) {
            log.warn("skipping empty heartbeat payload at offset={}", record.offset());
            return;
        }
        try {
            int applied = applyAll(decoder.decode(payload));
            if (applied == 0) {
                log.debug("heartbeat payload contained no agent.heartbeat data points");
            }
        } catch (Exception ex) {
            log.warn("failed to decode heartbeat payload at offset={}", record.offset(), ex);
        }
    }

    /**
     * 디코드된 heartbeat들을 latest map / registry에 반영한다. 반영한 dataPoint 수를
     * 반환 (테스트와 진단용).
     */
    int applyAll(List<DecodedHeartbeat> heartbeats) {
        int applied = 0;
        for (DecodedHeartbeat hb : heartbeats) {
            latestMap.upsert(new HeartbeatState(hb.agentId(), hb.lastSeen()));
            registry.updateLastSeen(hb.agentId());
            applied++;
        }
        return applied;
    }
}
