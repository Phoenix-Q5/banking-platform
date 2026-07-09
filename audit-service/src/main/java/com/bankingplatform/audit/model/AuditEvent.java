
package com.bankingplatform.audit.model;
import jakarta.persistence.*;
import java.time.Instant; import java.util.UUID;
@Entity @Table(name="audit_events")
public class AuditEvent {
    @Id private UUID id;
    @Column(nullable=false) private String actor;
    @Column(nullable=false) private String action;
    @Column(name="resource_type", nullable=false) private String resourceType;
    @Column(name="resource_id") private String resourceId;
    @Column(name="customer_id") private UUID customerId;
    private String details;
    @Column(name="ip_address") private String ipAddress;
    @Column(name="created_at", nullable=false) private Instant createdAt;
    @PrePersist void onCreate(){ if(id==null) id=UUID.randomUUID(); createdAt=Instant.now(); }
    public UUID getId(){return id;} public void setId(UUID id){this.id=id;}
    public String getActor(){return actor;} public void setActor(String actor){this.actor=actor;}
    public String getAction(){return action;} public void setAction(String action){this.action=action;}
    public String getResourceType(){return resourceType;} public void setResourceType(String resourceType){this.resourceType=resourceType;}
    public String getResourceId(){return resourceId;} public void setResourceId(String resourceId){this.resourceId=resourceId;}
    public UUID getCustomerId(){return customerId;} public void setCustomerId(UUID customerId){this.customerId=customerId;}
    public String getDetails(){return details;} public void setDetails(String details){this.details=details;}
    public String getIpAddress(){return ipAddress;} public void setIpAddress(String ipAddress){this.ipAddress=ipAddress;}
    public Instant getCreatedAt(){return createdAt;}
}
