package com.monitoring.hub.domain.audit;

/**
 * 감사 이벤트의 target 종류. spec §5.3.4.
 *
 * <ul>
 *   <li>{@link #AGENT} — AGENT_STARTED / AGENT_STOPPED 이벤트의 target</li>
 *   <li>{@link #SCRIPT} — JOB_EXECUTED + SCRIPT_JOB</li>
 *   <li>{@link #LOG_FILE} — JOB_EXECUTED + LOG_JOB</li>
 * </ul>
 */
public enum TargetType {
    AGENT,
    SCRIPT,
    LOG_FILE
}
