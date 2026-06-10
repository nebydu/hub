package com.monitoring.hub.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;

import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.StatObjectArgs;

import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch._types.Refresh;
import org.opensearch.client.opensearch.core.GetRequest;
import org.opensearch.client.opensearch.core.GetResponse;
import org.opensearch.client.opensearch.core.IndexRequest;
import org.opensearch.client.opensearch.indices.DeleteIndexRequest;

/**
 * 데이터스토어 연결 Gated Smoke 테스트 (phase1-050-hub).
 *
 * <p>이 테스트는 {@code SMOKE_INFRA=1} 환경변수가 설정된 경우에만 실행된다.
 * 미설정 시 클래스 레벨에서 통째로 skip 되어 기존 90 tests 그린 회귀가 유지된다.
 *
 * <p>각 테스트는 고유 UUID prefix를 사용해 임시 리소스를 만들고,
 * finally 블록에서 반드시 정리한다. 도메인 스키마/영속 산출물은 절대 만들지 않는다.
 *
 * <p>인프라 미기동 상태에서 {@code SMOKE_INFRA=1}로 실행하면 연결 오류가 발생한다.
 * 이 경우 인프라 기동 누락 문제이며 smoke 자체의 결함이 아니다.
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "SMOKE_INFRA", matches = "1")
class DatastoreConnectionSmokeTest {

    private static final Logger log = LoggerFactory.getLogger(DatastoreConnectionSmokeTest.class);

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private OpenSearchClient openSearchClient;

    @Autowired
    private MinioClient minioClient;

    @Autowired
    private MinioProperties minioProperties;

    /**
     * PostgreSQL 임시 테이블 read-write 왕복 검증.
     *
     * <p>TEMP TABLE은 세션 종료 시 자동 정리된다. INSERT 후 SELECT로 동일 값을 확인한다.
     * 도메인 스키마에 영향을 주지 않는다.
     */
    @Test
    void postgres_tempTableReadWriteRoundTrip() {
        // UUID suffix로 고유 테이블명 생성
        String suffix = UUID.randomUUID().toString().replace("-", "");
        String tableName = "smoke_" + suffix;

        // TEMP TABLE 생성 — 세션 종료 시 자동 정리, 도메인 스키마 불변
        jdbcTemplate.execute(
                "CREATE TEMP TABLE " + tableName + " (id TEXT NOT NULL, val TEXT NOT NULL)");

        try {
            String id = UUID.randomUUID().toString();
            String val = "smoke-value-" + id;

            // 임시 레코드 삽입
            int inserted = jdbcTemplate.update(
                    "INSERT INTO " + tableName + " (id, val) VALUES (?, ?)", id, val);
            assertThat(inserted).as("PG: INSERT 결과가 1행이어야 한다").isEqualTo(1);

            // 삽입한 값 조회 검증
            String fetched = jdbcTemplate.queryForObject(
                    "SELECT val FROM " + tableName + " WHERE id = ?",
                    String.class, id);
            assertThat(fetched)
                    .as("PG: SELECT한 값이 INSERT한 값과 일치해야 한다")
                    .isEqualTo(val);

            log.info("[smoke][PG] 왕복 검증 성공 — table={}, id={}", tableName, id);

        } finally {
            // TEMP TABLE은 세션 종료 시 자동 삭제되지만 명시 정리
            try {
                jdbcTemplate.execute("DROP TABLE IF EXISTS " + tableName);
            } catch (Exception ex) {
                log.warn("[smoke][PG] 임시 테이블 DROP 실패(무시 가능): {}", ex.getMessage());
            }
        }
    }

    /**
     * Redis read-write 왕복 검증.
     *
     * <p>고유 키 {@code smoke:<uuid>}를 set → get → delete 순서로 검증한다.
     * 테스트 종료 시 키를 반드시 삭제한다.
     */
    @Test
    void redis_setGetDeleteRoundTrip() {
        String key = "smoke:" + UUID.randomUUID();
        String value = "smoke-val-" + UUID.randomUUID();

        try {
            // 키 설정
            redisTemplate.opsForValue().set(key, value);

            // 값 조회 검증
            String fetched = redisTemplate.opsForValue().get(key);
            assertThat(fetched)
                    .as("Redis: GET 결과가 SET한 값과 일치해야 한다")
                    .isEqualTo(value);

            log.info("[smoke][Redis] 왕복 검증 성공 — key={}", key);

        } finally {
            // 임시 키 정리
            try {
                redisTemplate.delete(key);
            } catch (Exception ex) {
                log.warn("[smoke][Redis] 키 삭제 실패(무시 가능): {}", ex.getMessage());
            }
        }
    }

    /**
     * OpenSearch 임시 인덱스 doc index/get 왕복 검증 후 인덱스 삭제.
     *
     * <p>임시 인덱스 {@code smoke-<uuid>}에 문서를 색인(refresh=true)하고,
     * get으로 동일 문서를 가져온 뒤 인덱스를 통째로 삭제한다.
     * 도메인 인덱스에 영향을 주지 않는다.
     */
    @Test
    void opensearch_indexGetDeleteRoundTrip() throws Exception {
        String indexName = "smoke-" + UUID.randomUUID();
        String docId = UUID.randomUUID().toString();

        try {
            // doc index — 임시 인덱스는 첫 색인 시 자동 생성됨
            // refresh=TRUE 로 즉시 검색 가능하게 강제
            IndexRequest<Object> indexReq = IndexRequest.of(b -> b
                    .index(indexName)
                    .id(docId)
                    .document(new SmokeDoc(docId, "ok"))
                    .refresh(Refresh.True));

            var indexResp = openSearchClient.index(indexReq);
            assertThat(indexResp.id())
                    .as("OpenSearch: index 응답의 doc id가 일치해야 한다")
                    .isEqualTo(docId);

            // get 검증
            GetRequest getReq = GetRequest.of(b -> b
                    .index(indexName)
                    .id(docId));
            GetResponse<SmokeDoc> getResp = openSearchClient.get(getReq, SmokeDoc.class);
            assertThat(getResp.found())
                    .as("OpenSearch: index 후 get이 found=true여야 한다")
                    .isTrue();
            assertThat(getResp.source())
                    .as("OpenSearch: 문서 source가 null이 아니어야 한다")
                    .isNotNull();
            assertThat(getResp.source().smokeKey())
                    .as("OpenSearch: smokeKey 필드가 docId와 일치해야 한다")
                    .isEqualTo(docId);

            log.info("[smoke][OpenSearch] 왕복 검증 성공 — index={}, id={}", indexName, docId);

        } finally {
            // 임시 인덱스 전체 삭제
            try {
                openSearchClient.indices().delete(
                        DeleteIndexRequest.of(b -> b.index(indexName)));
            } catch (Exception ex) {
                log.warn("[smoke][OpenSearch] 인덱스 삭제 실패(무시 가능): {}", ex.getMessage());
            }
        }
    }

    /**
     * MinIO 임시 객체 put/stat 왕복 검증 후 객체 삭제.
     *
     * <p>기본 버킷({@code hub.minio.bucket})에 임시 객체 {@code smoke/<uuid>}를
     * putObject → statObject 검증 → removeObject 순서로 실행한다.
     * 버킷 자체가 없으면 makeBucket 후 진행하되, 이는 infra 초기화 누락 신호이므로
     * WARN 레벨로 로그를 남긴다.
     */
    @Test
    void minio_putStatRemoveRoundTrip() throws Exception {
        String bucket = minioProperties.bucket();
        String objectKey = "smoke/" + UUID.randomUUID();
        String content = "smoke-content-" + UUID.randomUUID();
        byte[] contentBytes = content.getBytes(StandardCharsets.UTF_8);

        // 버킷 존재 확인 — 없으면 생성(infra 초기화 누락 신호로 WARN 로그)
        boolean bucketExists = minioClient.bucketExists(
                BucketExistsArgs.builder().bucket(bucket).build());
        if (!bucketExists) {
            log.warn("[smoke][MinIO] 버킷 '{}' 미존재 — makeBucket 실행 (infra 초기화 누락 신호)", bucket);
            minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
        }

        try {
            // 임시 객체 업로드
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectKey)
                    .stream(new ByteArrayInputStream(contentBytes), contentBytes.length, -1)
                    .contentType("text/plain")
                    .build());

            // stat 검증 — 객체 존재 및 크기 확인
            var stat = minioClient.statObject(
                    StatObjectArgs.builder().bucket(bucket).object(objectKey).build());
            assertThat(stat.object())
                    .as("MinIO: stat object 이름이 일치해야 한다")
                    .isEqualTo(objectKey);
            assertThat(stat.size())
                    .as("MinIO: stat size가 업로드한 바이트 수와 일치해야 한다")
                    .isEqualTo((long) contentBytes.length);

            log.info("[smoke][MinIO] 왕복 검증 성공 — bucket={}, object={}", bucket, objectKey);

        } finally {
            // 임시 객체 삭제
            try {
                minioClient.removeObject(
                        RemoveObjectArgs.builder().bucket(bucket).object(objectKey).build());
            } catch (Exception ex) {
                log.warn("[smoke][MinIO] 객체 삭제 실패(무시 가능): {}", ex.getMessage());
            }
        }
    }

    /**
     * OpenSearch smoke 문서 모델.
     *
     * <p>이 레코드는 smoke 테스트 전용이며 도메인 모델과 무관하다.
     * OpenSearch Java client의 직렬화/역직렬화 대상으로만 사용된다.
     *
     * @param smokeKey  doc id와 동일한 고유 식별자
     * @param status    항상 "ok"
     */
    record SmokeDoc(String smokeKey, String status) {}
}
