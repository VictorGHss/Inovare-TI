package br.dev.ctrls.inovareti.config.security;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;
import lombok.extern.slf4j.Slf4j;

/**
 * Validador de assinatura criptográfica para webhooks utilizando HMAC-SHA256.
 * Previne ataques de injeção de payloads e timing attacks (ataques de temporização).
 */
@Component
@Slf4j
public class WebhookSignatureValidator {

    private static final String HMAC_SHA256 = "HmacSHA256";

    /**
     * Valida se a assinatura enviada corresponde ao hash HMAC-SHA256 do payload bruto.
     *
     * @param payload O corpo bruto da requisição HTTP (String)
     * @param signature A assinatura recebida no cabeçalho HTTP (Hexadecimal)
     * @param secret A chave secreta compartilhada para computar o hash
     * @return true se a assinatura for válida, false caso contrário
     */
    public boolean isValid(String payload, String signature, String secret) {
        if (payload == null || signature == null || secret == null) {
            log.warn("Falha na validação de assinatura: parâmetros nulos.");
            return false;
        }

        try {
            // Inicializa a chave de segurança utilizando os bytes do secret no padrão UTF-8
            SecretKeySpec secretKeySpec = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_SHA256);
            
            // Instancia o algoritmo HMAC-SHA256 nativo do Java Cryptography Architecture
            Mac mac = Mac.getInstance(HMAC_SHA256);
            mac.init(secretKeySpec);

            // Gera o hash HMAC do payload bruto enviado
            byte[] rawHmac = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            
            // Converte os bytes do hash binário para representação Hexadecimal textual minúscula
            StringBuilder hexString = new StringBuilder();
            for (byte b : rawHmac) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            String generatedSignature = hexString.toString();

            // OBRIGATÓRIO: Utiliza MessageDigest.isEqual para comparação resistente a Timing Attacks (O(1))
            byte[] generatedBytes = generatedSignature.getBytes(StandardCharsets.UTF_8);
            byte[] signatureBytes = signature.trim().toLowerCase().getBytes(StandardCharsets.UTF_8);

            boolean result = MessageDigest.isEqual(generatedBytes, signatureBytes);
            if (!result) {
                log.warn("Assinatura de webhook inválida. Assinatura gerada não condiz com a recebida.");
            }
            return result;
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            log.error("Erro interno ao processar a validação de assinatura HMAC-SHA256 para o webhook", e);
            return false;
        }
    }
}
