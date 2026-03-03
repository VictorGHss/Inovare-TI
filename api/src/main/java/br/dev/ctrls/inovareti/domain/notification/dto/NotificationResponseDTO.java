package br.dev.ctrls.inovareti.domain.notification.dto;

import java.time.LocalDateTime;
import java.util.UUID;

import br.dev.ctrls.inovareti.domain.notification.Notification;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * DTO para resposta de notificações.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationResponseDTO {

    private UUID id;
    private String title;
    private String message;
    private Boolean isRead;
    private String link;
    private LocalDateTime createdAt;

    /**
     * Converte uma Notification para NotificationResponseDTO.
     */
    public static NotificationResponseDTO from(Notification notification) {
        return NotificationResponseDTO.builder()
                .id(notification.getId())
                .title(notification.getTitle())
                .message(notification.getMessage())
                .isRead(notification.getIsRead())
                .link(notification.getLink())
                .createdAt(notification.getCreatedAt())
                .build();
    }
}
