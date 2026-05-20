package com.monitoring.hub.store;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.junit.jupiter.api.Test;

import com.monitoring.hub.domain.heartbeat.HeartbeatState;

class HeartbeatLatestMapTest {

    @Test
    void upsertFreshAgentAddsEntry() {
        HeartbeatLatestMap map = new HeartbeatLatestMap();
        Instant t1 = Instant.parse("2026-05-20T10:00:00Z");

        map.upsert(new HeartbeatState("agent-001", t1));

        assertThat(map.size()).isEqualTo(1);
        assertThat(map.find("agent-001"))
                .hasValueSatisfying(s -> assertThat(s.lastSeen()).isEqualTo(t1));
    }

    @Test
    void upsertNewerHeartbeatReplacesOlder() {
        HeartbeatLatestMap map = new HeartbeatLatestMap();
        Instant t1 = Instant.parse("2026-05-20T10:00:00Z");
        Instant t2 = Instant.parse("2026-05-20T10:00:10Z");

        map.upsert(new HeartbeatState("agent-001", t1));
        map.upsert(new HeartbeatState("agent-001", t2));

        assertThat(map.find("agent-001"))
                .hasValueSatisfying(s -> assertThat(s.lastSeen()).isEqualTo(t2));
    }

    @Test
    void upsertOlderHeartbeatIsIgnored() {
        // OTel Collector의 partition 분배가 Agent 단위가 아니므로 (spec §2.3),
        // 동일 Agent의 더 오래된 heartbeat이 늦게 도착할 수 있다 — 무시해야 한다.
        HeartbeatLatestMap map = new HeartbeatLatestMap();
        Instant t1 = Instant.parse("2026-05-20T10:00:00Z");
        Instant t2 = Instant.parse("2026-05-20T10:00:10Z");

        map.upsert(new HeartbeatState("agent-001", t2));
        map.upsert(new HeartbeatState("agent-001", t1));

        assertThat(map.find("agent-001"))
                .hasValueSatisfying(s -> assertThat(s.lastSeen()).isEqualTo(t2));
    }

    @Test
    void multipleAgentsAreIndependent() {
        HeartbeatLatestMap map = new HeartbeatLatestMap();
        Instant t = Instant.parse("2026-05-20T10:00:00Z");

        map.upsert(new HeartbeatState("agent-001", t));
        map.upsert(new HeartbeatState("agent-002", t));

        assertThat(map.size()).isEqualTo(2);
        assertThat(map.snapshot()).containsKeys("agent-001", "agent-002");
    }
}
