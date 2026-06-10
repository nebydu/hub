package com.monitoring.hub.config;

import static org.assertj.core.api.Assertions.assertThat;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.data.redis.connection.RedisConnectionFactory;

import io.minio.MinioClient;

import org.opensearch.client.opensearch.OpenSearchClient;

/**
 * 데이터스토어 빈 부팅 회귀 가드 테스트 (non-gated, phase1-050-hub).
 *
 * <p><b>목적</b>: phase1-050에서 새로 추가된 datastore 빈
 * (DataSource/Redis/OpenSearchClient/MinioClient)이 인프라·env 미설정 상태에서도
 * Spring 컨텍스트 로드를 성공하는지 검증한다.
 *
 * <p><b>non-gated</b>: {@code @EnabledIf...} 조건 없이 기본 {@code mvn test}에서
 * 항상 실행된다. 인프라가 없어도 PASS여야 부팅 회귀 가드 역할을 한다.
 *
 * <p><b>부팅 허용 이유</b>:
 * <ul>
 *   <li>PG(Hikari): {@code initialization-fail-timeout: -1} — 커넥션 획득 실패를 무시하고 부팅 통과</li>
 *   <li>Redis(Lettuce): 연결이 lazy — 실제 명령 전까지 서버에 연결하지 않음</li>
 *   <li>OpenSearchClient: transport 빌더가 객체 생성만 하고 eager ping 없음</li>
 *   <li>MinioClient: 빌더가 OkHttp client 설정만 하고 버킷 조회/연결 없음</li>
 * </ul>
 *
 * <p><b>Kafka</b>: broker 부재 시 컨슈머 컨테이너가 재연결 백오프 경고를 내지만
 * 컨텍스트 로드 자체는 실패하지 않는다(Spring Kafka 재시도 특성).
 *
 * <p>실제 연결(CRUD round-trip)은 {@link DatastoreConnectionSmokeTest}
 * (SMOKE_INFRA=1 gated)에서만 수행한다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class DatastoreContextBootTest {

    /**
     * 애플리케이션 컨텍스트 — 빈 존재 여부 확인용.
     * full context({@code @SpringBootTest})이므로 모든 자동구성 빈이 로드된다.
     */
    @Autowired
    private ApplicationContext applicationContext;

    /**
     * DataSource 빈 — Hikari 커넥션 풀.
     * {@code initialization-fail-timeout: -1} 덕분에 PG 미기동 시에도 부팅 통과.
     */
    @Autowired
    private DataSource dataSource;

    /**
     * RedisConnectionFactory 빈 — Lettuce 기반.
     * Redis 연결은 lazy이므로 서버 미기동 시에도 부팅 통과.
     */
    @Autowired
    private RedisConnectionFactory redisConnectionFactory;

    /**
     * OpenSearchClient 빈 — {@link DatastoreClientConfig}가 등록.
     * 빈 생성 시 네트워크 호출 없음 — eager ping 금지 설계.
     */
    @Autowired
    private OpenSearchClient openSearchClient;

    /**
     * MinioClient 빈 — {@link DatastoreClientConfig}가 등록.
     * 빈 생성 시 버킷 조회/연결 없음 — credential 미설정 시 anonymous client로 생성.
     */
    @Autowired
    private MinioClient minioClient;

    /**
     * 컨텍스트 자체가 로드되면 이 테스트는 PASS.
     * 컨텍스트 로드 실패 시 {@code @SpringBootTest}가 이 테스트보다 먼저 예외를 던진다.
     */
    @Test
    void contextLoads_withoutInfra() {
        // 컨텍스트가 여기까지 도달했다면 부팅 성공 — 어설션 불필요
        // (contextLoads 패턴: 테스트 메서드 자체가 도달 여부로 검증)
    }

    /**
     * DataSource 빈이 컨텍스트에 존재하는지 검증.
     * {@code spring-boot-starter-jdbc}의 Hikari 자동구성 결과.
     */
    @Test
    void dataSourceBeanExists() {
        assertThat(dataSource)
                .as("DataSource(Hikari) 빈이 컨텍스트에 등록되어 있어야 한다")
                .isNotNull();
    }

    /**
     * RedisConnectionFactory 빈이 컨텍스트에 존재하는지 검증.
     * {@code spring-boot-starter-data-redis}의 Lettuce 자동구성 결과.
     */
    @Test
    void redisConnectionFactoryBeanExists() {
        assertThat(redisConnectionFactory)
                .as("RedisConnectionFactory(Lettuce) 빈이 컨텍스트에 등록되어 있어야 한다")
                .isNotNull();
    }

    /**
     * OpenSearchClient 빈이 컨텍스트에 존재하는지 검증.
     * {@link DatastoreClientConfig#openSearchClient}가 생성한 결과.
     */
    @Test
    void openSearchClientBeanExists() {
        assertThat(openSearchClient)
                .as("OpenSearchClient 빈이 컨텍스트에 등록되어 있어야 한다")
                .isNotNull();

        // ApplicationContext.getBean 경로로도 확인 (이중 검증)
        OpenSearchClient fromCtx = applicationContext.getBean(OpenSearchClient.class);
        assertThat(fromCtx)
                .as("ApplicationContext.getBean으로 가져온 OpenSearchClient가 null이 아니어야 한다")
                .isNotNull();
    }

    /**
     * MinioClient 빈이 컨텍스트에 존재하는지 검증.
     * {@link DatastoreClientConfig#minioClient}가 생성한 결과.
     * credential 미설정(기본값 빈 문자열)이어도 빈 생성이 성공해야 한다.
     */
    @Test
    void minioClientBeanExists() {
        assertThat(minioClient)
                .as("MinioClient 빈이 컨텍스트에 등록되어 있어야 한다")
                .isNotNull();

        // ApplicationContext.getBean 경로로도 확인 (이중 검증)
        MinioClient fromCtx = applicationContext.getBean(MinioClient.class);
        assertThat(fromCtx)
                .as("ApplicationContext.getBean으로 가져온 MinioClient가 null이 아니어야 한다")
                .isNotNull();
    }

    /**
     * 4종 datastore 빈이 모두 컨텍스트에 동시에 존재하는지 한 번에 검증.
     * 개별 테스트가 모두 통과해도 상호 의존성 충돌로 한 쪽이 null일 가능성을 배제한다.
     */
    @Test
    void allDatastoreBeansExist_simultaneously() {
        assertThat(dataSource)
                .as("DataSource 빈 — 동시 로드 검증")
                .isNotNull();
        assertThat(redisConnectionFactory)
                .as("RedisConnectionFactory 빈 — 동시 로드 검증")
                .isNotNull();
        assertThat(openSearchClient)
                .as("OpenSearchClient 빈 — 동시 로드 검증")
                .isNotNull();
        assertThat(minioClient)
                .as("MinioClient 빈 — 동시 로드 검증")
                .isNotNull();
    }
}
