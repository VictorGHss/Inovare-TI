package br.dev.ctrls.inovareti.modules.auth.infrastructure.adapter.output;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import br.dev.ctrls.inovareti.modules.auth.domain.port.output.HashPort;
import lombok.RequiredArgsConstructor;

/**
 * Adaptador concretizador de infraestrutura de hashing e criptografia.
 * Implementa a porta HashPort encapsulando o PasswordEncoder do Spring Security.
 */
@Component
@RequiredArgsConstructor
public class PasswordEncoderAdapter implements HashPort {

    private final PasswordEncoder passwordEncoder;

    @Override
    public String encode(CharSequence rawPassword) {
        return passwordEncoder.encode(rawPassword);
    }

    @Override
    public boolean matches(CharSequence rawPassword, String encodedPassword) {
        return passwordEncoder.matches(rawPassword, encodedPassword);
    }
}
