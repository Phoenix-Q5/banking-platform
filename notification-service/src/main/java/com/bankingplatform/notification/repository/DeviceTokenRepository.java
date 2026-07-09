package com.bankingplatform.notification.repository;

import com.bankingplatform.notification.model.DeviceToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DeviceTokenRepository extends JpaRepository<DeviceToken, UUID> {
    List<DeviceToken> findByCustomerIdAndActiveTrue(UUID customerId);
    Optional<DeviceToken> findByToken(String token);
}
