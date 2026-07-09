package com.bankingplatform.customer.controller;

import com.bankingplatform.customer.dto.CustomerDtos.*;
import com.bankingplatform.customer.exception.NotFoundException;
import com.bankingplatform.customer.model.Customer;
import com.bankingplatform.customer.repository.CustomerRepository;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

@RestController
@RequestMapping("/api/customers")
public class CustomerController {

    private static final Logger log = LoggerFactory.getLogger(CustomerController.class);
    private final CustomerRepository repository;

    public CustomerController(CustomerRepository repository) {
        this.repository = repository;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CustomerResponse create(@Valid @RequestBody CreateCustomerRequest request) {
        repository.findByEmailIgnoreCase(request.email()).ifPresent(c -> {
            throw new IllegalArgumentException("Customer already exists for email " + request.email());
        });
        Customer customer = new Customer();
        customer.setExternalUserId(request.externalUserId());
        customer.setEmail(request.email().toLowerCase(Locale.ROOT));
        customer.setFirstName(request.firstName());
        customer.setLastName(request.lastName());
        customer.setPhone(request.phone());
        customer.setDateOfBirth(request.dateOfBirth());
        customer.setAddressLine1(request.addressLine1());
        customer.setAddressLine2(request.addressLine2());
        customer.setCity(request.city());
        customer.setState(request.state());
        customer.setPostalCode(request.postalCode());
        if (request.country() != null) {
            customer.setCountry(request.country().toUpperCase(Locale.ROOT));
        }
        Customer saved = repository.save(customer);
        log.info("customer_created customerId={} email={}", saved.getId(), saved.getEmail());
        return CustomerResponse.from(saved);
    }

    @GetMapping("/{id}")
    public CustomerResponse get(@PathVariable UUID id) {
        return CustomerResponse.from(find(id));
    }

    @GetMapping
    public List<CustomerResponse> search(
        @RequestParam(required = false) String email,
        @RequestParam(required = false) String lastName,
        @RequestParam(required = false) String externalUserId
    ) {
        if (email != null && !email.isBlank()) {
            return repository.findByEmailIgnoreCase(email)
                .map(CustomerResponse::from)
                .map(List::of)
                .orElse(List.of());
        }
        if (externalUserId != null && !externalUserId.isBlank()) {
            return repository.findByExternalUserId(externalUserId)
                .map(CustomerResponse::from)
                .map(List::of)
                .orElse(List.of());
        }
        if (lastName != null && !lastName.isBlank()) {
            return repository.findByLastNameContainingIgnoreCase(lastName).stream()
                .map(CustomerResponse::from)
                .toList();
        }
        return repository.findAll().stream().map(CustomerResponse::from).toList();
    }

    @PutMapping("/{id}")
    public CustomerResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateCustomerRequest request) {
        Customer customer = find(id);
        if (request.phone() != null) customer.setPhone(request.phone());
        if (request.addressLine1() != null) customer.setAddressLine1(request.addressLine1());
        if (request.addressLine2() != null) customer.setAddressLine2(request.addressLine2());
        if (request.city() != null) customer.setCity(request.city());
        if (request.state() != null) customer.setState(request.state());
        if (request.postalCode() != null) customer.setPostalCode(request.postalCode());
        if (request.country() != null) customer.setCountry(request.country().toUpperCase(Locale.ROOT));
        return CustomerResponse.from(repository.save(customer));
    }

    @PostMapping("/{id}/kyc")
    public CustomerResponse updateKyc(@PathVariable UUID id, @Valid @RequestBody KycUpdateRequest request) {
        Customer customer = find(id);
        customer.setKycStatus(Customer.KycStatus.valueOf(request.kycStatus().toUpperCase(Locale.ROOT)));
        Customer saved = repository.save(customer);
        log.info("customer_kyc_updated customerId={} kycStatus={}", id, saved.getKycStatus());
        return CustomerResponse.from(saved);
    }

    @PostMapping("/{id}/suspend")
    public CustomerResponse suspend(@PathVariable UUID id) {
        Customer customer = find(id);
        customer.setStatus(Customer.Status.SUSPENDED);
        return CustomerResponse.from(repository.save(customer));
    }

    private Customer find(UUID id) {
        return repository.findById(id).orElseThrow(() -> new NotFoundException("Customer not found: " + id));
    }
}
