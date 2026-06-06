package com.monitoring.hub.store;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.springframework.stereotype.Component;

import com.monitoring.hub.domain.heartbeat.HeartbeatState;

/**
 * Agent별 최신 heartbeat의 in-memory latest map. spec §4.3.
 *
 * <p>ring buffer가 아니라 latest map인 이유: 동일 Agent의 heartbeat이 ring 전체를
 * 채워 다른 Agent의 최신 정보를 밀어내는 문제를 피하기 위함 (spec §4.3).
 *
 * <p>같은 agent_id로 들어온 새 heartbeat이 더 오래된 timestamp일 가능성도
 * 있으므로, {@link #upsert(HeartbeatState)}는 항상 새 값으로 덮어쓰지 않고
 * {@code lastSeen}이 더 최신일 때만 갱신한다 — Kafka 파티션 안에서는 순서가
 * 보장되지만 heartbeats-topic은 OTel Collector가 발행하므로 Agent별 partition 분배
 * 보장이 없다 (spec §2.3).
 *
 * <p>thread-safe. {@link ConcurrentHashMap#merge}로 atomic upsert.
 */
@Component
public final class HeartbeatLatestMap {

    private final ConcurrentMap<String, HeartbeatState> states = new ConcurrentHashMap<>();

    /**
     * agent_id 키로 최신 heartbeat을 적재. 기존 값이 더 최근이면 무시한다.
     *
     * @return 적용 후의 현재 상태 (덮어쓰지 않은 경우엔 기존 상태)
     */
    public HeartbeatState upsert(HeartbeatState incoming) {
        return states.merge(incoming.agentId(), incoming, (existing, next) ->
                next.lastSeen().isAfter(existing.lastSeen()) ? next : existing);
    }

    /** agent_id로 단일 조회. 미수신이면 빈 Optional. */
    public Optional<HeartbeatState> find(String agentId) {
        return Optional.ofNullable(states.get(agentId));
    }

    /** 전체 스냅샷 (불변 view). */
    public Map<String, HeartbeatState> snapshot() {
        return Collections.unmodifiableMap(states);
    }

    /** 등록된 Agent 수. */
    public int size() {
        return states.size();
    }
}
