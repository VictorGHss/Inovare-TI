package br.dev.ctrls.inovareti.modules.auth.application.service;

import io.micrometer.observation.annotation.Observed;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.dev.ctrls.inovareti.core.shared.domain.model.exception.BadRequestException;
import br.dev.ctrls.inovareti.core.shared.domain.model.exception.NotFoundException;
import br.dev.ctrls.inovareti.domain.audit.AuditAction;
import br.dev.ctrls.inovareti.domain.audit.AuditEvent;
import br.dev.ctrls.inovareti.domain.audit.AuditLogService;
import br.dev.ctrls.inovareti.modules.auth.application.dto.AuthResponseDTO;
import br.dev.ctrls.inovareti.modules.auth.domain.port.output.HashPort;
import br.dev.ctrls.inovareti.modules.auth.domain.port.output.TokenPort;
import br.dev.ctrls.inovareti.modules.notification.infrastructure.adapter.output.discord.bot.DiscordDirectMessageService;
import br.dev.ctrls.inovareti.modules.user.domain.model.User;
import br.dev.ctrls.inovareti.modules.user.domain.port.output.UserRepositoryPort;
import br.dev.ctrls.inovareti.modules.user.application.dto.UserResponseDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * ServiÃ§o responsÃ¡vel pelo fluxo completo de recuperaÃ§Ã£o do 2FA.
 * Passos:
 *  1. initiateReset: gera cÃ³digo aleatÃ³rio, salva hash no banco e envia ao Discord do usuÃ¡rio.
 *  2. confirmReset : valida cÃ³digo + senha atual e limpa o segredo TOTP.
 *  3. adminReset  : admin apaga diretamente o TOTP de outro usuÃ¡rio.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Observed
public class TwoFactorResetService {

    private static final int CODE_LENGTH = 8;
    private static final int CODE_EXPIRY_MINUTES = 15;
    private static final String CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"; // sem ambÃ­guos

    private final UserRepositoryPort userRepository;
    private final TokenPort tokenPort;
    private final HashPort hashPort;
    private final DiscordDirectMessageService discordDirectMessageService;
    private final AuditLogService auditLogService;

    /**
     * Solicita a recuperaÃ§Ã£o do 2FA: gera e envia um cÃ³digo ao Discord.
     *
     * @param userId ID do usuÃ¡rio autenticado (JWT sem 2FA verificado)
     */
    @Transactional
    public void initiateReset(UUID userId) {
        User user = findUserOrThrow(userId);

        if (!isTwoFactorEnabled(user)) {
            throw new BadRequestException("O 2FA nÃ£o estÃ¡ ativado neste usuÃ¡rio.");
        }

        if (user.getDiscordUserId() == null || user.getDiscordUserId().isBlank()) {
            throw new BadRequestException(
                    "Este usuÃ¡rio nÃ£o possui conta Discord vinculada. PeÃ§a a um administrador para resetar seu 2FA.");
        }

        log.info("Initiating 2FA reset for user {} with Discord ID {}", userId, user.getDiscordUserId());

        String code = generateSecureCode();
        user.setRecoveryCodeHash(hashPort.encode(code));
        user.setRecoveryCodeExpiresAt(LocalDateTime.now().plusMinutes(CODE_EXPIRY_MINUTES));
        userRepository.save(user);

        // Envia o cÃ³digo via Discord DM
        discordDirectMessageService.sendTwoFactorResetCode(user.getDiscordUserId(), code, user.getName());

        log.info("2FA recovery code generated for user {}", userId);
    }

    /**
     * Confirma a recuperaÃ§Ã£o do 2FA: valida o cÃ³digo e a senha, depois limpa o TOTP.
     *
     * @param userId   ID do usuÃ¡rio autenticado
     * @param code     CÃ³digo recebido via Discord
     * @param password Senha atual do usuÃ¡rio
     * @return novo JWT com 2FA em false (totp_secret = null)
     */
    @Transactional
    public AuthResponseDTO confirmReset(UUID userId, String code, String password, String ipAddress) {
        User user = findUserOrThrow(userId);

        if (!isTwoFactorEnabled(user)) {
            throw new BadRequestException("O 2FA jÃ¡ estÃ¡ desativado para este usuÃ¡rio.");
        }

        // Valida senha atual
        if (!hashPort.matches(password, user.getPasswordHash())) {
            throw new BadRequestException("Senha incorreta.");
        }

        // Valida cÃ³digo de recuperaÃ§Ã£o
        if (user.getRecoveryCodeHash() == null) {
            throw new BadRequestException(
                    "Nenhuma solicitaÃ§Ã£o de recuperaÃ§Ã£o encontrada. Solicite um novo cÃ³digo.");
        }

        if (user.getRecoveryCodeExpiresAt() == null
                 || user.getRecoveryCodeExpiresAt().isBefore(LocalDateTime.now())) {
            throw new BadRequestException("O cÃ³digo de recuperaÃ§Ã£o expirou. Solicite um novo cÃ³digo.");
        }

        if (!hashPort.matches(code.trim().toUpperCase(), user.getRecoveryCodeHash())) {
            throw new BadRequestException("CÃ³digo de recuperaÃ§Ã£o invÃ¡lido.");
        }

        // Limpa o 2FA e o cÃ³digo de recuperaÃ§Ã£o
        user.setTotpSecret(null);
        user.setRecoveryCodeHash(null);
        user.setRecoveryCodeExpiresAt(null);
        userRepository.save(user);

        auditLogService.publish(AuditEvent.of(AuditAction.TWO_FACTOR_RESET)
                .userId(userId)
                .ipAddress(ipAddress)
                .build());

        log.info("2FA successfully reset for user {} via recovery flow", userId);

        // Emite novo JWT sem flag de 2FA
        String token = tokenPort.generateToken(user, false);
        return AuthResponseDTO.authenticated(token, UserResponseDTO.from(user));
    }

    /**
     * Reset administrativo do 2FA: qualquer ADMIN pode limpar o TOTP de outro usuÃ¡rio.
     *
     * @param targetUserId ID do usuÃ¡rio cujo 2FA serÃ¡ resetado
     */
    @Transactional
    public void adminReset(UUID targetUserId, UUID adminUserId, String ipAddress) {
        User targetUser = findUserOrThrow(targetUserId);
        User adminUser = findUserOrThrow(adminUserId);

        if (!isTwoFactorEnabled(targetUser)) {
            throw new BadRequestException("O 2FA jÃ¡ estÃ¡ desativado para este usuÃ¡rio.");
        }

        targetUser.setTotpSecret(null);
        targetUser.setRecoveryCodeHash(null);
        targetUser.setRecoveryCodeExpiresAt(null);
        userRepository.save(targetUser);

        if (targetUser.getDiscordUserId() != null && !targetUser.getDiscordUserId().isBlank()) {
            discordDirectMessageService.sendTwoFactorResetByAdminNotification(
                    targetUser.getDiscordUserId(),
                    targetUser.getName(),
                    adminUser.getName());
        }

        auditLogService.publish(AuditEvent.of(AuditAction.USER_2FA_ADMIN_RESET)
                .userId(targetUserId)
                .details("{\"adminUserId\": \"" + adminUserId + "\"}")
                .ipAddress(ipAddress)
                .build());

        log.info("2FA administratively reset for user {} by admin {}", targetUserId, adminUserId);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private User findUserOrThrow(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("UsuÃ¡rio nÃ£o encontrado."));
    }

    private boolean isTwoFactorEnabled(User user) {
        return user.getTotpSecret() != null && !user.getTotpSecret().isBlank();
    }

    private String generateSecureCode() {
        SecureRandom random = new SecureRandom();
        StringBuilder sb = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) {
            sb.append(CODE_CHARS.charAt(random.nextInt(CODE_CHARS.length())));
        }
        return sb.toString();
    }
}


