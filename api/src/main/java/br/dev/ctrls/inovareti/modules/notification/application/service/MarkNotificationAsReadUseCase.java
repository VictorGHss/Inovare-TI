package br.dev.ctrls.inovareti.modules.notification.application.service;

import io.micrometer.observation.annotation.Observed;

import java.util.UUID;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import br.dev.ctrls.inovareti.modules.notification.application.dto.NotificationResponseDTO;
import br.dev.ctrls.inovareti.modules.notification.domain.model.Notification;
import br.dev.ctrls.inovareti.modules.notification.domain.port.output.NotificationRepositoryPort;
import lombok.RequiredArgsConstructor;

/**
 * Use case para marcar uma notificaÃ§Ã£o como lida.
 */
@Service
@RequiredArgsConstructor
@Observed
public class MarkNotificationAsReadUseCase {

    private final NotificationRepositoryPort notificationRepository;

    /**
     * Executa a marcaÃ§Ã£o de notificaÃ§Ã£o como lida.
     * @param notificationId o UUID da notificaÃ§Ã£o
     * @param authenticatedUserId o UUID do usuÃ¡rio autenticado
     * @return a notificaÃ§Ã£o atualizada
     */
    public NotificationResponseDTO execute(UUID notificationId, UUID authenticatedUserId) {
        Notification notification = notificationRepository.findById(notificationId)
            .orElseThrow(() -> new IllegalArgumentException("NotificaÃ§Ã£o nÃ£o encontrada: " + notificationId));

        if (!notification.getUserId().equals(authenticatedUserId)) {
            throw new AccessDeniedException("VocÃª nÃ£o tem permissÃ£o para acessar esta notificaÃ§Ã£o.");
        }

        notification.setIsRead(true);
        Notification updated = notificationRepository.save(notification);

        return NotificationResponseDTO.from(updated);
    }
}


