package com.monitoring.hub.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

import java.nio.charset.StandardCharsets;

import org.apache.kafka.common.header.internals.RecordHeaders;
import org.junit.jupiter.api.Test;

/**
 * EnvelopeHeaders 단위 테스트.
 *
 * <p>검증 항목:
 * <ul>
 *   <li>{@code isKnownSource}: 알려진 값 / 미지값 / null 각각의 반환 결과</li>
 *   <li>{@code inspectSource}: null headers, 헤더 부재, 미지값 — 예외 없이 통과</li>
 *   <li>헤더 키 상수 값 불변 검사(spec §2.2 1바이트도 다르면 회귀)</li>
 * </ul>
 */
class EnvelopeHeadersTest {

    // ──────────────────────────────────────────────────────────────────────────
    // 헤더 키 상수 값 검증 (spec §2.2 — 1바이트도 달라지면 안 됨)
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    void headerKeyConstantsAreUnchanged() {
        // spec §2.2 명세와 1바이트도 일치해야 한다. 값이 바뀌면 consumer/producer 간 계약이 깨진다.
        assertThat(EnvelopeHeaders.X_MESSAGE_ID).isEqualTo("x-message-id");
        assertThat(EnvelopeHeaders.X_MESSAGE_VERSION).isEqualTo("x-message-version");
        assertThat(EnvelopeHeaders.X_SOURCE).isEqualTo("x-source");
        assertThat(EnvelopeHeaders.X_TRACE_ID).isEqualTo("x-trace-id");
    }

    // ──────────────────────────────────────────────────────────────────────────
    // isKnownSource 검증
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    void isKnownSource_returnsTrue_forScriptAgent() {
        // 알려진 x-source 값은 true를 반환해야 한다.
        assertThat(EnvelopeHeaders.isKnownSource("script-agent")).isTrue();
    }

    @Test
    void isKnownSource_returnsTrue_forMonitoringBe() {
        assertThat(EnvelopeHeaders.isKnownSource("monitoring-be")).isTrue();
    }

    @Test
    void isKnownSource_returnsFalse_forUnknownSource() {
        // 미지값은 false를 반환해야 한다 — 처리를 거부하지는 않는다(판단만).
        assertThat(EnvelopeHeaders.isKnownSource("unknown-future-service")).isFalse();
    }

    @Test
    void isKnownSource_returnsFalse_forNull() {
        // null은 false를 반환해야 하며 NPE를 던지지 않는다.
        assertThat(EnvelopeHeaders.isKnownSource(null)).isFalse();
    }

    @Test
    void isKnownSource_returnsFalse_forEmptyString() {
        assertThat(EnvelopeHeaders.isKnownSource("")).isFalse();
    }

    // ──────────────────────────────────────────────────────────────────────────
    // inspectSource 검증 — 예외 없이 통과(non-throw guarantee)
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    void inspectSource_doesNotThrow_whenHeadersIsNull() {
        // null Headers 전달 — NPE 없이 조용히 반환.
        assertThatNoException().isThrownBy(
                () -> EnvelopeHeaders.inspectSource(null, "test-context"));
    }

    @Test
    void inspectSource_doesNotThrow_whenXSourceHeaderAbsent() {
        // x-source 헤더가 아예 없는 경우 — 정상 처리(헤더 부재는 정상).
        RecordHeaders headers = new RecordHeaders();
        assertThatNoException().isThrownBy(
                () -> EnvelopeHeaders.inspectSource(headers, "test-context"));
    }

    @Test
    void inspectSource_doesNotThrow_whenXSourceIsUnknown() {
        // 미지값 헤더가 달려 있어도 예외 없이 통과 — envelope §2.3 가드 의도.
        RecordHeaders headers = new RecordHeaders();
        headers.add(EnvelopeHeaders.X_SOURCE,
                "unknown-future-service".getBytes(StandardCharsets.UTF_8));

        assertThatNoException().isThrownBy(
                () -> EnvelopeHeaders.inspectSource(headers, "test-context"));
    }

    @Test
    void inspectSource_doesNotThrow_whenXSourceIsKnown() {
        // 알려진 값도 동일하게 예외 없이 통과.
        RecordHeaders headers = new RecordHeaders();
        headers.add(EnvelopeHeaders.X_SOURCE,
                "script-agent".getBytes(StandardCharsets.UTF_8));

        assertThatNoException().isThrownBy(
                () -> EnvelopeHeaders.inspectSource(headers, "test-context"));
    }
}
