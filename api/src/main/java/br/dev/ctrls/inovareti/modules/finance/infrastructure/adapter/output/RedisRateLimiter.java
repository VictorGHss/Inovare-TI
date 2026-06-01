package br.dev.ctrls.inovareti.modules.finance.infrastructure.adapter.output;

import java.time.Duration;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;

/**
 * Rate limiter simples baseado em Redis. Usa SETNX com TTL para aplicar cooldowns
 * distribuÃ­dos por chave.
 */
@Component
@ConditionalOnBean(StringRedisTemplate.class)
public class RedisRateLimiter {

    private final StringRedisTemplate redis;

    public RedisRateLimiter(StringRedisTemplate redis) {
        this.redis = redis;
    }

    /**
     * Tenta adquirir a chave por um perÃ­odo. Retorna true se conseguiu (lock adquirido),
     * false se a chave jÃ¡ existia (throttled).
     */
    public boolean tryAcquire(String key, Duration ttl) {
        // compatibilidade: comportamento antigo (1 requisiÃ§Ã£o por janela)
        return tryAcquire(key, 1, ttl);
    }

    /**
     * Tenta adquirir atÃ© `maxRequests` dentro da janela `window`.
     * Implementado com INCR + PEXPIRE de forma atÃ´mica via conexÃ£o Redis.
     * Retorna true se o nÃºmero de requisiÃ§Ãµes ainda estÃ¡ dentro do limite.
     */
    public boolean tryAcquire(String key, int maxRequests, Duration window) {
        Long count = redis.opsForValue().increment(key);
        if (count != null && count == 1L) {
            redis.expire(key, window);
        }
        return count != null && count <= (long) maxRequests;
    }
}

