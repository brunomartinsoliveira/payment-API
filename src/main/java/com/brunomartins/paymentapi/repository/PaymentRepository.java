package com.brunomartins.paymentapi.repository;

import com.brunomartins.paymentapi.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {
    List<Payment> findByMerchantId(String merchantId);
}
