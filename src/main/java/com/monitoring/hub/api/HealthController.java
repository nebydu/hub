package com.monitoring.hub.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 단순 health check. {@code GET /health → "OK"}.
 *
 * <p>1단계 골격에서는 actuator 등 별도 의존성 없이 plain controller로 둔다.
 * 본격 화면(Thymeleaf)과 다른 endpoint는 다음 단계 영역.
 */
@RestController
public final class HealthController {

    @GetMapping("/health")
    public String health() {
        return "OK";
    }
}
