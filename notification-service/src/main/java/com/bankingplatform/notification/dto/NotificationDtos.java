package com.bankingplatform.notification.dto;

import com.bankingplatform.notification.model.Notification;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.UUID;

public final class NotificationDtos {
    private NotificationDtos() {}

    public record CreateNotificationRequest(
        @NotNull UUID customerId,
        @NotBlank String channel,
        @NotBlank String category,
        @NotBlank String title,
        @NotBlank String body
    ) {}

    public record NotificationResponse(
        UUID id,
        UUID customerId,
        String channel,
        String category,
        String title,
        String body,
        String status,
        Instant readAt,
        String eventId,
        String eventType,
        Instant createdAt
    ) {
        public static NotificationResponse from(Notification n) {
            return new NotificationResponse(
                n.getId(), n.getCustomerId(), n.getChannel().name(), n.getCategory(),
                n.getTitle(), n.getBody(), n.getStatus().name(), n.getReadAt(),
                n.getEventId(), n.getEventType(), n.getCreatedAt()
            );
        }
    }
}
