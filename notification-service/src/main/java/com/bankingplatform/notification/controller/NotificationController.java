package com.bankingplatform.notification.controller;

import com.bankingplatform.notification.dto.NotificationDtos.*;
import com.bankingplatform.notification.exception.NotFoundException;
import com.bankingplatform.notification.model.DeviceToken;
import com.bankingplatform.notification.model.Notification;
import com.bankingplatform.notification.repository.DeviceTokenRepository;
import com.bankingplatform.notification.repository.NotificationRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private static final Logger log = LoggerFactory.getLogger(NotificationController.class);

    private final NotificationRepository repository;
    private final DeviceTokenRepository deviceTokenRepository;

    public NotificationController(NotificationRepository repository, DeviceTokenRepository deviceTokenRepository) {
        this.repository = repository;
        this.deviceTokenRepository = deviceTokenRepository;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public NotificationResponse create(@Valid @RequestBody CreateNotificationRequest request) {
        Notification n = new Notification();
        n.setCustomerId(request.customerId());
        n.setChannel(Notification.Channel.valueOf(request.channel().toUpperCase(Locale.ROOT)));
        n.setCategory(request.category());
        n.setTitle(request.title());
        n.setBody(request.body());
        n.setStatus(Notification.Status.SENT);
        Notification saved = repository.save(n);
        log.info("notification_sent id={} customerId={} channel={}", saved.getId(), saved.getCustomerId(), saved.getChannel());
        return NotificationResponse.from(saved);
    }

    @GetMapping
    public List<NotificationResponse> list(@RequestParam UUID customerId) {
        return repository.findByCustomerIdOrderByCreatedAtDesc(customerId).stream()
            .map(NotificationResponse::from)
            .toList();
    }

    @PostMapping("/{id}/read")
    public NotificationResponse markRead(@PathVariable UUID id) {
        Notification n = repository.findById(id).orElseThrow(() -> new NotFoundException("Notification not found: " + id));
        n.setStatus(Notification.Status.READ);
        n.setReadAt(Instant.now());
        return NotificationResponse.from(repository.save(n));
    }

    @PostMapping("/devices")
    @ResponseStatus(HttpStatus.CREATED)
    public DeviceTokenResponse registerDevice(@Valid @RequestBody RegisterDeviceRequest request) {
        DeviceToken device = deviceTokenRepository.findByToken(request.token()).orElseGet(DeviceToken::new);
        device.setCustomerId(request.customerId());
        device.setPlatform(DeviceToken.Platform.valueOf(request.platform().toUpperCase(Locale.ROOT)));
        device.setToken(request.token());
        device.setActive(true);
        DeviceToken saved = deviceTokenRepository.save(device);
        log.info("device_registered customerId={} platform={}", saved.getCustomerId(), saved.getPlatform());
        return DeviceTokenResponse.from(saved);
    }

    @DeleteMapping("/devices/{token}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deactivateDevice(@PathVariable String token) {
        deviceTokenRepository.findByToken(token).ifPresent(d -> {
            d.setActive(false);
            deviceTokenRepository.save(d);
        });
    }

    @GetMapping("/devices")
    public List<DeviceTokenResponse> listDevices(@RequestParam UUID customerId) {
        return deviceTokenRepository.findByCustomerIdAndActiveTrue(customerId).stream()
            .map(DeviceTokenResponse::from)
            .toList();
    }

    public record RegisterDeviceRequest(
        @NotNull UUID customerId,
        @NotBlank String platform,
        @NotBlank String token
    ) {}

    public record DeviceTokenResponse(UUID id, UUID customerId, String platform, String token, boolean active) {
        public static DeviceTokenResponse from(DeviceToken d) {
            return new DeviceTokenResponse(d.getId(), d.getCustomerId(), d.getPlatform().name(), d.getToken(), d.isActive());
        }
    }
}
