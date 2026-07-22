package br.dev.ctrls.inovareti.modules.access.infrastructure.adapter.output;

import br.dev.ctrls.inovareti.modules.access.domain.model.GerAcessoRequest;
import br.dev.ctrls.inovareti.modules.access.domain.model.GerAcessoResponse;
import br.dev.ctrls.inovareti.modules.access.domain.model.GerAcessoVisitorRequest;
import br.dev.ctrls.inovareti.modules.access.domain.port.output.GerAcessoClientPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Adaptador de saída síncrono para integração com a catraca física local da GerAcesso.
 * Reutiliza a infraestrutura unificada do GerAcessoClientPort / GerAcessoRestClientAdapter.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GerAcessoCatracaAdapter {

    private final GerAcessoClientPort gerAcessoClientPort;

    /**
     * Efetua o cadastro de agendamento de visita na catraca física reutilizando o GerAcessoClientPort.
     *
     * @param request Payload do visitante formatado conforme esperado pela API externa.
     * @return Resposta opcional contendo credencial e localizador retornado pela GerAcesso.
     */
    public Optional<GerAcessoResponse> sendVisitorRequest(GerAcessoVisitorRequest request) {
        log.info("[CATRACA-POST] Encaminhando cadastro físico para GerAcessoRestClientAdapter. CPF: {}, Médico CPF: {}", 
                request.cpf(), request.cpf_visitado());

        GerAcessoRequest gerAcessoRequest = GerAcessoRequest.builder()
                .cpf(request.cpf() != null ? request.cpf().replaceAll("\\D", "") : "")
                .status(request.status() > 0 ? request.status() : 1)
                .name(request.nome())
                .phone(request.telefone() != null ? request.telefone() : "")
                .email(request.email() != null ? request.email() : "")
                .visitType(request.tipovisista() > 0 ? request.tipovisista() : 1)
                .visitedRegistration(request.matricula_visitado())
                .visitedCpf(request.cpf_visitado() != null ? request.cpf_visitado() : "")
                .startVisit(request.inicio_visita())
                .endVisit(request.fim_visita())
                .build();

        return gerAcessoClientPort.registerAccess(gerAcessoRequest);
    }

    public Optional<GerAcessoResponse> sendVisitorRequest(GerAcessoRequest request) {
        return gerAcessoClientPort.registerAccess(request);
    }
}
