package br.dev.ctrls.inovareti.infra.security;

import java.util.Map;
import java.util.UUID;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import br.dev.ctrls.inovareti.domain.user.UserRepository;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class TwoFactorSessionGuard {

    private final UserRepository userRepository;

    public void assertVerified(Authentication authentication) {
        if (authentication == null) {
            throw new AccessDeniedException("É necessário validar o 2FA para acessar este recurso.");
        }

        UUID userId;
        try {
            userId = UUID.fromString(authentication.getPrincipal().toString());
        } catch (Exception ex) {
            throw new AccessDeniedException("Identificador de usuário inválido para validação do 2FA.");
        }

        Object details = authentication.getDetails();
        if (!(details instanceof Map<?, ?> detailMap)) {
            throw new AccessDeniedException("É necessário validar o 2FA para acessar este recurso.");
        }

        Object value = detailMap.get("twoFactorVerified");
        if (!(value instanceof Boolean verified) || !verified) {
            throw new AccessDeniedException("É necessário validar o 2FA para acessar este recurso.");
        }

        boolean twoFactorCurrentlyEnabled = userRepository.findById(userId)
                .map(user -> user.getTotpSecret() != null && !user.getTotpSecret().isBlank())
                .orElse(false);

        if (!twoFactorCurrentlyEnabled) {
            throw new AccessDeniedException("Seu 2FA foi resetado. Reconfigure para continuar acessando o cofre.");
        }
    }
}