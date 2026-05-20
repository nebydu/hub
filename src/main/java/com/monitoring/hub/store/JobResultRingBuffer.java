package com.monitoring.hub.store;

import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

import org.apache.commons.collections4.queue.CircularFifoQueue;
import org.springframework.stereotype.Component;

import com.monitoring.hub.config.AppProperties;
import com.monitoring.hub.domain.job.JobResult;

/**
 * job-results 토픽 in-memory ring buffer. spec §4.3.
 *
 * <p>{@link AuditRingBuffer}와 동일한 구조 (CircularFifoQueue + ReentrantLock).
 * capacity는 {@link AppProperties.Job#ringBufferSize()}에서 주입되며 spec 기본값
 * 100은 {@code application.yml}에서 부여.
 */
@Component
public final class JobResultRingBuffer {

    private final CircularFifoQueue<JobResult> queue;
    private final ReentrantLock lock = new ReentrantLock();

    public JobResultRingBuffer(AppProperties props) {
        this.queue = new CircularFifoQueue<>(props.job().ringBufferSize());
    }

    /** 새 job result를 ring에 추가. capacity 초과 시 가장 오래된 항목이 자동 evict. */
    public void add(JobResult result) {
        lock.lock();
        try {
            queue.add(result);
        } finally {
            lock.unlock();
        }
    }

    /** 현재 ring의 불변 스냅샷. 호출 시점의 순서 그대로 (oldest first). */
    public List<JobResult> snapshot() {
        lock.lock();
        try {
            return List.copyOf(queue);
        } finally {
            lock.unlock();
        }
    }

    /** 현재 ring에 담긴 항목 수. */
    public int size() {
        lock.lock();
        try {
            return queue.size();
        } finally {
            lock.unlock();
        }
    }

    /** ring의 capacity (고정). */
    public int capacity() {
        return queue.maxSize();
    }
}
