package com.monitoring.hub.domain.agent;

/**
 * Agent의 in-memory 상태. spec §3.2.
 *
 * <p>AGENT_STARTED 수신 시 {@link #ONLINE}, AGENT_STOPPED 수신 시 {@link #OFFLINE}.
 * OFFLINE이 되어도 Agent 목록 맵에서 제거하지 않는다 (spec §3.2).
 */
public enum AgentState {
    ONLINE,
    OFFLINE
}
