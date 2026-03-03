package br.dev.ctrls.inovareti.domain.notification;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, UUID> {
    /**
     * Busca todas as notificações não lidas de um usuário.
     * @param userId o UUID do usuário
     * @return lista de notificações não lidas
     */
    List<Notification> findByUserIdAndIsReadFalseOrderByCreatedAtDesc(UUID userId);
}
