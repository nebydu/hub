package com.monitoring.hub.ingest.jobresult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.monitoring.hub.config.AppProperties;
import com.monitoring.hub.config.KafkaConfig;
import com.monitoring.hub.domain.job.JobResult;
import com.monitoring.hub.domain.job.JobStatus;
import com.monitoring.hub.domain.job.JobType;
import com.monitoring.hub.domain.job.LogResult;
import com.monitoring.hub.domain.job.ScriptResult;
import com.monitoring.hub.messaging.EnvelopeHeaders;
import com.monitoring.hub.store.JobResultRingBuffer;

/**
 * JobResultConsumer 단위 테스트.
 *
 * <p>envelope §2.3 "알 수 없는 x-source 가드" 적용 후에도 payload가 ring buffer에
 * 정상 적재됨을 보장한다. 가드는 처리 흐름에 절대 영향을 주지 않는다.
 *
 * <p>검증 항목:
 * <ol>
 *   <li>미지 x-source 헤더가 달린 레코드 → ring buffer 정상 적재</li>
 *   <li>x-source 헤더 부재 레코드 → ring buffer 정상 적재</li>
 *   <li>알려진 x-source 헤더가 달린 레코드 → ring buffer 정상 적재(동작 동일 확인)</li>
 * </ol>
 *
 * <p>Spring Context를 띄우지 않는 순수 단위 테스트다 — 실제 인스턴스를 직접 구성.
 */
class JobResultConsumerTest {

    // T4-2 분리: 두 토픽을 단일 listener가 구독한다. x-source 가드 테스트는 둘 중
    // 하나(RESULT_JOB)로 충분하고, 분기 정확성(R-C) 테스트는 두 토픽을 함께 쓴다.
    private static final String TOPIC = KafkaConfig.Topics.RESULT_JOB;

    private JobResultRingBuffer buffer;
    private JobResultConsumer consumer;

    @BeforeEach
    void setUp() {
        AppProperties props = new AppProperties(
                new AppProperties.Kafka("localhost:9092"),
                new AppProperties.Audit(200),
                new AppProperties.Job(100),
                new AppProperties.Command(50),
                new AppProperties.Agent(30)
        );
        buffer = new JobResultRingBuffer(props);
        consumer = new JobResultConsumer(buffer);
    }

    private static JobResult sampleJobResult(String executionId) {
        return new JobResult(
                executionId,
                "schedule-1",
                "job-1",
                "agent-test",
                JobType.SCRIPT_JOB,
                JobStatus.SUCCESS,
                Instant.parse("2026-05-19T14:00:01Z"),
                Instant.parse("2026-05-19T14:00:03Z"),
                new ScriptResult(0, "ok", "", false),
                null
        );
    }

    /** LOG_JOB 결과 샘플 — result-topic-log로 수신되는 payload. */
    private static JobResult sampleLogJobResult(String executionId) {
        return new JobResult(
                executionId,
                "schedule-1",
                "job-1",
                "agent-test",
                JobType.LOG_JOB,
                JobStatus.SUCCESS,
                Instant.parse("2026-05-19T15:00:01Z"),
                Instant.parse("2026-05-19T15:00:02Z"),
                null,
                new LogResult(2, java.util.List.of("ERROR boom", "ERROR again"))
        );
    }

    /**
     * 미지 x-source 헤더가 달린 레코드를 consume해도 ring buffer에 정상 적재된다.
     * 가드가 reject/throw하지 않음을 검증.
     */
    @Test
    void consume_withUnknownXSource_addsToRingBuffer() {
        ConsumerRecord<String, JobResult> record = new ConsumerRecord<>(
                TOPIC, 0, 0L, "agent-test", sampleJobResult("exec-001"));
        // 미지값 x-source 헤더 부착
        record.headers().add(new RecordHeader(
                EnvelopeHeaders.X_SOURCE,
                "unknown-future-service".getBytes(StandardCharsets.UTF_8)));

        assertThatNoException().isThrownBy(() -> consumer.consume(record));
        assertThat(buffer.size()).isEqualTo(1);
        assertThat(buffer.snapshot().get(0).executionId()).isEqualTo("exec-001");
    }

    /**
     * x-source 헤더가 아예 없는 레코드 — 헤더 부재는 정상이며 ring buffer에 적재된다.
     */
    @Test
    void consume_withNoXSourceHeader_addsToRingBuffer() {
        ConsumerRecord<String, JobResult> record = new ConsumerRecord<>(
                TOPIC, 0, 1L, "agent-test", sampleJobResult("exec-002"));
        // 헤더를 추가하지 않음

        assertThatNoException().isThrownBy(() -> consumer.consume(record));
        assertThat(buffer.size()).isEqualTo(1);
        assertThat(buffer.snapshot().get(0).executionId()).isEqualTo("exec-002");
    }

    /**
     * 알려진 x-source(script-agent)가 달린 레코드 — 정상 적재되어야 한다.
     */
    @Test
    void consume_withKnownXSource_addsToRingBuffer() {
        ConsumerRecord<String, JobResult> record = new ConsumerRecord<>(
                TOPIC, 0, 2L, "agent-test", sampleJobResult("exec-003"));
        record.headers().add(new RecordHeader(
                EnvelopeHeaders.X_SOURCE,
                "script-agent".getBytes(StandardCharsets.UTF_8)));

        assertThatNoException().isThrownBy(() -> consumer.consume(record));
        assertThat(buffer.size()).isEqualTo(1);
        assertThat(buffer.snapshot().get(0).executionId()).isEqualTo("exec-003");
    }

    /**
     * 여러 헤더 케이스를 연속 consume 시 모두 ring buffer에 누적된다.
     */
    @Test
    void consume_multipleRecordsWithVariousHeaders_allAddedToRingBuffer() {
        // 미지값 헤더
        ConsumerRecord<String, JobResult> rec1 = new ConsumerRecord<>(
                TOPIC, 0, 0L, "agent-test", sampleJobResult("exec-101"));
        rec1.headers().add(new RecordHeader(
                EnvelopeHeaders.X_SOURCE,
                "future-service".getBytes(StandardCharsets.UTF_8)));

        // 헤더 부재
        ConsumerRecord<String, JobResult> rec2 = new ConsumerRecord<>(
                TOPIC, 0, 1L, "agent-test", sampleJobResult("exec-102"));

        // 알려진 헤더
        ConsumerRecord<String, JobResult> rec3 = new ConsumerRecord<>(
                TOPIC, 0, 2L, "agent-test", sampleJobResult("exec-103"));
        rec3.headers().add(new RecordHeader(
                EnvelopeHeaders.X_SOURCE,
                "script-agent".getBytes(StandardCharsets.UTF_8)));

        consumer.consume(rec1);
        consumer.consume(rec2);
        consumer.consume(rec3);

        assertThat(buffer.size()).isEqualTo(3);
        assertThat(buffer.snapshot())
                .extracting(JobResult::executionId)
                .containsExactly("exec-101", "exec-102", "exec-103");
    }

    /**
     * null payload는 ring buffer에 적재되지 않고 warn 로깅만 한다.
     * 가드와 무관한 방어적 동작도 유지됨 확인.
     */
    @Test
    void consume_withNullPayload_doesNotAddToRingBuffer() {
        ConsumerRecord<String, JobResult> record = new ConsumerRecord<>(
                TOPIC, 0, 3L, "agent-test", null);

        assertThatNoException().isThrownBy(() -> consumer.consume(record));
        assertThat(buffer.size()).isEqualTo(0);
    }

    /**
     * R-C 분기 정확성(T4-2): SCRIPT_JOB 결과는 result-topic-job으로, LOG_JOB 결과는
     * result-topic-log로 수신된다. 단일 멀티토픽 listener가 두 토픽을 모두 받아
     * 같은 단일 ring buffer에 적재하며(R-A 동작 등가), payload의 job_type이
     * 토픽과 정합함을 확인한다(오분류 0).
     */
    @Test
    void consume_routesBothTopicsToSingleRingBuffer() {
        // SCRIPT_JOB → result-topic-job
        ConsumerRecord<String, JobResult> jobRec = new ConsumerRecord<>(
                KafkaConfig.Topics.RESULT_JOB, 0, 0L, "agent-test", sampleJobResult("exec-job-1"));
        // LOG_JOB → result-topic-log
        ConsumerRecord<String, JobResult> logRec = new ConsumerRecord<>(
                KafkaConfig.Topics.RESULT_LOG, 0, 0L, "agent-test", sampleLogJobResult("exec-log-1"));

        consumer.consume(jobRec);
        consumer.consume(logRec);

        // 두 토픽 결과가 같은 단일 ring buffer에 순서대로 누적.
        assertThat(buffer.size()).isEqualTo(2);
        assertThat(buffer.snapshot())
                .extracting(JobResult::executionId)
                .containsExactly("exec-job-1", "exec-log-1");
        // job_type ↔ 토픽 정합: result-topic-job엔 SCRIPT_JOB, result-topic-log엔 LOG_JOB.
        assertThat(buffer.snapshot().get(0).jobType()).isEqualTo(JobType.SCRIPT_JOB);
        assertThat(buffer.snapshot().get(1).jobType()).isEqualTo(JobType.LOG_JOB);
    }
}
