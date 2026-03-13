package br.dev.ctrls.inovareti.domain.auth.usecase;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;

import javax.imageio.ImageIO;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.warrenstrange.googleauth.GoogleAuthenticator;

import br.dev.ctrls.inovareti.config.TokenService;
import br.dev.ctrls.inovareti.core.exception.BadRequestException;
import br.dev.ctrls.inovareti.core.exception.NotFoundException;
import br.dev.ctrls.inovareti.domain.audit.AuditAction;
import br.dev.ctrls.inovareti.domain.audit.AuditEvent;
import br.dev.ctrls.inovareti.domain.audit.AuditLogService;
import br.dev.ctrls.inovareti.domain.auth.dto.AuthResponseDTO;
import br.dev.ctrls.inovareti.domain.auth.dto.TwoFactorGenerateResponseDTO;
import br.dev.ctrls.inovareti.domain.user.User;
import br.dev.ctrls.inovareti.domain.user.UserRepository;
import br.dev.ctrls.inovareti.domain.user.dto.UserResponseDTO;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class TwoFactorAuthService {

    private final UserRepository userRepository;
    private final TokenService tokenService;
    private final AuditLogService auditLogService;
    private final GoogleAuthenticator googleAuthenticator = new GoogleAuthenticator();

    @Value("${spring.application.name:inovare-ti}")
    private String issuer;

    public TwoFactorGenerateResponseDTO generateForUser(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("Usuário não encontrado para geração de 2FA."));

        String secret = googleAuthenticator.createCredentials().getKey();
        user.setTotpSecret(secret);
        userRepository.save(user);

        String otpauthUrl = buildOtpAuthUrl(user.getEmail(), secret);
        String qrCodeBase64 = generateQrCodeBase64(otpauthUrl);
        return new TwoFactorGenerateResponseDTO(qrCodeBase64, otpauthUrl);
    }

    public AuthResponseDTO verifyCode(UUID userId, String code, String ipAddress) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("Usuário não encontrado para validação de 2FA."));

        if (user.getTotpSecret() == null || user.getTotpSecret().isBlank()) {
            throw new BadRequestException("O 2FA ainda não foi configurado para este usuário.");
        }

        int codeNumber;
        try {
            codeNumber = Integer.parseInt(code);
        } catch (NumberFormatException ex) {
            throw new BadRequestException("O código 2FA informado é inválido.");
        }

        boolean isValid = googleAuthenticator.authorize(user.getTotpSecret(), codeNumber);
        if (!isValid) {
            auditLogService.publish(AuditEvent.of(AuditAction.VAULT_AUTH_FAIL)
                    .userId(userId)
                    .resourceType("Vault")
                    .details("{\"reason\": \"INVALID_2FA_CODE\"}")
                    .ipAddress(ipAddress)
                    .build());
            throw new BadRequestException("Código 2FA inválido.");
        }

        auditLogService.publish(AuditEvent.of(AuditAction.VAULT_AUTH_SUCCESS)
                .userId(userId)
                .resourceType("Vault")
                .details("{\"result\": \"2FA_VERIFIED\"}")
                .ipAddress(ipAddress)
                .build());

        String token = tokenService.generateToken(user, true);
        return AuthResponseDTO.authenticated(token, UserResponseDTO.from(user));
    }

    private String buildOtpAuthUrl(String email, String secret) {
        String encodedIssuer = URLEncoder.encode(issuer, StandardCharsets.UTF_8);
        String encodedAccount = URLEncoder.encode(email, StandardCharsets.UTF_8);
        return "otpauth://totp/" + encodedIssuer + ":" + encodedAccount
                + "?secret=" + secret + "&issuer=" + encodedIssuer;
    }

    private String generateQrCodeBase64(String content) {
        try {
            var hints = java.util.Map.of(EncodeHintType.MARGIN, 1);
            BitMatrix bitMatrix = new MultiFormatWriter().encode(content, BarcodeFormat.QR_CODE, 280, 280, hints);
            BufferedImage image = MatrixToImageWriter.toBufferedImage(bitMatrix);
            try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
                ImageIO.write(image, "PNG", outputStream);
                return Base64.getEncoder().encodeToString(outputStream.toByteArray());
            }
        } catch (WriterException | IOException ex) {
            throw new IllegalStateException("Falha ao gerar o QR Code do 2FA.", ex);
        }
    }
}