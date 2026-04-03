package com.drystorm.payment.service.outbox;

import com.drystorm.payment.config.RabbitMQConfig;
import com.drystorm.payment.entity.OutboxEvent;
import com.drystorm.payment.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Outbox Publisher — publica eventos pendentes no RabbitMQ.
 *
 * Roda a cada ${app.outbox.poll-interval-ms} ms e processa um batch
 * de eventos PENDING. Usa FOR UPDATE SKIP LOCKED para ser seguro em
 * ambientes com múltiplas instâncias da aplicação (horizontal scaling).
 *
 * Garante entrega at-least-once: se o RabbitMQ cair, os eventos
 * permanecem PENDING e serão publicados na próxima execução.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OutboxPublisher {

    private final OutboxEventRepository outboxRepository;
    private final RabbitTemplate rabbitTemplate;

    @Value("${app.outbox.batch-size:50}")
    private int batchSize;

    @Scheduled(fixedDelayString = "${app.outbox.poll-interval-ms:3000}")
    @Transactional
    public void publishPendingEvents() {
        List<OutboxEvent> pending = outboxRepository.findPendingBatchForUpdate(batchSize);

        if (pending.isEmpty()) return;

        log.debug("[OUTBOX] Processando {} eventos pendentes", pending.size());

        int published = 0, failed = 0;

        for (OutboxEvent event : pending) {
            try {
                rabbitTemplate.convertAndSend(
                        RabbitMQConfig.EXCHANGE,
                        event.getRoutingKey(),
                        event.getPayload()
                );

                event.setStatus(OutboxEvent.OutboxStatus.PUBLISHED);
                event.setPublishedAt(LocalDateTime.now());
                published++;

                log.debug("[OUTBOX] Evento publicado id={} type={} routing={}",
                        event.getId(), event.getEventType(), event.getRoutingKey());

            } catch (Exception ex) {
                failed++;
                event.setAttempts(event.getAttempts() + 1);
                event.setLastError(ex.getMessage());

                // Após 10 tentativas de publicação, marca como FAILED
                if (event.getAttempts() >= 10) {
                    event.setStatus(OutboxEvent.OutboxStatus.FAILED);
                    log.error("[OUTBOX] Evento marcado como FAILED após {} tentativas id={}",
                            event.getAttempts(), event.getId());
                } else {
                    log.warn("[OUTBOX] Falha ao publicar evento id={} tentativa={} erro={}",
                            event.getId(), event.getAttempts(), ex.getMessage());
                }
            }

            outboxRepository.save(event);
        }

        if (published > 0 || failed > 0) {
            log.info("[OUTBOX] Ciclo concluído: {} publicados, {} falhas", published, failed);
        }
    }

    /**
     * Limpeza de eventos publicados há mais de 7 dias.
     * Roda diariamente às 03:00.
     */
    @Scheduled(cron = "0 0 3 * * *")
    @Transactional
    public void cleanOldEvents() {
        int deleted = outboxRepository.deleteOldPublishedEvents();
        if (deleted > 0) {
            log.info("[OUTBOX] Limpeza: {} eventos antigos removidos", deleted);
        }
    }
}
