package br.dev.ctrls.inovareti.domain.notification;

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
import org.springframework.web.bind.annotation.RestController;

import br.dev.ctrls.inovareti.domain.notification.dto.NotificationResponseDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Controller REST para gerenciamento de notificações in-app.
 * Base path: /api/notifications
 */
@Slf4j
@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final GetUnreadNotificationsUseCase getUnreadNotificationsUseCase;
    private final MarkNotificationAsReadUseCase markNotificationAsReadUseCase;
    private final NotificationRepository notificationRepository;

    /**
     * Lista todas as notificações do usuário autenticado (lidas e não lidas).
     * Retorna 200 OK com a lista de notificações ordenadas por data decrescente.
     */
    @GetMapping
    public ResponseEntity<List<NotificationResponseDTO>> getAll() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        UUID userId;

        try {
            userId = UUID.fromString(auth.getPrincipal().toString());
        } catch (Exception e) {
            log.warn("Could not parse user ID from authentication");
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
     * Lista todas as notificações não lidas do usuário autenticado.
     * Retorna 200 OK com a lista de notificações.
     */
    @GetMapping("/unread")
    public ResponseEntity<List<NotificationResponseDTO>> getUnread() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        UUID userId;

        try {
            userId = UUID.fromString(auth.getPrincipal().toString());
        } catch (Exception e) {
            log.warn("Could not parse user ID from authentication");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        List<NotificationResponseDTO> notifications = getUnreadNotificationsUseCase.execute(userId);
        return ResponseEntity.ok(notifications);
    }

    /**
     * Marca uma notificação específica como lida.
     * Retorna 200 OK com a notificação atualizada.
     */
    @PatchMapping("/{id}/read")
    public ResponseEntity<NotificationResponseDTO> markAsRead(@PathVariable UUID id) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        UUID authenticatedUserId;

        try {
            authenticatedUserId = UUID.fromString(auth.getPrincipal().toString());
        } catch (Exception e) {
            log.warn("Could not parse user ID from authentication");
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
