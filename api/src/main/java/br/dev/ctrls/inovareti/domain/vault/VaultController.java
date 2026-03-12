package br.dev.ctrls.inovareti.domain.vault;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.fasterxml.jackson.databind.ObjectMapper;

import br.dev.ctrls.inovareti.core.exception.BadRequestException;
import br.dev.ctrls.inovareti.domain.audit.AuditAction;
import br.dev.ctrls.inovareti.domain.audit.AuditEvent;
import br.dev.ctrls.inovareti.domain.audit.AuditLogService;
import br.dev.ctrls.inovareti.domain.vault.dto.VaultCreateItemRequestDTO;
import br.dev.ctrls.inovareti.domain.vault.dto.VaultItemResponseDTO;
import br.dev.ctrls.inovareti.domain.vault.dto.VaultSecretResponseDTO;
import br.dev.ctrls.inovareti.infra.security.TwoFactorSessionGuard;
import br.dev.ctrls.inovareti.infra.storage.LocalFileStorageService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/vault")
@RequiredArgsConstructor
public class VaultController {

    private final VaultService vaultService;
    private final TwoFactorSessionGuard twoFactorSessionGuard;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final LocalFileStorageService fileStorageService;
    private final AuditLogService auditLogService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<VaultItemResponseDTO> createItem(
            @RequestPart("payload") String payload,
            @RequestPart(value = "file", required = false) MultipartFile file,
            HttpServletRequest httpRequest) {

        VaultCreateItemRequestDTO request = parseCreatePayload(payload);
        VaultItemResponseDTO response = vaultService.createItem(
                getAuthenticatedUserId(), request, file, getClientIp(httpRequest));
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<List<VaultItemResponseDTO>> listVisibleItems() {
        return ResponseEntity.ok(vaultService.listVisibleItems(getAuthenticatedUserId()));
    }

    @GetMapping("/{itemId}/secret")
    public ResponseEntity<VaultSecretResponseDTO> getSecret(
            @PathVariable UUID itemId,
            HttpServletRequest httpRequest) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        twoFactorSessionGuard.assertVerified(authentication);
        return ResponseEntity.ok(
                vaultService.getSecret(getAuthenticatedUserId(), itemId, getClientIp(httpRequest)));
    }

    @GetMapping("/{itemId}/file")
    public ResponseEntity<byte[]> getVaultFile(
            @PathVariable UUID itemId,
            HttpServletRequest httpRequest) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        twoFactorSessionGuard.assertVerified(authentication);

        UUID userId = getAuthenticatedUserId();
        var item = vaultService.findAccessibleItem(userId, itemId);
        if (item.getFilePath() == null || item.getFilePath().isBlank()) {
            throw new BadRequestException("Este item não possui anexo para visualização.");
        }

        try {
            var resource = fileStorageService.load(item.getFilePath());
            byte[] fileBytes = resource.getInputStream().readAllBytes();
            String contentType = resolveContentType(item.getFilePath());

            // Registra visualização de arquivo do Vault na trilha de auditoria
            auditLogService.publish(AuditEvent.of(AuditAction.VAULT_FILE_VIEW)
                    .userId(userId)
                    .resourceType("VaultItem")
                    .resourceId(item.getId())
                    .details("{\"itemTitle\": \"" + item.getTitle() + "\"}")
                    .ipAddress(getClientIp(httpRequest))
                    .build());

            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(contentType))
                    .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + item.getFilePath() + "\"")
                    .body(fileBytes);
        } catch (IOException ex) {
            throw new BadRequestException("Não foi possível carregar o anexo do item do cofre.");
        }
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

    /** Extrai o IP real do cliente, respeitando proxies reversos via X-Forwarded-For. */
    private String getClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private VaultCreateItemRequestDTO parseCreatePayload(String payload) {
        try {
            return objectMapper.readValue(payload, VaultCreateItemRequestDTO.class);
        } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
            throw new BadRequestException("Payload do item do cofre inválido.");
        }
    }

    private String resolveContentType(String fileName) {
        String normalizedFileName = fileName.toLowerCase();

        if (normalizedFileName.endsWith(".png")) return "image/png";
        if (normalizedFileName.endsWith(".jpg") || normalizedFileName.endsWith(".jpeg")) return "image/jpeg";
        if (normalizedFileName.endsWith(".gif")) return "image/gif";
        if (normalizedFileName.endsWith(".webp")) return "image/webp";
        if (normalizedFileName.endsWith(".mp4")) return "video/mp4";
        if (normalizedFileName.endsWith(".webm")) return "video/webm";
        if (normalizedFileName.endsWith(".mov")) return "video/quicktime";
        if (normalizedFileName.endsWith(".pdf")) return "application/pdf";

        return "application/octet-stream";
    }
}