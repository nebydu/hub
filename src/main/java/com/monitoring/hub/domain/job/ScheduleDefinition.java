package com.monitoring.hub.domain.job;

/**
 * Schedule 정의. spec §4.1 — "어떤 Job을 언제, 어떤 Agent에서" 실행할지.
 *
 * <p>{@link com.monitoring.hub.store.ScheduleRegistry}가
 * {@code Map<ScheduleId, ScheduleDefinition>}로 관리.
 *
 * <p>실제 트리거는 Quartz {@link org.quartz.CronTrigger}가 cronExpression을 따라
 * 주기적으로 발생시키며, 트리거마다
 * {@link com.monitoring.hub.scheduler.ScheduleTriggerJob}이 실행되어 commands
 * 토픽에 발행한다.
 *
 * <p>{@code enabled}는 일시 정지 기능을 위한 자리 — 데모는 등록 시 항상 true로
 * 시작. 본개발에서 토글 endpoint 추가 예정.
 *
 * @param scheduleId      이 Schedule의 식별자 (UUIDv4, spec §2.4)
 * @param jobId           이 Schedule이 트리거할 JobDefinition의 ID
 * @param targetAgentId   실행 대상 Agent
 * @param cronExpression  Quartz cron 표현식 (예: {@code "0 0/5 * * * ?"} — 5분 간격)
 * @param enabled         활성 여부 — false면 Quartz는 트리거를 만들지 않는다
 */
public record ScheduleDefinition(
        String scheduleId,
        String jobId,
        String targetAgentId,
        String cronExpression,
        boolean enabled
) {
}
