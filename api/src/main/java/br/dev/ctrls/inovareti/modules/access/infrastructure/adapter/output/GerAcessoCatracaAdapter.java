package br.dev.ctrls.inovareti.modules.access.infrastructure.adapter.output;

import br.dev.ctrls.inovareti.modules.access.domain.model.GerAcessoVisitorRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import java.time.Duration;

/**
 * Adaptador de saída síncrono para integração com a catraca física local da GerAcesso.
 * Dispara requisições POST para a URL da clínica com timeout estrito de 3 segundos.
 */
@Slf4j
@Component
public class GerAcessoCatracaAdapter {

    private final RestClient restClient;

    public GerAcessoCatracaAdapter() {
        java.net.http.HttpClient httpClient = java.net.http.HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                .build();

        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(Duration.ofSeconds(3));

        this.restClient = RestClient.builder()
                .baseUrl("http://172.25.100.106:8082/AgendamentoVisita")
                .requestFactory(factory)
                .build();
    }

    /**
     * Efetua o cadastro de agendamento de visita na catraca física.
     *
     * @param request Payload do visitante formatado conforme esperado pela API externa.
     */
    public void sendVisitorRequest(GerAcessoVisitorRequest request) {
        log.info("[CATRACA-POST] Enviando agendamento físico para catraca GerAcesso. CPF: {}, Médico CPF: {}", 
                request.cpf(), request.cpf_visitado());
        try {
            ResponseEntity<String> response = restClient.post()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .toEntity(String.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                log.info("[CATRACA-POST] Sincronização da catraca concluída com sucesso para o CPF: {}", request.cpf());
            } else {
                log.warn("[CATRACA-POST] Falha na sincronização da catraca. Código de retorno HTTP: {}", response.getStatusCode());
            }
        } catch (Exception ex) {
            log.error("[CATRACA-POST] Erro ao enviar requisição para a catraca GerAcesso para o CPF: {}. Detalhes: {}", 
                    request.cpf(), ex.getMessage(), ex);
        }
    }
}
