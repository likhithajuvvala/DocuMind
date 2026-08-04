package com.documind.common.config;

import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@Configuration
@EnableTransactionManagement
@EntityScan(basePackages = "com.documind.common.persistence.entity")
@EnableJpaRepositories(basePackages = "com.documind.common.persistence.repository")
public class PersistenceConfiguration {
}
