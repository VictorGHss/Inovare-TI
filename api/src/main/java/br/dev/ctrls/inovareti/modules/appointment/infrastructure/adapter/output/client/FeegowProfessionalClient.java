package br.dev.ctrls.inovareti.modules.appointment.infrastructure.adapter.output.client;

import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;

/**
 * Cliente HTTP Declarativo: FeegowProfessionalClient.
 * Mapeia os endpoints relativos aos profissionais de saúde da Feegow por meio das HTTP Interfaces do Spring 3.x.
 */
@HttpExchange
public interface FeegowProfessionalClient {

    /**
     * Efetua requisição GET para obter detalhes cadastrais ou lista de profissionais na Feegow de forma dinâmica.
     *
     * @param uri URI absoluta contendo query params
     * @param accessToken token x-access-token enviado no cabeçalho
     * @return resposta HTTP com o JSON bruto
     */
    @GetExchange
    ResponseEntity<String> getProfessionalDetails(
            URI uri,
            @RequestHeader("x-access-token") String accessToken);
}
