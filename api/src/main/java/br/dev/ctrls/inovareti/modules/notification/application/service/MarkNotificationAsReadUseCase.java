package br.dev.ctrls.inovareti.modules.notification.application.service;

import java.util.UUID;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import br.dev.ctrls.inovareti.modules.notification.application.dto.NotificationResponseDTO;
import br.dev.ctrls.inovareti.modules.notification.domain.model.Notification;
import br.dev.ctrls.inovareti.modules.notification.domain.port.output.NotificationRepositoryPort;
import lombok.RequiredArgsConstructor;

/**
 * Use case para marcar uma notificação como lida.
 */
@Service
@RequiredArgsConstructor
public class MarkNotificationAsReadUseCase {

    private final NotificationRepositoryPort notificationRepository;

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
