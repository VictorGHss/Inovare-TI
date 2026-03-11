package br.dev.ctrls.inovareti.domain.vault;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.dev.ctrls.inovareti.core.exception.BadRequestException;
import br.dev.ctrls.inovareti.domain.vault.dto.VaultCreateItemRequestDTO;
import br.dev.ctrls.inovareti.domain.vault.dto.VaultItemResponseDTO;
import br.dev.ctrls.inovareti.domain.vault.dto.VaultSecretResponseDTO;
import br.dev.ctrls.inovareti.infra.security.TwoFactorSessionGuard;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/vault")
@RequiredArgsConstructor
public class VaultController {

    private final VaultService vaultService;
    private final TwoFactorSessionGuard twoFactorSessionGuard;

    @PostMapping
    public ResponseEntity<VaultItemResponseDTO> createItem(@Valid @RequestBody VaultCreateItemRequestDTO request) {
        VaultItemResponseDTO response = vaultService.createItem(getAuthenticatedUserId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<List<VaultItemResponseDTO>> listVisibleItems() {
        return ResponseEntity.ok(vaultService.listVisibleItems(getAuthenticatedUserId()));
    }

    @GetMapping("/{itemId}/secret")
    public ResponseEntity<VaultSecretResponseDTO> getSecret(@PathVariable UUID itemId) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        twoFactorSessionGuard.assertVerified(authentication);
        return ResponseEntity.ok(vaultService.getSecret(getAuthenticatedUserId(), itemId));
    }

    private UUID getAuthenticatedUserId() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getPrincipal() == null) {
            throw new BadRequestException("Usuário autenticado não encontrado.");
        }

        try {
            return UUID.fromString(authentication.getPrincipal().toString());
        } catch (IllegalArgumentException ex) {
            throw new BadRequestException("Identificador do usuário autenticado inválido.");
        }
    }
}