package com.monitoring.hub.ingest.heartbeat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.google.protobuf.InvalidProtocolBufferException;
import com.monitoring.hub.ingest.heartbeat.HeartbeatOtlpDecoder.DecodedHeartbeat;

import io.opentelemetry.proto.collector.metrics.v1.ExportMetricsServiceRequest;
import io.opentelemetry.proto.common.v1.AnyValue;
import io.opentelemetry.proto.common.v1.KeyValue;
import io.opentelemetry.proto.metrics.v1.Gauge;
import io.opentelemetry.proto.metrics.v1.Metric;
import io.opentelemetry.proto.metrics.v1.NumberDataPoint;
import io.opentelemetry.proto.metrics.v1.ResourceMetrics;
import io.opentelemetry.proto.metrics.v1.ScopeMetrics;

/**
 * HeartbeatOtlpDecoder 단위 테스트.
 *
 * <p>OTLP proto 빌더로 fixture byte[]를 조립해 디코더의 추출 로직을 검증한다.
 * spec §5.4.2 / ADR #2(B-1) 회귀 방지.
 *
 * <p>핵심 회귀 포인트: time_unix_nano는 JSON 위상에서는 string이었으나, proto 위상에서는
 * fixed64(long 정수)다. 동일 long 값으로 조립 → 동일 Instant가 나와야 한다.
 */
class HeartbeatOtlpDecoderTest {

    /** 테스트 기준 시각: 2025-05-20T07:00:00Z 에 해당하는 nanoseconds */
    private static final long NANOS = 1_747_734_000_000_000_000L;

    /** NANOS를 Instant로 변환한 기대 값 */
    private static final Instant EXPECTED_INSTANT = Instant.ofEpochSecond(1_747_734_000L);

    private static final String AGENT_ID = "agent-001";

    private HeartbeatOtlpDecoder decoder;

    @BeforeEach
    void setUp() {
        decoder = new HeartbeatOtlpDecoder();
    }

    // -----------------------------------------------------------------------
    // 헬퍼: proto fixture 조립
    // -----------------------------------------------------------------------

    /**
     * 단일 agent_id + timeUnixNano를 가진 Gauge dataPoint로 ExportMetricsServiceRequest
     * byte[]를 조립하는 헬퍼.
     */
    private static byte[] buildGaugePayload(String metricName, String agentId, long timeUnixNano) {
        NumberDataPoint dp = NumberDataPoint.newBuilder()
                .addAttributes(KeyValue.newBuilder()
                        .setKey("agent_id")
                        .setValue(AnyValue.newBuilder().setStringValue(agentId).build())
                        .build())
                .setTimeUnixNano(timeUnixNano)
                .setAsDouble(1.0)
                .build();

        Metric metric = Metric.newBuilder()
                .setName(metricName)
                .setGauge(Gauge.newBuilder().addDataPoints(dp).build())
                .build();

        return ExportMetricsServiceRequest.newBuilder()
                .addResourceMetrics(ResourceMetrics.newBuilder()
                        .addScopeMetrics(ScopeMetrics.newBuilder()
                                .addMetrics(metric)
                                .build())
                        .build())
                .build()
                .toByteArray();
    }

    // -----------------------------------------------------------------------
    // 케이스 ①: gauge 정상 파싱 — Instant 변환 회귀 포인트 직접 검증
    // -----------------------------------------------------------------------

    @Test
    void decodesGaugeDataPointAndConvertsTimeUnixNanoToInstant() throws Exception {
        // JSON 위상의 "1747734000000000000" string → proto 위상의 fixed64 long.
        // 동일 long 값 → 동일 Instant가 나와야 한다.
        byte[] payload = buildGaugePayload("agent.heartbeat", AGENT_ID, NANOS);

        List<DecodedHeartbeat> result = decoder.decode(payload);

        assertThat(result).hasSize(1);
        DecodedHeartbeat hb = result.get(0);
        assertThat(hb.agentId()).isEqualTo(AGENT_ID);
        // 핵심 회귀: proto fixed64 → Instant 변환이 정확해야 한다.
        assertThat(hb.lastSeen()).isEqualTo(EXPECTED_INSTANT);
    }

    // -----------------------------------------------------------------------
    // 케이스 ②: unknown metric name 무시
    // -----------------------------------------------------------------------

    @Test
    void ignoresMetricWithUnknownName() throws Exception {
        // Collector가 다른 metric을 함께 보낼 수 있다 — agent.heartbeat 아닌 것은 무시.
        byte[] payload = buildGaugePayload("process.cpu.utilization", AGENT_ID, NANOS);

        List<DecodedHeartbeat> result = decoder.decode(payload);

        assertThat(result).isEmpty();
    }

    // -----------------------------------------------------------------------
    // 케이스 ③: agent_id attribute 없는 dataPoint skip
    // -----------------------------------------------------------------------

    @Test
    void skipsDataPointWithoutAgentIdAttribute() throws Exception {
        // agent_id 대신 service.name만 있는 dataPoint — 건너뛰어야 한다.
        NumberDataPoint dp = NumberDataPoint.newBuilder()
                .addAttributes(KeyValue.newBuilder()
                        .setKey("service.name")
                        .setValue(AnyValue.newBuilder().setStringValue("x").build())
                        .build())
                .setTimeUnixNano(NANOS)
                .build();

        Metric metric = Metric.newBuilder()
                .setName("agent.heartbeat")
                .setGauge(Gauge.newBuilder().addDataPoints(dp).build())
                .build();

        byte[] payload = ExportMetricsServiceRequest.newBuilder()
                .addResourceMetrics(ResourceMetrics.newBuilder()
                        .addScopeMetrics(ScopeMetrics.newBuilder()
                                .addMetrics(metric).build()).build())
                .build().toByteArray();

        List<DecodedHeartbeat> result = decoder.decode(payload);

        assertThat(result).isEmpty();
    }

    // -----------------------------------------------------------------------
    // 케이스 ④: timeUnixNano = 0 인 dataPoint skip (proto "미설정" 규칙)
    // -----------------------------------------------------------------------

    @Test
    void skipsDataPointWithZeroTimeUnixNano() throws Exception {
        // proto fixed64 기본값 0 → JSON 위상의 빈 문자열 skip에 대응하는 규칙.
        NumberDataPoint dp = NumberDataPoint.newBuilder()
                .addAttributes(KeyValue.newBuilder()
                        .setKey("agent_id")
                        .setValue(AnyValue.newBuilder().setStringValue(AGENT_ID).build())
                        .build())
                .setTimeUnixNano(0L) // 미설정
                .build();

        Metric metric = Metric.newBuilder()
                .setName("agent.heartbeat")
                .setGauge(Gauge.newBuilder().addDataPoints(dp).build())
                .build();

        byte[] payload = ExportMetricsServiceRequest.newBuilder()
                .addResourceMetrics(ResourceMetrics.newBuilder()
                        .addScopeMetrics(ScopeMetrics.newBuilder()
                                .addMetrics(metric).build()).build())
                .build().toByteArray();

        List<DecodedHeartbeat> result = decoder.decode(payload);

        assertThat(result).isEmpty();
    }

    // -----------------------------------------------------------------------
    // 케이스 ⑤: 멀티 dataPoint / 멀티 agent 처리
    // -----------------------------------------------------------------------

    @Test
    void handlesMultipleDataPointsAcrossAgents() throws Exception {
        long nanosA = 1_747_734_000_000_000_000L;
        long nanosB = 1_747_734_010_000_000_000L;

        NumberDataPoint dpA = NumberDataPoint.newBuilder()
                .addAttributes(KeyValue.newBuilder()
                        .setKey("agent_id")
                        .setValue(AnyValue.newBuilder().setStringValue("agent-A").build())
                        .build())
                .setTimeUnixNano(nanosA)
                .build();

        NumberDataPoint dpB = NumberDataPoint.newBuilder()
                .addAttributes(KeyValue.newBuilder()
                        .setKey("agent_id")
                        .setValue(AnyValue.newBuilder().setStringValue("agent-B").build())
                        .build())
                .setTimeUnixNano(nanosB)
                .build();

        Metric metric = Metric.newBuilder()
                .setName("agent.heartbeat")
                .setGauge(Gauge.newBuilder()
                        .addDataPoints(dpA)
                        .addDataPoints(dpB)
                        .build())
                .build();

        byte[] payload = ExportMetricsServiceRequest.newBuilder()
                .addResourceMetrics(ResourceMetrics.newBuilder()
                        .addScopeMetrics(ScopeMetrics.newBuilder()
                                .addMetrics(metric).build()).build())
                .build().toByteArray();

        List<DecodedHeartbeat> result = decoder.decode(payload);

        assertThat(result).hasSize(2);
        assertThat(result).extracting(DecodedHeartbeat::agentId)
                .containsExactlyInAnyOrder("agent-A", "agent-B");

        // nanosA/B → Instant 변환이 각각 정확해야 한다.
        assertThat(result).filteredOn(hb -> "agent-A".equals(hb.agentId()))
                .first().extracting(DecodedHeartbeat::lastSeen)
                .isEqualTo(Instant.ofEpochSecond(1_747_734_000L));
        assertThat(result).filteredOn(hb -> "agent-B".equals(hb.agentId()))
                .first().extracting(DecodedHeartbeat::lastSeen)
                .isEqualTo(Instant.ofEpochSecond(1_747_734_010L));
    }

    // -----------------------------------------------------------------------
    // 유효하지 않은 byte[] → InvalidProtocolBufferException 전파
    // -----------------------------------------------------------------------

    @Test
    void throwsOnInvalidProtoBytes() {
        // 디코더는 예외를 삼키지 않고 전파해야 한다(consumer가 WARN 로깅 후 진행).
        byte[] garbage = new byte[]{0x01, 0x02, 0x03};
        assertThatThrownBy(() -> decoder.decode(garbage))
                .isInstanceOf(InvalidProtocolBufferException.class);
    }

    // -----------------------------------------------------------------------
    // 빈 ExportMetricsServiceRequest → 빈 리스트 반환
    // -----------------------------------------------------------------------

    @Test
    void returnsEmptyListForEmptyRequest() throws Exception {
        byte[] payload = ExportMetricsServiceRequest.newBuilder().build().toByteArray();

        List<DecodedHeartbeat> result = decoder.decode(payload);

        assertThat(result).isEmpty();
    }
}
