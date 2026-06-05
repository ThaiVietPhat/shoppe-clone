package com.shopee.monolith.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.orm.jpa.persistenceunit.PersistenceUnitPostProcessor;

/**
 * Central JPA configuration: enables auditing and registers Spring Modulith's
 * internal JPA entity into the persistence unit.
 *
 * <p>Spring Boot 4 removed {@code @EntityScan} from the autoconfigure domain package.
 * Using {@link PersistenceUnitPostProcessor} is the Spring-native way to add managed
 * class names to the persistence unit without relying on boot-specific scanning annotations.
 *
 * <p>Without the Modulith entity registration, Hibernate cannot resolve
 * {@code DefaultJpaEventPublication} and the {@code EventPublicationRetryScheduler}
 * throws {@code UnknownEntityException} at runtime, silently swallowing all retries.
 */
@Configuration
@EnableJpaAuditing
public class JpaConfig {

    @Bean
    public PersistenceUnitPostProcessor modulithEntityRegistrar() {
        return persistenceUnitInfo ->
                persistenceUnitInfo.getManagedClassNames()
                        .add("org.springframework.modulith.events.jpa.DefaultJpaEventPublication");
    }
}

