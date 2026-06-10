package com.monitoring.hub.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * MinIO(S3 호환) 연결 설정. prefix {@code hub.minio}.
 *
 * <p>phase1-050: 클라이언트 연결 설정 단계. {@link DatastoreClientConfig}가 이 값으로
 * {@code MinioClient}를 <b>생성만</b> 하며, 빈 생성 시점에 eager connect/ping 하지 않는다.
 * 실제 연결 검증(버킷 존재 확인 등)은 Gated smoke에서만 한다.
 *
 * <p>credential은 infra .env의 {@code MINIO_ROOT_USER}/{@code MINIO_ROOT_PASSWORD}를
 * application.yml을 통해 주입받는다(평문 하드코딩 금지).
 */
@ConfigurationProperties(prefix = "hub.minio")
@Validated
public record MinioProperties(
        /** MinIO endpoint (예: {@code http://localhost:9000}). */
        @NotBlank String endpoint,
        /** access key. infra {@code MINIO_ROOT_USER}. 기본값은 비워 둘 수 있다. */
        String accessKey,
        /** secret key. infra {@code MINIO_ROOT_PASSWORD}. 기본값은 비워 둘 수 있다. */
        String secretKey,
        /** 객체 저장 버킷 이름. 데모 기본 {@code app-objects}. */
        @NotBlank String bucket
) {
}
