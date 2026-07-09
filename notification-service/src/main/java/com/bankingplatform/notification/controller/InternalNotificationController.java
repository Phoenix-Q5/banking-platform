package com.bankingplatform.notification.controller;

import com.bankingplatform.notification.model.Notification;
import com.bankingplatform.notification.push.PushNotificationDispatcher;
import com.bankingplatform.notification.repository.DeviceTokenRepository;
import com.bankingplatform.notification.repository.NotificationRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Internal S2S endpoints for Harbor Bank service alerts (ops-agent → mobile admins).
 */
@RestController
@RequestMapping("/api/notifications/internal")
public class InternalNotificationController {

    /** Well-known audience id used by ADMIN/SUPPORT mobile device registrations. */
    public static final UUID SERVICE_ALERT_AUDIENCE_ID =
        UUID.fromString("00000000-0000-4000-8000-0000000000aa");

    private static final Logger log = LoggerFactory.getLogger(InternalNotificationController.class);

    private final NotificationRepository notificationRepository;
    private final DeviceTokenRepository deviceTokenRepository;
    private final PushNotificationDispatcher pushDispatcher;

    public InternalNotificationController(NotificationRepository notificationRepository,
                                          DeviceTokenRepository deviceTokenRepository,
                                          PushNotificationDispatcher pushDispatcher) {
        this.notificationRepository = notificationRepository;
        this.deviceTokenRepository = deviceTokenRepository;
        this.pushDispatcher = pushDispatcher;
    }

    public record ServiceAlertRequest(
        @NotBlank String title,
        @NotBlank String body,
        String category,
        String severity,
        String incidentId,
        String affectedService
    ) {}

    @PostMapping("/service-alert")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> publishServiceAlert(@Valid @RequestBody ServiceAlertRequest request) {
        String category = request.category() == null ? "SERVICE" : request.category().toUpperCase(Locale.ROOT);
        String eventId = request.incidentId() == null ? UUID.randomUUID().toString() : request.incidentId();

        int created = 0;
        int pushed = 0;
        for (String channelName : List.of("IN_APP", "PUSH")) {
            Notification.Channel channel = Notification.Channel.valueOf(channelName);
            String dedupe = eventId + ":service:" + channelName;
            if (notificationRepository.existsByEventIdAndChannel(dedupe, channel)) {
                continue;
            }
            Notification n = new Notification();
            n.setCustomerId(SERVICE_ALERT_AUDIENCE_ID);
            n.setChannel(channel);
            n.setCategory(category.startsWith("SERVICE") ? category : "SERVICE_" + category);
            n.setTitle(request.title());
            n.setBody(request.body() + (request.affectedService() == null ? "" : " [" + request.affectedService() + "]"));
            n.setEventId(dedupe);
            n.setEventType("service.alert");
            n.setStatus(Notification.Status.SENT);
            Notification saved = notificationRepository.save(n);
            created++;
            if (channel == Notification.Channel.PUSH) {
                pushed += pushDispatcher.dispatch(saved);
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("audienceId", SERVICE_ALERT_AUDIENCE_ID.toString());
        result.put("notificationsCreated", created);
        result.put("devicesPushed", pushed);
        result.put("registeredDevices", deviceTokenRepository.findByCustomerIdAndActiveTrue(SERVICE_ALERT_AUDIENCE_ID).size());
        log.info("service_alert_ingested incidentId={} created={} pushed={}", request.incidentId(), created, pushed);
        return result;
    }
}
