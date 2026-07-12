
package com.bankingplatform.payment.repository;
import com.bankingplatform.payment.model.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List; import java.util.UUID;
public interface PaymentRepository extends JpaRepository<Payment, UUID> {
    List<Payment> findByCustomerIdOrderByCreatedAtDesc(UUID customerId);
    List<Payment> findByFromAccountIdOrderByCreatedAtDesc(UUID fromAccountId);
}
