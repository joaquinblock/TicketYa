package org.group10.queue.repository;

import org.group10.queue.entity.QueueEntrySnapshot;
import org.group10.queue.config.QueueProperties;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Repository
public class QueueRepository {

    private static final DefaultRedisScript<List> JOIN_SCRIPT = new DefaultRedisScript<>("""
            local current = redis.call('HGET', KEYS[3], ARGV[1])
            if current then
              local state = redis.call('HGET', KEYS[4], current)
              if state == 'WAITING' then
                local rank = redis.call('ZRANK', KEYS[1], current)
                local cursor = tonumber(redis.call('GET', KEYS[5]) or '0')
                redis.call('SET', KEYS[7], ARGV[3], 'NX')
                redis.call('SADD', KEYS[8], ARGV[4])
                return {current, state, tostring(rank - cursor + 1), '0', '0', ''}
              end
              if state == 'ENABLED' then
                local enabledUntil = tonumber(redis.call('HGET', KEYS[6], current) or '0')
                if enabledUntil > tonumber(ARGV[2]) then
                  local enabledAt = tonumber(redis.call('HGET', KEYS[9], current) or '0')
                  local token = redis.call('HGET', KEYS[10], current) or ''
                  return {current, state, '0', tostring(enabledUntil), tostring(enabledAt), token}
                end
                redis.call('HSET', KEYS[4], current, 'EXPIRED')
                redis.call('HDEL', KEYS[10], current)
              end
            end

            local sequence = redis.call('INCR', KEYS[2])
            local entryId = ARGV[1] .. ':' .. tostring(sequence)
            redis.call('ZADD', KEYS[1], sequence, entryId)
            redis.call('HSET', KEYS[3], ARGV[1], entryId)
            redis.call('HSET', KEYS[4], entryId, 'WAITING')
            redis.call('SET', KEYS[7], ARGV[3], 'NX')
            redis.call('SADD', KEYS[8], ARGV[4])
            local rank = redis.call('ZRANK', KEYS[1], entryId)
            local cursor = tonumber(redis.call('GET', KEYS[5]) or '0')
            return {entryId, 'WAITING', tostring(rank - cursor + 1), '0', '0', ''}
            """, List.class);

    private static final DefaultRedisScript<Long> ADVANCE_DUE_SCRIPT = new DefaultRedisScript<>("""
            local nextPromotion = tonumber(redis.call('GET', KEYS[6]) or '0')
            local now = tonumber(ARGV[2])
            if nextPromotion == 0 or nextPromotion > now then
              return 0
            end

            local cursor = tonumber(redis.call('GET', KEYS[5]) or '0')
            local batchSize = tonumber(ARGV[3])
            local entries = redis.call('ZRANGE', KEYS[1], cursor, cursor + batchSize - 1)
            local promoted = 0
            local enabledAt = now
            local enabledUntil = now + tonumber(ARGV[5])
            for _, entryId in ipairs(entries) do
              if redis.call('HGET', KEYS[2], entryId) == 'WAITING' then
                redis.call('HSET', KEYS[2], entryId, 'ENABLED')
                redis.call('HSET', KEYS[3], entryId, enabledAt)
                redis.call('HSET', KEYS[4], entryId, enabledUntil)
                promoted = promoted + 1
              end
            end

            local nextCursor = cursor + #entries
            redis.call('SET', KEYS[5], nextCursor)
            if redis.call('ZCARD', KEYS[1]) > nextCursor then
              redis.call('SET', KEYS[6], now + tonumber(ARGV[4]))
            else
              redis.call('DEL', KEYS[6])
              redis.call('SREM', KEYS[7], ARGV[1])
            end
            return promoted
            """, Long.class);

    private static final DefaultRedisScript<List> FIND_CURRENT_SCRIPT = new DefaultRedisScript<>("""
            local current = redis.call('HGET', KEYS[1], ARGV[1])
            if not current then
              return {}
            end

            local state = redis.call('HGET', KEYS[2], current)
            if state == 'WAITING' then
              local rank = redis.call('ZRANK', KEYS[3], current)
              local cursor = tonumber(redis.call('GET', KEYS[4]) or '0')
              return {current, state, tostring(rank - cursor + 1), '0', '0', ''}
            end

            if state == 'ENABLED' then
              local enabledUntil = tonumber(redis.call('HGET', KEYS[5], current) or '0')
              if enabledUntil > tonumber(ARGV[2]) then
                local enabledAt = tonumber(redis.call('HGET', KEYS[6], current) or '0')
                local token = redis.call('HGET', KEYS[7], current) or ''
                return {current, state, '0', tostring(enabledUntil), tostring(enabledAt), token}
              end
              redis.call('HSET', KEYS[2], current, 'EXPIRED')
              redis.call('HDEL', KEYS[7], current)
              state = 'EXPIRED'
            end

            return {current, state or 'EXPIRED', '0', '0', '0', ''}
            """, List.class);

    private final StringRedisTemplate redis;
    private final QueueProperties queueProperties;

    public QueueRepository(StringRedisTemplate redis, QueueProperties queueProperties) {
        this.redis = redis;
        this.queueProperties = queueProperties;
    }

    public QueueEntrySnapshot join(Long eventId, Long userId, Instant now) {
        String prefix = prefix(eventId);
        List<?> result = redis.execute(JOIN_SCRIPT, List.of(
                        prefix + "arrivals",
                        prefix + "sequence",
                        prefix + "current",
                        prefix + "status",
                        prefix + "enabled-up-to",
                        prefix + "enabled-until",
                        prefix + "next-promotion",
                        ACTIVE_EVENTS_KEY,
                        prefix + "enabled-at",
                        prefix + "queue-token"),
                userId.toString(),
                Long.toString(now.toEpochMilli()),
                Long.toString(now.plus(queueProperties.batchInterval()).toEpochMilli()),
                eventId.toString());
        if (result == null || result.size() != 6) {
            throw new IllegalStateException("Redis devolvió una respuesta inválida al ingresar a la fila");
        }
        return toSnapshot(result);
    }

    public int advanceDue(Long eventId, Instant now, int batchSize, Duration interval, Duration enabledWindow) {
        String prefix = prefix(eventId);
        Long promoted = redis.execute(ADVANCE_DUE_SCRIPT, List.of(
                        prefix + "arrivals",
                        prefix + "status",
                        prefix + "enabled-at",
                        prefix + "enabled-until",
                        prefix + "enabled-up-to",
                        prefix + "next-promotion",
                        ACTIVE_EVENTS_KEY),
                eventId.toString(),
                Long.toString(now.toEpochMilli()),
                Integer.toString(batchSize),
                Long.toString(interval.toMillis()),
                Long.toString(enabledWindow.toMillis()));
        return promoted == null ? 0 : promoted.intValue();
    }

    public void advanceDueQueues(Instant now) {
        Set<String> eventIds = redis.opsForSet().members(ACTIVE_EVENTS_KEY);
        if (eventIds == null || eventIds.isEmpty()) {
            return;
        }
        for (String eventId : eventIds) {
            advanceDue(Long.valueOf(eventId), now, queueProperties.batchSize(),
                    queueProperties.batchInterval(), queueProperties.enabledWindow());
        }
    }

    public Optional<QueueEntrySnapshot> findCurrent(Long eventId, Long userId, Instant now) {
        String prefix = prefix(eventId);
        List<?> result = redis.execute(FIND_CURRENT_SCRIPT, List.of(
                        prefix + "current",
                        prefix + "status",
                        prefix + "arrivals",
                        prefix + "enabled-up-to",
                        prefix + "enabled-until",
                        prefix + "enabled-at",
                        prefix + "queue-token"),
                userId.toString(), Long.toString(now.toEpochMilli()));
        if (result == null || result.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(toSnapshot(result));
    }

    public String storeQueueTokenIfAbsent(Long eventId, String entryId, String token) {
        String tokenKey = prefix(eventId) + "queue-token";
        redis.opsForHash().putIfAbsent(tokenKey, entryId, token);
        Object storedToken = redis.opsForHash().get(tokenKey, entryId);
        if (storedToken == null) {
            throw new IllegalStateException("Redis no devolvió el turno almacenado");
        }
        return storedToken.toString();
    }

    private static QueueEntrySnapshot toSnapshot(List<?> result) {
        String entryId = result.get(0).toString();
        String status = result.get(1).toString();
        int position = Integer.parseInt(result.get(2).toString());
        long enabledUntil = Long.parseLong(result.get(3).toString());
        long enabledAt = Long.parseLong(result.get(4).toString());
        String queueToken = result.get(5).toString();
        return new QueueEntrySnapshot(entryId, status, position == 0 ? null : position,
                enabledUntil == 0 ? null : enabledUntil, enabledAt == 0 ? null : enabledAt,
                queueToken.isBlank() ? null : queueToken);
    }

    private static final String ACTIVE_EVENTS_KEY = "queue:active-events";

    private static String prefix(Long eventId) {
        return "queue:{" + eventId + "}:";
    }
}
