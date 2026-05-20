package com.monitoring.hub.domain.audit;

import java.time.Instant;
import java.util.Map;

/**
 * audit-events 토픽 페이로드. spec §5.3.4 필드 정의 그대로.
 *
 * <p>필드는 Java camelCase로 두고, Kafka 페이로드의 snake_case와의 매핑은
 * Jackson 전역 정책({@code spring.jackson.property-naming-strategy=SNAKE_CASE})으로
 * 처리한다. {@code @JsonProperty}는 사용하지 않는다.
 *
 * <p>{@code metadata}는 action별 자유 형식이므로 {@code Map<String, Object>}로 둔다:
 * <ul>
 *   <li>AGENT_STARTED: {@code hostname}, {@code os}, {@code agent_version}, {@code started_at}</li>
 *   <li>AGENT_STOPPED: {@code reason}</li>
 *   <li>JOB_EXECUTED: {@code execution_id}, {@code schedule_id}, {@code job_id},
 *       {@code job_type}, ({@code exit_code})</li>
 * </ul>
 *
 * <p>{@code occurredAt}은 RFC3339 문자열로 직렬화되며 {@link Instant}로
 * 역직렬화된다 (Spring Boot 자동 등록 {@code JavaTimeModule}).
 * JOB_EXECUTED의 경우 spec §5.3에 따라 Job 실행 종료 시각이다.
 */
public record AuditEvent(
        String eventId,
        Actor actor,
        AuditAction action,
        Target target,
        EventResult result,
        Instant occurredAt,
        Map<String, Object> metadata
) {
}
