package com.documind.gateway.routing;

import static org.assertj.core.api.Assertions.assertThat;

import com.documind.common.security.CorsProperties;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

class CorsHeaderTest {

    @Test
    void downstreamServicesDoNotAddCorsHeadersOfTheirOwn() {
        CorsProperties downstream = new CorsProperties();

        assertThat(downstream.isEnabled())
                .as(
                        "only the gateway may answer CORS, otherwise a proxied response carries the header twice "
                                + "and the browser rejects it")
                .isFalse();
    }

    @Test
    void theGatewayAllowsExactlyOneConfiguredOrigin() {
        CorsProperties gateway = new CorsProperties();
        gateway.setEnabled(true);
        gateway.setAllowedOrigins(List.of("http://localhost:3000"));

        CorsConfiguration configuration = configurationFor(gateway, "/api/documents");

        assertThat(configuration).isNotNull();
        assertThat(configuration.getAllowedOrigins()).containsExactly("http://localhost:3000");
    }

    private CorsConfiguration configurationFor(CorsProperties properties, String path) {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(properties.getAllowedOrigins());
        configuration.setAllowedMethods(properties.getAllowedMethods());
        configuration.setAllowedHeaders(properties.getAllowedHeaders());

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", configuration);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
        request.addHeader("Origin", "http://localhost:3000");
        return ((CorsConfigurationSource) source).getCorsConfiguration(request);
    }
}
