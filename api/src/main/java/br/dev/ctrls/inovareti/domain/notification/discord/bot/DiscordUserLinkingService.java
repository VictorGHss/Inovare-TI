package br.dev.ctrls.inovareti.domain.notification.discord.bot;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.dev.ctrls.inovareti.domain.user.User;
import br.dev.ctrls.inovareti.domain.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Serviço responsável por vincular contas do Discord a usuários da clínica.
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
     * Vincula uma conta do Discord a um usuário da clínica e retorna a mensagem final de resposta.
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

        // Monta a mensagem final dentro da transação para acessar relações lazy com segurança.
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
