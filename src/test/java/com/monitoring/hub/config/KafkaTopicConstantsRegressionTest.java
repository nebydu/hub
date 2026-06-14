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
 *   <li>RESULT_JOB = "result-topic-job" (T4-2 분리 — 구 job-results 폐기)</li>
 *   <li>RESULT_LOG = "result-topic-log" (T4-2 분리 — 구 job-results 폐기)</li>
 * </ul>
 *
 * <p>상수 *이름*(COMMANDS/AUDIT_EVENTS/HEARTBEATS/RESULT_JOB/RESULT_LOG)은 코드
 * 식별자로 유지되며, **값만** 이 테스트가 고정한다.
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

    // ─── T4-2 result-topic 분리 (구 job-results 폐기) ─────────────────────────

    @Test
    void resultJobTopic_isResultTopicJob() {
        // phase1-041-hub T4-2: 구 "job-results" → SCRIPT_JOB 결과는 "result-topic-job"
        assertThat(KafkaConfig.Topics.RESULT_JOB)
                .as("RESULT_JOB 토픽 값이 result-topic-job이어야 한다 (T4-2)")
                .isEqualTo("result-topic-job");
    }

    @Test
    void resultLogTopic_isResultTopicLog() {
        // phase1-041-hub T4-2: 구 "job-results" → LOG_JOB 결과는 "result-topic-log"
        assertThat(KafkaConfig.Topics.RESULT_LOG)
                .as("RESULT_LOG 토픽 값이 result-topic-log이어야 한다 (T4-2)")
                .isEqualTo("result-topic-log");
    }

    // ─── 토픽 상수 이름(식별자) 접근 가능 여부 보조 확인 ────────────────────────

    @Test
    void allFiveTopicConstantsAreNonNull() {
        // 상수 이름 유지 확인 — 컴파일 오류가 아닌 null 여부만 단언해
        // 식별자 존재를 런타임 레벨에서 한 번 더 확인한다.
        assertThat(KafkaConfig.Topics.COMMANDS).isNotNull();
        assertThat(KafkaConfig.Topics.AUDIT_EVENTS).isNotNull();
        assertThat(KafkaConfig.Topics.HEARTBEATS).isNotNull();
        assertThat(KafkaConfig.Topics.RESULT_JOB).isNotNull();
        assertThat(KafkaConfig.Topics.RESULT_LOG).isNotNull();
    }
}
