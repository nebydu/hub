package com.monitoring.hub.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 단순 health check. {@code GET /health → "OK"}.
 *
 * <p>actuator 등 별도 의존성 없이 plain controller로 둔다. 데모 콘솔(Thymeleaf)과
 * 다른 endpoint는 각 전용 controller가 별도로 담당한다.
 */
@RestController
public final class HealthController {

    @GetMapping("/health")
    public String health() {
        return "OK";
    }
}
