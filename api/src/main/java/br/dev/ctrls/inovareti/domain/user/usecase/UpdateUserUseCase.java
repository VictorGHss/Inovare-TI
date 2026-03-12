package br.dev.ctrls.inovareti.domain.user.usecase;

import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import br.dev.ctrls.inovareti.core.exception.ConflictException;
import br.dev.ctrls.inovareti.core.exception.NotFoundException;
import br.dev.ctrls.inovareti.domain.audit.AuditAction;
import br.dev.ctrls.inovareti.domain.audit.AuditEvent;
import br.dev.ctrls.inovareti.domain.audit.AuditLogService;
import br.dev.ctrls.inovareti.domain.user.Sector;
import br.dev.ctrls.inovareti.domain.user.SectorRepository;
import br.dev.ctrls.inovareti.domain.user.User;
import br.dev.ctrls.inovareti.domain.user.UserRepository;
import br.dev.ctrls.inovareti.domain.user.dto.UpdateUserRequestDTO;
import br.dev.ctrls.inovareti.domain.user.dto.UserResponseDTO;
import lombok.RequiredArgsConstructor;

/**
 * Caso de uso: atualiza nome, e-mail, perfil e setor de um usuário existente.
 * O e-mail único é validado excluindo o próprio usuário sendo editado.
 */
@Component
@RequiredArgsConstructor
public class UpdateUserUseCase {

    private final UserRepository userRepository;
    private final SectorRepository sectorRepository;
    private final AuditLogService auditLogService;

    @Transactional
    public UserResponseDTO execute(UUID userId, UpdateUserRequestDTO request, UUID adminUserId, String ipAddress) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found: " + userId));

        // Verifica conflito de e-mail apenas em relação a outros usuários
        if (!user.getEmail().equalsIgnoreCase(request.email())
                && userRepository.existsByEmail(request.email())) {
            throw new ConflictException("Email already in use: " + request.email());
        }

        Sector sector = sectorRepository.findById(request.sectorId())
                .orElseThrow(() -> new NotFoundException("Sector not found: " + request.sectorId()));

        String oldRole = user.getRole() != null ? user.getRole().name() : null;
        UUID oldSectorId = user.getSector() != null ? user.getSector().getId() : null;

        user.setName(request.name());
        user.setEmail(request.email());
        user.setRole(request.role());
        user.setSector(sector);

        UserResponseDTO result = UserResponseDTO.from(userRepository.save(user));

        auditLogService.publish(AuditEvent.of(AuditAction.USER_UPDATE)
                .userId(adminUserId)
                .resourceType("User")
                .resourceId(userId)
                .details("{\"targetUserId\": \"" + userId + "\"}")
                .ipAddress(ipAddress)
                .build());

        String newRole = request.role().name();
        boolean roleChanged = !java.util.Objects.equals(oldRole, newRole);
        boolean sectorChanged = !java.util.Objects.equals(oldSectorId, request.sectorId());

        if (roleChanged || sectorChanged) {
            String details = String.format(
                    "{\"targetUserId\": \"%s\", \"adminUserId\": \"%s\", \"oldRole\": \"%s\", \"newRole\": \"%s\", \"sectorChanged\": %b}",
                    userId, adminUserId, oldRole, newRole, sectorChanged);
            auditLogService.publish(AuditEvent.of(AuditAction.USER_PERMISSION_CHANGE)
                    .userId(userId)
                    .details(details)
                    .ipAddress(ipAddress)
                    .build());
        }

        return result;
    }
}
