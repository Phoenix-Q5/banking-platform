package com.bankingplatform.customer.config;

import com.bankingplatform.customer.model.Customer;
import com.bankingplatform.customer.repository.CustomerRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.time.Instant;
import java.time.LocalDate;

@Configuration
public class DemoDataConfig {

    /** Demo support PIN for seeded customers (documented in README). Stored hashed. */
    private static final String DEMO_SUPPORT_PIN = "1234";

    private static final Logger log = LoggerFactory.getLogger(DemoDataConfig.class);

    @Bean
    CommandLineRunner seedCustomers(CustomerRepository repository) {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        return args -> {
            if (repository.findByEmailIgnoreCase("demo.customer@example.com").isEmpty()) {
                Customer c = new Customer();
                c.setExternalUserId("demo.customer");
                c.setEmail("demo.customer@example.com");
                c.setFirstName("Demo");
                c.setLastName("Customer");
                c.setPhone("+1-555-0100");
                c.setDateOfBirth(LocalDate.of(1990, 5, 15));
                c.setAddressLine1("100 Market Street");
                c.setCity("San Francisco");
                c.setState("CA");
                c.setPostalCode("94105");
                c.setCountry("US");
                c.setKycStatus(Customer.KycStatus.VERIFIED);
                c.setStatus(Customer.Status.ACTIVE);
                c.setSupportPinHash(encoder.encode(DEMO_SUPPORT_PIN));
                c.setSupportPinSetAt(Instant.now());
                repository.save(c);
                log.info("seeded demo customer {}", c.getEmail());
            }
            if (repository.findByEmailIgnoreCase("alex.rivera@example.com").isEmpty()) {
                Customer c = new Customer();
                c.setExternalUserId("demo.customer2");
                c.setEmail("alex.rivera@example.com");
                c.setFirstName("Alex");
                c.setLastName("Rivera");
                c.setPhone("+1-555-0101");
                c.setDateOfBirth(LocalDate.of(1988, 11, 2));
                c.setAddressLine1("200 Mission Street");
                c.setCity("San Francisco");
                c.setState("CA");
                c.setPostalCode("94105");
                c.setCountry("US");
                c.setKycStatus(Customer.KycStatus.IN_REVIEW);
                c.setStatus(Customer.Status.ACTIVE);
                c.setSupportPinHash(encoder.encode(DEMO_SUPPORT_PIN));
                c.setSupportPinSetAt(Instant.now());
                repository.save(c);
                log.info("seeded demo customer {}", c.getEmail());
            }
            // Backfill the demo PIN for pre-existing databases created before support PINs existed.
            for (String email : new String[] {"demo.customer@example.com", "alex.rivera@example.com"}) {
                repository.findByEmailIgnoreCase(email)
                    .filter(c -> c.getSupportPinHash() == null)
                    .ifPresent(c -> {
                        c.setSupportPinHash(encoder.encode(DEMO_SUPPORT_PIN));
                        c.setSupportPinSetAt(Instant.now());
                        repository.save(c);
                        log.info("backfilled demo support pin for {}", email);
                    });
            }
        };
    }
}
