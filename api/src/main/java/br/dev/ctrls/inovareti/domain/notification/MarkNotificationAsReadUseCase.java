package br.dev.ctrls.inovareti.domain.notification;

import java.util.UUID;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import br.dev.ctrls.inovareti.domain.notification.dto.NotificationResponseDTO;
import lombok.RequiredArgsConstructor;

/**
 * Use case para marcar uma notificação como lida.
 */
@Service
@RequiredArgsConstructor
public class MarkNotificationAsReadUseCase {

    private final NotificationRepository notificationRepository;

    /**
     * Executa a marcação de notificação como lida.
     * @param notificationId o UUID da notificação
     * @param authenticatedUserId o UUID do usuário autenticado
     * @return a notificação atualizada
     */
    public NotificationResponseDTO execute(UUID notificationId, UUID authenticatedUserId) {
        Notification notification = notificationRepository.findById(notificationId)
            .orElseThrow(() -> new IllegalArgumentException("Notificação não encontrada: " + notificationId));

        if (!notification.getUserId().equals(authenticatedUserId)) {
            throw new AccessDeniedException("Você não tem permissão para acessar esta notificação.");
        }

        notification.setIsRead(true);
        Notification updated = notificationRepository.save(notification);

        return NotificationResponseDTO.from(updated);
    }
}
