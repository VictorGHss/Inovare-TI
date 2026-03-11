package br.dev.ctrls.inovareti.domain.user;

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

import br.dev.ctrls.inovareti.domain.user.dto.ChangePasswordRequestDTO;
import br.dev.ctrls.inovareti.domain.user.dto.UpdateUserRequestDTO;
import br.dev.ctrls.inovareti.domain.user.dto.UserRequestDTO;
import br.dev.ctrls.inovareti.domain.user.dto.UserResponseDTO;
import br.dev.ctrls.inovareti.domain.auth.usecase.TwoFactorResetService;
import br.dev.ctrls.inovareti.domain.user.usecase.ChangeMyPasswordUseCase;
import br.dev.ctrls.inovareti.domain.user.usecase.CreateUserUseCase;
import br.dev.ctrls.inovareti.domain.user.usecase.ListAllUsersUseCase;
import br.dev.ctrls.inovareti.domain.user.usecase.ResetUserPasswordUseCase;
import br.dev.ctrls.inovareti.domain.user.usecase.UpdateUserUseCase;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Controller REST para gerenciamento de usuários.
 * Base path: /api/users
 */
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final CreateUserUseCase createUserUseCase;
    private final ListAllUsersUseCase listAllUsersUseCase;
    private final ChangeMyPasswordUseCase changeMyPasswordUseCase;
    private final UpdateUserUseCase updateUserUseCase;
    private final ResetUserPasswordUseCase resetUserPasswordUseCase;
    private final TwoFactorResetService twoFactorResetService;

    /**
     * Cria um novo usuário.
     * Retorna 201 Created com o usuário criado no corpo da resposta.
     * Requer permissão ADMIN.
     */
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping
    public ResponseEntity<UserResponseDTO> create(@Valid @RequestBody UserRequestDTO request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(createUserUseCase.execute(request));
    }

    /**
     * Lista todos os usuários cadastrados.
     * Retorna 200 OK com a lista.
     * Requer permissão ADMIN, TECHNICIAN ou USER.
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
     * Atualiza nome, e-mail, perfil e setor de um usuário.
     * Restrito a ADMIN.
     */
    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/{id}")
    public ResponseEntity<UserResponseDTO> update(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateUserRequestDTO request) {
        return ResponseEntity.ok(updateUserUseCase.execute(id, request));
    }

    /**
     * Redefine a senha de um usuário para o valor padrão "Mudar@123"
     * e força a troca de senha no próximo login.
     * Restrito a ADMIN.
     */
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/{id}/reset-password")
    public ResponseEntity<Void> resetPassword(@PathVariable UUID id) {
        resetUserPasswordUseCase.execute(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Reseta o 2FA de um usuário diretamente (sem código de recuperação).
     * Exclusivo para ADMIN — útil quando o usuário perdeu completamente o acesso.
     */
    @PreAuthorize("hasRole('ADMIN')")
    @PatchMapping("/{id}/2fa/reset")
    public ResponseEntity<Void> adminResetTwoFactor(@PathVariable UUID id) {
        twoFactorResetService.adminReset(id);
        return ResponseEntity.noContent().build();
    }
}
