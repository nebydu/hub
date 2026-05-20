package com.monitoring.hub.scheduler;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import com.monitoring.hub.domain.command.Command;
import com.monitoring.hub.domain.job.JobDefinition;
import com.monitoring.hub.domain.job.ScheduleDefinition;
import com.monitoring.hub.producer.CommandPublisher;
import com.monitoring.hub.store.JobRegistry;
import com.monitoring.hub.store.ScheduleRegistry;

/**
 * Quartz가 cron trigger를 fire할 때마다 실행되는 Job. spec §5.1.
 *
 * <p>흐름:
 * <ol>
 *   <li>JobDataMap에서 {@code scheduleId} lookup</li>
 *   <li>{@link ScheduleRegistry}에서 {@link ScheduleDefinition}, 그리고 그 jobId로
 *       {@link JobRegistry}에서 {@link JobDefinition} lookup</li>
 *   <li>execution_id 발급 (UUIDv4 — spec §2.4 BE Quartz가 발급)</li>
 *   <li>valid_until 계산 — {@link JobExecutionContext#getNextFireTime()}이 이번 fire 이후
 *       다음 예정 시각이며, 그 사이 interval의 90% 지점을 fire 시각에 더해 산출
 *       (spec §5.1.3)</li>
 *   <li>{@link Command} 생성 → {@link CommandPublisher#publish}</li>
 * </ol>
 *
 * <p>Spring Boot 자동구성의 {@code SpringBeanJobFactory}가 Quartz Job 인스턴스에
 * Spring 빈을 autowire해주므로 필드 주입을 사용한다 — 생성자 주입은 Quartz가
 * no-arg 생성자를 요구하기 때문에 부적합.
 *
 * <p>spec §5.1 misfire 정책({@code MISFIRE_INSTRUCTION_DO_NOTHING})은 트리거 생성
 * 시점에 설정하므로 본 Job 안에서는 별도 처리 없음 — 누락된 fire는 그대로
 * 흘려보낸다.
 */
public class ScheduleTriggerJob implements Job {

    private static final Logger log = LoggerFactory.getLogger(ScheduleTriggerJob.class);

    /** JobDataMap 키. {@link ScheduleService}와 공유. */
    public static final String SCHEDULE_ID_KEY = "scheduleId";

    @Autowired
    private ScheduleRegistry scheduleRegistry;

    @Autowired
    private JobRegistry jobRegistry;

    @Autowired
    private CommandPublisher publisher;

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        String scheduleId = context.getJobDetail().getJobDataMap().getString(SCHEDULE_ID_KEY);
        if (scheduleId == null) {
            log.warn("scheduleId missing in JobDataMap — skipping fire");
            return;
        }

        ScheduleDefinition schedule = scheduleRegistry.find(scheduleId).orElse(null);
        if (schedule == null || !schedule.enabled()) {
            log.warn("schedule {} not found or disabled — skipping fire", scheduleId);
            return;
        }

        JobDefinition job = jobRegistry.find(schedule.jobId()).orElse(null);
        if (job == null) {
            log.warn("job {} for schedule {} not found — skipping fire", schedule.jobId(), scheduleId);
            return;
        }

        Instant issuedAt = context.getFireTime().toInstant();
        Instant nextFireAt = context.getNextFireTime() != null
                ? context.getNextFireTime().toInstant()
                : null;
        Instant validUntil = computeValidUntil(issuedAt, nextFireAt);
        String executionId = UUID.randomUUID().toString();

        Command command = new Command(
                executionId,
                schedule.scheduleId(),
                job.jobId(),
                schedule.targetAgentId(),
                job.jobType(),
                issuedAt,
                validUntil,
                job.spec());

        publisher.publish(command, null);
    }

    /**
     * spec §5.1.3 — valid_until = issued + (next - issued) * 0.9.
     *
     * <p>nextFireAt이 null이면(트리거의 마지막 fire) 일회성 정책은 본개발 영역.
     * 데모는 안전한 기본값으로 60초를 부여한다 — 누적 실행은 막혀야 하므로
     * 짧게 잡는 게 옳다.
     */
    static Instant computeValidUntil(Instant issuedAt, Instant nextFireAt) {
        if (nextFireAt == null) {
            return issuedAt.plusSeconds(60);
        }
        long intervalNanos = Duration.between(issuedAt, nextFireAt).toNanos();
        long ninetyPctNanos = Math.round(intervalNanos * 0.9d);
        return issuedAt.plusNanos(ninetyPctNanos);
    }
}
