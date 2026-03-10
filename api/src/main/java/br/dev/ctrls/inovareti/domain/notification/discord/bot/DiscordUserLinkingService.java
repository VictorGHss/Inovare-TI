package br.dev.ctrls.inovareti.domain.notification.discord.bot;

import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.dev.ctrls.inovareti.domain.user.User;
import br.dev.ctrls.inovareti.domain.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Serviço responsável por gerenciar a vinculação de usuários Discord com contas no sistema.
 * Permite que usuários vinculem seus IDs do Discord aos seus emails no sistema.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DiscordUserLinkingService {

    private final UserRepository userRepository;

    /**
     * Vincula um ID do Discord a um usuário com o email fornecido.
     * Se o usuário for encontrado, atualiza o campo discord_user_id e salva no banco.
     *
     * @param email o email do usuário no sistema
     * @param discordUserId o ID do usuário no Discord
     * @return Optional contendo o usuário se encontrado e vinculado com sucesso
     */
    @Transactional
    public Optional<User> linkDiscordToUser(String email, String discordUserId) {
        log.info("Attempting to link Discord user ID {} to email {}", discordUserId, email);

        // Busca o usuário pelo email
        Optional<User> userOpt = userRepository.findByEmail(email);

        if (userOpt.isPresent()) {
            User user = userOpt.get();

            // Atualiza o discord_user_id
            user.setDiscordUserId(discordUserId);

            // Salva o usuário com o novo discord_user_id
            userRepository.save(user);

            log.info("✅ Discord user {} successfully linked to user {}", 
                    discordUserId, user.getId());

            return Optional.of(user);
        } else {
            log.warn("⚠️ User with email {} not found. Discord linking failed.", email);
            return Optional.empty();
        }
    }

    /**
     * Busca um usuário pelo seu ID do Discord.
     *
     * @param discordUserId o ID do usuário no Discord
     * @return Optional contendo o usuário se encontrado
     */
    public Optional<User> findUserByDiscordId(String discordUserId) {
        log.debug("Searching for user with Discord ID: {}", discordUserId);
        return userRepository.findByDiscordUserId(discordUserId);
    }

    /**
     * Verifica se um Discord ID está vinculado a algum usuário.
     *
     * @param discordUserId o ID do usuário no Discord
     * @return true se o Discord ID está vinculado, false caso contrário
     */
    public boolean isDiscordIdLinked(String discordUserId) {
        return userRepository.findByDiscordUserId(discordUserId).isPresent();
    }
}
