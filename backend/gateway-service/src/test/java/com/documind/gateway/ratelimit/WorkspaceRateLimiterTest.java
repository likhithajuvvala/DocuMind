package com.documind.gateway.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Runs against a real Redis, since the entire point of this class is that the quota lives outside
 * the JVM: a mock would prove nothing about whether two {@link WorkspaceRateLimiter} instances
 * (standing in for two gateway-service replicas) actually observe each other's consumption.
 */
@Testcontainers
class WorkspaceRateLimiterTest {

    @Container
    static final GenericContainer<?> REDIS =
            new GenericContainer<>(DockerImageName.parse("redis:7.4-alpine")).withExposedPorts(6379);

    private LettuceConnectionFactory connectionFactory;

    @BeforeEach
    void setUp() {
        connectionFactory = new LettuceConnectionFactory(REDIS.getHost(), REDIS.getMappedPort(6379));
        connectionFactory.afterPropertiesSet();
    }

    @AfterEach
    void tearDown() {
        connectionFactory.destroy();
    }

    @Test
    void twoLimiterInstancesShareTheSameQuotaThroughRedis() {
        RateLimitProperties properties = properties(2, Duration.ofMinutes(1));
        // Two separate instances stand in for two gateway-service pods behind the same Redis,
        // rather than each keeping its own independent in-memory counter.
        WorkspaceRateLimiter podOne = new WorkspaceRateLimiter(redisTemplate(), properties);
        WorkspaceRateLimiter podTwo = new WorkspaceRateLimiter(redisTemplate(), properties);
        UUID workspaceId = UUID.randomUUID();

        assertThat(podOne.tryConsume(workspaceId)).isTrue();
        assertThat(podTwo.tryConsume(workspaceId))
                .as("second replica must see the first replica's consumption")
                .isTrue();
        assertThat(podOne.tryConsume(workspaceId))
                .as("quota is exhausted across both replicas combined, not per replica")
                .isFalse();
    }

    @Test
    void distinctWorkspacesGetIndependentQuotas() {
        WorkspaceRateLimiter limiter = new WorkspaceRateLimiter(redisTemplate(), properties(1, Duration.ofMinutes(1)));

        assertThat(limiter.tryConsume(UUID.randomUUID())).isTrue();
        assertThat(limiter.tryConsume(UUID.randomUUID()))
                .as("a different workspace must not be affected by another workspace's usage")
                .isTrue();
    }

    @Test
    void windowResetsOnceItExpires() throws InterruptedException {
        WorkspaceRateLimiter limiter =
                new WorkspaceRateLimiter(redisTemplate(), properties(1, Duration.ofMillis(200)));
        UUID workspaceId = UUID.randomUUID();

        assertThat(limiter.tryConsume(workspaceId)).isTrue();
        assertThat(limiter.tryConsume(workspaceId)).isFalse();

        Thread.sleep(300);

        assertThat(limiter.tryConsume(workspaceId))
                .as("a fresh window must allow requests again once the old one expires")
                .isTrue();
    }

    @Test
    void disabledLimiterAlwaysAllowsRequestsRegardlessOfLimit() {
        RateLimitProperties properties = properties(0, Duration.ofMinutes(1));
        properties.setEnabled(false);
        WorkspaceRateLimiter limiter = new WorkspaceRateLimiter(redisTemplate(), properties);
        UUID workspaceId = UUID.randomUUID();

        assertThat(limiter.tryConsume(workspaceId)).isTrue();
        assertThat(limiter.tryConsume(workspaceId)).isTrue();
    }

    private StringRedisTemplate redisTemplate() {
        StringRedisTemplate template = new StringRedisTemplate(connectionFactory);
        template.afterPropertiesSet();
        return template;
    }

    private RateLimitProperties properties(int requestsPerWindow, Duration window) {
        RateLimitProperties properties = new RateLimitProperties();
        properties.setEnabled(true);
        properties.setRequestsPerWindow(requestsPerWindow);
        properties.setWindow(window);
        return properties;
    }
}
