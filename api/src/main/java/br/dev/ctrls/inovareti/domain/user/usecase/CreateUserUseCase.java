package br.dev.ctrls.inovareti.domain.user.usecase;

import org.springframework.security.crypto.password.PasswordEncoder;
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
import br.dev.ctrls.inovareti.domain.user.dto.UserRequestDTO;
import br.dev.ctrls.inovareti.domain.user.dto.UserResponseDTO;
import lombok.RequiredArgsConstructor;

/**
 * Caso de uso: cria um novo usuário no sistema.
 * Responsabilidades:
 *   1. Verificar unicidade do e-mail.
 *   2. Validar existência do setor.
 *   3. Gerar hash BCrypt da senha.
 *   4. Persistir e retornar o DTO de resposta.
 */
@Component
@RequiredArgsConstructor
public class CreateUserUseCase {

    private final UserRepository userRepository;
    private final SectorRepository sectorRepository;
    private final PasswordEncoder passwordEncoder;
        private final AuditLogService auditLogService;

    /**
     * Executa a criação do usuário.
     *
     * @param request DTO com os dados do novo usuário (senha em texto puro)
     * @return DTO com os dados públicos do usuário criado
     * @throws ConflictException  se o e-mail já estiver cadastrado
     * @throws NotFoundException  se o sectorId não corresponder a um setor existente
     */
    @Transactional
    public UserResponseDTO execute(UserRequestDTO request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new ConflictException(
                    "Já existe um usuário com o e-mail: " + request.email()
            );
        }

        Sector sector = sectorRepository.findById(request.sectorId())
                .orElseThrow(() -> new NotFoundException(
                        "Setor não encontrado com o id: " + request.sectorId()
                ));

        User user = User.builder()
                .name(request.name())
                .email(request.email())
                .passwordHash(passwordEncoder.encode(request.password()))
                .role(request.role())
                .sector(sector)
                .location(request.location() != null && !request.location().isBlank() 
                        ? request.location() 
                        : "Não especificado")
                .discordUserId(request.discordUserId())
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
