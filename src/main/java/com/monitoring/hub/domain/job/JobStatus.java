package com.monitoring.hub.domain.job;

/**
 * Job 실행 결과 상태. spec §5.2.3 status.
 *
 * <p>{@link com.monitoring.hub.domain.audit.EventResult}와 값 집합은 같지만
 * 도메인이 다르므로 별도 enum으로 분리한다 — job-results의 status와
 * audit-topic의 result는 spec상 같은 값을 갖도록 강제되지만 (JOB_EXECUTED 경우),
 * 타입 수준에서 패키지 의존성을 만들지 않는다.
 */
public enum JobStatus {
    SUCCESS,
    FAIL,
    TIMEOUT
}
