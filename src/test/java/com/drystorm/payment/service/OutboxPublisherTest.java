package com.drystorm.payment.service;

import com.drystorm.payment.entity.OutboxEvent;
import com.drystorm.payment.repository.OutboxEventRepository;
import com.drystorm.payment.service.outbox.OutboxPublisher;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("OutboxPublisher — publicação de eventos")
class OutboxPublisherTest {

    @Mock OutboxEventRepository outboxRepository;
    @Mock RabbitTemplate rabbitTemplate;
    @InjectMocks OutboxPublisher publisher;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(publisher, "batchSize", 10);
    }

    @Test
    @DisplayName("Deve publicar eventos PENDING e marcá-los como PUBLISHED")
    void publishPendingEvents_success() {
        OutboxEvent event = OutboxEvent.builder()
                .id(UUID.randomUUID())
                .eventType(OutboxEvent.EVENT_PAYMENT_CREATED)
                .routingKey("payments.process")
                .payload("{\"id\":\"test\"}")
                .status(OutboxEvent.OutboxStatus.PENDING)
                .attempts(0)
                .build();

        given(outboxRepository.findPendingBatchForUpdate(10)).willReturn(List.of(event));
        given(outboxRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

        publisher.publishPendingEvents();

        assertThat(event.getStatus()).isEqualTo(OutboxEvent.OutboxStatus.PUBLISHED);
        assertThat(event.getPublishedAt()).isNotNull();
        then(rabbitTemplate).should().convertAndSend(any(), eq("payments.process"), any(Object.class));
    }

    @Test
    @DisplayName("Deve incrementar tentativas e manter PENDING em caso de falha no RabbitMQ")
    void publishPendingEvents_rabbitMQFails_incrementsAttempts() {
        OutboxEvent event = OutboxEvent.builder()
                .id(UUID.randomUUID())
                .eventType(OutboxEvent.EVENT_PAYMENT_CREATED)
                .routingKey("payments.process")
                .payload("{\"id\":\"test\"}")
                .status(OutboxEvent.OutboxStatus.PENDING)
                .attempts(0)
                .build();

        given(outboxRepository.findPendingBatchForUpdate(10)).willReturn(List.of(event));
        given(outboxRepository.save(any())).willAnswer(inv -> inv.getArgument(0));
        willThrow(new RuntimeException("RabbitMQ connection refused"))
                .given(rabbitTemplate).convertAndSend(any(), anyString(), any(Object.class));

        publisher.publishPendingEvents();

        assertThat(event.getStatus()).isEqualTo(OutboxEvent.OutboxStatus.PENDING);
        assertThat(event.getAttempts()).isEqualTo(1);
        assertThat(event.getLastError()).contains("connection refused");
    }

    @Test
    @DisplayName("Deve marcar como FAILED após 10 tentativas sem sucesso")
    void publishPendingEvents_exhaustedAttempts_marksAsFailed() {
        OutboxEvent event = OutboxEvent.builder()
                .id(UUID.randomUUID())
                .routingKey("payments.process")
                .payload("{}")
                .status(OutboxEvent.OutboxStatus.PENDING)
                .attempts(9) // próxima será a 10ª
                .build();

        given(outboxRepository.findPendingBatchForUpdate(10)).willReturn(List.of(event));
        given(outboxRepository.save(any())).willAnswer(inv -> inv.getArgument(0));
        willThrow(new RuntimeException("timeout"))
                .given(rabbitTemplate).convertAndSend(any(), anyString(), any(Object.class));

        publisher.publishPendingEvents();

        assertThat(event.getStatus()).isEqualTo(OutboxEvent.OutboxStatus.FAILED);
        assertThat(event.getAttempts()).isEqualTo(10);
    }

    @Test
    @DisplayName("Não deve fazer nada se não houver eventos pendentes")
    void publishPendingEvents_noPending_doesNothing() {
        given(outboxRepository.findPendingBatchForUpdate(10)).willReturn(List.of());

        publisher.publishPendingEvents();

        then(rabbitTemplate).should(never()).convertAndSend(any(), anyString(), any(Object.class));
        then(outboxRepository).should(never()).save(any());
    }
}
