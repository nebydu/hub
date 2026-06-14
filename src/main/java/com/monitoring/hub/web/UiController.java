package com.monitoring.hub.web;

import java.time.Instant;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import com.monitoring.hub.config.AppProperties;
import com.monitoring.hub.domain.job.JobType;
import com.monitoring.hub.scheduler.ScheduleService;
import com.monitoring.hub.store.AgentRegistry;
import com.monitoring.hub.store.AuditRingBuffer;
import com.monitoring.hub.store.CommandRingBuffer;
import com.monitoring.hub.store.HeartbeatLatestMap;
import com.monitoring.hub.store.JobResultRingBuffer;
import com.monitoring.hub.store.ScheduleRegistry;

/**
 * 데모 단일 페이지 UI. spec §4.2.
 *
 * <p>한 화면에 모든 in-memory state를 노출 — Agent 목록 / Schedule 등록 폼 /
 * Schedule 목록 / 최근 commands·job 결과·audit-events 3종 패널 + heartbeats
 * latest map. heartbeats는 ring buffer가 아닌 Agent별 최신 1개이므로 별도 컬럼.
 *
 * <p>본 컨트롤러는 기존 JSON REST API({@code /schedules}, {@code /commands})와
 * 공존한다 — 외부 자동화는 REST, 사람이 보는 화면은 본 페이지.
 */
@Controller
public class UiController {

    private final AgentRegistry agentRegistry;
    private final ScheduleRegistry scheduleRegistry;
    private final AuditRingBuffer auditBuffer;
    private final JobResultRingBuffer jobResultBuffer;
    private final CommandRingBuffer commandBuffer;
    private final HeartbeatLatestMap heartbeatMap;
    private final AppProperties appProperties;
    private final ScheduleService scheduleService;

    public UiController(
            AgentRegistry agentRegistry,
            ScheduleRegistry scheduleRegistry,
            AuditRingBuffer auditBuffer,
            JobResultRingBuffer jobResultBuffer,
            CommandRingBuffer commandBuffer,
            HeartbeatLatestMap heartbeatMap,
            AppProperties appProperties,
            ScheduleService scheduleService) {
        this.agentRegistry = agentRegistry;
        this.scheduleRegistry = scheduleRegistry;
        this.auditBuffer = auditBuffer;
        this.jobResultBuffer = jobResultBuffer;
        this.commandBuffer = commandBuffer;
        this.heartbeatMap = heartbeatMap;
        this.appProperties = appProperties;
        this.scheduleService = scheduleService;
    }

    @GetMapping("/")
    public String index(Model model) {
        model.addAttribute("now", Instant.now());
        model.addAttribute("heartbeatTimeoutSeconds", appProperties.agent().heartbeatTimeoutSeconds());
        model.addAttribute("agents", agentRegistry.findAll());
        model.addAttribute("heartbeats", heartbeatMap.snapshot());
        model.addAttribute("schedules", scheduleRegistry.findAll());
        model.addAttribute("commands", reversed(commandBuffer.snapshot()));
        model.addAttribute("jobResults", reversed(jobResultBuffer.snapshot()));
        model.addAttribute("auditEvents", reversed(auditBuffer.snapshot()));
        model.addAttribute("jobTypes", JobType.values());
        return "index";
    }

    @PostMapping(path = "/ui/schedules")
    public String submitSchedule(@ModelAttribute ScheduleFormRequest form) {
        scheduleService.register(form.jobType(), form.toSpec(), form.targetAgentId(), form.cron());
        return "redirect:/";
    }

    /** ring buffer는 oldest-first지만 화면은 최신을 위에 보여주는 게 직관적. */
    private static <T> java.util.List<T> reversed(java.util.List<T> list) {
        return list.reversed();
    }
}
