package com.monitoring.hub.store;

import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

import org.apache.commons.collections4.queue.CircularFifoQueue;
import org.springframework.stereotype.Component;

import com.monitoring.hub.config.AppProperties;
import com.monitoring.hub.domain.audit.AuditEvent;

/**
 * audit-events 토픽 in-memory ring buffer. spec §4.3.
 *
 * <p>{@link CircularFifoQueue}는 capacity 초과 시 자동으로 가장 오래된 항목을
 * evict한다. Spring Kafka consumer 스레드(현재는 1개, 추후 다중 가능)와 향후
 * Thymeleaf 요청 스레드가 동시에 add / snapshot할 수 있으므로 {@link ReentrantLock}으로
 * 직렬화한다.
 *
 * <p>{@link #snapshot()}은 불변 복사본을 반환한다 — 호출 측이 반환 리스트를
 * 순회하는 동안 ring이 변형되어도 영향이 없다.
 *
 * <p>capacity는 {@link AppProperties.Audit#ringBufferSize()}에서 주입된다.
 * spec 기본값 200은 {@code application.yml}에서 부여.
 */
@Component
public final class AuditRingBuffer {

    private final CircularFifoQueue<AuditEvent> queue;
    private final ReentrantLock lock = new ReentrantLock();

    public AuditRingBuffer(AppProperties props) {
        this.queue = new CircularFifoQueue<>(props.audit().ringBufferSize());
    }

    /** 새 audit event를 ring에 추가. capacity 초과 시 가장 오래된 항목이 자동 evict. */
    public void add(AuditEvent event) {
        lock.lock();
        try {
            queue.add(event);
        } finally {
            lock.unlock();
        }
    }

    /** 현재 ring의 불변 스냅샷. 호출 시점의 순서 그대로 (oldest first). */
    public List<AuditEvent> snapshot() {
        lock.lock();
        try {
            return List.copyOf(queue);
        } finally {
            lock.unlock();
        }
    }

    /** 현재 ring에 담긴 항목 수. capacity와 같거나 작다. */
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
