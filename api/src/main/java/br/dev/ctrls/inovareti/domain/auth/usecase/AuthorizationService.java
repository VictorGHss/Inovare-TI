package br.dev.ctrls.inovareti.domain.auth.usecase;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import br.dev.ctrls.inovareti.domain.user.UserRepository;
import lombok.RequiredArgsConstructor;

/**
 * Serviço responsável por carregar um usuário pelo seu e-mail.
 * Integra-se ao pipeline de autenticação do Spring Security.
 */
@Service
@RequiredArgsConstructor
public class AuthorizationService implements UserDetailsService {

    private final UserRepository userRepository;

    /**
     * Localiza um usuário pelo e-mail informado (usado como username no Spring Security).
     *
     * @param email o endereço de e-mail a ser buscado
     * @return o {@link UserDetails} correspondente
     * @throws UsernameNotFoundException se não existir usuário com o e-mail informado
     */
    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + email));
    }
}
