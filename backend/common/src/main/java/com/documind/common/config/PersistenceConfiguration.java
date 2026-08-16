package com.documind.common.config;

import com.documind.common.tenant.WorkspaceScopedTransactionManager;
import jakarta.persistence.EntityManagerFactory;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@Configuration
@EnableTransactionManagement
@EntityScan(basePackages = "com.documind.common.persistence.entity")
@EnableJpaRepositories(basePackages = "com.documind.common.persistence.repository")
public class PersistenceConfiguration {

    // Replaces Spring Boot's auto-configured JpaTransactionManager (which backs off once any
    // PlatformTransactionManager bean exists) so that every service built on this shared
    // configuration enforces workspace isolation at the persistence layer by construction, rather
    // than each service needing to remember to wire this up itself.
    @Bean
    public PlatformTransactionManager transactionManager(
            EntityManagerFactory entityManagerFactory) {
        return new WorkspaceScopedTransactionManager(entityManagerFactory);
    }
}
