package com.monitoring.hub.store;

import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.springframework.stereotype.Component;

import com.monitoring.hub.domain.agent.AgentInfo;
import com.monitoring.hub.domain.agent.AgentState;

/**
 * 등록된 Agent들의 in-memory 레지스트리. spec §3.2 / §4.3.
 *
 * <p>등록은 AGENT_STARTED audit event 수신 시점에 일어난다 (데모 단계는 별도
 * register endpoint 없음, spec §3.2). 동일 agent_id가 이미 있으면 last_seen을
 * 갱신하고 state를 {@link AgentState#ONLINE}로 복귀시킨다 — OFFLINE 상태의
 * Agent가 재기동되면 ONLINE으로 자연 복귀하는 것이 정합하다고 해석. spec §3.2
 * 문구의 "last_seen만 갱신"은 새 인스턴스 생성 vs 기존 갱신을 일반화한
 * 표현으로 본다.
 *
 * <p>{@link #markOffline(String)}은 state만 변경하고 맵에서 제거하지 않는다
 * (spec §3.2 명시 — OFFLINE Agent도 등록 목록에는 남는다).
 *
 * <p>thread-safe. {@link ConcurrentHashMap#compute}로 신규/갱신을 한 atomic
 * step에서 처리한다.
 */
@Component
public final class AgentRegistry {

    private final ConcurrentMap<String, AgentInfo> agents = new ConcurrentHashMap<>();

    /**
     * Agent를 등록하거나 기존 등록을 갱신한다.
     *
     * @return 등록 후의 {@link AgentInfo}
     */
    public AgentInfo register(String agentId, String hostname, String os, String agentVersion) {
        Instant now = Instant.now();
        return agents.compute(agentId, (id, existing) -> {
            if (existing == null) {
                return new AgentInfo(id, hostname, os, agentVersion, now, AgentState.ONLINE);
            }
            // 기존 Agent의 재기동: hostname/os/version은 메타데이터 그대로 갱신,
            // last_seen은 지금, state는 ONLINE으로 복귀.
            return new AgentInfo(id, hostname, os, agentVersion, now, AgentState.ONLINE);
        });
    }

    /**
     * Agent의 last_seen만 갱신. heartbeat 경로(다음 단계)에서 호출 예정.
     * 미등록 agent_id면 no-op.
     */
    public void updateLastSeen(String agentId) {
        Instant now = Instant.now();
        agents.computeIfPresent(agentId, (id, info) -> info.withLastSeen(now));
    }

    /**
     * Agent를 OFFLINE으로 마킹. 맵에서 제거하지는 않는다. 미등록 agent_id면 no-op.
     */
    public void markOffline(String agentId) {
        agents.computeIfPresent(agentId, (id, info) -> info.withState(AgentState.OFFLINE));
    }

    /** 등록된 모든 Agent의 불변 스냅샷. */
    public Collection<AgentInfo> findAll() {
        return Collections.unmodifiableCollection(agents.values());
    }

    /** agent_id로 단일 조회. 미등록이면 빈 Optional. */
    public Optional<AgentInfo> find(String agentId) {
        return Optional.ofNullable(agents.get(agentId));
    }

    /** 현재 등록된 Agent 수. */
    public int size() {
        return agents.size();
    }
}
