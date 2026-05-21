package com.monitoring.hub.config;

import java.util.HashMap;
import java.util.Map;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.monitoring.hub.domain.audit.AuditEvent;
import com.monitoring.hub.domain.command.Command;
import com.monitoring.hub.domain.job.JobResult;

/**
 * Kafka 관련 설정의 단일 진입점.
 *
 * <p>토픽 이름 상수와 각 토픽 전용 consumer / listener container factory를
 * 정의한다. brokers는 {@link AppProperties}의 단일 진실에서 가져와 Spring Boot
 * 자동 구성({@code spring.kafka.consumer.*})에 override한다.
 *
 * <p>현재 정의된 토픽:
 * <ul>
 *   <li>audit-events (typed AuditEvent consumer)</li>
 *   <li>job-results (typed JobResult consumer)</li>
 *   <li>heartbeats (raw String consumer — OTLP JSON을 직접 파싱)</li>
 *   <li>commands (typed Command producer)</li>
 * </ul>
 */
@Configuration
public class KafkaConfig {

    /** Kafka 토픽 이름 상수. spec §1 토픽 명명 규약. */
    public static final class Topics {

        /** Agent → BE 감사 이벤트 토픽. spec §5.3. */
        public static final String AUDIT_EVENTS = "audit-events";

        /** Agent → BE Job 실행 결과. spec §5.2. 다음 단계 사용. */
        public static final String JOB_RESULTS = "job-results";

        /** BE → Agent 명령. spec §5.1. 다음 단계 사용. */
        public static final String COMMANDS = "commands";

        /** OTel Collector → BE heartbeat (OTLP JSON). spec §5.4. 다음 단계 사용. */
        public static final String HEARTBEATS = "heartbeats";

        private Topics() {
        }
    }

    /**
     * audit-events 전용 {@link ConsumerFactory}.
     *
     * <p>{@link JsonDeserializer}를 직접 인스턴스화해 {@code ignoreTypeHeaders()}를
     * 명시한다 — script-agent는 {@code __TypeId__} 헤더를 발행하지 않으므로
     * payload class를 코드에 박는다.
     *
     * <p>Spring Boot가 자동으로 만드는 {@code Map<String, Object>}에서
     * {@code spring.kafka.consumer.*}(group-id, auto-offset-reset 등) 설정을 흡수하고,
     * brokers만 {@link AppProperties}로 override한다.
     */
    @Bean
    public ConsumerFactory<String, AuditEvent> auditEventConsumerFactory(
            AppProperties appProperties,
            KafkaProperties kafkaProperties,
            ObjectMapper objectMapper) {

        Map<String, Object> props = new HashMap<>(kafkaProperties.buildConsumerProperties(null));
        // brokers 단일 진실: hub.kafka.brokers.
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, appProperties.kafka().brokers());

        StringDeserializer keyDeserializer = new StringDeserializer();
        // 글로벌 ObjectMapper(snake_case 정책 + JavaTimeModule)를 주입 — payload가
        // snake_case로 발행되므로 자체 ObjectMapper(camelCase 디폴트)를 쓰면
        // event_id/occurred_at 같은 필드가 null로 매핑된다.
        JsonDeserializer<AuditEvent> valueDeserializer = new JsonDeserializer<>(AuditEvent.class, objectMapper, false);
        valueDeserializer.ignoreTypeHeaders();
        // spec §2.4 payload 외부에서 온 신뢰 가능 클래스 단일.
        valueDeserializer.addTrustedPackages("com.monitoring.hub.domain.audit");

        return new DefaultKafkaConsumerFactory<>(props, keyDeserializer, valueDeserializer);
    }

    /**
     * audit-events 전용 listener container factory. {@code @KafkaListener}의
     * {@code containerFactory} 속성으로 참조된다.
     */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, AuditEvent> auditEventListenerFactory(
            ConsumerFactory<String, AuditEvent> auditEventConsumerFactory) {

        ConcurrentKafkaListenerContainerFactory<String, AuditEvent> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(auditEventConsumerFactory);
        return factory;
    }

    /**
     * job-results 전용 {@link ConsumerFactory}. 구조는 {@code auditEventConsumerFactory}와
     * 동일 — payload 클래스만 {@link JobResult}로 바뀐다.
     *
     * <p>{@code addTrustedPackages}는 신뢰 가능 payload 패키지를 명시 — JobResult의
     * 중첩 record(ScriptResult/LogResult)도 같은 패키지에 있으므로 단일 항목으로
     * 충분.
     */
    @Bean
    public ConsumerFactory<String, JobResult> jobResultConsumerFactory(
            AppProperties appProperties,
            KafkaProperties kafkaProperties,
            ObjectMapper objectMapper) {

        Map<String, Object> props = new HashMap<>(kafkaProperties.buildConsumerProperties(null));
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, appProperties.kafka().brokers());

        StringDeserializer keyDeserializer = new StringDeserializer();
        // auditEventConsumerFactory와 같은 이유로 글로벌 ObjectMapper 주입.
        JsonDeserializer<JobResult> valueDeserializer = new JsonDeserializer<>(JobResult.class, objectMapper, false);
        valueDeserializer.ignoreTypeHeaders();
        valueDeserializer.addTrustedPackages("com.monitoring.hub.domain.job");

        return new DefaultKafkaConsumerFactory<>(props, keyDeserializer, valueDeserializer);
    }

    /** job-results 전용 listener container factory. */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, JobResult> jobResultListenerFactory(
            ConsumerFactory<String, JobResult> jobResultConsumerFactory) {

        ConcurrentKafkaListenerContainerFactory<String, JobResult> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(jobResultConsumerFactory);
        return factory;
    }

    /**
     * heartbeats 전용 {@link ConsumerFactory}. OTel Collector가 발행하는 OTLP JSON은
     * 본 도메인의 typed record로 매핑하지 않고 {@link String}으로 받아
     * {@code HeartbeatConsumer}가 직접 트리 파싱한다 (spec §5.4.2).
     *
     * <p>spec §2.2 envelope 헤더 규약은 heartbeats에 적용되지 않으므로
     * {@link JsonDeserializer}의 {@code __TypeId__} 같은 메타 또한 신경 쓸 필요 없음.
     */
    @Bean
    public ConsumerFactory<String, String> heartbeatConsumerFactory(
            AppProperties appProperties,
            KafkaProperties kafkaProperties) {

        Map<String, Object> props = new HashMap<>(kafkaProperties.buildConsumerProperties(null));
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, appProperties.kafka().brokers());

        return new DefaultKafkaConsumerFactory<>(props, new StringDeserializer(), new StringDeserializer());
    }

    /** heartbeats 전용 listener container factory. */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String> heartbeatListenerFactory(
            ConsumerFactory<String, String> heartbeatConsumerFactory) {

        ConcurrentKafkaListenerContainerFactory<String, String> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(heartbeatConsumerFactory);
        return factory;
    }

    /**
     * commands 토픽 전용 {@link ProducerFactory}. key는 spec §2.3에 따라
     * {@code target_agent_id}(String). value는 {@link Command}를 글로벌
     * {@link ObjectMapper}(snake_case + JavaTimeModule)로 직렬화.
     *
     * <p>{@code addTypeInfo=false}로 두어 Spring Kafka의 {@code __TypeId__} 헤더가
     * 발행되지 않게 한다 — spec §2.2 envelope 4종 외 헤더는 추가하지 않는 게
     * 원칙. payload type은 토픽 자체로 결정된다.
     */
    @Bean
    public ProducerFactory<String, Command> commandProducerFactory(
            AppProperties appProperties,
            KafkaProperties kafkaProperties,
            ObjectMapper objectMapper) {

        Map<String, Object> props = new HashMap<>(kafkaProperties.buildProducerProperties(null));
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, appProperties.kafka().brokers());

        JsonSerializer<Command> valueSerializer = new JsonSerializer<>(objectMapper);
        valueSerializer.setAddTypeInfo(false);

        return new DefaultKafkaProducerFactory<>(props, new StringSerializer(), valueSerializer);
    }

    /**
     * commands 전용 {@link KafkaTemplate}. {@link com.monitoring.hub.producer.CommandPublisher}가
     * 사용한다.
     */
    @Bean
    public KafkaTemplate<String, Command> commandKafkaTemplate(
            ProducerFactory<String, Command> commandProducerFactory) {
        return new KafkaTemplate<>(commandProducerFactory);
    }
}
