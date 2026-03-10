package br.dev.ctrls.inovareti.domain.security;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Base64;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import lombok.extern.slf4j.Slf4j;

/**
 * JPA AttributeConverter para criptografar e descriptografar colunas de string sensíveis usando AES-128.
 * O segredo de criptografia é carregado do application.properties.
 *
 * Aplique este converter a campos sensíveis com:
 * @Convert(converter = CryptoConverter.class)
 */
@Slf4j
@Component
@Converter
public class CryptoConverter implements AttributeConverter<String, String> {

    private static final String ALGORITHM = "AES";
    private static String encryptionSecret;

    @Value("${app.security.encryption.secret}")
    public void setEncryptionSecret(String secret) {
        encryptionSecret = secret;
    }

    /**
     * Criptografa o valor do atributo antes de armazená-lo no banco de dados.
     * Retorna null se a entrada for nula ou vazia.
     */
    @Override
    public String convertToDatabaseColumn(String attribute) {
        if (attribute == null || attribute.isEmpty()) {
            return null;
        }

        try {
            SecretKeySpec key = generateKey();
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, key);
            byte[] encryptedBytes = cipher.doFinal(attribute.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(encryptedBytes);
        } catch (NoSuchAlgorithmException | NoSuchPaddingException | InvalidKeyException | 
                 IllegalBlockSizeException | BadPaddingException e) {
            log.error("Error encrypting attribute", e);
            throw new RuntimeException("Failed to encrypt attribute", e);
        }
    }

    /**
     * Descriptografa o valor do banco de dados de volta ao valor original do atributo.
     * Retorna null se a entrada for nula ou vazia.
     */
    @Override
    public String convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isEmpty()) {
            return null;
        }

        try {
            SecretKeySpec key = generateKey();
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, key);
            byte[] decodedBytes = Base64.getDecoder().decode(dbData);
            byte[] decryptedBytes = cipher.doFinal(decodedBytes);
            return new String(decryptedBytes, StandardCharsets.UTF_8);
        } catch (NoSuchAlgorithmException | NoSuchPaddingException | InvalidKeyException | 
                 IllegalBlockSizeException | BadPaddingException | IllegalArgumentException e) {
            log.error("Error decrypting attribute", e);
            throw new RuntimeException("Failed to decrypt attribute", e);
        }
    }

    /**
     * Gera uma chave AES de 128 bits a partir do segredo configurado usando hash SHA-256.
     */
    private SecretKeySpec generateKey() {
        try {
            byte[] key = encryptionSecret.getBytes(StandardCharsets.UTF_8);
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            key = sha.digest(key);
            key = Arrays.copyOf(key, 16); // Usa os primeiros 128 bits para AES-128
            return new SecretKeySpec(key, ALGORITHM);
        } catch (NoSuchAlgorithmException e) {
            log.error("Error generating encryption key", e);
            throw new RuntimeException("Failed to generate encryption key", e);
        }
    }
}
