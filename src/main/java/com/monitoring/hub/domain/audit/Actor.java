package com.monitoring.hub.domain.audit;

/**
 * 감사 이벤트의 actor. spec §5.3.4.
 *
 * <p>데모 단계에서 {@code type}은 항상 {@link ActorType#AGENT},
 * {@code id}는 agent_id다.
 */
public record Actor(ActorType type, String id) {
}
