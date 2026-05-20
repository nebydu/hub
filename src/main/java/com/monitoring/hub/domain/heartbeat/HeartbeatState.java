package com.monitoring.hub.domain.heartbeat;

import java.time.Instant;

/**
 * 단일 Agent의 최근 heartbeat 상태. spec §4.3 / §5.4.2.
 *
 * <p>{@link com.monitoring.hub.store.HeartbeatLatestMap}이 Agent별로 최신 1개만
 * 유지한다 — ring buffer가 아닌 latest map 구조다 (spec §4.3 — 동일 Agent의
 * heartbeat이 ring을 채워 다른 Agent를 밀어내는 문제 방지).
 *
 * @param agentId  heartbeat의 attribute에서 추출한 agent_id
 * @param lastSeen OTLP dataPoint의 {@code timeUnixNano}를 {@link Instant}로 변환한 값
 */
public record HeartbeatState(
        String agentId,
        Instant lastSeen
) {
}
