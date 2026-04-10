package com.naturehood.naturehood_backend.config;

import com.naturehood.naturehood_backend.sse.RedisMessageSubscriber;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.listener.adapter.MessageListenerAdapter;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Redis configuration.
 */
@Configuration
public class RedisConfig {

    private static final Logger log = LoggerFactory.getLogger(RedisConfig.class);

    @Value("${redis.channel.feed}")
    private String feedChannelName;

    @Bean
    public RedisTemplate<String, String> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, String> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        StringRedisSerializer stringSerializer = new StringRedisSerializer();
        template.setKeySerializer(stringSerializer);
        template.setValueSerializer(stringSerializer);
        template.setHashKeySerializer(stringSerializer);
        template.setHashValueSerializer(stringSerializer);
        template.afterPropertiesSet();

        return template;
    }

    @Bean
    public RedisMessageListenerContainer redisListenerContainer(
            RedisConnectionFactory connectionFactory,
            MessageListenerAdapter listenerAdapter,
            ChannelTopic feedTopic
    ) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(listenerAdapter, feedTopic);

        // Do NOT let a missing Redis connection crash the entire Spring context on startup.
        // Instead, the container will silently keep retrying the connection every 5 seconds
        // in the background until Redis becomes reachable, then subscribe automatically.
        // This makes the app resilient to Redis starting slightly later than the backend
        // (relevant when running the image standalone or when Redis restarts).
        container.setRecoveryInterval(5000);

        // Suppress the fatal startup exception: the container will attempt to connect
        // lazily instead of blocking the application context refresh.
        container.setErrorHandler(e ->
                log.warn("Redis listener error (will retry in {}ms): {}", 5000, e.getMessage()));

        return container;
    }

    @Bean
    public MessageListenerAdapter messageListenerAdapter(RedisMessageSubscriber subscriber) {
        return new MessageListenerAdapter(subscriber, "onMessage");
    }

    @Bean
    public ChannelTopic feedTopic() {
        return new ChannelTopic(feedChannelName);
    }
}
