package com.monitoring.hub.store;

import java.util.Collection;
import java.util.Collections;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.springframework.stereotype.Component;

import com.monitoring.hub.domain.job.ScheduleDefinition;

/**
 * 등록된 Schedule 정의의 in-memory 레지스트리. spec §4.3 —
 * Map&lt;ScheduleId, ScheduleDefinition&gt;.
 *
 * <p>실제 트리거링은 Quartz가 담당. 본 레지스트리는 Quartz Job 안에서
 * scheduleId로 정의를 lookup하는 용도와, UI/REST에서 목록을 조회하는 용도다.
 */
@Component
public final class ScheduleRegistry {

    private final ConcurrentMap<String, ScheduleDefinition> schedules = new ConcurrentHashMap<>();

    /** 새 Schedule 정의를 등록. 동일 id 재등록은 덮어쓴다. */
    public void register(ScheduleDefinition schedule) {
        schedules.put(schedule.scheduleId(), schedule);
    }

    public Optional<ScheduleDefinition> find(String scheduleId) {
        return Optional.ofNullable(schedules.get(scheduleId));
    }

    public Collection<ScheduleDefinition> findAll() {
        return Collections.unmodifiableCollection(schedules.values());
    }

    public int size() {
        return schedules.size();
    }
}
