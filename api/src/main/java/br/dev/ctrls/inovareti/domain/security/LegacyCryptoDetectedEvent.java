package br.dev.ctrls.inovareti.domain.security;

import org.springframework.context.ApplicationEvent;

/**
 * Evento disparado quando um dado criptografado legado ou em texto puro é detectado
 * na leitura do banco de dados, sinalizando a necessidade de migração para o padrão AES-GCM.
 */
public class LegacyCryptoDetectedEvent extends ApplicationEvent {

    private final String dbData;
    private final String decryptedValue;

    public LegacyCryptoDetectedEvent(Object source, String dbData, String decryptedValue) {
        super(source);
        this.dbData = dbData;
        this.decryptedValue = decryptedValue;
    }

    public String getDbData() {
        return dbData;
    }

    public String getDecryptedValue() {
        return decryptedValue;
    }
}
