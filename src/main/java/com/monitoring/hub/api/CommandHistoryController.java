package com.monitoring.hub.api;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.monitoring.hub.domain.command.Command;
import com.monitoring.hub.store.CommandRingBuffer;

/**
 * 최근 발행 commands 조회 — UI 진입점 자리. spec §4.3 commands ring buffer.
 *
 * <p>Thymeleaf 화면이 추가되는 다음 단계에서는 audit / result-topic-job·log / heartbeats /
 * agents도 같은 패턴으로 노출. 현재는 commands만 JSON으로.
 */
@RestController
public class CommandHistoryController {

    private final CommandRingBuffer buffer;

    public CommandHistoryController(CommandRingBuffer buffer) {
        this.buffer = buffer;
    }

    @GetMapping("/commands")
    public List<Command> recent() {
        return buffer.snapshot();
    }
}
