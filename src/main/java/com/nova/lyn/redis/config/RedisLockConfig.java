package com.nova.lyn.redis.config;

import com.nova.lyn.redis.lock.RedisLockRegistry;
import io.lettuce.core.ClientOptions;
import io.lettuce.core.SocketOptions;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;

import java.time.Duration;

/***
 * @ClassName: RedisLockConfig
 * @Description: TODO
 * @Author: Lyn
 * @Date: 2019/3/26 下午9:41
 * @version : V1.0
 */
@Configuration
public class RedisLockConfig {

    private static final int REDIS_PORT = 6379;

    @Bean("redisLockRegistry")
    public RedisLockRegistry getRedisLockRegistry(@Qualifier("redisConnectionFactory") RedisConnectionFactory redisConnectionFactory, String prefixKey) {

        return new RedisLockRegistry(redisConnectionFactory,prefixKey);
    }

    @Bean("redisConnectionFactory")
    public RedisConnectionFactory getLettuceCF() {

        LettuceConnectionFactory connectionFactory;
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
        return connectionFactory;
    }


}
