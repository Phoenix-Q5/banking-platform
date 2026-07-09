package com.bankingplatform.notification.messaging;

import com.bankingplatform.events.DomainEvent;
import com.bankingplatform.events.EventTypes;
import com.bankingplatform.notification.config.NotificationProperties;
import com.bankingplatform.notification.model.Notification;
import com.bankingplatform.notification.push.PushNotificationDispatcher;
import com.bankingplatform.notification.repository.NotificationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
public class AlertNotificationService {

    private static final Logger log = LoggerFactory.getLogger(AlertNotificationService.class);

    private final NotificationRepository notificationRepository;
    private final PushNotificationDispatcher pushDispatcher;
    private final NotificationProperties properties;

    public AlertNotificationService(NotificationRepository notificationRepository,
                                    PushNotificationDispatcher pushDispatcher,
                                    NotificationProperties properties) {
        this.notificationRepository = notificationRepository;
        this.pushDispatcher = pushDispatcher;
        this.properties = properties;
    }

    @Transactional
    public List<Notification> handleDomainEvent(DomainEvent event) {
        AlertContent content = mapEvent(event);
        if (content == null) {
            log.debug("event_ignored type={}", event.getEventType());
            return List.of();
        }

        List<UUID> recipients = resolveRecipients(event);
        if (recipients.isEmpty()) {
            log.warn("event_skipped_no_customer type={} id={}", event.getEventType(), event.getEventId());
            return List.of();
        }

        List<Notification> created = new ArrayList<>();
        for (UUID customerId : recipients) {
            created.addAll(createForCustomer(event, customerId, content));
        }
        log.info("alerts_created eventType={} recipients={} count={}", event.getEventType(), recipients.size(), created.size());
        return created;
    }

    private List<UUID> resolveRecipients(DomainEvent event) {
        List<UUID> ids = new ArrayList<>();
        Map<String, Object> p = event.getPayload() == null ? Map.of() : event.getPayload();
        if (EventTypes.TRANSFER_COMPLETED.equals(event.getEventType()) || EventTypes.TRANSFER_FAILED.equals(event.getEventType())) {
            addUuid(ids, p.get("fromCustomerId"));
            addUuid(ids, p.get("toCustomerId"));
        }
        if (ids.isEmpty() && event.getCustomerId() != null && !event.getCustomerId().isBlank()) {
            addUuid(ids, event.getCustomerId());
        }
        return ids;
    }

    private void addUuid(List<UUID> ids, Object value) {
        if (value == null) return;
        String s = value.toString().trim();
        if (s.isEmpty() || "null".equalsIgnoreCase(s)) return;
        UUID id = UUID.fromString(s);
        if (!ids.contains(id)) ids.add(id);
    }

    private List<Notification> createForCustomer(DomainEvent event, UUID customerId, AlertContent content) {
        List<Notification> created = new ArrayList<>();
        String dedupeKey = event.getEventId() + ":" + customerId;
        for (String channelName : properties.resolvedChannels()) {
            Notification.Channel channel = Notification.Channel.valueOf(channelName.trim().toUpperCase(Locale.ROOT));
            if (channel == Notification.Channel.PUSH && !properties.isPushEnabled()) continue;
            if (channel == Notification.Channel.EMAIL && !properties.isEmailEnabled()) continue;
            if (channel == Notification.Channel.SMS && !properties.isSmsEnabled()) continue;

            if (notificationRepository.existsByEventIdAndChannel(dedupeKey, channel)) {
                continue;
            }

            Notification n = new Notification();
            n.setCustomerId(customerId);
            n.setChannel(channel);
            n.setCategory(content.category());
            n.setTitle(content.title());
            n.setBody(content.body());
            n.setEventId(dedupeKey);
            n.setEventType(event.getEventType());
            n.setStatus(Notification.Status.SENT);
            Notification saved = notificationRepository.save(n);
            created.add(saved);

            if (channel == Notification.Channel.PUSH) {
                int devices = pushDispatcher.dispatch(saved);
                if (devices == 0) {
                    saved.setStatus(Notification.Status.PENDING);
                    notificationRepository.save(saved);
                }
            } else if (channel == Notification.Channel.EMAIL || channel == Notification.Channel.SMS) {
                log.info("{}_queued customerId={} title={}", channel.name().toLowerCase(Locale.ROOT), customerId, content.title());
            }
        }
        return created;
    }

    private AlertContent mapEvent(DomainEvent event) {
        Map<String, Object> p = event.getPayload() == null ? Map.of() : event.getPayload();
        String type = event.getEventType();
        return switch (type) {
            case EventTypes.TRANSFER_COMPLETED -> new AlertContent(
                "TRANSACTION",
                "Transfer completed",
                "Harbor Bank: " + money(p.get("amount"), p.get("currency"))
                    + " moved from account " + shortId(p.get("fromAccountId"))
                    + " to " + shortId(p.get("toAccountId")) + "."
            );
            case EventTypes.TRANSFER_FAILED -> new AlertContent(
                "TRANSACTION",
                "Transfer failed",
                "Harbor Bank could not complete your transfer"
                    + (p.get("failureReason") != null ? ": " + p.get("failureReason") : ".")
            );
            case EventTypes.PAYMENT_COMPLETED -> new AlertContent(
                "PAYMENT",
                "Payment completed",
                "Your " + str(p.get("paymentType")) + " payment of "
                    + money(p.get("amount"), p.get("currency")) + " was completed."
            );
            case EventTypes.ACCOUNT_OPENED -> new AlertContent(
                "ACCOUNT",
                "Account opened",
                "Your new Harbor Bank account " + str(p.get("accountNumber")) + " is ready."
            );
            case EventTypes.CARD_ISSUED -> new AlertContent(
                "CARD",
                "Card issued",
                "A new " + str(p.get("cardType")) + " card ending in " + str(p.get("last4")) + " is active."
            );
            case EventTypes.CARD_FROZEN -> new AlertContent(
                "CARD",
                "Card frozen",
                "Your card ending in " + str(p.get("last4")) + " has been frozen."
            );
            case EventTypes.CARD_UNFROZEN -> new AlertContent(
                "CARD",
                "Card unfrozen",
                "Your card ending in " + str(p.get("last4")) + " is active again."
            );
            case EventTypes.LOAN_APPLIED -> new AlertContent(
                "LOAN",
                "Loan application received",
                "We received your " + str(p.get("productCode")) + " application for "
                    + money(p.get("principal"), p.get("currency")) + "."
            );
            case EventTypes.LOAN_STATUS_CHANGED -> new AlertContent(
                "LOAN",
                "Loan status update",
                "Your loan application is now " + str(p.get("status")) + "."
            );
            default -> null;
        };
    }

    private String money(Object amount, Object currency) {
        String cur = currency == null ? "USD" : currency.toString();
        return amount == null ? cur : amount + " " + cur;
    }

    private String shortId(Object id) {
        if (id == null) return "****";
        String s = id.toString();
        return s.length() <= 8 ? s : s.substring(0, 8) + "…";
    }

    private String str(Object o) {
        return o == null ? "" : o.toString();
    }

    private record AlertContent(String category, String title, String body) {}
}
