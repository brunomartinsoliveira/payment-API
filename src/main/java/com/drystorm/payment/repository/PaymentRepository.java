package com.drystorm.payment.repository;

import com.drystorm.payment.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    Optional<Payment> findByIdempotencyKey(String idempotencyKey);

    List<Payment> findByMerchantIdOrderByCreatedAtDesc(String merchantId);

    @Query("""
        SELECT p FROM Payment p
        WHERE p.status = 'PENDING_RETRY'
          AND p.nextRetryAt <= CURRENT_TIMESTAMP
          AND p.attemptCount < p.maxAttempts
        ORDER BY p.nextRetryAt ASC
    """)
    List<Payment> findPaymentsReadyForRetry();

    @Query("SELECT COUNT(p) FROM Payment p WHERE p.status = :status")
    long countByStatus(@Param("status") Payment.PaymentStatus status);
}
