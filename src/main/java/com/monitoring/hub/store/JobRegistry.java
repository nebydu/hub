package com.monitoring.hub.store;

import java.util.Collection;
import java.util.Collections;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.springframework.stereotype.Component;

import com.monitoring.hub.domain.job.JobDefinition;

/**
 * 등록된 Job 정의의 in-memory 레지스트리. spec §4.3 — Map&lt;JobId, JobDefinition&gt;.
 *
 * <p>Schedule 등록 흐름의 일부로 함께 생성된다 (spec §4.2 — 데모 UI는 Job 등록
 * 화면을 별도로 노출하지 않는다). thread-safe.
 */
@Component
public final class JobRegistry {

    private final ConcurrentMap<String, JobDefinition> jobs = new ConcurrentHashMap<>();

    /** 새 Job 정의를 등록. 동일 id 재등록은 덮어쓴다. */
    public void register(JobDefinition job) {
        jobs.put(job.jobId(), job);
    }

    public Optional<JobDefinition> find(String jobId) {
        return Optional.ofNullable(jobs.get(jobId));
    }

    public Collection<JobDefinition> findAll() {
        return Collections.unmodifiableCollection(jobs.values());
    }

    public int size() {
        return jobs.size();
    }
}
