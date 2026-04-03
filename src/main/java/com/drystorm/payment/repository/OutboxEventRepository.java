package com.drystorm.payment.repository;

import com.drystorm.payment.entity.OutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {

    /**
     * Busca batch de eventos pendentes com lock pessimista para evitar
     * processamento duplicado em ambientes com múltiplas instâncias.
     */
    @Query(value = """
        SELECT * FROM outbox_events
        WHERE status = 'PENDING'
        ORDER BY created_at ASC
        LIMIT :batchSize
        FOR UPDATE SKIP LOCKED
        """, nativeQuery = true)
    List<OutboxEvent> findPendingBatchForUpdate(@Param("batchSize") int batchSize);

    @Modifying
    @Query("DELETE FROM OutboxEvent o WHERE o.status = 'PUBLISHED' AND o.publishedAt < CURRENT_TIMESTAMP - 7 DAY")
    int deleteOldPublishedEvents();
}
