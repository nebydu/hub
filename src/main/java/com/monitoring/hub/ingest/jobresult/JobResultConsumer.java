package com.monitoring.hub.ingest.jobresult;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import com.monitoring.hub.config.KafkaConfig;
import com.monitoring.hub.domain.job.JobResult;
import com.monitoring.hub.messaging.EnvelopeHeaders;
import com.monitoring.hub.store.JobResultRingBuffer;

/**
 * job-results 토픽 consumer. spec §5.2.
 *
 * <p>책임:
 * <ul>
 *   <li>수신한 모든 job result를 {@link JobResultRingBuffer}에 적재.</li>
 *   <li>Schedule/Job 매칭은 Quartz·Schedule 도메인이 도입되는 다음 단계 영역
 *       — 현재는 적재만 한다.</li>
 * </ul>
 *
 * <p>{@code execution_id}로 commands ↔ job-results ↔ JOB_EXECUTED audit을 상관
 * (spec §5.1.3 / §5.2.3) — 데모 단계에서는 화면 렌더 시점에 ring buffer 스냅샷을
 * 같은 execution_id로 join하는 정도로 충분.
 *
 * <p>{@link com.monitoring.hub.ingest.audit.AuditConsumer}와 마찬가지로 단일
 * 메시지 처리 예외는 잡아 WARN 로깅만 하고 다음 메시지로 진행.
 */
@Component
public class JobResultConsumer {

    private static final Logger log = LoggerFactory.getLogger(JobResultConsumer.class);

    private final JobResultRingBuffer buffer;

    public JobResultConsumer(JobResultRingBuffer buffer) {
        this.buffer = buffer;
    }

    @KafkaListener(
            topics = KafkaConfig.Topics.JOB_RESULTS,
            containerFactory = "jobResultListenerFactory",
            groupId = "hub-job-result-consumer"
    )
    public void consume(ConsumerRecord<String, JobResult> record) {
        // envelope §2.3 x-source 가드: 헤더를 읽되 부재/미지값이어도 처리는 그대로
        // 계속한다(미지값에 안 깨지는 것이 의도된 동작). 미지값일 때만 debug 로깅.
        EnvelopeHeaders.inspectSource(record.headers(), KafkaConfig.Topics.JOB_RESULTS);

        JobResult result = record.value();
        if (result == null) {
            log.warn("skipping null job-result payload at offset={}", record.offset());
            return;
        }
        try {
            buffer.add(result);
            log.info(
                    "JOB_RESULT received: execution_id={} agent_id={} job_type={} status={}",
                    result.executionId(), result.agentId(), result.jobType(), result.status());
        } catch (RuntimeException ex) {
            log.warn("failed to handle job-result: execution_id={} agent_id={}",
                    result.executionId(), result.agentId(), ex);
        }
    }
}
