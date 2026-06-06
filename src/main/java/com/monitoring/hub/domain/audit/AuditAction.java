package com.monitoring.hub.domain.audit;

/**
 * audit-topic의 action 종류. spec §5.3.
 *
 * <p>데모 단계 audit 액션은 다음 세 가지로 한정된다:
 * <ul>
 *   <li>{@link #AGENT_STARTED} — Agent 프로세스 시작 직후 (BE 등록 역할 겸함, spec §3.2)</li>
 *   <li>{@link #AGENT_STOPPED} — Agent 정상 종료 직전</li>
 *   <li>{@link #JOB_EXECUTED} — Job 실행 종료 시 (occurred_at = 종료 시각)</li>
 * </ul>
 */
public enum AuditAction {
    AGENT_STARTED,
    AGENT_STOPPED,
    JOB_EXECUTED
}
