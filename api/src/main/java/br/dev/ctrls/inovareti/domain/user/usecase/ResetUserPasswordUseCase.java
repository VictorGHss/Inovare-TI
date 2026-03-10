package br.dev.ctrls.inovareti.domain.user.usecase;

import java.util.UUID;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import br.dev.ctrls.inovareti.core.exception.NotFoundException;
import br.dev.ctrls.inovareti.domain.user.User;
import br.dev.ctrls.inovareti.domain.user.UserRepository;
import lombok.RequiredArgsConstructor;

/**
 * Use case: resets a user's password to the default value "Mudar@123"
 * and forces the user to change it on next login.
 */
@Component
@RequiredArgsConstructor
public class ResetUserPasswordUseCase {

    private static final String DEFAULT_PASSWORD = "Mudar@123";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public void execute(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found: " + userId));

        user.setPasswordHash(passwordEncoder.encode(DEFAULT_PASSWORD));
        user.setMustChangePassword(true);
        userRepository.save(user);
    }
}
