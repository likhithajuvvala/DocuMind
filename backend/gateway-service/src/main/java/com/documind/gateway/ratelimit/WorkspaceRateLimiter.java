package com.documind.gateway.ratelimit;

import java.util.List;
import java.util.UUID;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

@Component
public class WorkspaceRateLimiter {

    // INCR and, only on the first hit of a window (current == 1), PEXPIRE — done as one Lua
    // script so the two commands are atomic. This anchors each workspace's window to whenever
    // its first request lands in Redis, the same fixed-window semantics as the previous
    // in-memory version, except every gateway-service replica now shares the one counter instead
    // of each pod keeping its own, which silently multiplied the real limit under autoscaling.
    private static final DefaultRedisScript<Long> INCREMENT_SCRIPT = new DefaultRedisScript<>(
            "local current = redis.call('INCR', KEYS[1]) "
                    + "if current == 1 then redis.call('PEXPIRE', KEYS[1], ARGV[1]) end "
                    + "return current",
            Long.class);

    private static final String KEY_PREFIX = "documind:rate-limit:workspace:";

    private final StringRedisTemplate redisTemplate;
    private final RateLimitProperties properties;

    public WorkspaceRateLimiter(StringRedisTemplate redisTemplate, RateLimitProperties properties) {
        this.redisTemplate = redisTemplate;
        this.properties = properties;
    }

    public boolean tryConsume(UUID workspaceId) {
        if (!properties.isEnabled()) {
            return true;
        }

        String key = KEY_PREFIX + workspaceId;
        Long count = redisTemplate.execute(
                INCREMENT_SCRIPT, List.of(key), String.valueOf(properties.getWindow().toMillis()));

        return count != null && count <= properties.getRequestsPerWindow();
    }
}
