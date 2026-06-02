package br.dev.ctrls.inovareti.modules.user.application.service;

import io.micrometer.observation.annotation.Observed;

import java.util.UUID;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import br.dev.ctrls.inovareti.core.shared.domain.model.exception.BadRequestException;
import br.dev.ctrls.inovareti.core.shared.domain.model.exception.NotFoundException;
import br.dev.ctrls.inovareti.domain.audit.AuditAction;
import br.dev.ctrls.inovareti.domain.audit.AuditEvent;
import br.dev.ctrls.inovareti.domain.audit.AuditLogService;
import br.dev.ctrls.inovareti.modules.user.domain.port.output.UserRepositoryPort;
import br.dev.ctrls.inovareti.modules.user.application.dto.ChangePasswordRequestDTO;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
@Observed
public class ChangeMyPasswordUseCase {

    private final UserRepositoryPort userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditLogService auditLogService;

    @Transactional
    public void execute(String authenticatedUserId, ChangePasswordRequestDTO request) {
        UUID userId = UUID.fromString(authenticatedUserId);

        var user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("UsuÃ¡rio nÃ£o encontrado com o ID: " + userId));

        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new BadRequestException("Senha atual invÃ¡lida.");
        }

        if (passwordEncoder.matches(request.newPassword(), user.getPasswordHash())) {
            throw new BadRequestException("A nova senha deve ser diferente da senha atual.");
        }

        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);

        auditLogService.publish(AuditEvent.of(AuditAction.PROFILE_PASSWORD_CHANGE)
                .userId(userId)
                .resourceType("UserProfile")
                .resourceId(userId)
                .details("{\"mode\": \"SELF_SERVICE\"}")
                .build());
    }
}


