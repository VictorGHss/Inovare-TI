package br.dev.ctrls.inovareti.modules.auth.application.service;

import io.micrometer.observation.annotation.Observed;

import org.springframework.stereotype.Component;

import br.dev.ctrls.inovareti.core.shared.domain.model.exception.BadRequestException;
import br.dev.ctrls.inovareti.core.shared.domain.model.exception.NotFoundException;
import br.dev.ctrls.inovareti.modules.auth.application.dto.AuthResponseDTO;
import br.dev.ctrls.inovareti.modules.auth.application.dto.ResetInitialPasswordRequestDTO;
import br.dev.ctrls.inovareti.modules.auth.domain.port.output.HashPort;
import br.dev.ctrls.inovareti.modules.auth.domain.port.output.TokenPort;
import br.dev.ctrls.inovareti.modules.user.domain.port.output.UserRepositoryPort;
import br.dev.ctrls.inovareti.modules.user.application.dto.UserResponseDTO;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
@Observed
public class ResetInitialPasswordUseCase {

    private final TokenPort tokenPort;
    private final UserRepositoryPort userRepository;
    private final HashPort hashPort;

    public AuthResponseDTO execute(ResetInitialPasswordRequestDTO request) {
        var tokenUserId = tokenPort.validateInitialPasswordResetToken(request.tempToken());
        if (tokenUserId == null) {
            throw new BadRequestException("Token temporÃ¡rio invÃ¡lido ou expirado.");
        }
        if (!tokenUserId.equals(request.userId())) {
            throw new BadRequestException("Token temporÃ¡rio nÃ£o pertence ao usuÃ¡rio informado.");
        }

        var user = userRepository.findById(request.userId())
                .orElseThrow(() -> new NotFoundException("User not found with id: " + request.userId()));

        if (!user.isMustChangePassword()) {
            throw new BadRequestException("Este usuÃ¡rio nÃ£o exige redefiniÃ§Ã£o inicial de senha.");
        }

        user.setPasswordHash(hashPort.encode(request.newPassword()));
        user.setMustChangePassword(false);
        var savedUser = userRepository.save(user);

        String finalToken = tokenPort.generateToken(savedUser);
        return AuthResponseDTO.authenticated(finalToken, UserResponseDTO.from(savedUser));
    }
}


