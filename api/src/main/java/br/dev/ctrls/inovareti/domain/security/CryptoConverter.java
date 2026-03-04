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
 * JPA AttributeConverter for encrypting and decrypting sensitive string columns using AES-128.
 * The encryption secret is loaded from application.properties.
 * 
 * Apply this converter to sensitive fields with:
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
     * Encrypts the attribute value before storing it in the database.
     * Returns null if the input is null or empty.
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
     * Decrypts the database value back to the original attribute value.
     * Returns null if the input is null or empty.
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
     * Generates a 128-bit AES key from the configured secret using SHA-256 hash.
     */
    private SecretKeySpec generateKey() {
        try {
            byte[] key = encryptionSecret.getBytes(StandardCharsets.UTF_8);
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            key = sha.digest(key);
            key = Arrays.copyOf(key, 16); // Use first 128 bits for AES-128
            return new SecretKeySpec(key, ALGORITHM);
        } catch (NoSuchAlgorithmException e) {
            log.error("Error generating encryption key", e);
            throw new RuntimeException("Failed to generate encryption key", e);
        }
    }
}
