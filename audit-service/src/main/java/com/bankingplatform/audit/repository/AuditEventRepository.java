
package com.bankingplatform.audit.repository;
import com.bankingplatform.audit.model.AuditEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List; import java.util.UUID;
public interface AuditEventRepository extends JpaRepository<AuditEvent, UUID> {
    List<AuditEvent> findByCustomerIdOrderByCreatedAtDesc(UUID customerId);
    List<AuditEvent> findByResourceTypeAndResourceIdOrderByCreatedAtDesc(String resourceType, String resourceId);
    List<AuditEvent> findTop100ByOrderByCreatedAtDesc();
}
