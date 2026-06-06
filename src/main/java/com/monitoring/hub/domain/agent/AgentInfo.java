package com.monitoring.hub.domain.agent;

import java.time.Instant;

/**
 * 등록된 Agent의 in-memory 정보. spec §3.2 / §4.3.
 *
 * <p>{@link com.monitoring.hub.store.AgentRegistry}가 {@code Map<AgentId, AgentInfo>}로
 * 관리한다. immutable record로 두어 {@code ConcurrentMap.compute(...)} 람다 안에서
 * 안전한 갱신이 가능하게 한다 — {@link #withLastSeen} / {@link #withState} 헬퍼
 * 참조.
 *
 * @param agentId       Agent의 영구 식별자 (UUIDv4, spec §2.4)
 * @param hostname      AGENT_STARTED metadata에서 추출
 * @param os            AGENT_STARTED metadata에서 추출 (예: {@code linux/amd64})
 * @param agentVersion  AGENT_STARTED metadata에서 추출
 * @param lastSeen      마지막으로 살아있음이 확인된 시각. AGENT_STARTED 수신
 *                      시점과 heartbeat 수신 시점에 갱신
 * @param state         현재 상태 — ONLINE / OFFLINE
 */
public record AgentInfo(
        String agentId,
        String hostname,
        String os,
        String agentVersion,
        Instant lastSeen,
        AgentState state
) {

    /** {@code lastSeen}만 갱신한 새 인스턴스를 반환. */
    public AgentInfo withLastSeen(Instant newLastSeen) {
        return new AgentInfo(agentId, hostname, os, agentVersion, newLastSeen, state);
    }

    /** {@code state}만 갱신한 새 인스턴스를 반환. */
    public AgentInfo withState(AgentState newState) {
        return new AgentInfo(agentId, hostname, os, agentVersion, lastSeen, newState);
    }
}
