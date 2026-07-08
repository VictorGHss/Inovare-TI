package br.dev.ctrls.inovareti.modules.access.domain.port.output;

import br.dev.ctrls.inovareti.modules.access.domain.model.GerAcessoRequest;
import br.dev.ctrls.inovareti.modules.access.domain.model.GerAcessoResponse;
import java.util.Optional;

/**
 * Porta de saída (Output Port) para integração com a API local do GerAcesso.
 * Comentários mantidos em PT-BR.
 */
public interface GerAcessoClientPort {

    /**
     * Efetua o cadastro de agendamento de visita/acesso físico no GerAcesso.
     *
     * @param request Payload contendo os dados a serem cadastrados.
     * @return Opcional com a resposta recebida do servidor GerAcesso.
     */
    Optional<GerAcessoResponse> registerAccess(GerAcessoRequest request);
}
