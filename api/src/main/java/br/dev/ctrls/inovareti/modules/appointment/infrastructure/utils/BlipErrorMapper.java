package br.dev.ctrls.inovareti.modules.appointment.infrastructure.utils;

public enum BlipErrorMapper {

    INVALID_ENVELOPE(21, "Erro de Formatação (Envelope Inválido)."),
    WHATSAPP_BLOCKED_OR_INVALID(471, "Número não é WhatsApp ou bloqueado por spam."),
    TOKEN_EXPIRED_OR_FORBIDDEN(135, "Token expirado ou sem permissão."),
    UNKNOWN(-1, "Erro desconhecido na API do Blip.");

    private final int code;
    private final String description;

    BlipErrorMapper(int code, String description) {
        this.code = code;
        this.description = description;
    }

    public int getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }

    public static BlipErrorMapper fromCode(Integer code) {
        if (code == null) {
            return UNKNOWN;
        }

        for (BlipErrorMapper value : values()) {
            if (value.code == code) {
                return value;
            }
        }

        return UNKNOWN;
    }
}
