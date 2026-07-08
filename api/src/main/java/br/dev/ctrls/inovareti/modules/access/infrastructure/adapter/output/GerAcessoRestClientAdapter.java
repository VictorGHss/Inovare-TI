package br.dev.ctrls.inovareti.modules.access.infrastructure.adapter.output;

import br.dev.ctrls.inovareti.modules.access.domain.model.GerAcessoRequest;
import br.dev.ctrls.inovareti.modules.access.domain.model.GerAcessoResponse;
import br.dev.ctrls.inovareti.modules.access.domain.port.output.GerAcessoClientPort;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import java.util.Optional;

/**
 * Adaptador de infraestrutura GerAcessoRestClientAdapter.
 * Realiza as requisições HTTP locais para a API local da GerAcesso física.
 * Comentários mantidos em PT-BR pelas Regras de Ouro.
 */
@Slf4j
@Component
public class GerAcessoRestClientAdapter implements GerAcessoClientPort {

    @Value("${inovare.geracesso.url}")
    private String url;

    @Value("${inovare.geracesso.token}")
    private String token;

    private RestClient restClient;

    @PostConstruct
    public void init() {
        log.info("[GerAcesso-Adapter] Inicializando cliente RestClient apontando para: {}", url);
        
        java.net.http.HttpClient httpClient = java.net.http.HttpClient.newBuilder()
                .connectTimeout(java.time.Duration.ofSeconds(10))
                .build();
                
        org.springframework.http.client.JdkClientHttpRequestFactory factory = 
                new org.springframework.http.client.JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(java.time.Duration.ofSeconds(10));

        this.restClient = RestClient.builder()
                .baseUrl(url)
                .requestFactory(factory)
                .build();
    }

    @Override
    public Optional<GerAcessoResponse> registerAccess(GerAcessoRequest request) {
        log.info("[GerAcesso-Adapter] Realizando requisição POST para cadastrar visitante CPF: {}", request.cpf());
        try {
            ResponseEntity<GerAcessoResponse> response = restClient.post()
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer " + token.trim())
                    .body(request)
                    .retrieve()
                    .toEntity(GerAcessoResponse.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                log.info("[GerAcesso-Adapter] Cadastro concluído. Resposta: status={}, mensagem={}", 
                        response.getBody().status(), response.getBody().message());
                return Optional.of(response.getBody());
            }

            log.warn("[GerAcesso-Adapter] Servidor GerAcesso retornou status HTTP: {}", response.getStatusCode());
            return Optional.empty();

        } catch (Exception ex) {
            log.error("[GerAcesso-Adapter] Erro na requisição HTTP para GerAcesso. CPF={}. Erro: {}", 
                    request.cpf(), ex.getMessage(), ex);
            return Optional.empty();
        }
    }
}
