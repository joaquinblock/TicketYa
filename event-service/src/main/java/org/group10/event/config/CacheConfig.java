package org.group10.event.config;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.logging.LogFactory;
import org.group10.event.dto.EventDetailResponse;
import org.group10.event.dto.EventPageResponse;
import org.group10.event.dto.ParticipantResponse;
import org.group10.event.dto.VenueDetailResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.cache.interceptor.LoggingCacheErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.cache.RedisCacheWriter;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.JacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext.SerializationPair;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.util.Map;

/**
 * Cache of the public catalog in the event-service's own Redis (ADR 0009). Each cache has its time to live and holds
 * one type, stored as JSON. The writes evict what they change; see {@link CatalogCaches}.
 * <p>
 * Caching runs outside the transactions ({@code order}), so an eviction happens after the commit: evicting before it
 * would let a read in between cache the old value again until the TTL.
 */
@Configuration
@EnableCaching(order = Ordered.HIGHEST_PRECEDENCE)
@Slf4j
public class CacheConfig implements CachingConfigurer {

    @Bean
    RedisCacheManager cacheManager(RedisConnectionFactory connectionFactory, JsonMapper jsonMapper,
                                   @Value("${ticketya.cache.event}") Duration event,
                                   @Value("${ticketya.cache.events-upcoming}") Duration upcoming,
                                   @Value("${ticketya.cache.venue}") Duration venue,
                                   @Value("${ticketya.cache.participant}") Duration participant) {
        // Immediate writes: by default a put goes out asynchronously, so a value read before a change can reach Redis
        // after that change evicted it, and stay there until the TTL
        RedisCacheWriter writer = RedisCacheWriter.create(connectionFactory, writes -> writes.immediateWrites());
        return RedisCacheManager.builder(writer)
                .withInitialCacheConfigurations(Map.of(
                        CatalogCaches.EVENT, cache(jsonMapper, EventDetailResponse.class, event),
                        CatalogCaches.EVENTS_UPCOMING, cache(jsonMapper, EventPageResponse.class, upcoming),
                        CatalogCaches.VENUE, cache(jsonMapper, VenueDetailResponse.class, venue),
                        CatalogCaches.PARTICIPANT, cache(jsonMapper, ParticipantResponse.class, participant)))
                .disableCreateOnMissingCache()
                .build();
    }

    private static RedisCacheConfiguration cache(JsonMapper jsonMapper, Class<?> type, Duration ttl) {
        return RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(ttl)
                .disableCachingNullValues()
                .computePrefixWith(name -> "catalog:" + name + ":")
                .serializeValuesWith(SerializationPair.fromSerializer(new JacksonJsonRedisSerializer<>(jsonMapper, type)));
    }

    /**
     * If Redis fails, the error is logged and the request goes on against the database: slower, but it answers.
     */
    @Override
    public CacheErrorHandler errorHandler() {
        return new LoggingCacheErrorHandler(LogFactory.getLog(CacheConfig.class), false);
    }

    /**
     * The database is recreated and seeded on every start, but Redis outlives the service: what it kept from the
     * previous run would show events, venues and participants that are no longer those.
     */
    @Bean
    ApplicationRunner clearCatalogCachesOnStart(CacheManager cacheManager) {
        return args -> cacheManager.getCacheNames().forEach(name -> {
            try {
                cacheManager.getCache(name).clear();
            } catch (RuntimeException failure) {
                log.warn("No se pudo vaciar la cache {} al arrancar: {}", name, failure.getMessage());
            }
        });
    }
}
