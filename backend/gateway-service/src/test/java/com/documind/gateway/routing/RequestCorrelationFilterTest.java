package com.documind.gateway.routing;

import static org.assertj.core.api.Assertions.assertThat;

import com.documind.common.logging.RequestCorrelationFilter;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Collections;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class RequestCorrelationFilterTest {

    private final RequestCorrelationFilter filter = new RequestCorrelationFilter();

    @Test
    void generatesARequestIdWhenTheCallerDidNotSendOne() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/documents");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getHeader(RequestCorrelationFilter.REQUEST_ID_HEADER)).isNotBlank();
    }

    @Test
    void doesNotRepeatAnInboundRequestIdOnTheResponse() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/documents");
        request.addHeader(RequestCorrelationFilter.REQUEST_ID_HEADER, "abc-123");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getHeader(RequestCorrelationFilter.REQUEST_ID_HEADER))
                .as(
                        "a proxied response already carries the header set by the service that minted the id, "
                                + "so setting it again returns it twice")
                .isNull();
        assertThat(
                        ((HttpServletRequest) chain.getRequest())
                                .getHeader(RequestCorrelationFilter.REQUEST_ID_HEADER))
                .isEqualTo("abc-123");
    }

    @Test
    void passesAGeneratedIdOnToTheProxiedRequestSoServicesShareIt() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/documents");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        HttpServletRequest forwarded = (HttpServletRequest) chain.getRequest();
        String forwardedId = forwarded.getHeader(RequestCorrelationFilter.REQUEST_ID_HEADER);
        assertThat(forwardedId)
                .isEqualTo(response.getHeader(RequestCorrelationFilter.REQUEST_ID_HEADER));
        assertThat(Collections.list(forwarded.getHeaderNames()))
                .contains(RequestCorrelationFilter.REQUEST_ID_HEADER);
    }

    @Test
    void doesNotLeakTheRequestIdIntoLaterRequestsOnTheSameThread() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/documents");

        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        assertThat(MDC.get(RequestCorrelationFilter.REQUEST_ID_KEY)).isNull();
    }
}
