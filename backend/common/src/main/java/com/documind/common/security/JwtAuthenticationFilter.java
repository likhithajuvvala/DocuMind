package com.documind.common.security;

import com.documind.common.logging.RequestCorrelationFilter;
import com.documind.common.tenant.WorkspaceContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import org.slf4j.MDC;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";
    private static final String WORKSPACE_ID_KEY = "workspace_id";
    private static final String USER_ID_KEY = "user_id";

    private final JwtTokenService tokenService;

    public JwtAuthenticationFilter(JwtTokenService tokenService) {
        this.tokenService = tokenService;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        try {
            extractBearerToken(request)
                    .flatMap(tokenService::resolveAccessToken)
                    .ifPresent(user -> authenticate(user, request));
            chain.doFilter(request, response);
        } finally {
            WorkspaceContext.clear();
        }
    }

    private void authenticate(AuthenticatedUser user, HttpServletRequest request) {
        var authorities = List.of(new SimpleGrantedAuthority("ROLE_" + user.role().name()));
        var authentication = new UsernamePasswordAuthenticationToken(user, null, authorities);
        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(authentication);
        WorkspaceContext.set(user.workspaceId());
        MDC.put(WORKSPACE_ID_KEY, user.workspaceId().toString());
        MDC.put(USER_ID_KEY, user.userId().toString());
        request.setAttribute(
                RequestCorrelationFilter.WORKSPACE_ID_ATTRIBUTE, user.workspaceId().toString());
        request.setAttribute(RequestCorrelationFilter.USER_ID_ATTRIBUTE, user.userId().toString());
    }

    private Optional<String> extractBearerToken(HttpServletRequest request) {
        String header = request.getHeader(AUTHORIZATION_HEADER);
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            return Optional.empty();
        }
        return Optional.of(header.substring(BEARER_PREFIX.length()).trim());
    }
}
