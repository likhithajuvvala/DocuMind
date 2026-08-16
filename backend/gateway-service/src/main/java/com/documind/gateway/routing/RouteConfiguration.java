package com.documind.gateway.routing;

import static org.springframework.cloud.gateway.server.mvc.filter.BeforeFilterFunctions.uri;
import static org.springframework.cloud.gateway.server.mvc.handler.GatewayRouterFunctions.route;
import static org.springframework.cloud.gateway.server.mvc.handler.HandlerFunctions.http;
import static org.springframework.web.servlet.function.RequestPredicates.path;

import com.documind.gateway.ratelimit.RateLimitProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.ServerResponse;

@Configuration
@EnableConfigurationProperties({DownstreamServiceProperties.class, RateLimitProperties.class})
public class RouteConfiguration {

    @Bean
    public RouterFunction<ServerResponse> documentServiceRoutes(
            DownstreamServiceProperties properties) {
        return route("document-service")
                .route(path("/api/documents/**"), http())
                .before(uri(properties.getDocumentService()))
                .build();
    }

    @Bean
    public RouterFunction<ServerResponse> queryServiceRoutes(
            DownstreamServiceProperties properties) {
        return route("query-service")
                .route(path("/api/chat/**"), http())
                .route(path("/api/admin/**"), http())
                .before(uri(properties.getQueryService()))
                .build();
    }
}
