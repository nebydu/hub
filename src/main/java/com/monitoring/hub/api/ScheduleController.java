package com.monitoring.hub.api;

import java.util.Collection;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

import com.monitoring.hub.domain.job.ScheduleDefinition;
import com.monitoring.hub.scheduler.ScheduleService;
import com.monitoring.hub.store.ScheduleRegistry;

/**
 * Schedule 등록/조회 REST API. spec §4.2 데모 UI의 데이터 경로.
 *
 * <p>Thymeleaf 화면이 추가되는 다음 단계에서는 form submission도 같은 endpoint를
 * 거치도록 확장 가능. 현재는 JSON API만.
 */
@RestController
@RequestMapping("/schedules")
public class ScheduleController {

    private final ScheduleService service;
    private final ScheduleRegistry registry;

    public ScheduleController(ScheduleService service, ScheduleRegistry registry) {
        this.service = service;
        this.registry = registry;
    }

    @PostMapping
    public ResponseEntity<ScheduleDefinition> register(@Valid @RequestBody ScheduleRegistrationRequest req) {
        ScheduleDefinition created = service.register(
                req.jobType(), req.spec(), req.targetAgentId(), req.cron());
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping
    public Collection<ScheduleDefinition> list() {
        return registry.findAll();
    }
}
