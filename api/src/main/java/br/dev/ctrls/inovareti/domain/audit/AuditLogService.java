package br.dev.ctrls.inovareti.domain.audit;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import br.dev.ctrls.inovareti.domain.audit.dto.AuditLogResponseDTO;
import br.dev.ctrls.inovareti.domain.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Serviço de auditoria: publica eventos e consulta registros com filtros.
 * A publicação de eventos é desacoplada — falhas no listener nunca propagam para o chamador.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuditLogService {

    private final ApplicationEventPublisher eventPublisher;
    private final AuditLogRepository auditLogRepository;
    private final UserRepository userRepository;

    // Cache simples de nome de usuário para evitar N+1 nas consultas de log
    private final Map<UUID, String> userNameCache = new ConcurrentHashMap<>();

    /**
     * Publica um evento de auditoria. O listener persiste de forma assíncrona.
     */
    public void publish(AuditEvent event) {
        eventPublisher.publishEvent(event);
    }

    /**
     * Consulta a trilha de auditoria com filtros opcionais.
     *
     * @param userId    filtra por usuário (opcional)
     * @param action    filtra por tipo de ação (opcional)
     * @param startDate filtro de data inicial (opcional)
     * @param endDate   filtro de data final (opcional)
     * @param page      número da página (zero-based)
     * @param size      tamanho da página (máximo 100)
     */
    public Page<AuditLogResponseDTO> query(
            UUID userId,
            AuditAction action,
            LocalDateTime startDate,
            LocalDateTime endDate,
            int page,
            int size) {

        int safeSize = Math.min(size, 100);
        PageRequest pageable = PageRequest.of(page, safeSize, Sort.by("createdAt").descending());
        String actionName = action != null ? action.name() : null;

        return auditLogRepository
            .findWithFilters(userId, actionName, startDate, endDate, pageable)
                .map(logEntry -> {
                    String name = resolveUserName(logEntry.getUserId());
                    return AuditLogResponseDTO.from(logEntry, name);
                });
    }

    private String resolveUserName(UUID userId) {
        if (userId == null) return "Sistema";
        return userNameCache.computeIfAbsent(userId, id ->
                userRepository.findById(id)
                        .map(u -> u.getName())
                        .orElse("Usuário removido"));
    }
}
