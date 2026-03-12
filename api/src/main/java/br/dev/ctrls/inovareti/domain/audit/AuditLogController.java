package br.dev.ctrls.inovareti.domain.audit;

import java.time.LocalDateTime;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import br.dev.ctrls.inovareti.core.exception.BadRequestException;
import br.dev.ctrls.inovareti.domain.audit.dto.QrScanAuditRequestDTO;
import br.dev.ctrls.inovareti.domain.audit.dto.AuditLogResponseDTO;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Endpoint REST para consulta de logs de auditoria.
 * Exclusivo para administradores: GET /api/audit-logs
 */
@RestController
@RequestMapping("/api/audit-logs")
@RequiredArgsConstructor
public class AuditLogController {

    private final AuditLogService auditLogService;

    /**
     * Lista os logs de auditoria com suporte a filtros opcionais.
     *
     * @param userId    filtra por usuário específico
     * @param action    filtra por tipo de ação (ex: LOGIN_FAILURE)
     * @param startDate limita registros a partir desta data/hora (ISO-8601)
     * @param endDate   limita registros até esta data/hora (ISO-8601)
     * @param page      número da página (zero-based, padrão 0)
     * @param size      itens por página (padrão 20, máx 100)
     * @return página paginada de {@link AuditLogResponseDTO}
     */
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping
    public ResponseEntity<Page<AuditLogResponseDTO>> getAuditLogs(
            @RequestParam(required = false) UUID userId,
            @RequestParam(required = false) AuditAction action,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        // Limita o tamanho máximo de página para evitar consultas excessivas
        int safeSize = Math.min(size, 100);
        return ResponseEntity.ok(auditLogService.query(userId, action, startDate, endDate, page, safeSize));
    }

    @PreAuthorize("isAuthenticated()")
    @PostMapping("/qr-scan")
    public ResponseEntity<Void> registerQrScan(
            @Valid @RequestBody QrScanAuditRequestDTO request,
            HttpServletRequest httpRequest) {

        UUID authenticatedUserId = getAuthenticatedUserId();
        auditLogService.publish(AuditEvent.of(AuditAction.QR_SCAN)
                .userId(authenticatedUserId)
                .resourceType("QR")
                .details("{\"path\": \"" + request.scannedPath() + "\"}")
                .ipAddress(getClientIp(httpRequest))
                .build());

        return ResponseEntity.noContent().build();
    }

    private UUID getAuthenticatedUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getPrincipal() == null) {
            throw new BadRequestException("Usuário autenticado não encontrado.");
        }

        try {
            return UUID.fromString(auth.getPrincipal().toString());
        } catch (IllegalArgumentException ex) {
            throw new BadRequestException("Identificador do usuário autenticado inválido.");
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
