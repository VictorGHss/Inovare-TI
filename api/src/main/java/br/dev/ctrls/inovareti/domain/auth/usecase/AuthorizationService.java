package br.dev.ctrls.inovareti.domain.auth.usecase;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import br.dev.ctrls.inovareti.domain.user.UserRepository;
import lombok.RequiredArgsConstructor;

/**
 * Service responsible for loading a user by their e-mail address.
 * Integrates with Spring Security's authentication pipeline.
 */
@Service
@RequiredArgsConstructor
public class AuthorizationService implements UserDetailsService {

    private final UserRepository userRepository;

    /**
     * Locates a user by the given e-mail (used as the Spring Security username).
     *
     * @param email the e-mail address to search for
     * @return the matching {@link UserDetails}
     * @throws UsernameNotFoundException if no user with the given e-mail exists
     */
    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + email));
    }
}
