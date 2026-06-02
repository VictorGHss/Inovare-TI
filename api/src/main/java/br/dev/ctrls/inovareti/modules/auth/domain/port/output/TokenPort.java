package br.dev.ctrls.inovareti.modules.auth.domain.port.output;

import java.util.UUID;
import br.dev.ctrls.inovareti.modules.user.domain.model.User;

public interface TokenPort {
    String generateToken(User user);
    String generateToken(User user, boolean twoFactorVerified);
    String getUserIdFromToken(String token);
    String generateInitialPasswordResetToken(User user);
    String validateToken(String token);
    UUID validateInitialPasswordResetToken(String token);
    boolean isTwoFactorVerified(String token);
    void blacklistToken(String token);
    boolean isTokenBlacklisted(String token);
}
