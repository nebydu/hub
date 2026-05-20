package com.monitoring.hub.store;

import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

import org.apache.commons.collections4.queue.CircularFifoQueue;
import org.springframework.stereotype.Component;

import com.monitoring.hub.config.AppProperties;
import com.monitoring.hub.domain.command.Command;

/**
 * BE가 발행한 commands의 in-memory ring buffer. spec §4.3.
 *
 * <p>{@link com.monitoring.hub.producer.CommandPublisher}가 Kafka send 시점에
 * 함께 적재 — UI에서 발행 이력을 조회하기 위한 자리 (Thymeleaf 화면이 추가되는
 * 다음 단계에서 활용).
 *
 * <p>capacity는 {@link AppProperties.Command#ringBufferSize()}에서 주입되며
 * spec 기본값 50은 {@code application.yml}에서 부여.
 */
@Component
public final class CommandRingBuffer {

    private final CircularFifoQueue<Command> queue;
    private final ReentrantLock lock = new ReentrantLock();

    public CommandRingBuffer(AppProperties props) {
        this.queue = new CircularFifoQueue<>(props.command().ringBufferSize());
    }

    /** 새 command를 ring에 추가. capacity 초과 시 가장 오래된 항목이 자동 evict. */
    public void add(Command command) {
        lock.lock();
        try {
            queue.add(command);
        } finally {
            lock.unlock();
        }
    }

    /** 현재 ring의 불변 스냅샷 (oldest first). */
    public List<Command> snapshot() {
        lock.lock();
        try {
            return List.copyOf(queue);
        } finally {
            lock.unlock();
        }
    }

    public int size() {
        lock.lock();
        try {
            return queue.size();
        } finally {
            lock.unlock();
        }
    }

    public int capacity() {
        return queue.maxSize();
    }
}
