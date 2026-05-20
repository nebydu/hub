package com.monitoring.hub.domain.job;

/**
 * SCRIPT_JOB의 실행 결과 상세. spec §5.2.1.
 *
 * <p>{@link JobResult}의 {@code script} 필드에 들어가며, LOG_JOB일 경우 null.
 * 필드는 Java camelCase로 두고 글로벌 {@code SNAKE_CASE} 정책으로 매핑된다.
 *
 * @param exitCode  스크립트 종료 코드. timeout 시 Agent가 어떤 값을 채울지는
 *                  Agent 측 결정 — BE는 단순히 기록만 한다
 * @param stdoutCap stdout cap (spec §5.1.1 output_cap_bytes로 제한된 길이까지)
 * @param stderrCap stderr cap
 * @param truncated cap 초과로 잘렸는지 여부
 */
public record ScriptResult(
        int exitCode,
        String stdoutCap,
        String stderrCap,
        boolean truncated
) {
}
