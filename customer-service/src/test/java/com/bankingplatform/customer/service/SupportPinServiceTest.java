package com.bankingplatform.customer.service;

import com.bankingplatform.customer.dto.CustomerDtos.SupportPinVerifyResponse;
import com.bankingplatform.customer.model.Customer;
import com.bankingplatform.customer.repository.CustomerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

class SupportPinServiceTest {

    private CustomerRepository repository;
    private SupportPinService service;
    private Customer customer;
    private final UUID customerId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        repository = Mockito.mock(CustomerRepository.class);
        service = new SupportPinService(repository);
        customer = new Customer();
        customer.setId(customerId);
        customer.setEmail("pin.test@example.com");
        customer.setFirstName("Pin");
        customer.setLastName("Test");
        when(repository.findById(customerId)).thenReturn(Optional.of(customer));
        when(repository.save(any(Customer.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void setPinStoresHashNotPlaintext() {
        service.setPin(customerId, "1234");
        assertNotNull(customer.getSupportPinHash());
        assertNotEquals("1234", customer.getSupportPinHash());
        assertTrue(customer.getSupportPinHash().startsWith("$2"));
        assertNotNull(customer.getSupportPinSetAt());
    }

    @Test
    void verifySucceedsWithCorrectPinAndResetsFailures() {
        service.setPin(customerId, "4321");
        customer.setSupportPinFailedAttempts(3);
        SupportPinVerifyResponse result = service.verify(customerId, "4321");
        assertTrue(result.verified());
        assertEquals(0, customer.getSupportPinFailedAttempts());
    }

    @Test
    void verifyFailsWithWrongPinAndCountsAttempts() {
        service.setPin(customerId, "1234");
        SupportPinVerifyResponse result = service.verify(customerId, "0000");
        assertFalse(result.verified());
        assertFalse(result.locked());
        assertEquals(SupportPinService.MAX_FAILED_ATTEMPTS - 1, result.attemptsRemaining());
    }

    @Test
    void locksAfterMaxFailedAttempts() {
        service.setPin(customerId, "1234");
        SupportPinVerifyResponse result = null;
        for (int i = 0; i < SupportPinService.MAX_FAILED_ATTEMPTS; i++) {
            result = service.verify(customerId, "0000");
        }
        assertNotNull(result);
        assertTrue(result.locked());
        assertNotNull(customer.getSupportPinLockedUntil());

        // Even the correct PIN is rejected while locked
        SupportPinVerifyResponse whileLocked = service.verify(customerId, "1234");
        assertFalse(whileLocked.verified());
        assertTrue(whileLocked.locked());
    }

    @Test
    void lockExpiryAllowsVerificationAgain() {
        service.setPin(customerId, "1234");
        customer.setSupportPinFailedAttempts(SupportPinService.MAX_FAILED_ATTEMPTS);
        customer.setSupportPinLockedUntil(Instant.now().minusSeconds(1));
        SupportPinVerifyResponse result = service.verify(customerId, "1234");
        assertTrue(result.verified());
    }

    @Test
    void verifyWithoutPinOnFileThrows() {
        assertThrows(IllegalArgumentException.class, () -> service.verify(customerId, "1234"));
    }
}
