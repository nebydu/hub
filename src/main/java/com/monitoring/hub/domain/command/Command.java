package com.monitoring.hub.domain.command;

import java.time.Instant;
import java.util.Map;

import com.monitoring.hub.domain.job.JobType;

/**
 * commands 토픽 페이로드. spec §5.1.3 envelope.
 *
 * <p>BE → Agent 방향. {@code target_agent_id}가 자기 것이 아닌 Agent는 무시한다.
 *
 * <p>{@code spec} 필드는 job_type별로 구조가 다르므로 {@link Map}으로 둔다 —
 * SCRIPT_JOB은 spec §5.1.1, LOG_JOB은 spec §5.1.2 참조. typed sealed 모델로의
 * 전환은 본개발 영역.
 *
 * <p>{@code valid_until}은 spec §5.1 오프라인 Agent 처리 규약의 핵심:
 * Agent가 명령을 consume한 시점에 현재 시각이 {@code valid_until}을 지났으면
 * silently skip한다. 누적 실행 회피용 표준 패턴. 계산은
 * {@link com.monitoring.hub.scheduler.ScheduleTriggerJob}이
 * "다음 트리거 예정 시각의 90% 지점"으로 수행 (spec §5.1.3).
 *
 * <p>필드는 Java camelCase로 두고 글로벌 {@code SNAKE_CASE} 정책으로 매핑된다.
 *
 * @param executionId    이 한 번 실행의 식별자 (spec §2.4 — BE Quartz가 발급)
 * @param scheduleId     트리거된 Schedule 식별자
 * @param jobId          Job 정의 식별자
 * @param targetAgentId  실행 대상 Agent (다른 Agent는 무시)
 * @param jobType        SCRIPT_JOB | LOG_JOB
 * @param issuedAt       BE가 명령을 발행한 시각 (RFC3339)
 * @param validUntil     이 시각 이후 Agent는 skip (spec §5.1.3)
 * @param spec           job_type별 자유 구조 (script_path/args/... 또는 log_path/...)
 */
public record Command(
        String executionId,
        String scheduleId,
        String jobId,
        String targetAgentId,
        JobType jobType,
        Instant issuedAt,
        Instant validUntil,
        Map<String, Object> spec
) {
}
