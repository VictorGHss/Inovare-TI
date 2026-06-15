package br.dev.ctrls.inovareti.modules.appointment.domain.port.output;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import br.dev.ctrls.inovareti.modules.appointment.domain.model.BlipDeliveryFailure;

/**
 * Porta de saída (Outbound Port) que define o contrato de persistência para falhas de entrega de mensagens do Blip.
 */
public interface BlipDeliveryFailureRepositoryPort {

    /**
     * Persiste uma falha de entrega de mensagem.
     *
     * @param failure A entidade de domínio contendo os dados do erro.
     * @return A entidade de domínio persistida e atualizada.
     */
    BlipDeliveryFailure save(BlipDeliveryFailure failure);

    /**
     * Busca uma falha de entrega específica pelo seu identificador único.
     *
     * @param id O ID único gerado.
     * @return Opcional contendo a falha encontrada.
     */
    Optional<BlipDeliveryFailure> findById(UUID id);

    /**
     * Busca todas as falhas de entrega associadas a um identificador de mensagem do Blip.
     *
     * @param messageId O ID da mensagem do Blip.
     * @return Lista de falhas registradas para a mensagem.
     */
    List<BlipDeliveryFailure> findByMessageId(String messageId);

    /**
     * Busca todas as falhas de entrega associadas a um agendamento Feegow.
     *
     * @param appointmentId O ID do agendamento Feegow.
     * @return Lista de falhas registradas para o agendamento.
     */
    List<BlipDeliveryFailure> findByAppointmentId(String appointmentId);
}
