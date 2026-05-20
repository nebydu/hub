package com.monitoring.hub.domain.audit;

/**
 * 감사 이벤트의 target. spec §5.3.4.
 *
 * <p>action별 매핑:
 * <ul>
 *   <li>AGENT_STARTED / AGENT_STOPPED → {@code type=AGENT}, {@code id=agent_id}</li>
 *   <li>JOB_EXECUTED + SCRIPT_JOB → {@code type=SCRIPT}, {@code id=script_path}</li>
 *   <li>JOB_EXECUTED + LOG_JOB → {@code type=LOG_FILE}, {@code id=log_path}</li>
 * </ul>
 */
public record Target(TargetType type, String id) {
}
