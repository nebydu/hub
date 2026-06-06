package com.monitoring.hub.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * hub 애플리케이션의 환경변수 기반 설정.
 *
 * <p>prefix는 {@code hub}. {@code application.yml}의 {@code hub.*} 블록을 매핑한다.
 * Spring Kafka 자체 설정({@code spring.kafka.*})은 Spring Boot 자동 구성이 별도로
 * 매핑하므로 여기에는 두지 않는다. brokers만 도메인 설정으로 노출해
 * KafkaConfig가 ConsumerFactory를 만들 때 단일 진실로 활용한다.
 *
 * <p>각 도메인(audit/job/agent)의 ring buffer 또는 latest map 용량과 heartbeat
 * timeout 등 in-memory 자료구조 파라미터를 한 곳에 모은다.
 */
@ConfigurationProperties(prefix = "hub")
@Validated
public record AppProperties(
        @Valid @NotNull Kafka kafka,
        @Valid @NotNull Audit audit,
        @Valid @NotNull Job job,
        @Valid @NotNull Command command,
        @Valid @NotNull Agent agent
) {

    /** Kafka 접속 설정. */
    public record Kafka(
            /** 콤마 구분 brokers (예: {@code localhost:9092}). spec §1 인프라 전제. */
            @NotBlank String brokers
    ) {
    }

    /** Audit 도메인 설정. */
    public record Audit(
            /** audit-topic ring buffer 용량. spec §4.3 기본 200. */
            @Positive int ringBufferSize
    ) {
    }

    /** Job 도메인 설정. */
    public record Job(
            /** job-results ring buffer 용량. spec §4.3 기본 100. */
            @Positive int ringBufferSize
    ) {
    }

    /** Command 도메인 설정 (BE → Agent 발행 이력). */
    public record Command(
            /** command-topic ring buffer 용량. spec §4.3 기본 50. */
            @Positive int ringBufferSize
    ) {
    }

    /** Agent 도메인 설정. */
    public record Agent(
            /**
             * heartbeat 미수신 허용 시간(초). spec §3.2 — 이 시간 동안 heartbeat이
             * 끊기면 Agent를 OFFLINE 후보로 본다. 데모 단계에서는 별도 sweeper를
             * 두지 않고 화면 렌더 시점에 판단할 수 있는 기준값으로만 사용.
             */
            @Positive int heartbeatTimeoutSeconds
    ) {
    }
}
