
package com.bankingplatform.card.repository;
import com.bankingplatform.card.model.Card;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List; import java.util.UUID;
public interface CardRepository extends JpaRepository<Card, UUID> {
    List<Card> findByCustomerId(UUID customerId);
    List<Card> findByAccountId(UUID accountId);
}
