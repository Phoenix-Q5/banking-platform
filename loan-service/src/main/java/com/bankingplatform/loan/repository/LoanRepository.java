
package com.bankingplatform.loan.repository;
import com.bankingplatform.loan.model.Loan;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List; import java.util.UUID;
public interface LoanRepository extends JpaRepository<Loan, UUID> {
    List<Loan> findByCustomerIdOrderByCreatedAtDesc(UUID customerId);
    List<Loan> findByStatus(Loan.Status status);
}
