package br.dev.ctrls.inovareti.domain.notification.discord.bot;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.dev.ctrls.inovareti.domain.user.User;
import br.dev.ctrls.inovareti.domain.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Service responsible for linking Discord users to clinic accounts.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DiscordUserLinkingService {

    private final UserRepository userRepository;

    private static final String USER_NOT_FOUND_MESSAGE = "❌ Usuário não encontrado para o e-mail informado.";

    public boolean isDiscordUserLinked(String discordUserId) {
        return userRepository.findByDiscordUserId(discordUserId).isPresent();
    }

    /**
     * Links a Discord account to a clinic user and returns a final response message.
     */
    @Transactional
    public String linkDiscordToUserAndBuildMessage(String email, String discordUserId) {
        log.info("Attempting to link Discord user ID {} to email {}", discordUserId, email);

        User user = userRepository.findByEmail(email).orElse(null);
        if (user == null) {
            log.warn("⚠️ User with email {} not found. Discord linking failed.", email);
            return USER_NOT_FOUND_MESSAGE;
        }

        user.setDiscordUserId(discordUserId);
        userRepository.save(user);

        // Build the final message inside transaction to safely access lazy relations.
        String sectorName = user.getSector().getName();
        String successMessage = "✅ Conta vinculada com sucesso ao e-mail "
                + user.getEmail()
                + " (Setor: "
                + sectorName
                + ").";

        log.info("✅ Discord user {} successfully linked to user {}", discordUserId, user.getId());
        return successMessage;
    }
}
