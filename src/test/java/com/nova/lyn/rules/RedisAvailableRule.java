package com.nova.lyn.rules;

import io.lettuce.core.ClientOptions;
import io.lettuce.core.SocketOptions;
import org.junit.Assume;
import org.junit.rules.MethodRule;
import org.junit.runners.model.FrameworkMethod;
import org.junit.runners.model.Statement;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;

import java.time.Duration;

/**
 * @ClassName RedisAvailableRule
 * @Description TODO
 * @Author Lyn
 * @Date 2019/3/26 0026 上午 11:36
 * @Version 1.0
 */
public class RedisAvailableRule implements MethodRule {

    private static final int REDIS_PORT= 6379;
    public static LettuceConnectionFactory connectionFactory;

    protected static void setupConnectionFactory() {
        RedisStandaloneConfiguration redisStandaloneConfiguration = new RedisStandaloneConfiguration();
        redisStandaloneConfiguration.setPort(REDIS_PORT);

        LettuceClientConfiguration clientConfiguration = LettuceClientConfiguration.builder()
                .clientOptions(
                        ClientOptions.builder()
                        .socketOptions(
                                SocketOptions.builder()
                                .connectTimeout(Duration.ofMillis(10000))
                                .build())
                        .build()).commandTimeout(Duration.ofSeconds(10000)).build();
        connectionFactory = new LettuceConnectionFactory(redisStandaloneConfiguration, clientConfiguration);
        connectionFactory.afterPropertiesSet();
    }

    public static void cleanUpConnectionFactoryIfAny() {
        if (connectionFactory != null) {
            connectionFactory.destroy();
        }
    }

    @Override
    public Statement apply(Statement base, FrameworkMethod method, Object target) {
        return new Statement() {
            @Override
            public void evaluate() throws Throwable {
                RedisAvailable redisAvailable = method.getAnnotation(RedisAvailable.class);
                if (redisAvailable != null) {
                    if (connectionFactory != null) {
                        try {
                            connectionFactory.getConnection();
                            base.evaluate();
                        }
                        catch (Exception e) {
                            //TODO Assume.assumeTrue("Skipping test due to Redis not being available on port: " + REDIS_PORT + ": " + e, false);
                        }
                    }
                }
            }
        };
    }
}
