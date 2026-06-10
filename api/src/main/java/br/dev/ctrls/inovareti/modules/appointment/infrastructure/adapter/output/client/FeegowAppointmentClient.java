package br.dev.ctrls.inovareti.modules.appointment.infrastructure.adapter.output.client;

import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.PostExchange;
import org.springframework.web.service.annotation.HttpExchange;

/**
 * Cliente HTTP Declarativo: FeegowAppointmentClient.
 * Mapeia os endpoints relativos à busca e atualização de agendamentos da Feegow por meio das HTTP Interfaces do Spring 3.x.
 */
@HttpExchange
public interface FeegowAppointmentClient {

    /**
     * Efetua requisição GET para buscar agendamentos na Feegow de forma dinâmica.
     *
     * @param uri URI absoluta contendo query params
     * @param accessToken token x-access-token enviado no cabeçalho
     * @return resposta HTTP com o JSON bruto
     */
    @GetExchange
    ResponseEntity<String> searchAppointments(
            URI uri,
            @RequestHeader("x-access-token") String accessToken);

    /**
     * Efetua requisição POST para atualizar o status de um agendamento na Feegow de forma dinâmica.
     *
     * @param uri URI absoluta de atualização de status
     * @param accessToken token x-access-token enviado no cabeçalho
     * @param payload DTO contendo o ID do agendamento, ID do status e observação opcional
     * @return resposta HTTP com o resultado
     */
    @PostExchange
    ResponseEntity<String> updateStatus(
            URI uri,
            @RequestHeader("x-access-token") String accessToken,
            @RequestBody FeegowStatusUpdatePayload payload);

    @PostExchange
    ResponseEntity<String> cancelAppointment(
            URI uri,
            @RequestHeader("x-access-token") String accessToken,
            @RequestBody FeegowCancelPayload payload);
}
