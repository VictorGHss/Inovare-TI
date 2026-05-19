package br.dev.ctrls.inovareti.modules.appointment.infrastructure.utils;

import java.time.Duration;
import java.util.Random;

/**
 * Utilitário para adicionar "jitter" (aleatoriedade) a durações de cache.
 * Isso ajuda a evitar o problema de "cache stampede", onde múltiplos clientes
 * tentam recarregar o mesmo item de cache expirado ao mesmo tempo.
 */
public class CacheJitter {

    private static final Random RANDOM = new Random();
    private static final double JITTER_FACTOR = 0.1; // 10% de jitter

    /**
     * Adiciona um jitter aleatório a uma duração base.
     * O jitter é um valor aleatório entre -10% e +10% da duração base.
     * @param baseDuration A duração base.
     * @return Uma nova duração com jitter.
     */
    public static Duration withJitter(Duration baseDuration) {
        long baseSeconds = baseDuration.getSeconds();
        long jitterSeconds = (long) (baseSeconds * JITTER_FACTOR * (RANDOM.nextDouble() * 2 - 1)); // Random entre -JITTER_FACTOR e +JITTER_FACTOR
        return Duration.ofSeconds(baseSeconds + jitterSeconds);
    }
}