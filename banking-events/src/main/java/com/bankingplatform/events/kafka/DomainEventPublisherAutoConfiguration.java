package com.bankingplatform.events.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;

@Configuration
@ConditionalOnClass(KafkaTemplate.class)
public class DomainEventPublisherAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(name = "harbor.kafka.enabled", havingValue = "true", matchIfMissing = true)
    public DomainEventPublisher domainEventPublisher(KafkaTemplate<String, String> kafkaTemplate,
                                                     ObjectMapper objectMapper) {
        return new DomainEventPublisher(kafkaTemplate, objectMapper, true);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(name = "harbor.kafka.enabled", havingValue = "false")
    public DomainEventPublisher noopDomainEventPublisher(ObjectMapper objectMapper) {
        return new DomainEventPublisher(null, objectMapper, false);
    }
}
