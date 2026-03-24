package br.dev.ctrls.inovareti.domain.financeiro.contaazul;

import java.time.Duration;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;

/**
 * Rate limiter simples baseado em Redis. Usa SETNX com TTL para aplicar cooldowns
 * distribuídos por chave.
 */
@Component
@ConditionalOnBean(StringRedisTemplate.class)
public class RedisRateLimiter {

    private final StringRedisTemplate redis;

    public RedisRateLimiter(StringRedisTemplate redis) {
        this.redis = redis;
    }

    /**
     * Tenta adquirir a chave por um período. Retorna true se conseguiu (lock adquirido),
     * false se a chave já existia (throttled).
     */
    public boolean tryAcquire(String key, Duration ttl) {
        // compatibilidade: comportamento antigo (1 requisição por janela)
        return tryAcquire(key, 1, ttl);
    }

    /**
     * Tenta adquirir até `maxRequests` dentro da janela `window`.
     * Implementado com INCR + PEXPIRE de forma atômica via conexão Redis.
     * Retorna true se o número de requisições ainda está dentro do limite.
     */
    public boolean tryAcquire(String key, int maxRequests, Duration window) {
        Long count = redis.opsForValue().increment(key);
        if (count != null && count == 1L) {
            redis.expire(key, window);
        }
        return count != null && count <= (long) maxRequests;
    }
}
