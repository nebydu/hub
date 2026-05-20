package com.monitoring.hub.domain.job;

import java.time.Instant;

/**
 * job-results 토픽 페이로드. spec §5.2.3 envelope.
 *
 * <p>job_type별로 둘 중 하나만 채움:
 * <ul>
 *   <li>SCRIPT_JOB → {@link #script}만 채움, {@link #log}는 null</li>
 *   <li>LOG_JOB    → {@link #log}만 채움,    {@link #script}는 null</li>
 * </ul>
 * 본개발에서 envelope + job_type별 분기 메시지 구조로 전환 검토 (spec ADR 후보).
 *
 * <p>{@code startedAt}/{@code finishedAt}은 Agent의 작업 시작/종료 시각이며,
 * 로그 발생 시각과는 별개다 (spec §5.2.3 Timestamp 주의).
 *
 * <p>필드는 Java camelCase로 두고 글로벌 {@code SNAKE_CASE} 정책으로 매핑된다.
 *
 * @param executionId 이 한 번 실행의 식별자 — command와 매치 (spec §2.4)
 * @param scheduleId  트리거된 Schedule 식별자
 * @param jobId       Job 정의 식별자
 * @param agentId     발행 Agent
 * @param jobType     SCRIPT_JOB | LOG_JOB
 * @param status      SUCCESS | FAIL | TIMEOUT
 * @param startedAt   Agent가 작업을 시작한 시각 (RFC3339)
 * @param finishedAt  Agent가 작업을 종료한 시각 (RFC3339)
 * @param script      SCRIPT_JOB일 때만 채움
 * @param log         LOG_JOB일 때만 채움
 */
public record JobResult(
        String executionId,
        String scheduleId,
        String jobId,
        String agentId,
        JobType jobType,
        JobStatus status,
        Instant startedAt,
        Instant finishedAt,
        ScriptResult script,
        LogResult log
) {
}
