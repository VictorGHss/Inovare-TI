package br.dev.ctrls.inovareti.modules.appointment.infrastructure.adapter.output.feegow;

import br.dev.ctrls.inovareti.modules.appointment.infrastructure.config.AppointmentMotorProperties;
import br.dev.ctrls.inovareti.modules.appointment.infrastructure.config.FeegowProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Classe Base Abstrata: AbstractFeegowAdapter.
 * Centraliza a injeção de propriedades comuns da Feegow e fornece
 * métodos auxiliares para normalização de chaves de acesso, resolução de unidades,
 * truncamento de logs e mascaramento de tokens para fins de diagnóstico.
 */
@Slf4j
@RequiredArgsConstructor
public abstract class AbstractFeegowAdapter {

    protected final AppointmentMotorProperties properties;
    protected final FeegowProperties feegowProperties;
    protected final ObjectMapper objectMapper;

    /**
     * Retorna a chave de acesso (API Key) normalizada da Feegow.
     * Remove o prefixo "Bearer " caso presente.
     *
     * @return token limpo de acesso
     */
    protected String getAccessToken() {
        String apiKey = feegowProperties.getApiKey();
        if (apiKey == null) {
            return "";
        }
        String normalized = apiKey.trim();
        if (normalized.regionMatches(true, 0, "Bearer ", 0, 7)) {
            normalized = normalized.substring(7).trim();
        }
        return normalized;
    }

    /**
     * Resolve a Unidade ID da Feegow seguindo a precedência:
     * 1. Variável de ambiente FEEGOW_UNIDADE_ID.
     * 2. Propriedade configurada no arquivo de properties.
     *
     * @return ID da unidade ou String vazia caso indisponível
     */
    protected String resolveUnidadeId() {
        String env = System.getenv("FEEGOW_UNIDADE_ID");
        if (env != null && !env.isBlank() && !"0".equals(env.trim())) {
            return env.trim();
        }
        String localUnidadeId = feegowProperties.getUnidadeId();
        if (localUnidadeId != null && !localUnidadeId.isBlank() && !"0".equals(localUnidadeId.trim())) {
            return localUnidadeId.trim();
        }
        return "";
    }

    /**
     * Abrevia a resposta da requisição Feegow para evitar logs extremamente grandes.
     *
     * @param responseBody corpo do JSON retornado
     * @return String abreviada (máximo 500 caracteres)
     */
    protected String abbreviateResponseBody(String responseBody) {
        if (responseBody == null) {
            return "";
        }
        String normalized = responseBody.trim();
        int maxLength = 500;
        if (normalized.length() <= maxLength) {
            return normalized;
        }
        return normalized.substring(0, maxLength) + "...";
    }

    /**
     * Mascara tokens de autenticação para evitar exibição de segredos nos logs.
     *
     * @param token token completo
     * @return token mascarado
     */
    protected String maskToken(String token) {
        if (token == null || token.isBlank()) {
            return "";
        }
        if (token.length() <= 8) {
            return "***";
        }
        return token.substring(0, 4) + "..." + token.substring(token.length() - 4);
    }
}
