package com.documind.common.config;

import com.documind.common.logging.RequestCorrelationFilter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

@Configuration
public class LoggingConfiguration {

    @Bean
    public FilterRegistrationBean<RequestCorrelationFilter> requestCorrelationFilter() {
        FilterRegistrationBean<RequestCorrelationFilter> registration =
                new FilterRegistrationBean<>(new RequestCorrelationFilter());
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        registration.addUrlPatterns("/*");
        return registration;
    }
}
