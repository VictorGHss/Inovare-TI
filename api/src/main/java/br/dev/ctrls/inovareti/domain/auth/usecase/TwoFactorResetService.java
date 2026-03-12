package br.dev.ctrls.inovareti.domain.auth.usecase;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.UUID;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.dev.ctrls.inovareti.config.TokenService;
import br.dev.ctrls.inovareti.core.exception.BadRequestException;
import br.dev.ctrls.inovareti.core.exception.NotFoundException;
import br.dev.ctrls.inovareti.domain.audit.AuditAction;
import br.dev.ctrls.inovareti.domain.audit.AuditEvent;
import br.dev.ctrls.inovareti.domain.audit.AuditLogService;
import br.dev.ctrls.inovareti.domain.auth.dto.AuthResponseDTO;
import br.dev.ctrls.inovareti.domain.notification.discord.bot.DiscordDirectMessageService;
import br.dev.ctrls.inovareti.domain.user.User;
import br.dev.ctrls.inovareti.domain.user.UserRepository;
import br.dev.ctrls.inovareti.domain.user.dto.UserResponseDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Serviço responsável pelo fluxo completo de recuperação do 2FA.
 * Passos:
 *  1. initiateReset: gera código aleatório, salva hash no banco e envia ao Discord do usuário.
 *  2. confirmReset : valida código + senha atual e limpa o segredo TOTP.
 *  3. adminReset  : admin apaga diretamente o TOTP de outro usuário.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TwoFactorResetService {

    private static final int CODE_LENGTH = 8;
    private static final int CODE_EXPIRY_MINUTES = 15;
    private static final String CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"; // sem ambíguos

    private final UserRepository userRepository;
    private final TokenService tokenService;
    private final PasswordEncoder passwordEncoder;
    private final DiscordDirectMessageService discordDirectMessageService;
    private final AuditLogService auditLogService;

    /**
     * Solicita a recuperação do 2FA: gera e envia um código ao Discord.
     *
     * @param userId ID do usuário autenticado (JWT sem 2FA verificado)
     */
    @Transactional
    public void initiateReset(UUID userId) {
        User user = findUserOrThrow(userId);

        if (!isTwoFactorEnabled(user)) {
            throw new BadRequestException("O 2FA não está ativado neste usuário.");
        }

        if (user.getDiscordUserId() == null || user.getDiscordUserId().isBlank()) {
            throw new BadRequestException(
                    "Este usuário não possui conta Discord vinculada. Peça a um administrador para resetar seu 2FA.");
        }

        log.info("Initiating 2FA reset for user {} with Discord ID {}", userId, user.getDiscordUserId());

        String code = generateSecureCode();
        user.setRecoveryCodeHash(passwordEncoder.encode(code));
        user.setRecoveryCodeExpiresAt(LocalDateTime.now().plusMinutes(CODE_EXPIRY_MINUTES));
        userRepository.save(user);

        // Envia o código via Discord DM
        discordDirectMessageService.sendTwoFactorResetCode(user.getDiscordUserId(), code, user.getName());

        log.info("2FA recovery code generated for user {}", userId);
    }

    /**
     * Confirma a recuperação do 2FA: valida o código e a senha, depois limpa o TOTP.
     *
     * @param userId   ID do usuário autenticado
     * @param code     Código recebido via Discord
     * @param password Senha atual do usuário
     * @return novo JWT com 2FA em false (totp_secret = null)
     */
    @Transactional
    public AuthResponseDTO confirmReset(UUID userId, String code, String password, String ipAddress) {
        User user = findUserOrThrow(userId);

        if (!isTwoFactorEnabled(user)) {
            throw new BadRequestException("O 2FA já está desativado para este usuário.");
        }

        // Valida senha atual
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new BadRequestException("Senha incorreta.");
        }

        // Valida código de recuperação
        if (user.getRecoveryCodeHash() == null) {
            throw new BadRequestException(
                    "Nenhuma solicitação de recuperação encontrada. Solicite um novo código.");
        }

        if (user.getRecoveryCodeExpiresAt() == null
                || user.getRecoveryCodeExpiresAt().isBefore(LocalDateTime.now())) {
            throw new BadRequestException("O código de recuperação expirou. Solicite um novo código.");
        }

        if (!passwordEncoder.matches(code.trim().toUpperCase(), user.getRecoveryCodeHash())) {
            throw new BadRequestException("Código de recuperação inválido.");
        }

        // Limpa o 2FA e o código de recuperação
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
        String token = tokenService.generateToken(user, false);
        return AuthResponseDTO.authenticated(token, UserResponseDTO.from(user));
    }

    /**
     * Reset administrativo do 2FA: qualquer ADMIN pode limpar o TOTP de outro usuário.
     *
     * @param targetUserId ID do usuário cujo 2FA será resetado
     */
    @Transactional
    public void adminReset(UUID targetUserId, UUID adminUserId, String ipAddress) {
        User targetUser = findUserOrThrow(targetUserId);
        User adminUser = findUserOrThrow(adminUserId);

        if (!isTwoFactorEnabled(targetUser)) {
            throw new BadRequestException("O 2FA já está desativado para este usuário.");
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

        auditLogService.publish(AuditEvent.of(AuditAction.TWO_FACTOR_ADMIN_RESET)
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
                .orElseThrow(() -> new NotFoundException("Usuário não encontrado."));
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
