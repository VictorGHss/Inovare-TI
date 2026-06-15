package br.dev.ctrls.inovareti.modules.appointment.infrastructure.adapter.output.jpa.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import br.dev.ctrls.inovareti.modules.appointment.infrastructure.adapter.output.jpa.entity.BlipDeliveryFailureEntity;

/**
 * Interface de repositório Spring Data JPA para realizar operações na tabela blip_delivery_failures.
 */
@Repository
public interface SpringDataBlipDeliveryFailureRepository extends JpaRepository<BlipDeliveryFailureEntity, UUID> {

    /**
     * Busca falhas de entrega associadas a uma mensagem do Blip específica.
     *
     * @param messageId O ID da mensagem do Blip.
     * @return Lista de entidades de falha encontradas.
     */
    List<BlipDeliveryFailureEntity> findByMessageId(String messageId);

    /**
     * Busca falhas de entrega associadas a um agendamento Feegow específico.
     *
     * @param appointmentId O ID do agendamento Feegow.
     * @return Lista de entidades de falha encontradas.
     */
    List<BlipDeliveryFailureEntity> findByAppointmentId(String appointmentId);
}
