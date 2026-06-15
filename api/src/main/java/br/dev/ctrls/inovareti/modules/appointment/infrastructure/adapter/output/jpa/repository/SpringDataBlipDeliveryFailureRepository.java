package br.dev.ctrls.inovareti.modules.appointment.infrastructure.adapter.output.jpa.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import br.dev.ctrls.inovareti.modules.appointment.infrastructure.adapter.output.jpa.entity.BlipDeliveryFailureEntity;

/**
 * Interface de repositório Spring Data JPA para realizar operações na tabela blip_delivery_failures.
 */
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

    /**
     * Busca filtrada e paginada de falhas de entrega no banco de dados.
     * Permite pesquisar opcionalmente por ID do agendamento Feegow e códigos de erro específicos (inclusão/exclusão).
     *
     * @param appointmentId O ID do agendamento Feegow (opcional).
     * @param filterByCodes Indica se deve filtrar pelos códigos em errorCodes.
     * @param errorCodes Coleção de códigos de erro a serem incluídos na busca.
     * @param filterByNotInCodes Indica se deve filtrar excluindo os códigos em notInErrorCodes.
     * @param notInErrorCodes Coleção de códigos de erro a serem excluídos da busca (usado para falhas desconhecidas).
     * @param pageable Configuração de paginação.
     * @return Página contendo as entidades BlipDeliveryFailureEntity correspondentes.
     */
    @org.springframework.data.jpa.repository.Query("SELECT f FROM BlipDeliveryFailureEntity f WHERE " +
           "(:appointmentId IS NULL OR f.appointmentId = :appointmentId) AND " +
           "(:filterByCodes = false OR f.errorCode IN :errorCodes) AND " +
           "(:filterByNotInCodes = false OR f.errorCode NOT IN :notInErrorCodes)")
    org.springframework.data.domain.Page<BlipDeliveryFailureEntity> findByFilters(
            @org.springframework.data.repository.query.Param("appointmentId") String appointmentId,
            @org.springframework.data.repository.query.Param("filterByCodes") boolean filterByCodes,
            @org.springframework.data.repository.query.Param("errorCodes") java.util.Collection<Integer> errorCodes,
            @org.springframework.data.repository.query.Param("filterByNotInCodes") boolean filterByNotInCodes,
            @org.springframework.data.repository.query.Param("notInErrorCodes") java.util.Collection<Integer> notInErrorCodes,
            org.springframework.data.domain.Pageable pageable
    );
}
