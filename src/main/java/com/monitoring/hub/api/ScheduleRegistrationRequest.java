package com.monitoring.hub.api;

import java.util.Map;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import com.monitoring.hub.domain.job.JobType;

/**
 * POST {@code /schedules} 요청 본문. spec §4.2 데모 UI 단순화 형태 —
 * job_type, spec, target_agent_id, cron을 한 번에 받는다.
 *
 * <p>{@code spec}의 구조는 {@link JobType}에 따라 다르지만 데모 단계는
 * {@link Map}으로 흡수 (sealed 모델은 본개발 영역). 검증은 Agent 측의 spec
 * 필드 해석에서 자연스럽게 일어난다.
 *
 * @param jobType        SCRIPT_JOB | LOG_JOB
 * @param spec           job_type별 자유 구조 (script_path/args/... 또는 log_path/...)
 * @param targetAgentId  실행 대상 Agent
 * @param cron           Quartz cron 표현식
 */
public record ScheduleRegistrationRequest(
        @NotNull JobType jobType,
        @NotNull Map<String, Object> spec,
        @NotBlank String targetAgentId,
        @NotBlank String cron
) {
}
