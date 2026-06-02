package br.dev.ctrls.inovareti.modules.notification.infrastructure.adapter.input;

import io.micrometer.observation.annotation.Observed;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RestController;

import br.dev.ctrls.inovareti.modules.notification.application.dto.NotificationResponseDTO;
import br.dev.ctrls.inovareti.modules.notification.application.service.GetUnreadNotificationsUseCase;
import br.dev.ctrls.inovareti.modules.notification.application.service.MarkNotificationAsReadUseCase;
import br.dev.ctrls.inovareti.modules.notification.domain.port.output.NotificationRepositoryPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Controller REST para gerenciamento de notificaÃ§Ãµes in-app.
 * Base path: /api/notifications
 */
@Slf4j
@RestController
@RequestMapping("/notifications")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
@Observed
public class NotificationController {

    private final GetUnreadNotificationsUseCase getUnreadNotificationsUseCase;
    private final MarkNotificationAsReadUseCase markNotificationAsReadUseCase;
    private final NotificationRepositoryPort notificationRepository;

    /**
     * Lista todas as notificaÃ§Ãµes do usuÃ¡rio autenticado (lidas e nÃ£o lidas).
     * Retorna 200 OK com a lista de notificaÃ§Ãµes ordenadas por data decrescente.
     */
    @GetMapping
    public ResponseEntity<List<NotificationResponseDTO>> getAll() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        UUID userId;

        try {
            userId = UUID.fromString(auth.getPrincipal().toString());
        } catch (Exception e) {
            log.warn("NÃ£o foi possÃ­vel obter ID do usuÃ¡rio a partir da autenticaÃ§Ã£o");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        List<NotificationResponseDTO> notifications = notificationRepository
            .findByUserIdOrderByCreatedAtDesc(userId)
            .stream()
            .map(NotificationResponseDTO::from)
            .collect(Collectors.toList());
        
        return ResponseEntity.ok(notifications);
    }

    /**
     * Lista todas as notificaÃ§Ãµes nÃ£o lidas do usuÃ¡rio autenticado.
     * Retorna 200 OK com a lista de notificaÃ§Ãµes.
     */
    @GetMapping("/unread")
    public ResponseEntity<List<NotificationResponseDTO>> getUnread() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        UUID userId;

        try {
            userId = UUID.fromString(auth.getPrincipal().toString());
        } catch (Exception e) {
            log.warn("NÃ£o foi possÃ­vel obter ID do usuÃ¡rio a partir da autenticaÃ§Ã£o");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        List<NotificationResponseDTO> notifications = getUnreadNotificationsUseCase.execute(userId);
        return ResponseEntity.ok(notifications);
    }

    /**
     * Marca uma notificaÃ§Ã£o especÃ­fica como lida.
     * Retorna 200 OK com a notificaÃ§Ã£o atualizada.
     */
    @PatchMapping("/{id}/read")
    public ResponseEntity<NotificationResponseDTO> markAsRead(@PathVariable UUID id) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        UUID authenticatedUserId;

        try {
            authenticatedUserId = UUID.fromString(auth.getPrincipal().toString());
        } catch (Exception e) {
            log.warn("NÃ£o foi possÃ­vel obter ID do usuÃ¡rio a partir da autenticaÃ§Ã£o");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        try {
            NotificationResponseDTO notification = markNotificationAsReadUseCase.execute(id, authenticatedUserId);
            log.info("Notification marked as read: {}", id);
            return ResponseEntity.ok(notification);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }
}


