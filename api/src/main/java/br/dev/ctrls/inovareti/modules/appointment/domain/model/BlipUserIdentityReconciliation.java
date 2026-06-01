package br.dev.ctrls.inovareti.modules.appointment.domain.model;

import java.time.LocalDateTime;
import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Modelo de Domínio: BlipUserIdentityReconciliation.
 * COMENTÁRIO EM PORTUGUÊS (PT-BR):
 * Representa no domínio a reconciliação e o mapeamento entre a identidade digital (GUID)
 * gerada pela plataforma Blip, o identificador do negócio da Meta (BSUID) e o número
 * de telefone físico do paciente, permitindo total rastreabilidade.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BlipUserIdentityReconciliation {

    private UUID id;
    private String blipGuid;
    private String bsuid;
    private String phoneNumber;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
