package br.dev.ctrls.inovareti.domain.auth.usecase;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import br.dev.ctrls.inovareti.config.TokenService;
import br.dev.ctrls.inovareti.core.exception.BadRequestException;
import br.dev.ctrls.inovareti.core.exception.NotFoundException;
import br.dev.ctrls.inovareti.domain.auth.dto.AuthResponseDTO;
import br.dev.ctrls.inovareti.domain.auth.dto.ResetInitialPasswordRequestDTO;
import br.dev.ctrls.inovareti.domain.user.UserRepository;
import br.dev.ctrls.inovareti.domain.user.dto.UserResponseDTO;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class ResetInitialPasswordUseCase {

    private final TokenService tokenService;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public AuthResponseDTO execute(ResetInitialPasswordRequestDTO request) {
        var tokenUserId = tokenService.validateInitialPasswordResetToken(request.tempToken());
        if (tokenUserId == null) {
            throw new BadRequestException("Token temporário inválido ou expirado.");
        }
        if (!tokenUserId.equals(request.userId())) {
            throw new BadRequestException("Token temporário não pertence ao usuário informado.");
        }

        var user = userRepository.findById(request.userId())
                .orElseThrow(() -> new NotFoundException("User not found with id: " + request.userId()));

        if (!user.isMustChangePassword()) {
            throw new BadRequestException("Este usuário não exige redefinição inicial de senha.");
        }

        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        user.setMustChangePassword(false);
        var savedUser = userRepository.save(user);

        String finalToken = tokenService.generateToken(savedUser);
        return AuthResponseDTO.authenticated(finalToken, UserResponseDTO.from(savedUser));
    }
}
