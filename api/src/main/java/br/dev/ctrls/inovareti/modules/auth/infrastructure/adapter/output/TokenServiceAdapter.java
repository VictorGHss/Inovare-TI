package br.dev.ctrls.inovareti.modules.auth.infrastructure.adapter.output;

import java.time.Instant;
import java.util.UUID;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;

import br.dev.ctrls.inovareti.modules.auth.domain.port.output.TokenPort;
import br.dev.ctrls.inovareti.modules.user.domain.model.User;

/**
 * Adaptador para gerência de tokens JWT e persistência física de blacklist no Redis.
 * Implementa a porta TokenPort.
 */
@Service
public class TokenServiceAdapter implements TokenPort {

    private static final String ISSUER = "inovare-ti";
    private static final int EXPIRATION_HOURS = 24;
    private static final int RESET_EXPIRATION_MINUTES = 15;

    private final String secret;
    private final ObjectProvider<StringRedisTemplate> redisTemplateProvider;

    public TokenServiceAdapter(
            @Value("${api.security.token.secret}") String secret,
            ObjectProvider<StringRedisTemplate> redisTemplateProvider) {
        this.secret = secret;
        this.redisTemplateProvider = redisTemplateProvider;
    }

    @Override
    public String generateToken(User user) {
        return generateToken(user, false);
    }

    @Override
    public String generateToken(User user, boolean twoFactorVerified) {
        Algorithm algorithm = Algorithm.HMAC256(secret);
        return JWT.create()
                .withIssuer(ISSUER)
                .withSubject(user.getEmail())
                // Expor o ID do usuário em ambos os claims para compatibilidade
                .withClaim("userId", user.getId() != null ? user.getId().toString() : "")
                .withClaim("id", user.getId() != null ? user.getId().toString() : "")
                .withClaim("two_factor_verified", twoFactorVerified)
                .withExpiresAt(expiresAt())
                .sign(algorithm);
    }

    @Override
    public String getUserIdFromToken(String token) {
        try {
            Algorithm algorithm = Algorithm.HMAC256(secret);
            var decoded = JWT.require(algorithm)
                    .withIssuer(ISSUER)
                    .build()
                    .verify(token);
            // Primeiro tenta o claim 'userId', em seguida 'id' para retrocompatibilidade
            var claimUserId = decoded.getClaim("userId");
            if (claimUserId != null && !claimUserId.isNull() && claimUserId.asString() != null
                    && !claimUserId.asString().isBlank()) {
                return claimUserId.asString();
            }
            var claimId = decoded.getClaim("id");
            if (claimId != null && !claimId.isNull() && claimId.asString() != null
                    && !claimId.asString().isBlank()) {
                return claimId.asString();
            }
            return "";
        } catch (JWTVerificationException | IllegalArgumentException e) {
            return "";
        }
    }

    @Override
    public String generateInitialPasswordResetToken(User user) {
        Algorithm algorithm = Algorithm.HMAC256(secret);
        return JWT.create()
                .withIssuer(ISSUER)
                .withSubject(user.getId().toString())
                .withClaim("purpose", "INITIAL_PASSWORD_RESET")
                .withExpiresAt(resetExpiresAt())
                .sign(algorithm);
    }

    @Override
    public String validateToken(String token) {
        try {
            Algorithm algorithm = Algorithm.HMAC256(secret);
            return JWT.require(algorithm)
                    .withIssuer(ISSUER)
                    .build()
                    .verify(token)
                    .getSubject();
        } catch (JWTVerificationException e) {
            return "";
        }
    }

    @Override
    public UUID validateInitialPasswordResetToken(String token) {
        try {
            Algorithm algorithm = Algorithm.HMAC256(secret);
            var decoded = JWT.require(algorithm)
                    .withIssuer(ISSUER)
                    .withClaim("purpose", "INITIAL_PASSWORD_RESET")
                    .build()
                    .verify(token);
            return UUID.fromString(decoded.getSubject());
        } catch (JWTVerificationException | IllegalArgumentException e) {
            return null;
        }
    }

    @Override
    public boolean isTwoFactorVerified(String token) {
        try {
            Algorithm algorithm = Algorithm.HMAC256(secret);
            var decoded = JWT.require(algorithm)
                    .withIssuer(ISSUER)
                    .build()
                    .verify(token);
            return decoded.getClaim("two_factor_verified").asBoolean() != null
                    && decoded.getClaim("two_factor_verified").asBoolean();
        } catch (JWTVerificationException | IllegalArgumentException e) {
            return false;
        }
    }

    @Override
    public void blacklistToken(String token) {
        if (token == null || token.isBlank()) {
            return;
        }
        try {
            Algorithm algorithm = Algorithm.HMAC256(secret);
            var decoded = JWT.require(algorithm)
                    .withIssuer(ISSUER)
                    .build()
                    .verify(token);
            java.util.Date expiresAt = decoded.getExpiresAt();
            if (expiresAt != null) {
                long ttlMillis = expiresAt.getTime() - System.currentTimeMillis();
                if (ttlMillis > 0) {
                    StringRedisTemplate redis = redisTemplateProvider.getIfAvailable();
                    if (redis != null) {
                        String key = "blacklist:token:" + token;
                        redis.opsForValue().set(key, "revoked", java.time.Duration.ofMillis(ttlMillis));
                    }
                }
            }
        } catch (JWTVerificationException | IllegalArgumentException | BeansException e) {
            // Silently ignore verification exceptions during blacklisting
        }
    }

    @Override
    public boolean isTokenBlacklisted(String token) {
        if (token == null || token.isBlank()) {
            return false;
        }
        try {
            StringRedisTemplate redis = redisTemplateProvider.getIfAvailable();
            if (redis != null) {
                return Boolean.TRUE.equals(redis.hasKey("blacklist:token:" + token));
            }
        } catch (BeansException e) {
            // Resilient fallback: allow authorization if Redis has an outage
        }
        return false;
    }

    private Instant expiresAt() {
        return Instant.now().plus(java.time.Duration.ofHours(EXPIRATION_HOURS));
    }

    private Instant resetExpiresAt() {
        return Instant.now().plus(java.time.Duration.ofMinutes(RESET_EXPIRATION_MINUTES));
    }
}
