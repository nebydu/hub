package com.monitoring.hub.scheduler;

import java.util.Map;
import java.util.UUID;

import org.quartz.CronScheduleBuilder;
import org.quartz.CronTrigger;
import org.quartz.JobBuilder;
import org.quartz.JobDetail;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.TriggerBuilder;
import org.quartz.TriggerKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.monitoring.hub.domain.job.JobDefinition;
import com.monitoring.hub.domain.job.JobType;
import com.monitoring.hub.domain.job.ScheduleDefinition;
import com.monitoring.hub.store.JobRegistry;
import com.monitoring.hub.store.ScheduleRegistry;

/**
 * Schedule 등록의 단일 진입점. spec §4 / §5.1.
 *
 * <p>데모 UI는 Schedule 등록 폼 하나에서 job_type/spec/cron/target_agent_id를
 * 모두 받는다 (spec §4.2). 본 서비스는 그 요청을 받아 내부적으로
 * {@link JobDefinition}과 {@link ScheduleDefinition} 두 record를 짝으로 생성하고
 * Quartz Scheduler에 {@link CronTrigger}를 등록한다.
 *
 * <p>Trigger의 misfire policy는 {@code MISFIRE_INSTRUCTION_DO_NOTHING} —
 * BE 자체가 다운됐다 복구된 경우에도 누적 실행을 회피한다 (spec §5.1 +
 * ADR #17). 데모는 Quartz의 RAMJobStore를 쓰므로 재시작 시 Trigger도 사라지지만,
 * 본개발에서 JDBCJobStore로 영속화해도 동일 policy를 유지하면 된다.
 *
 * <p>JobDetail의 {@link JobKey} / Trigger의 {@link TriggerKey}는 모두 scheduleId
 * 기반으로 1:1 매핑한다 — 데모는 단순화. 본개발에서 그룹/네임스페이스 도입 검토.
 */
@Service
public class ScheduleService {

    private static final Logger log = LoggerFactory.getLogger(ScheduleService.class);
    private static final String JOB_GROUP = "monitoring-schedules";
    private static final String TRIGGER_GROUP = "monitoring-triggers";

    private final JobRegistry jobRegistry;
    private final ScheduleRegistry scheduleRegistry;
    private final Scheduler scheduler;

    public ScheduleService(
            JobRegistry jobRegistry,
            ScheduleRegistry scheduleRegistry,
            Scheduler scheduler) {
        this.jobRegistry = jobRegistry;
        this.scheduleRegistry = scheduleRegistry;
        this.scheduler = scheduler;
    }

    /**
     * Schedule을 등록한다 — JobDefinition·ScheduleDefinition 짝 생성 + Quartz Trigger 등록.
     *
     * @return 생성된 ScheduleDefinition (scheduleId·jobId 포함)
     */
    public ScheduleDefinition register(
            JobType jobType,
            Map<String, Object> spec,
            String targetAgentId,
            String cronExpression) {

        String jobId = UUID.randomUUID().toString();
        String scheduleId = UUID.randomUUID().toString();

        JobDefinition jobDef = new JobDefinition(jobId, jobType, spec);
        ScheduleDefinition scheduleDef = new ScheduleDefinition(
                scheduleId, jobId, targetAgentId, cronExpression, true);

        jobRegistry.register(jobDef);
        scheduleRegistry.register(scheduleDef);

        try {
            scheduleQuartzJob(scheduleDef);
        } catch (SchedulerException ex) {
            throw new IllegalStateException(
                    "failed to schedule Quartz job for schedule_id=" + scheduleId, ex);
        }

        log.info("registered schedule: schedule_id={} job_id={} target_agent={} cron={}",
                scheduleId, jobId, targetAgentId, cronExpression);
        return scheduleDef;
    }

    private void scheduleQuartzJob(ScheduleDefinition schedule) throws SchedulerException {
        JobDetail jobDetail = JobBuilder.newJob(ScheduleTriggerJob.class)
                .withIdentity(jobKey(schedule.scheduleId()))
                .usingJobData(ScheduleTriggerJob.SCHEDULE_ID_KEY, schedule.scheduleId())
                .storeDurably(false)
                .build();

        CronTrigger trigger = TriggerBuilder.newTrigger()
                .withIdentity(triggerKey(schedule.scheduleId()))
                .withSchedule(CronScheduleBuilder.cronSchedule(schedule.cronExpression())
                        // spec §5.1 + ADR #17 — 누적 실행 회피.
                        .withMisfireHandlingInstructionDoNothing())
                .forJob(jobDetail)
                .build();

        scheduler.scheduleJob(jobDetail, trigger);
    }

    private static JobKey jobKey(String scheduleId) {
        return new JobKey("schedule-" + scheduleId, JOB_GROUP);
    }

    private static TriggerKey triggerKey(String scheduleId) {
        return new TriggerKey("trigger-" + scheduleId, TRIGGER_GROUP);
    }
}
