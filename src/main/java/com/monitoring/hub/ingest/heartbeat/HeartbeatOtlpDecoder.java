package com.monitoring.hub.ingest.heartbeat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import io.opentelemetry.proto.collector.metrics.v1.ExportMetricsServiceRequest;
import io.opentelemetry.proto.common.v1.KeyValue;
import io.opentelemetry.proto.metrics.v1.Metric;
import io.opentelemetry.proto.metrics.v1.NumberDataPoint;
import io.opentelemetry.proto.metrics.v1.ResourceMetrics;
import io.opentelemetry.proto.metrics.v1.ScopeMetrics;

/**
 * heartbeats-topic의 OTLP MetricsData protobuf(byte[])를 디코드해 도메인 추출값
 * ({@code agent_id}, last_seen)을 뽑아내는 순수 변환기. spec §5.4 / ADR #2(B-1).
 *
 * <p>OTel Java 표준 라이브러리({@code io.opentelemetry.proto})에 내장된 proto 바인딩을
 * 사용하며, 자체 .proto·protoc 생성물은 두지 않는다. 진입점은
 * {@link ExportMetricsServiceRequest#parseFrom(byte[])}이다 — OTel Collector kafka
 * exporter가 {@code encoding: otlp_proto}로 발행하는 메트릭 wire는 이 컨테이너 타입이다.
 *
 * <p>추출 경로(정본 kafka-payloads.md {@code heartbeats-topic} / spec §5.4.2):
 * <ul>
 *   <li>{@code resource_metrics[] → scope_metrics[] → metrics[]}</li>
 *   <li>metric name이 {@code agent.heartbeat}인 항목만 처리</li>
 *   <li>Gauge(정본) dataPoints를 순회 — 송신 측 변경에도 견디도록 Sum도 후보로 본다</li>
 *   <li>각 dataPoint의 attribute에서 {@code agent_id}의 string value 추출</li>
 *   <li>dataPoint의 {@code time_unix_nano}(proto fixed64, long)를 {@link Instant}로 변환</li>
 * </ul>
 *
 * <p>JSON 위상과 달리 {@code time_unix_nano}는 proto에서 fixed64(정수)이므로 문자열
 * 파싱 단계({@code Long.parseLong}) 없이 long 값을 그대로 쓴다 — 디코더 변경의 핵심 지점.
 *
 * <p>이 클래스는 Kafka/Spring 의존 없이 byte[] → 도메인 추출값 변환만 책임진다.
 * 추후 {@code shared-libs/otel} wrapper 모듈로 추출될 수 있도록 결합도를 낮춰 둔 것이다
 * (단 본 작업에서는 새 Maven 모듈을 신설하지 않고 hub 내부 클래스로만 둔다).
 */
@Component
public final class HeartbeatOtlpDecoder {

    /** 정본이 규정한 heartbeat metric name (spec §5.4.1). */
    static final String METRIC_NAME = "agent.heartbeat";

    /** heartbeat dataPoint attribute 키 (spec §5.4.2). */
    static final String ATTR_AGENT_ID = "agent_id";

    /**
     * OTLP protobuf wire(byte[])를 디코드해 {@code agent.heartbeat} dataPoint별
     * 추출값을 반환한다. agent_id 또는 time_unix_nano가 없는 dataPoint는 건너뛴다.
     *
     * @param payload OTLP {@link ExportMetricsServiceRequest} protobuf wire bytes
     * @return 추출된 heartbeat 목록 (없으면 빈 리스트)
     * @throws com.google.protobuf.InvalidProtocolBufferException wire가 OTLP proto로
     *         디코드되지 않을 때 — 호출 측이 WARN 로깅 후 진행한다(현 정책 보존).
     */
    public List<DecodedHeartbeat> decode(byte[] payload)
            throws com.google.protobuf.InvalidProtocolBufferException {
        ExportMetricsServiceRequest request = ExportMetricsServiceRequest.parseFrom(payload);

        List<DecodedHeartbeat> result = new ArrayList<>();
        for (ResourceMetrics rm : request.getResourceMetricsList()) {
            for (ScopeMetrics sm : rm.getScopeMetricsList()) {
                for (Metric metric : sm.getMetricsList()) {
                    if (!METRIC_NAME.equals(metric.getName())) {
                        continue;
                    }
                    collectFromMetric(metric, result);
                }
            }
        }
        return result;
    }

    /**
     * 단일 metric의 dataPoints를 순회. 정본은 Gauge로 정의하지만 송신 측 변경에도
     * 깨지지 않도록 Gauge/Sum 후보를 모두 본다.
     */
    private void collectFromMetric(Metric metric, List<DecodedHeartbeat> sink) {
        List<NumberDataPoint> dataPoints = new ArrayList<>();
        if (metric.hasGauge()) {
            dataPoints.addAll(metric.getGauge().getDataPointsList());
        }
        if (metric.hasSum()) {
            dataPoints.addAll(metric.getSum().getDataPointsList());
        }
        for (NumberDataPoint dp : dataPoints) {
            DecodedHeartbeat hb = toHeartbeat(dp);
            if (hb != null) {
                sink.add(hb);
            }
        }
    }

    /**
     * dataPoint에서 agent_id와 time_unix_nano를 추출한다. 둘 중 하나라도 없으면
     * {@code null}을 반환해 호출 측이 건너뛰게 한다.
     */
    private DecodedHeartbeat toHeartbeat(NumberDataPoint dp) {
        String agentId = extractAgentId(dp);
        if (agentId == null) {
            return null;
        }
        // proto fixed64: 0은 "미설정"으로 간주하고 건너뛴다(JSON 위상의 빈 문자열 skip 대응).
        long timeUnixNano = dp.getTimeUnixNano();
        if (timeUnixNano == 0L) {
            return null;
        }
        Instant lastSeen = Instant.ofEpochSecond(
                timeUnixNano / 1_000_000_000L,
                timeUnixNano % 1_000_000_000L);
        return new DecodedHeartbeat(agentId, lastSeen);
    }

    private static String extractAgentId(NumberDataPoint dp) {
        for (KeyValue attr : dp.getAttributesList()) {
            if (ATTR_AGENT_ID.equals(attr.getKey())) {
                String value = attr.getValue().getStringValue();
                if (value != null && !value.isBlank()) {
                    return value;
                }
            }
        }
        return null;
    }

    /**
     * 디코드된 단일 heartbeat 추출값. Kafka/proto 타입에 의존하지 않는 도메인 경계값이다.
     *
     * @param agentId  dataPoint attribute에서 추출한 agent_id
     * @param lastSeen {@code time_unix_nano}(fixed64)를 변환한 last_seen
     */
    public record DecodedHeartbeat(String agentId, Instant lastSeen) {
    }
}
