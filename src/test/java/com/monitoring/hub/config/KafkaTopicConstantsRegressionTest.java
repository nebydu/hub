package com.monitoring.hub.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * KafkaConfig.Topics 상수 값 회귀 앵커 테스트 (phase1-040-hub T4-1).
 *
 * <p>이 테스트의 유일한 목적은 토픽 재명명 결과를 리터럴로 못 박아
 * 오변경 / 부분 변경 / 롤백을 즉시 잡는 것이다.
 *
 * <p>검증 항목:
 * <ul>
 *   <li>COMMANDS = "command-topic" (T4-1 변경)</li>
 *   <li>AUDIT_EVENTS = "audit-topic" (T4-1 변경)</li>
 *   <li>HEARTBEATS = "heartbeats-topic" (T4-1 변경)</li>
 *   <li>JOB_RESULTS = "job-results" (T4-2 미변경 — 이 값이 바뀌면 바로 실패)</li>
 * </ul>
 *
 * <p>상수 *이름*(COMMANDS/AUDIT_EVENTS/HEARTBEATS/JOB_RESULTS)은 코드 식별자로
 * 유지되며, **값만** 이 테스트가 고정한다.
 */
class KafkaTopicConstantsRegressionTest {

    // ─── T4-1 변경 대상 3종 ───────────────────────────────────────────────────

    @Test
    void commandsTopic_isCommandTopic() {
        // phase1-040-hub T4-1: "commands" → "command-topic"
        assertThat(KafkaConfig.Topics.COMMANDS)
                .as("COMMANDS 토픽 값이 command-topic이어야 한다 (T4-1)")
                .isEqualTo("command-topic");
    }

    @Test
    void auditEventsTopic_isAuditTopic() {
        // phase1-040-hub T4-1: "audit-events" → "audit-topic"
        assertThat(KafkaConfig.Topics.AUDIT_EVENTS)
                .as("AUDIT_EVENTS 토픽 값이 audit-topic이어야 한다 (T4-1)")
                .isEqualTo("audit-topic");
    }

    @Test
    void heartbeatsTopic_isHeartbeatsTopic() {
        // phase1-040-hub T4-1: "heartbeats" → "heartbeats-topic"
        assertThat(KafkaConfig.Topics.HEARTBEATS)
                .as("HEARTBEATS 토픽 값이 heartbeats-topic이어야 한다 (T4-1)")
                .isEqualTo("heartbeats-topic");
    }

    // ─── T4-2 미변경 가드 ─────────────────────────────────────────────────────

    @Test
    void jobResultsTopic_remainsJobResults() {
        // T4-2 미결(D-5 Open) — 이번 작업에서 "job-results"는 변경하지 않는다.
        // 이 값이 달라지면 T4-2 범위 침범이다.
        assertThat(KafkaConfig.Topics.JOB_RESULTS)
                .as("JOB_RESULTS 토픽 값은 job-results를 유지해야 한다 (T4-2 미변경)")
                .isEqualTo("job-results");
    }

    // ─── 토픽 상수 이름(식별자) 접근 가능 여부 보조 확인 ────────────────────────

    @Test
    void allFourTopicConstantsAreNonNull() {
        // 상수 이름 유지 확인 — 컴파일 오류가 아닌 null 여부만 단언해
        // 식별자 존재를 런타임 레벨에서 한 번 더 확인한다.
        assertThat(KafkaConfig.Topics.COMMANDS).isNotNull();
        assertThat(KafkaConfig.Topics.AUDIT_EVENTS).isNotNull();
        assertThat(KafkaConfig.Topics.HEARTBEATS).isNotNull();
        assertThat(KafkaConfig.Topics.JOB_RESULTS).isNotNull();
    }
}
