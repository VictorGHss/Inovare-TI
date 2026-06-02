package br.dev.ctrls.inovareti.modules.auth.infrastructure.adapter.output.security;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import br.dev.ctrls.inovareti.modules.user.domain.port.output.UserRepositoryPort;
import lombok.RequiredArgsConstructor;

/**
 * Serviço de segurança responsável por carregar os dados detalhados do usuário a partir
 * do UserRepositoryPort para o Spring Security pipeline.
 */
@Service
@RequiredArgsConstructor
public class AuthorizationServiceAdapter implements UserDetailsService {

    private final UserRepositoryPort userRepository;

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + email));
    }
}
