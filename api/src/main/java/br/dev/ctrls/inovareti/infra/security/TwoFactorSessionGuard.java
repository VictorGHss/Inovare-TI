package br.dev.ctrls.inovareti.infra.security;

import java.util.Map;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

@Component
public class TwoFactorSessionGuard {

    public void assertVerified(Authentication authentication) {
        if (authentication == null) {
            throw new AccessDeniedException("É necessário validar o 2FA para acessar este recurso.");
        }

        Object details = authentication.getDetails();
        if (!(details instanceof Map<?, ?> detailMap)) {
            throw new AccessDeniedException("É necessário validar o 2FA para acessar este recurso.");
        }

        Object value = detailMap.get("twoFactorVerified");
        if (!(value instanceof Boolean verified) || !verified) {
            throw new AccessDeniedException("É necessário validar o 2FA para acessar este recurso.");
        }
    }
}