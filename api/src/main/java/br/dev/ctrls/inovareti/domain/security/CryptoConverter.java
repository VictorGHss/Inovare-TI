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

    @Value("${app.security.encryption.secret}")
    public void setEncryptionSecret(String secret) {
        encryptionSecret = secret;
    }

    /**
     * Criptografa o valor do atributo em modo seguro AES-GCM antes de armazená-lo no banco de dados.
     * Gera um vetor de inicialização (IV) randômico a cada nova criptografia.
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

            return Base64.getEncoder().encodeToString(buffer.array());
        } catch (Exception e) {
            log.error("Erro ao criptografar atributo com AES-GCM", e);
            throw new RuntimeException("Falha ao criptografar atributo", e);
        }
    }

    /**
     * Descriptografa o valor vindo do banco de dados.
     * Tenta primeiro decodificar usando o modo seguro AES-GCM.
     * Caso falhe ou caso o dado seja legado (menor ou igual a 12 bytes),
     * realiza fallback automático para descriptografia em modo AES-ECB.
     * Retorna null se a entrada for nula ou vazia.
     */
    @Override
    public String convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isEmpty()) {
            return null;
        }

        try {
            byte[] decoded = Base64.getDecoder().decode(dbData);
            
            // Dados menores ou iguais a 12 bytes não podem conter o IV e a tag de autenticação GCM.
            // Executa diretamente o fallback para o modo legado ECB.
            if (decoded.length <= IV_LENGTH_BYTES) {
                return decryptLegacy(dbData);
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
                log.warn("Falha ao descriptografar em modo AES-GCM. Ativando fallback resiliente para decodificação em modo legado AES-ECB: {}", ex.getMessage());
                return decryptLegacy(dbData);
            }
        } catch (Exception e) {
            log.error("Falha definitiva ao descriptografar atributo sensível do banco de dados", e);
            throw new RuntimeException("Falha definitiva ao descriptografar atributo", e);
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
            log.error("Falha ao descriptografar dado utilizando fallback do modo legado AES-ECB", e);
            throw new RuntimeException("Falha ao descriptografar no modo legado", e);
        }
    }

    /**
     * Gera uma chave AES simétrica de 128 bits a partir do segredo configurado usando hash SHA-256.
     */
    private SecretKeySpec generateKey() {
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
}
