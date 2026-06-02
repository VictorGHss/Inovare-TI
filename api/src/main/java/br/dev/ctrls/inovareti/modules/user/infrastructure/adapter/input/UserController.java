package br.dev.ctrls.inovareti.modules.user.infrastructure.adapter.input;

import io.micrometer.observation.annotation.Observed;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.dev.ctrls.inovareti.modules.user.application.dto.ChangePasswordRequestDTO;
import br.dev.ctrls.inovareti.modules.user.application.dto.UpdateUserRequestDTO;
import br.dev.ctrls.inovareti.modules.user.application.dto.UserRequestDTO;
import br.dev.ctrls.inovareti.modules.user.application.dto.UserResponseDTO;
import br.dev.ctrls.inovareti.core.shared.domain.model.exception.BadRequestException;
import br.dev.ctrls.inovareti.modules.auth.application.service.TwoFactorResetService;
import br.dev.ctrls.inovareti.modules.user.application.service.ChangeMyPasswordUseCase;
import br.dev.ctrls.inovareti.modules.user.application.service.CreateUserUseCase;
import br.dev.ctrls.inovareti.modules.user.application.service.ListAllUsersUseCase;
import br.dev.ctrls.inovareti.modules.user.application.service.ResetUserPasswordUseCase;
import br.dev.ctrls.inovareti.modules.user.application.service.UpdateUserUseCase;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Controller REST para gerenciamento de usuÃ¡rios.
 * Base path: /api/users
 */
@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
@Observed
public class UserController {

    private final CreateUserUseCase createUserUseCase;
    private final ListAllUsersUseCase listAllUsersUseCase;
    private final ChangeMyPasswordUseCase changeMyPasswordUseCase;
    private final UpdateUserUseCase updateUserUseCase;
    private final ResetUserPasswordUseCase resetUserPasswordUseCase;
    private final TwoFactorResetService twoFactorResetService;

    /**
     * Cria um novo usuÃ¡rio.
     * Retorna 201 Created com o usuÃ¡rio criado no corpo da resposta.
     * Requer permissÃ£o ADMIN.
     */
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping
    public ResponseEntity<UserResponseDTO> create(@Valid @RequestBody UserRequestDTO request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(createUserUseCase.execute(request));
    }

    /**
     * Lista todos os usuÃ¡rios cadastrados.
     * Retorna 200 OK com a lista.
     * Requer permissÃ£o ADMIN, TECHNICIAN ou USER.
     */
    @PreAuthorize("hasAnyRole('ADMIN', 'TECHNICIAN', 'USER')")
    @GetMapping
    public ResponseEntity<List<UserResponseDTO>> listAll() {
        return ResponseEntity.ok(listAllUsersUseCase.execute());
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'TECHNICIAN', 'USER')")
    @PutMapping("/me/password")
    public ResponseEntity<Void> changeMyPassword(@Valid @RequestBody ChangePasswordRequestDTO request) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String authenticatedUserId = auth.getPrincipal().toString();

        changeMyPasswordUseCase.execute(authenticatedUserId, request);
        return ResponseEntity.noContent().build();
    }

    /**
     * Atualiza nome, e-mail, perfil e setor de um usuÃ¡rio.
     * Restrito a ADMIN.
     */
    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/{id}")
    public ResponseEntity<UserResponseDTO> update(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateUserRequestDTO request,
            HttpServletRequest httpRequest) {
        UUID adminUserId = getAuthenticatedUserId();
        return ResponseEntity.ok(updateUserUseCase.execute(id, request, adminUserId, getClientIp(httpRequest)));
    }

    /**
     * Redefine a senha de um usuÃ¡rio para o valor padrÃ£o "Mudar@123"
     * e forÃ§a a troca de senha no prÃ³ximo login.
     * Restrito a ADMIN.
     */
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/{id}/reset-password")
    public ResponseEntity<Void> resetPassword(@PathVariable UUID id) {
        resetUserPasswordUseCase.execute(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Reseta o 2FA de um usuÃ¡rio diretamente (sem cÃ³digo de recuperaÃ§Ã£o).
     * Exclusivo para ADMIN â€” Ãºtil quando o usuÃ¡rio perdeu completamente o acesso.
     */
    @PreAuthorize("hasRole('ADMIN')")
    @PatchMapping("/{id}/2fa/reset")
    public ResponseEntity<Void> adminResetTwoFactor(@PathVariable UUID id, HttpServletRequest httpRequest) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getPrincipal() == null) {
            throw new BadRequestException("UsuÃ¡rio autenticado nÃ£o encontrado.");
        }

        UUID adminUserId;
        try {
            adminUserId = UUID.fromString(authentication.getPrincipal().toString());
        } catch (IllegalArgumentException ex) {
            throw new BadRequestException("Identificador do usuÃ¡rio autenticado invÃ¡lido.");
        }

        twoFactorResetService.adminReset(id, adminUserId, getClientIp(httpRequest));
        return ResponseEntity.noContent().build();
    }

    private UUID getAuthenticatedUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getPrincipal() == null) {
            throw new BadRequestException("UsuÃ¡rio autenticado nÃ£o encontrado.");
        }
        try {
            return UUID.fromString(auth.getPrincipal().toString());
        } catch (IllegalArgumentException ex) {
            throw new BadRequestException("Identificador do usuÃ¡rio autenticado invÃ¡lido.");
        }
    }

    private String getClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}


