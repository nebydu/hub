package com.monitoring.hub;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * 모니터링 솔루션 데모 BE(hub) 엔트리포인트.
 *
 * <p>이 모듈의 위상: Agent로부터의 데이터 수집(audit / job-results / heartbeats)뿐
 * 아니라 BE Quartz의 commands 발행까지 Agent와의 양방향 통신 중심 역할을 한다.
 * 추후 모듈 분할 시 hub-ingest / hub-scheduler / hub-api 등으로 확장될 prefix.
 *
 * <p>1단계 골격은 audit-topic 한 토픽만 소비한다. command-topic / job-results /
 * heartbeats-topic 경로는 추후 단계에서 추가된다.
 */
@SpringBootApplication
@ConfigurationPropertiesScan(basePackages = "com.monitoring.hub.config")
public class HubApplication {

    public static void main(String[] args) {
        SpringApplication.run(HubApplication.class, args);
    }
}
