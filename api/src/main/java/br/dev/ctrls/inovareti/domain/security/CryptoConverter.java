package br.dev.ctrls.inovareti.domain.security;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import lombok.extern.slf4j.Slf4j;

/**
 * JPA AttributeConverter para criptografar e descriptografar colunas de strings sensíveis
 * (como o segredo TOTP de autenticação de dois fatores dos usuários) de forma altamente segura.
 * 
 * Implementa o modo seguro AES-GCM (criptografia autenticada) com fallback resiliente para o
 * modo antigo AES-ECB (legado), permitindo ler dados legados de produção sem quebrar a aplicação,
 * e migrando-os automaticamente para o padrão seguro GCM na próxima persistência do registro.
 *
 * Aplique este conversor a campos sensíveis com a anotação:
 * @Convert(converter = CryptoConverter.class)
 */
@Slf4j
@Component
@Converter
public class CryptoConverter implements AttributeConverter<String, String> {

    private static final String ALGORITHM = "AES";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final String LEGACY_TRANSFORMATION = "AES/ECB/PKCS5Padding";
    
    private static final int GCM_TAG_LENGTH_BITS = 128;
    private static final int IV_LENGTH_BYTES = 12;

    private static String encryptionSecret;
    private final SecureRandom secureRandom = new SecureRandom();

    @org.springframework.beans.factory.annotation.Autowired
    private org.springframework.context.ApplicationEventPublisher eventPublisher;

    // Cache de dedup em memória de tamanho limitado para evitar tarefas de migração redundantes
    private final java.util.Set<String> migrationDeduplicationCache = java.util.concurrent.ConcurrentHashMap.newKeySet();

    @Value("${app.security.encryption.secret}")
    public void setEncryptionSecret(String secret) {
        encryptionSecret = secret;
    }

    /**
     * Criptografa o valor do atributo em modo seguro AES-GCM antes de armazená-lo no banco de dados.
     * Gera um vetor de inicialização (IV) randômico a cada nova criptografia.
     * Prepend do prefixo 'v1:' para habilitar futura rotação de chaves sem perdas.
     * Retorna null se a entrada for nula ou vazia.
     */
    @Override
    public String convertToDatabaseColumn(String attribute) {
        if (attribute == null || attribute.isEmpty()) {
            return null;
        }

        try {
            SecretKeySpec key = generateKey();
            byte[] iv = new byte[IV_LENGTH_BYTES];
            secureRandom.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));
            byte[] encryptedBytes = cipher.doFinal(attribute.getBytes(StandardCharsets.UTF_8));

            // Concatena o IV (12 bytes) e a carga encriptada para armazenar de forma compacta
            ByteBuffer buffer = ByteBuffer.allocate(iv.length + encryptedBytes.length);
            buffer.put(iv);
            buffer.put(encryptedBytes);

            String base64Encrypted = Base64.getEncoder().encodeToString(buffer.array());
            return "v1:" + base64Encrypted;
        } catch (Exception e) {
            log.error("Erro ao criptografar atributo com AES-GCM", e);
            throw new RuntimeException("Falha ao criptografar atributo", e);
        }
    }

    /**
     * Descriptografa o valor vindo do banco de dados.
     * Suporta a remoção do prefixo de chave 'v1:' e tenta decodificar em modo AES-GCM.
     * Caso falhe ou caso o dado seja legado (menor ou igual a 12 bytes ou sem prefixo),
     * realiza fallback automático para descriptografia em modo legado AES-ECB.
     * Retorna null se a entrada for nula ou vazia.
     */
    @Override
    public String convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isEmpty()) {
            return null;
        }

        try {
            boolean isV1 = dbData.startsWith("v1:");
            if (!isV1) {
                // Se não tem o prefixo v1:, tenta descriptografar como legado (ECB)
                // Se falhar (por ser texto puro), retorna o próprio dado original de forma resiliente.
                try {
                    String decrypted = decryptLegacy(dbData);
                    publishMigrationEvent(dbData, decrypted);
                    return decrypted;
                } catch (Exception ex) {
                    log.debug("Falha ao descriptografar dado sem prefixo v1. Retornando dado original em texto puro: {}", ex.getMessage());
                    publishMigrationEvent(dbData, dbData);
                    return dbData;
                }
            }

            String workingData = dbData.substring(3);
            byte[] decoded;
            try {
                decoded = Base64.getDecoder().decode(workingData);
            } catch (IllegalArgumentException ex) {
                log.warn("Falha ao decodificar Base64 para dados com prefixo v1. Retornando dado original: {}", ex.getMessage());
                return dbData;
            }
            
            if (decoded.length <= IV_LENGTH_BYTES) {
                log.warn("Dados GCM muito curtos para conter IV. Retornando dado original.");
                return dbData;
            }

            try {
                SecretKeySpec key = generateKey();
                ByteBuffer buffer = ByteBuffer.wrap(decoded);
                byte[] iv = new byte[IV_LENGTH_BYTES];
                buffer.get(iv);
                byte[] encryptedPayload = new byte[buffer.remaining()];
                buffer.get(encryptedPayload);

                Cipher cipher = Cipher.getInstance(TRANSFORMATION);
                cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));
                byte[] decryptedBytes = cipher.doFinal(encryptedPayload);
                return new String(decryptedBytes, StandardCharsets.UTF_8);
            } catch (Exception ex) {
                log.warn("Falha ao descriptografar em modo AES-GCM para dados v1. Retornando dado original: {}", ex.getMessage());
                return dbData;
            }
        } catch (Exception e) {
            log.warn("Falha definitiva ao descriptografar atributo sensível do banco de dados. Retornando dado original.", e);
            return dbData;
        }
    }

    /**
     * Método auxiliar de fallback para decodificar dados legados persistidos em AES-ECB clássico.
     */
    private String decryptLegacy(String dbData) {
        try {
            SecretKeySpec key = generateKey();
            Cipher cipher = Cipher.getInstance(LEGACY_TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key);
            byte[] decodedBytes = Base64.getDecoder().decode(dbData);
            byte[] decryptedBytes = cipher.doFinal(decodedBytes);
            return new String(decryptedBytes, StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.debug("Falha ao descriptografar dado utilizando fallback do modo legado AES-ECB: {}", e.getMessage());
            throw new RuntimeException("Falha ao descriptografar no modo legado", e);
        }
    }

    /**
     * Gera uma chave AES simétrica de 128 bits a partir do segredo configurado usando hash SHA-256.
     */
    private SecretKeySpec generateKey() {
        if (encryptionSecret == null || encryptionSecret.isBlank()) {
            throw new IllegalStateException("A chave de criptografia (encryptionSecret) não foi inicializada! Certifique-se de que a propriedade 'app.security.encryption.secret' esteja configurada e carregada pelo Spring.");
        }
        try {
            byte[] key = encryptionSecret.getBytes(StandardCharsets.UTF_8);
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            key = sha.digest(key);
            key = Arrays.copyOf(key, 16); // Utiliza os primeiros 128 bits (16 bytes) para AES-128
            return new SecretKeySpec(key, ALGORITHM);
        } catch (NoSuchAlgorithmException e) {
            log.error("Erro fatal ao gerar chave de criptografia de banco de dados", e);
            throw new RuntimeException("Falha ao gerar chave de criptografia", e);
        }
    }

    /**
     * Publica o evento de migração de criptografia legada de forma dedupicada.
     */
    private void publishMigrationEvent(String dbData, String decryptedValue) {
        if (migrationDeduplicationCache.size() > 5000) {
            migrationDeduplicationCache.clear();
        }
        if (eventPublisher != null && migrationDeduplicationCache.add(dbData)) {
            try {
                eventPublisher.publishEvent(new LegacyCryptoDetectedEvent(this, dbData, decryptedValue));
            } catch (Exception e) {
                log.warn("Falha ao publicar evento de migração de criptografia legada", e);
            }
        }
    }
}
