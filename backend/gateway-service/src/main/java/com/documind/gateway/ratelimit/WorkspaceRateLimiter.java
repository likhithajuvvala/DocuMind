package com.documind.gateway.ratelimit;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.stereotype.Component;

@Component
public class WorkspaceRateLimiter {

    private final RateLimitProperties properties;
    private final Map<UUID, Window> windows = new ConcurrentHashMap<>();

    public WorkspaceRateLimiter(RateLimitProperties properties) {
        this.properties = properties;
    }

    public boolean tryConsume(UUID workspaceId) {
        if (!properties.isEnabled()) {
            return true;
        }

        Duration windowLength = properties.getWindow();
        Window window = windows.compute(workspaceId, (key, existing) -> {
            Instant now = Instant.now();
            if (existing == null || existing.startedAt().plus(windowLength).isBefore(now)) {
                return new Window(now, new AtomicInteger());
            }
            return existing;
        });

        return window.counter().incrementAndGet() <= properties.getRequestsPerWindow();
    }

    private record Window(Instant startedAt, AtomicInteger counter) {
    }
}
