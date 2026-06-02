package br.dev.ctrls.inovareti.modules.auth.infrastructure.adapter.output;

import org.springframework.context.annotation.Lazy;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Component;

import br.dev.ctrls.inovareti.modules.auth.domain.port.output.AuthenticatorPort;
import br.dev.ctrls.inovareti.domain.user.User;

/**
 * Adaptador de infraestrutura responsável pelo acionamento do pipeline do Spring Security
 * para validação de credenciais de login.
 */
@Component
public class AuthenticatorAdapter implements AuthenticatorPort {

    private final AuthenticationManager authenticationManager;

    public AuthenticatorAdapter(@Lazy AuthenticationManager authenticationManager) {
        this.authenticationManager = authenticationManager;
    }

    @Override
    public User authenticate(String email, String password) {
        var credentials = new UsernamePasswordAuthenticationToken(email, password);
        var authentication = authenticationManager.authenticate(credentials);
        return (User) authentication.getPrincipal();
    }
}
