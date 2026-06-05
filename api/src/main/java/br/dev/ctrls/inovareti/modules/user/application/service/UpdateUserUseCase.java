package br.dev.ctrls.inovareti.modules.user.application.service;

import io.micrometer.observation.annotation.Observed;

import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import br.dev.ctrls.inovareti.core.shared.domain.model.exception.ConflictException;
import br.dev.ctrls.inovareti.core.shared.domain.model.exception.NotFoundException;
import br.dev.ctrls.inovareti.domain.audit.AuditAction;
import br.dev.ctrls.inovareti.domain.audit.AuditEvent;
import br.dev.ctrls.inovareti.domain.audit.AuditLogService;
import br.dev.ctrls.inovareti.modules.user.domain.model.Sector;
import br.dev.ctrls.inovareti.modules.user.domain.port.output.SectorRepositoryPort;
import br.dev.ctrls.inovareti.modules.user.domain.model.User;
import br.dev.ctrls.inovareti.modules.user.domain.port.output.UserRepositoryPort;
import br.dev.ctrls.inovareti.modules.user.application.dto.UpdateUserRequestDTO;
import br.dev.ctrls.inovareti.modules.user.application.dto.UserResponseDTO;
import lombok.RequiredArgsConstructor;

/**
 * Caso de uso: atualiza nome, e-mail, perfil e setor de um usuário existente.
 * O e-mail único é validado excluindo o próprio usuário sendo editado.
 */
@Component
@RequiredArgsConstructor
@Observed
public class UpdateUserUseCase {

    private final UserRepositoryPort userRepository;
    private final SectorRepositoryPort sectorRepository;
    private final AuditLogService auditLogService;

    @Transactional
    public UserResponseDTO execute(UUID userId, UpdateUserRequestDTO request, UUID adminUserId, String ipAddress) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("Usuário não encontrado: " + userId));

        String contaAzulId = request.contaAzulId() != null && !request.contaAzulId().isBlank()
                ? request.contaAzulId().trim()
                : null;

        // Verifica conflito de e-mail apenas em relação a outros usuários
        if (!user.getEmail().equalsIgnoreCase(request.email())
                && userRepository.existsByEmail(request.email())) {
            throw new ConflictException("E-mail já está em uso: " + request.email());
        }

        if (contaAzulId != null) {
            boolean contaAzulIdChanged = !contaAzulId.equals(user.getContaAzulId());
            if (contaAzulIdChanged && userRepository.existsByContaAzulId(contaAzulId)) {
                throw new ConflictException("ID Conta Azul já está em uso: " + contaAzulId);
            }
        }

        Sector sector = sectorRepository.findById(request.sectorId())
                .orElseThrow(() -> new NotFoundException("Setor não encontrado: " + request.sectorId()));

        String oldRole = user.getRole() != null ? user.getRole().name() : null;
        UUID oldSectorId = user.getSector() != null ? user.getSector().getId() : null;

        user.setName(request.name());
        user.setEmail(request.email());
        user.setRole(request.role());
        user.setSector(sector);
        user.setLocation(request.location());
        user.setContaAzulId(contaAzulId);
        if (request.receivesItNotifications() != null) {
            user.setReceivesItNotifications(request.receivesItNotifications());
        }

        UserResponseDTO result = UserResponseDTO.from(userRepository.save(user));

        auditLogService.publish(AuditEvent.of(AuditAction.USER_EDIT)
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


