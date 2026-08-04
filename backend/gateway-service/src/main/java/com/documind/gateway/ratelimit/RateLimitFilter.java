package com.documind.gateway.ratelimit;

import com.documind.common.security.CurrentUser;
import com.documind.common.web.ApiErrorResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.LOWEST_PRECEDENCE - 1)
public class RateLimitFilter extends OncePerRequestFilter {

    private final WorkspaceRateLimiter rateLimiter;
    private final ObjectMapper objectMapper;

    public RateLimitFilter(WorkspaceRateLimiter rateLimiter, ObjectMapper objectMapper) {
        this.rateLimiter = rateLimiter;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        var user = CurrentUser.find();
        if (user.isPresent() && !rateLimiter.tryConsume(user.get().workspaceId())) {
            writeRateLimitResponse(response);
            return;
        }
        chain.doFilter(request, response);
    }

    private void writeRateLimitResponse(HttpServletResponse response) throws IOException {
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(
                response.getOutputStream(),
                ApiErrorResponse.of("rate_limit_exceeded", "Workspace request quota exhausted, retry shortly"));
    }
}
