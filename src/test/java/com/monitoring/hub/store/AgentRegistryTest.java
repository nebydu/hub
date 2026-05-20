package com.monitoring.hub.store;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.junit.jupiter.api.Test;

import com.monitoring.hub.domain.agent.AgentInfo;
import com.monitoring.hub.domain.agent.AgentState;

class AgentRegistryTest {

    @Test
    void registersNewAgentAsOnline() {
        AgentRegistry registry = new AgentRegistry();
        Instant before = Instant.now();

        AgentInfo info = registry.register("agent-001", "host-1", "linux/amd64", "0.1.0");

        Instant after = Instant.now();
        assertThat(info.agentId()).isEqualTo("agent-001");
        assertThat(info.hostname()).isEqualTo("host-1");
        assertThat(info.os()).isEqualTo("linux/amd64");
        assertThat(info.agentVersion()).isEqualTo("0.1.0");
        assertThat(info.state()).isEqualTo(AgentState.ONLINE);
        assertThat(info.lastSeen()).isBetween(before, after);
        assertThat(registry.size()).isEqualTo(1);
    }

    @Test
    void reRegisterSameAgentUpdatesLastSeenAndKeepsOnline() throws InterruptedException {
        AgentRegistry registry = new AgentRegistry();
        AgentInfo first = registry.register("agent-001", "host-1", "linux/amd64", "0.1.0");

        // 시간 차이를 확실히 만들기 위해 짧게 대기.
        Thread.sleep(5);
        AgentInfo second = registry.register("agent-001", "host-1", "linux/amd64", "0.1.0");

        assertThat(second.lastSeen()).isAfter(first.lastSeen());
        assertThat(second.state()).isEqualTo(AgentState.ONLINE);
        assertThat(registry.size()).isEqualTo(1);
    }

    @Test
    void reRegisterAfterOfflineRestoresOnline() {
        AgentRegistry registry = new AgentRegistry();
        registry.register("agent-001", "host-1", "linux/amd64", "0.1.0");
        registry.markOffline("agent-001");
        assertThat(registry.find("agent-001")).hasValueSatisfying(
                info -> assertThat(info.state()).isEqualTo(AgentState.OFFLINE));

        AgentInfo restored = registry.register("agent-001", "host-1", "linux/amd64", "0.1.0");

        // spec §3.2 해석: AGENT_STARTED 재수신은 Agent 재기동을 의미하므로 ONLINE으로 복귀.
        assertThat(restored.state()).isEqualTo(AgentState.ONLINE);
    }

    @Test
    void markOfflineSetsStateButKeepsEntry() {
        AgentRegistry registry = new AgentRegistry();
        registry.register("agent-001", "host-1", "linux/amd64", "0.1.0");

        registry.markOffline("agent-001");

        assertThat(registry.size()).isEqualTo(1);
        assertThat(registry.find("agent-001")).hasValueSatisfying(
                info -> assertThat(info.state()).isEqualTo(AgentState.OFFLINE));
    }

    @Test
    void markOfflineUnknownAgentIsNoop() {
        AgentRegistry registry = new AgentRegistry();

        registry.markOffline("never-registered");

        assertThat(registry.size()).isEqualTo(0);
    }

    @Test
    void updateLastSeenUnknownAgentIsNoop() {
        AgentRegistry registry = new AgentRegistry();

        registry.updateLastSeen("never-registered");

        assertThat(registry.size()).isEqualTo(0);
    }

    @Test
    void updateLastSeenAdvancesTimestamp() throws InterruptedException {
        AgentRegistry registry = new AgentRegistry();
        AgentInfo first = registry.register("agent-001", "host-1", "linux/amd64", "0.1.0");

        Thread.sleep(5);
        registry.updateLastSeen("agent-001");

        AgentInfo after = registry.find("agent-001").orElseThrow();
        assertThat(after.lastSeen()).isAfter(first.lastSeen());
    }
}
