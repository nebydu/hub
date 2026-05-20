package com.monitoring.hub.domain.job;

import java.util.Map;

/**
 * Job 정의. spec §4.1 — "무엇을 어떻게 실행할지". 시간/대상과 무관.
 *
 * <p>{@code spec}은 {@link JobType}에 따라 다른 구조:
 * <ul>
 *   <li>SCRIPT_JOB: {@code script_path}, {@code args}, {@code timeout_seconds},
 *       {@code output_cap_bytes} (spec §5.1.1)</li>
 *   <li>LOG_JOB: {@code log_path}, {@code pattern}, {@code encoding} (spec §5.1.2)</li>
 * </ul>
 *
 * <p>{@link com.monitoring.hub.store.JobRegistry}가
 * {@code Map<JobId, JobDefinition>}로 관리 (spec §4.3).
 *
 * <p>데모 단계 UI는 Job 등록을 별도 화면으로 노출하지 않고 Schedule 등록 시
 * 함께 생성한다 (spec §4.2). 즉 외부에서 본 단일 객체이지만 내부적으로는
 * JobDefinition + ScheduleDefinition 두 record가 짝으로 만들어진다.
 */
public record JobDefinition(
        String jobId,
        JobType jobType,
        Map<String, Object> spec
) {
}
