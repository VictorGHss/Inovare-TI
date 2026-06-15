package br.dev.ctrls.inovareti.modules.appointment.domain.model;

/**
 * Classificador de erros do ecossistema Blip e WhatsApp (Meta).
 * Categoriza códigos numéricos de falhas em grupos textuais padronizados para fins de métricas e alertas.
 */
public class BlipErrorClassifier {

    /**
     * Traduz e agrupa os códigos de erro do Blip/Meta em categorias operacionais amigáveis.
     *
     * @param errorCode Código de erro numérico retornado.
     * @return String representando a tag de categoria de erro.
     */
    public static String classify(Integer errorCode) {
        if (errorCode == null) {
            return "FALHA_DESCONHECIDA";
        }

        return switch (errorCode) {
            // Códigos 1, 2 (erros gerais do sistema), 51 (gateway timeout), 
            // 81 (conexão pendente), 86 (tempo limite excedido) e 1601 (erro interno do provedor).
            case 1, 2, 51, 81, 86, 1601 -> "ERRO_INTERNO_OU_GATEWAY";

            // Código 38 (excesso de requisições paralelas) e 429 (limite de taxa padrão atingido).
            case 38, 429 -> "RATE_LIMIT_EXCEDIDO";

            // Código 100 (estrutura ou parâmetros do template incompatíveis/inválidos).
            case 100 -> "PARAMETRO_INVALIDO_TEMPLATE";

            // Códigos 1505 (número não registrado no WhatsApp) e 131026 (número de telefone inválido ou inexistente).
            case 1505, 131026 -> "DESTINATARIO_INVALIDO_WHATSAPP";

            // Código 1602 (tentativa de enviar mensagem ativa quando já existe um atendimento humano ou chatbot ativo).
            case 1602 -> "CONFLITO_ATENDIMENTO_ATIVO";

            // Código 130472 (política de experimento ou bloqueio Meta em campanhas/disparos ativos).
            case 130472 -> "EXPERIMENTO_META_BLOQUEADO";

            // Código 131031 (bloqueio temporário ou permanente da conta de negócios da Meta - WABA).
            case 131031 -> "CONTA_BUSINESS_BLOQUEADA";

            // Códigos 131051 (mídia não suportada), 131052 (tamanho de mídia inválido) e 131053 (tipo de arquivo incorreto).
            case 131051, 131052, 131053 -> "MEDIA_OU_TIPO_INCOMPATIVEL";

            // Qualquer outro código não mapeado na documentação do Blip cai em categoria genérica.
            default -> "FALHA_DESCONHECIDA";
        };
    }
}
