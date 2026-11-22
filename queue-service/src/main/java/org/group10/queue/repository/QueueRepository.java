package org.group10.queue.repository;

import org.group10.queue.entity.QueueEntrySnapshot;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public class QueueRepository {

    private static final DefaultRedisScript<List> JOIN_SCRIPT = new DefaultRedisScript<>("""
            local current = redis.call('HGET', KEYS[3], ARGV[1])
            if current then
              local state = redis.call('HGET', KEYS[4], current)
              if state == 'WAITING' then
                local rank = redis.call('ZRANK', KEYS[1], current)
                local cursor = tonumber(redis.call('GET', KEYS[5]) or '0')
                return {current, state, tostring(rank - cursor + 1), '0'}
              end
              if state == 'ENABLED' then
                local enabledUntil = tonumber(redis.call('HGET', KEYS[6], current) or '0')
                if enabledUntil > tonumber(ARGV[2]) then
                  return {current, state, '0', tostring(enabledUntil)}
                end
                redis.call('HSET', KEYS[4], current, 'EXPIRED')
              end
            end

            local sequence = redis.call('INCR', KEYS[2])
            local entryId = ARGV[1] .. ':' .. tostring(sequence)
            redis.call('ZADD', KEYS[1], sequence, entryId)
            redis.call('HSET', KEYS[3], ARGV[1], entryId)
            redis.call('HSET', KEYS[4], entryId, 'WAITING')
            local rank = redis.call('ZRANK', KEYS[1], entryId)
            local cursor = tonumber(redis.call('GET', KEYS[5]) or '0')
            return {entryId, 'WAITING', tostring(rank - cursor + 1), '0'}
            """, List.class);

    private final StringRedisTemplate redis;

    public QueueRepository(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public QueueEntrySnapshot join(Long eventId, Long userId, Instant now) {
        String prefix = prefix(eventId);
        List<?> result = redis.execute(JOIN_SCRIPT, List.of(
                        prefix + "arrivals",
                        prefix + "sequence",
                        prefix + "current",
                        prefix + "status",
                        prefix + "enabled-up-to",
                        prefix + "enabled-until"),
                userId.toString(), Long.toString(now.toEpochMilli()));
        if (result == null || result.size() != 4) {
            throw new IllegalStateException("Redis devolvió una respuesta inválida al ingresar a la fila");
        }
        String entryId = result.get(0).toString();
        String status = result.get(1).toString();
        int position = Integer.parseInt(result.get(2).toString());
        long enabledUntil = Long.parseLong(result.get(3).toString());
        return new QueueEntrySnapshot(entryId, status, position == 0 ? null : position,
                enabledUntil == 0 ? null : enabledUntil);
    }

    private static String prefix(Long eventId) {
        return "queue:{" + eventId + "}:";
    }
}
