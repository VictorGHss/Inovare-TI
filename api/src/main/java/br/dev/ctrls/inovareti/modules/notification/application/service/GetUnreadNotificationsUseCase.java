package br.dev.ctrls.inovareti.modules.notification.application.service;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import br.dev.ctrls.inovareti.modules.notification.application.dto.NotificationResponseDTO;
import br.dev.ctrls.inovareti.modules.notification.domain.port.output.NotificationRepositoryPort;
import lombok.RequiredArgsConstructor;

/**
 * Use case para buscar notificações não lidas de um usuário.
 */
@Service
@RequiredArgsConstructor
public class GetUnreadNotificationsUseCase {

    private final NotificationRepositoryPort notificationRepository;

    /**
     * Executa a busca de notificações não lidas.
     * @param userId o UUID do usuário autenticado
     * @return lista de notificações não lidas
     */
    public List<NotificationResponseDTO> execute(UUID userId) {
        return notificationRepository
            .findByUserIdAndIsReadFalseOrderByCreatedAtDesc(userId)
            .stream()
            .map(NotificationResponseDTO::from)
            .collect(Collectors.toList());
    }
}
