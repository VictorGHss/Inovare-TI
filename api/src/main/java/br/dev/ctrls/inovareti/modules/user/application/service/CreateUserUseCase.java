package br.dev.ctrls.inovareti.modules.user.application.service;

import io.micrometer.observation.annotation.Observed;

import org.springframework.security.crypto.password.PasswordEncoder;
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
import br.dev.ctrls.inovareti.modules.user.application.dto.UserRequestDTO;
import br.dev.ctrls.inovareti.modules.user.application.dto.UserResponseDTO;
import lombok.RequiredArgsConstructor;

/**
 * Caso de uso: cria um novo usuÃ¡rio no sistema.
 * Responsabilidades:
 *   1. Verificar unicidade do e-mail.
 *   2. Validar existÃªncia do setor.
 *   3. Gerar hash BCrypt da senha.
 *   4. Persistir e retornar o DTO de resposta.
 */
@Component
@RequiredArgsConstructor
@Observed
public class CreateUserUseCase {

    private final UserRepositoryPort userRepository;
    private final SectorRepositoryPort sectorRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditLogService auditLogService;

    /**
     * Executa a criaÃ§Ã£o do usuÃ¡rio.
     *
     * @param request DTO com os dados do novo usuÃ¡rio (senha em texto puro)
     * @return DTO com os dados pÃºblicos do usuÃ¡rio criado
     * @throws ConflictException  se o e-mail jÃ¡ estiver cadastrado
     * @throws NotFoundException  se o sectorId nÃ£o corresponder a um setor existente
     */
    @Transactional
    public UserResponseDTO execute(UserRequestDTO request) {
        String contaAzulId = request.contaAzulId() != null && !request.contaAzulId().isBlank()
                ? request.contaAzulId().trim()
                : null;

        if (userRepository.existsByEmail(request.email())) {
            throw new ConflictException(
                    "JÃ¡ existe um usuÃ¡rio com o e-mail: " + request.email()
            );
        }

        if (contaAzulId != null && userRepository.existsByContaAzulId(contaAzulId)) {
            throw new ConflictException(
                    "JÃ¡ existe um usuÃ¡rio com o ID Conta Azul: " + contaAzulId
            );
        }

        Sector sector = sectorRepository.findById(request.sectorId())
                .orElseThrow(() -> new NotFoundException(
                        "Setor nÃ£o encontrado com o id: " + request.sectorId()
                ));

        User user = User.builder()
                .name(request.name())
                .email(request.email())
                .passwordHash(passwordEncoder.encode(request.password()))
                .role(request.role())
                .sector(sector)
                .location(request.location() != null && !request.location().isBlank() 
                        ? request.location() 
                        : "NÃ£o especificado")
                .discordUserId(request.discordUserId())
                .contaAzulId(contaAzulId)
                .receivesItNotifications(
                        request.receivesItNotifications() == null || request.receivesItNotifications())
                .build();

        User savedUser = userRepository.save(user);
        auditLogService.publish(AuditEvent.of(AuditAction.USER_CREATE)
                .resourceType("User")
                .resourceId(savedUser.getId())
                .details("{\"email\": \"" + savedUser.getEmail() + "\"}")
                .build());

        return UserResponseDTO.from(savedUser);
    }
}


