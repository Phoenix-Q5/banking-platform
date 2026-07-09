package com.bankingplatform.notification.repository;

import com.bankingplatform.notification.model.Notification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {
    List<Notification> findByCustomerIdOrderByCreatedAtDesc(UUID customerId);
    boolean existsByEventIdAndChannel(String eventId, Notification.Channel channel);
}
