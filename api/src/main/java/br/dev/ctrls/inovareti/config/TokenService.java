package br.dev.ctrls.inovareti.config;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;

import br.dev.ctrls.inovareti.domain.user.User;

/**
 * Service responsible for generating and validating JWT tokens.
 * The signing secret is injected from application properties.
 * Tokens are issued by "inovare-ti" and expire after 8 hours.
 */
@Service
public class TokenService {

    private static final String ISSUER = "inovare-ti";
    private static final int EXPIRATION_HOURS = 8;
    private static final int RESET_EXPIRATION_MINUTES = 15;

    @Value("${api.security.token.secret}")
    private String secret;

    /**
     * Generates a signed JWT token for the given user.
     *
     * @param user the authenticated user
     * @return signed JWT string
     */
    public String generateToken(User user) {
        Algorithm algorithm = Algorithm.HMAC256(secret);
        return JWT.create()
                .withIssuer(ISSUER)
                .withSubject(user.getEmail())
                .withExpiresAt(expiresAt())
                .sign(algorithm);
    }

    public String generateInitialPasswordResetToken(User user) {
        Algorithm algorithm = Algorithm.HMAC256(secret);
        return JWT.create()
                .withIssuer(ISSUER)
                .withSubject(user.getId().toString())
                .withClaim("purpose", "INITIAL_PASSWORD_RESET")
                .withExpiresAt(resetExpiresAt())
                .sign(algorithm);
    }

    /**
     * Validates the given JWT token and returns the subject (e-mail).
     * Returns an empty string if the token is invalid or expired.
     *
     * @param token the raw JWT string (without "Bearer " prefix)
     * @return the subject claim (e-mail), or empty string on failure
     */
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

    private Instant expiresAt() {
        return LocalDateTime.now()
                .plusHours(EXPIRATION_HOURS)
                .toInstant(ZoneOffset.of("-03:00"));
    }

    private Instant resetExpiresAt() {
        return LocalDateTime.now()
                .plusMinutes(RESET_EXPIRATION_MINUTES)
                .toInstant(ZoneOffset.of("-03:00"));
    }
}
