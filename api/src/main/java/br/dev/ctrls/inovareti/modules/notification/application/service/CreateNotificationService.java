package br.dev.ctrls.inovareti.modules.notification.application.service;

import java.time.LocalDateTime;
import java.util.UUID;

import org.springframework.stereotype.Service;

import br.dev.ctrls.inovareti.modules.notification.domain.model.Notification;
import br.dev.ctrls.inovareti.modules.notification.domain.port.output.NotificationRepositoryPort;
import lombok.RequiredArgsConstructor;

/**
 * Service para criar notificações.
 * Usado internamente por outros use cases para disparar notificações.
 */
@Service
@RequiredArgsConstructor
public class CreateNotificationService {

    private final NotificationRepositoryPort notificationRepository;

    /**
     * Cria uma nova notificação.
     * @param userId o UUID do usuário destinatário
     * @param title o título da notificação
     * @param message a mensagem da notificação
     * @param link o link para redirecionamento (opcional)
     * @return a notificação criada
     */
    public Notification create(UUID userId, String title, String message, String link) {
        Notification notification = Notification.builder()
            .userId(userId)
            .title(title)
            .message(message)
            .isRead(false)
            .link(link)
            .createdAt(LocalDateTime.now())
            .build();

        return notificationRepository.save(notification);
    }
}
