package com.bankingplatform.customer.service;

import com.bankingplatform.customer.dto.CustomerDtos.CustomerResponse;
import com.bankingplatform.customer.dto.CustomerDtos.RegisterCustomerRequest;
import com.bankingplatform.customer.keycloak.KeycloakAdminClient;
import com.bankingplatform.customer.keycloak.KeycloakAdminClient.CreateUserCommand;
import com.bankingplatform.customer.model.Customer;
import com.bankingplatform.customer.repository.CustomerRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
public class CustomerRegistrationService {

    private static final Logger log = LoggerFactory.getLogger(CustomerRegistrationService.class);

    private final CustomerRepository repository;
    private final KeycloakAdminClient keycloak;

    public CustomerRegistrationService(CustomerRepository repository, KeycloakAdminClient keycloak) {
        this.repository = repository;
        this.keycloak = keycloak;
    }

    @Transactional
    public CustomerResponse register(RegisterCustomerRequest request) {
        String username = request.username().trim().toLowerCase(Locale.ROOT);
        String email = request.email().trim().toLowerCase(Locale.ROOT);

        if (repository.findByEmailIgnoreCase(email).isPresent()) {
            throw new IllegalArgumentException("Customer already exists for email " + email);
        }
        if (repository.findByExternalUserId(username).isPresent()) {
            throw new IllegalArgumentException("Username already taken: " + username);
        }

        String keycloakUserId = keycloak.createUser(new CreateUserCommand(
            username,
            request.password(),
            email,
            request.firstName().trim(),
            request.lastName().trim()
        ));

        try {
            Customer customer = new Customer();
            customer.setExternalUserId(username);
            customer.setEmail(email);
            customer.setFirstName(request.firstName().trim());
            customer.setLastName(request.lastName().trim());
            customer.setPhone(request.phone());
            customer.setDateOfBirth(request.dateOfBirth());
            customer.setAddressLine1(request.addressLine1());
            customer.setAddressLine2(request.addressLine2());
            customer.setCity(request.city());
            customer.setState(request.state());
            customer.setPostalCode(request.postalCode());
            if (request.country() != null && !request.country().isBlank()) {
                customer.setCountry(request.country().trim().toUpperCase(Locale.ROOT));
            }
            Customer saved = repository.save(customer);
            log.info("customer_registered customerId={} username={} keycloakUserId={}",
                saved.getId(), username, keycloakUserId);
            return CustomerResponse.from(saved);
        } catch (RuntimeException ex) {
            keycloak.deleteUser(keycloakUserId);
            throw ex;
        }
    }
}
