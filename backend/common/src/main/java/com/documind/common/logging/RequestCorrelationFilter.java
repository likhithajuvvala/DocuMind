package com.documind.common.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;

public class RequestCorrelationFilter extends OncePerRequestFilter {

    public static final String REQUEST_ID_HEADER = "X-Request-Id";
    public static final String REQUEST_ID_KEY = "request_id";
    public static final String WORKSPACE_ID_ATTRIBUTE = "documind.workspaceId";
    public static final String USER_ID_ATTRIBUTE = "documind.userId";

    private static final Logger LOGGER = LoggerFactory.getLogger(RequestCorrelationFilter.class);
    private static final String ACTUATOR_PREFIX = "/actuator";
    private static final String HTTP_METHOD_KEY = "http_method";
    private static final String HTTP_PATH_KEY = "http_path";
    private static final String HTTP_STATUS_KEY = "http_status";
    private static final String DURATION_KEY = "duration_ms";

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String inboundRequestId = request.getHeader(REQUEST_ID_HEADER);
        boolean generated = inboundRequestId == null || inboundRequestId.isBlank();
        String requestId = generated ? UUID.randomUUID().toString() : inboundRequestId;

        MDC.put(REQUEST_ID_KEY, requestId);
        if (generated) {
            response.setHeader(REQUEST_ID_HEADER, requestId);
        }

        long startedAt = System.nanoTime();
        try {
            chain.doFilter(
                    generated ? new CorrelatedRequest(request, requestId) : request, response);
        } finally {
            logCompletion(request, response, startedAt);
            MDC.clear();
        }
    }

    private void logCompletion(
            HttpServletRequest request, HttpServletResponse response, long startedAt) {
        if (request.getRequestURI().startsWith(ACTUATOR_PREFIX)) {
            return;
        }

        copyAttributeToMdc(request, WORKSPACE_ID_ATTRIBUTE, "workspace_id");
        copyAttributeToMdc(request, USER_ID_ATTRIBUTE, "user_id");
        MDC.put(HTTP_METHOD_KEY, request.getMethod());
        MDC.put(HTTP_PATH_KEY, request.getRequestURI());
        MDC.put(HTTP_STATUS_KEY, String.valueOf(response.getStatus()));
        MDC.put(
                DURATION_KEY,
                String.valueOf(Duration.ofNanos(System.nanoTime() - startedAt).toMillis()));
        try {
            LOGGER.info(
                    "{} {} responded {}",
                    request.getMethod(),
                    request.getRequestURI(),
                    response.getStatus());
        } finally {
            MDC.remove(HTTP_METHOD_KEY);
            MDC.remove(HTTP_PATH_KEY);
            MDC.remove(HTTP_STATUS_KEY);
            MDC.remove(DURATION_KEY);
        }
    }

    private void copyAttributeToMdc(HttpServletRequest request, String attribute, String key) {
        Object value = request.getAttribute(attribute);
        if (value != null) {
            MDC.put(key, value.toString());
        }
    }

    private static final class CorrelatedRequest extends HttpServletRequestWrapper {

        private final String requestId;

        private CorrelatedRequest(HttpServletRequest request, String requestId) {
            super(request);
            this.requestId = requestId;
        }

        @Override
        public String getHeader(String name) {
            return REQUEST_ID_HEADER.equalsIgnoreCase(name) ? requestId : super.getHeader(name);
        }

        @Override
        public Enumeration<String> getHeaders(String name) {
            if (REQUEST_ID_HEADER.equalsIgnoreCase(name)) {
                return Collections.enumeration(List.of(requestId));
            }
            return super.getHeaders(name);
        }

        @Override
        public Enumeration<String> getHeaderNames() {
            Set<String> names = new LinkedHashSet<>(Collections.list(super.getHeaderNames()));
            names.add(REQUEST_ID_HEADER);
            return Collections.enumeration(names);
        }
    }
}
