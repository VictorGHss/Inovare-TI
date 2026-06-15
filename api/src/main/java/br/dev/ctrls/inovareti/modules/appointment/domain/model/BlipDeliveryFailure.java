package br.dev.ctrls.inovareti.modules.appointment.domain.model;

import java.time.LocalDateTime;
import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Entidade de domínio pura que representa uma falha de entrega de notificação/mensagem do Blip.
 * Esta classe é livre de acoplamentos com anotações de infraestrutura (como JPA/@Entity).
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BlipDeliveryFailure {

    /**
     * Identificador único da falha de entrega no banco de dados.
     */
    private UUID id;

    /**
     * ID da mensagem original (UUID gerado pela aplicação) que gerou a falha.
     */
    private String messageId;

    /**
     * ID do agendamento correlacionado no Feegow.
     */
    private String appointmentId;

    /**
     * Código do erro de entrega retornado pela API da Blip/Meta (ex: 131042 para falta de saldo).
     */
    private Integer errorCode;

    /**
     * Mensagem ou descrição detalhada do motivo da falha.
     */
    private String errorMessage;

    /**
     * Identificador de rastreamento (traceId) da requisição que gerou ou recebeu a falha.
     */
    private String traceId;

    /**
     * Data e hora do registro da falha.
     */
    private LocalDateTime createdAt;
}
