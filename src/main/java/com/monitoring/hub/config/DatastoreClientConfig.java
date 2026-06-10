package com.monitoring.hub.config;

import java.net.URI;

import org.apache.hc.core5.http.HttpHost;
import org.springframework.util.StringUtils;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.transport.OpenSearchTransport;
import org.opensearch.client.transport.httpclient5.ApacheHttpClient5TransportBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.minio.MinioClient;

/**
 * 데이터스토어 클라이언트 빈 정의 (phase1-050).
 *
 * <p>OpenSearch / MinIO 클라이언트를 <b>객체 생성만</b> 한다. 빈 생성 시점에 절대
 * eager ping/connect 하지 않는다 — 인프라 미기동 상태에서도 hub 부팅이 회귀 없이
 * 통과해야 하기 때문이다(부팅 회귀 0 최우선). 실제 연결 검증은 Gated smoke
 * ({@code DatastoreConnectionSmokeTest}, tester 단계 작성)에서만 한다.
 *
 * <p>PostgreSQL DataSource(Hikari)와 Redis(Lettuce)는 Spring Boot 자동구성이
 * 빈을 만들므로 여기서 직접 선언하지 않는다. PG는 application.yml의
 * {@code hikari.initialization-fail-timeout: -1}로, Redis는 lazy 연결 특성으로
 * 각각 부팅 회귀를 차단한다.
 *
 * <p>이 단계는 도메인 영속을 도입하지 않는다 — repository/entity/DDL/트랜잭션 없음.
 * Phase 0 in-memory 흐름(store 패키지)은 불변.
 */
@Configuration
public class DatastoreClientConfig {

    /**
     * OpenSearch Java client. endpoint URI로 {@link OpenSearchTransport}를 구성하고
     * client 객체를 만들기만 한다(네트워크 호출 없음).
     *
     * <p>opensearch-java 2.x의 Apache HttpClient5 transport는 빌더에서 transport
     * 객체만 생성하며 빈 생성 시 서버에 접속하지 않는다 — eager ping 없음.
     */
    @Bean
    public OpenSearchClient openSearchClient(OpenSearchProperties properties) {
        HttpHost host = HttpHost.create(URI.create(properties.endpoint()));
        OpenSearchTransport transport =
                ApacheHttpClient5TransportBuilder.builder(host).build();
        return new OpenSearchClient(transport);
    }

    /**
     * MinIO(S3 호환) client. endpoint + credential로 client 객체만 만든다.
     *
     * <p>{@code MinioClient.Builder#build()}는 lazy하게 내부 OkHttp client를 구성할
     * 뿐 빈 생성 시 버킷 조회/연결을 하지 않는다 — eager connect 없음.
     * 실제 연결(버킷 존재 확인 등)은 Gated smoke에서만 한다.
     *
     * <p>주의: {@code credentials(...)}는 빈 access/secret key를 거부한다
     * ({@code AccessKey and SecretKey must not be empty}). credential 기본값이
     * 비어 있는 부팅(인프라 미기동·env 미설정)에서도 빈 생성이 회귀 없이 통과해야
     * 하므로, 둘 다 채워졌을 때만 {@code credentials()}를 호출한다. credential이
     * 비면 anonymous client로 생성되며, 인증이 필요한 실제 연결은 Gated smoke에서
     * env가 채워진 상태로 검증한다.
     */
    @Bean
    public MinioClient minioClient(MinioProperties properties) {
        MinioClient.Builder builder = MinioClient.builder()
                .endpoint(properties.endpoint());
        if (StringUtils.hasText(properties.accessKey()) && StringUtils.hasText(properties.secretKey())) {
            builder.credentials(properties.accessKey(), properties.secretKey());
        }
        return builder.build();
    }
}
