package com.monitoring.hub.domain.audit;

/**
 * 감사 이벤트의 result. spec §5.3.4.
 *
 * <p>AGENT_STARTED / AGENT_STOPPED는 {@link #SUCCESS} 고정.
 * JOB_EXECUTED는 Job 실행 결과에 따라 셋 중 하나.
 */
public enum EventResult {
    SUCCESS,
    FAIL,
    TIMEOUT
}
