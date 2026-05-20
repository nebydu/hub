package com.monitoring.hub.domain.job;

import java.util.List;

/**
 * LOG_JOB의 실행 결과 상세. spec §5.2.2.
 *
 * <p>{@link JobResult}의 {@code log} 필드에 들어가며, SCRIPT_JOB일 경우 null.
 *
 * <p>{@code sampleLines}는 로그 원문 그대로의 일부. 데모 단계는 로그 발생 시각
 * 추출을 별도 필드로 분리하지 않는다 (spec §5.2.3 ADR #10).
 *
 * @param matchedLinesCount 패턴 매치된 라인의 총 개수 (sample이 아닌 전체)
 * @param sampleLines       샘플로 잘라낸 매치 라인들 (원문 그대로)
 */
public record LogResult(
        int matchedLinesCount,
        List<String> sampleLines
) {
}
