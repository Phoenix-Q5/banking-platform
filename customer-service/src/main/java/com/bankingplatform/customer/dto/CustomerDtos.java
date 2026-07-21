package com.bankingplatform.customer.dto;

import com.bankingplatform.customer.model.Customer;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public final class CustomerDtos {

    private CustomerDtos() {}

    public record CreateCustomerRequest(
        String externalUserId,
        @NotBlank @Email String email,
        @NotBlank @Size(max = 100) String firstName,
        @NotBlank @Size(max = 100) String lastName,
        String phone,
        LocalDate dateOfBirth,
        String addressLine1,
        String addressLine2,
        String city,
        String state,
        String postalCode,
        @Size(min = 2, max = 2) String country
    ) {}

    /** Self-serve registration: creates Keycloak credentials + customer profile. */
    public record RegisterCustomerRequest(
        @NotBlank @Size(min = 3, max = 64) String username,
        @NotBlank @Size(min = 8, max = 128) String password,
        @NotBlank @Email String email,
        @NotBlank @Size(max = 100) String firstName,
        @NotBlank @Size(max = 100) String lastName,
        String phone,
        LocalDate dateOfBirth,
        String addressLine1,
        String addressLine2,
        String city,
        String state,
        String postalCode,
        @Size(min = 2, max = 2) String country
    ) {}

    public record UpdateCustomerRequest(
        String phone,
        String addressLine1,
        String addressLine2,
        String city,
        String state,
        String postalCode,
        @Size(min = 2, max = 2) String country
    ) {}

    public record KycUpdateRequest(
        @NotBlank String kycStatus
    ) {}

    public record CustomerResponse(
        UUID id,
        String externalUserId,
        String email,
        String firstName,
        String lastName,
        String phone,
        LocalDate dateOfBirth,
        String addressLine1,
        String addressLine2,
        String city,
        String state,
        String postalCode,
        String country,
        String kycStatus,
        String status,
        Instant createdAt,
        Instant updatedAt
    ) {
        public static CustomerResponse from(Customer c) {
            return new CustomerResponse(
                c.getId(), c.getExternalUserId(), c.getEmail(), c.getFirstName(), c.getLastName(),
                c.getPhone(), c.getDateOfBirth(), c.getAddressLine1(), c.getAddressLine2(),
                c.getCity(), c.getState(), c.getPostalCode(), c.getCountry(),
                c.getKycStatus().name(), c.getStatus().name(), c.getCreatedAt(), c.getUpdatedAt()
            );
        }
    }
}
