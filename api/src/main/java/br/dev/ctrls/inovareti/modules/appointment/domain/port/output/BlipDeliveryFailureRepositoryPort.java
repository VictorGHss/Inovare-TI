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

    /**
     * Busca paginada e filtrada de todas as falhas de entrega registradas.
     *
     * @param appointmentId O ID do agendamento Feegow (opcional).
     * @param category A categoria textual do erro (opcional).
     * @param pageable Configuração de paginação e ordenação.
     * @return Página de falhas de entrega encontradas.
     */
    org.springframework.data.domain.Page<BlipDeliveryFailure> findAllFiltered(
            String appointmentId,
            String category,
            org.springframework.data.domain.Pageable pageable
    );
}
