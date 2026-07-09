package com.bankingplatform.notification.push;

import com.bankingplatform.notification.model.DeviceToken;
import com.bankingplatform.notification.model.Notification;
import com.bankingplatform.notification.repository.DeviceTokenRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Push dispatcher for Harbor Bank.
 * <p>
 * Production would call FCM/APNs/Web Push here. This implementation logs a structured
 * delivery record for every registered device token so the pipeline is end-to-end
 * verifiable without vendor credentials.
 */
@Component
public class PushNotificationDispatcher {

    private static final Logger log = LoggerFactory.getLogger(PushNotificationDispatcher.class);

    private final DeviceTokenRepository deviceTokenRepository;

    public PushNotificationDispatcher(DeviceTokenRepository deviceTokenRepository) {
        this.deviceTokenRepository = deviceTokenRepository;
    }

    public int dispatch(Notification notification) {
        List<DeviceToken> devices = deviceTokenRepository.findByCustomerIdAndActiveTrue(notification.getCustomerId());
        if (devices.isEmpty()) {
            log.info("push_skipped_no_devices customerId={} notificationId={} title={}",
                notification.getCustomerId(), notification.getId(), notification.getTitle());
            return 0;
        }
        for (DeviceToken device : devices) {
            // Vendor integration point: FCM / APNs / Web Push
            log.info("push_dispatched platform={} tokenSuffix={} customerId={} notificationId={} title={} body={}",
                device.getPlatform(),
                suffix(device.getToken()),
                notification.getCustomerId(),
                notification.getId(),
                notification.getTitle(),
                notification.getBody());
        }
        return devices.size();
    }

    private String suffix(String token) {
        if (token == null || token.length() < 8) return "****";
        return "…" + token.substring(token.length() - 8);
    }
}
