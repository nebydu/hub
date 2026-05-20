package com.monitoring.hub.domain.job;

/**
 * Job 유형. spec §4.1.
 *
 * <p>데모 단계는 두 종만 정의. SQL_JOB 등 추가는 본개발 영역 (ADR #9).
 */
public enum JobType {
    SCRIPT_JOB,
    LOG_JOB
}
