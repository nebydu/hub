package com.monitoring.hub.scheduler;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.quartz.CronTrigger;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.Trigger;
import org.quartz.TriggerKey;
import org.quartz.impl.StdSchedulerFactory;
import org.quartz.impl.matchers.GroupMatcher;

import com.monitoring.hub.domain.job.JobType;
import com.monitoring.hub.domain.job.ScheduleDefinition;
import com.monitoring.hub.store.JobRegistry;
import com.monitoring.hub.store.ScheduleRegistry;

/**
 * Schedule 등록 후 Quartz Scheduler에 JobDetail + CronTrigger가 들어가는지,
 * misfire instruction이 spec §5.1 + ADR #17의 DO_NOTHING인지 검증.
 *
 * <p>Quartz scheduler를 standby로 두어 실제 fire는 발생시키지 않는다.
 */
class ScheduleServiceTest {

    private Scheduler scheduler;
    private JobRegistry jobRegistry;
    private ScheduleRegistry scheduleRegistry;
    private ScheduleService service;

    @BeforeEach
    void setUp() throws SchedulerException {
        scheduler = new StdSchedulerFactory().getScheduler();
        scheduler.standby(); // 등록만 확인 — fire는 막는다.
        jobRegistry = new JobRegistry();
        scheduleRegistry = new ScheduleRegistry();
        service = new ScheduleService(jobRegistry, scheduleRegistry, scheduler);
    }

    @AfterEach
    void tearDown() throws SchedulerException {
        scheduler.shutdown(true);
    }

    @Test
    void registerStoresDefinitionsAndQuartzArtifacts() throws SchedulerException {
        ScheduleDefinition created = service.register(
                JobType.SCRIPT_JOB,
                Map.of("script_path", "/opt/scripts/check_disk.sh"),
                "agent-001",
                "0 0/5 * * * ?");

        assertThat(jobRegistry.size()).isEqualTo(1);
        assertThat(scheduleRegistry.find(created.scheduleId())).isPresent();
        assertThat(created.jobId()).isNotBlank();
        assertThat(created.targetAgentId()).isEqualTo("agent-001");
        assertThat(created.enabled()).isTrue();

        Set<JobKey> jobKeys = scheduler.getJobKeys(GroupMatcher.anyGroup());
        assertThat(jobKeys).hasSize(1);

        Set<TriggerKey> triggerKeys = scheduler.getTriggerKeys(GroupMatcher.anyGroup());
        assertThat(triggerKeys).hasSize(1);

        Trigger trigger = scheduler.getTrigger(triggerKeys.iterator().next());
        assertThat(trigger).isInstanceOf(CronTrigger.class);
        // spec §5.1 + ADR #17 — 누적 실행 회피.
        assertThat(trigger.getMisfireInstruction())
                .isEqualTo(CronTrigger.MISFIRE_INSTRUCTION_DO_NOTHING);
    }

    @Test
    void registerLogJobUsesLogJobType() {
        ScheduleDefinition created = service.register(
                JobType.LOG_JOB,
                Map.of("log_path", "/var/log/app/error.log", "pattern", "ERROR"),
                "agent-002",
                "0 0/10 * * * ?");

        assertThat(jobRegistry.find(created.jobId())).hasValueSatisfying(
                job -> assertThat(job.jobType()).isEqualTo(JobType.LOG_JOB));
    }
}
