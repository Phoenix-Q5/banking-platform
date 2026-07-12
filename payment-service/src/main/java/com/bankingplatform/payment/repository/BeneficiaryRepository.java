
package com.bankingplatform.payment.repository;
import com.bankingplatform.payment.model.Beneficiary;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List; import java.util.UUID;
public interface BeneficiaryRepository extends JpaRepository<Beneficiary, UUID> {
    List<Beneficiary> findByCustomerId(UUID customerId);
}
