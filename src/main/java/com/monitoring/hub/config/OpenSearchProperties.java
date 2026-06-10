package com.monitoring.hub.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * OpenSearch 연결 설정. prefix {@code hub.opensearch}.
 *
 * <p>phase1-050: 클라이언트 연결 설정 단계. {@link DatastoreClientConfig}가 이 endpoint로
 * {@code OpenSearchClient}를 <b>생성만</b> 하며, 빈 생성 시점에 eager ping/connect 하지
 * 않는다. 실제 연결 검증은 Gated smoke에서만 한다.
 *
 * <p>데모 infra는 http + 인증 없음이므로 endpoint 하나만 노출한다. 인증/TLS는
 * 본개발 영역(통합본 Phase 1+).
 */
@ConfigurationProperties(prefix = "hub.opensearch")
@Validated
public record OpenSearchProperties(
        /** OpenSearch HTTP endpoint (예: {@code http://localhost:9200}). */
        @NotBlank String endpoint
) {
}
