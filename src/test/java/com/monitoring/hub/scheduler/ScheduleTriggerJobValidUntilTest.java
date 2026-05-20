package com.monitoring.hub.scheduler;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.Test;

/**
 * spec §5.1.3 valid_until 계산 규칙 — "다음 트리거 예정 시각의 90% 지점"을
 * 단위로 검증.
 */
class ScheduleTriggerJobValidUntilTest {

    @Test
    void fivePeriodInterval_ninetyPercentIsFourMinThirty() {
        Instant issued = Instant.parse("2026-05-19T14:00:00Z");
        Instant next = issued.plus(Duration.ofMinutes(5));

        Instant validUntil = ScheduleTriggerJob.computeValidUntil(issued, next);

        // 5분(300초)의 90% = 270초 = 4분 30초.
        assertThat(validUntil).isEqualTo(Instant.parse("2026-05-19T14:04:30Z"));
    }

    @Test
    void tenSecondInterval_ninetyPercentIsNineSeconds() {
        Instant issued = Instant.parse("2026-05-19T14:00:00Z");
        Instant next = issued.plus(Duration.ofSeconds(10));

        Instant validUntil = ScheduleTriggerJob.computeValidUntil(issued, next);

        assertThat(validUntil).isEqualTo(Instant.parse("2026-05-19T14:00:09Z"));
    }

    @Test
    void nullNextFireTime_fallsBackToSixtySeconds() {
        // 트리거의 마지막 fire — 일회성 정책은 본개발 영역. 데모는 60초 기본값.
        Instant issued = Instant.parse("2026-05-19T14:00:00Z");

        Instant validUntil = ScheduleTriggerJob.computeValidUntil(issued, null);

        assertThat(validUntil).isEqualTo(issued.plusSeconds(60));
    }

    @Test
    void resultIsAlwaysBeforeNextFireTime() {
        Instant issued = Instant.parse("2026-05-19T14:00:00Z");
        Instant next = issued.plus(Duration.ofSeconds(13));

        Instant validUntil = ScheduleTriggerJob.computeValidUntil(issued, next);

        assertThat(validUntil).isBefore(next);
        assertThat(validUntil).isAfter(issued);
    }
}
