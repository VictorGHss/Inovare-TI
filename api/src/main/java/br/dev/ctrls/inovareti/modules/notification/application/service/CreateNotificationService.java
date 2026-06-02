package br.dev.ctrls.inovareti.modules.notification.application.service;

import io.micrometer.observation.annotation.Observed;

import java.time.LocalDateTime;
import java.util.UUID;

import org.springframework.stereotype.Service;

import br.dev.ctrls.inovareti.modules.notification.domain.model.Notification;
import br.dev.ctrls.inovareti.modules.notification.domain.port.output.NotificationRepositoryPort;
import lombok.RequiredArgsConstructor;

/**
 * Service para criar notificaÃ§Ãµes.
 * Usado internamente por outros use cases para disparar notificaÃ§Ãµes.
 */
@Service
@RequiredArgsConstructor
@Observed
public class CreateNotificationService {

    private final NotificationRepositoryPort notificationRepository;

    /**
     * Cria uma nova notificaÃ§Ã£o.
     * @param userId o UUID do usuÃ¡rio destinatÃ¡rio
     * @param title o tÃ­tulo da notificaÃ§Ã£o
     * @param message a mensagem da notificaÃ§Ã£o
     * @param link o link para redirecionamento (opcional)
     * @return a notificaÃ§Ã£o criada
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


