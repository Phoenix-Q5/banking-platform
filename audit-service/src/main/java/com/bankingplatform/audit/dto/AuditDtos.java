
package com.bankingplatform.audit.dto;
import com.bankingplatform.audit.model.AuditEvent;
import jakarta.validation.constraints.*;
import java.time.Instant; import java.util.UUID;
public final class AuditDtos {
    private AuditDtos(){}
    public record CreateAuditEventRequest(
        @NotBlank String actor, @NotBlank String action, @NotBlank String resourceType,
        String resourceId, UUID customerId, String details, String ipAddress
    ){}
    public record AuditEventResponse(UUID id, String actor, String action, String resourceType, String resourceId,
                                     UUID customerId, String details, String ipAddress, Instant createdAt){
        public static AuditEventResponse from(AuditEvent e){
            return new AuditEventResponse(e.getId(), e.getActor(), e.getAction(), e.getResourceType(), e.getResourceId(),
                e.getCustomerId(), e.getDetails(), e.getIpAddress(), e.getCreatedAt());
        }
    }
}
