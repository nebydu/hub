package com.monitoring.hub.domain.audit;

/**
 * 감사 이벤트의 actor 종류. spec §5.3.4.
 *
 * <p>데모 단계에서는 {@link #AGENT} 하나로 고정. 본개발에서 {@code USER},
 * {@code SYSTEM}이 추가될 예정 (spec §5.3.4).
 */
public enum ActorType {
    AGENT
}
