package com.monitoring.hub.producer;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.Header;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import com.monitoring.hub.config.AppProperties;
import com.monitoring.hub.config.KafkaConfig;
import com.monitoring.hub.domain.command.Command;
import com.monitoring.hub.domain.job.JobType;
import com.monitoring.hub.store.CommandRingBuffer;

class CommandPublisherTest {

    @SuppressWarnings("unchecked")
    private final KafkaTemplate<String, Command> template = mock(KafkaTemplate.class);
    private CommandRingBuffer buffer;
    private CommandPublisher publisher;

    @BeforeEach
    void setUp() {
        AppProperties props = new AppProperties(
                new AppProperties.Kafka("localhost:9092"),
                new AppProperties.Audit(200),
                new AppProperties.Job(100),
                new AppProperties.Command(50),
                new AppProperties.Agent(30)
        );
        buffer = new CommandRingBuffer(props);
        publisher = new CommandPublisher(template, buffer);

        // KafkaTemplate.send는 fire-and-forget — 완료되지 않은 future로 충분.
        when(template.send(any(ProducerRecord.class)))
                .thenReturn(new CompletableFuture<SendResult<String, Command>>());
    }

    private static Command sampleCommand() {
        return new Command(
                "8f4b1c9e-0000-0000-0000-000000000000",
                "3a7d2b5f-0000-0000-0000-000000000000",
                "9c1e8a4d-0000-0000-0000-000000000000",
                "agent-001",
                JobType.SCRIPT_JOB,
                Instant.parse("2026-05-19T14:00:00Z"),
                Instant.parse("2026-05-19T14:04:30Z"),
                Map.of("script_path", "/opt/scripts/check_disk.sh"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void publishWritesEnvelopeHeadersAndUsesTargetAgentIdAsKey() {
        publisher.publish(sampleCommand(), null);

        ArgumentCaptor<ProducerRecord<String, Command>> captor = ArgumentCaptor.forClass(ProducerRecord.class);
        verify(template).send(captor.capture());
        ProducerRecord<String, Command> sent = captor.getValue();

        assertThat(sent.topic()).isEqualTo(KafkaConfig.Topics.COMMANDS);
        assertThat(sent.key()).isEqualTo("agent-001");

        Map<String, String> headers = headerMap(sent);
        assertThat(headers).containsKey(CommandPublisher.HEADER_MESSAGE_ID);
        assertThat(headers.get(CommandPublisher.HEADER_MESSAGE_ID)).isNotBlank();
        assertThat(headers).containsEntry(CommandPublisher.HEADER_MESSAGE_VERSION, "1");
        assertThat(headers).containsEntry(CommandPublisher.HEADER_SOURCE, "monitoring-be");
        assertThat(headers).doesNotContainKey(CommandPublisher.HEADER_TRACE_ID);
    }

    @Test
    @SuppressWarnings("unchecked")
    void publishAttachesTraceIdHeaderWhenProvided() {
        publisher.publish(sampleCommand(), "trace-xyz");

        ArgumentCaptor<ProducerRecord<String, Command>> captor = ArgumentCaptor.forClass(ProducerRecord.class);
        verify(template).send(captor.capture());

        Map<String, String> headers = headerMap(captor.getValue());
        assertThat(headers).containsEntry(CommandPublisher.HEADER_TRACE_ID, "trace-xyz");
    }

    @Test
    void publishAppendsToRingBuffer() {
        publisher.publish(sampleCommand(), null);

        assertThat(buffer.size()).isEqualTo(1);
        assertThat(buffer.snapshot().get(0).executionId())
                .isEqualTo("8f4b1c9e-0000-0000-0000-000000000000");
    }

    private static Map<String, String> headerMap(ProducerRecord<?, ?> record) {
        Map<String, String> result = new HashMap<>();
        for (Header h : record.headers()) {
            result.put(h.key(), new String(h.value(), UTF_8));
        }
        return result;
    }
}
