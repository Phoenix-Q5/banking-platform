package com.bankingplatform.customer.repository;

import com.bankingplatform.customer.model.Customer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CustomerRepository extends JpaRepository<Customer, UUID> {
    Optional<Customer> findByEmailIgnoreCase(String email);
    Optional<Customer> findByExternalUserId(String externalUserId);
    List<Customer> findByLastNameContainingIgnoreCase(String lastName);
}
