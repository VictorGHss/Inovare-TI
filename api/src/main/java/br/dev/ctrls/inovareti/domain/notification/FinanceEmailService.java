package br.dev.ctrls.inovareti.domain.notification;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class FinanceEmailService {

    private final JavaMailSender mailSender;

    @Value("${app.financeiro.test-mode}")
    private boolean financeiroTestMode;

    @Value("${app.financeiro.dev-email}")
    private String financeiroDeveloperEmail;

    @Value("${app.financeiro.smtp.from-email}")
    private String financeiroFromEmail;

    @Value("${app.financeiro.smtp.from-name}")
    private String financeiroFromName;

    public void sendReceiptEmail(String medicoNome, String destinationEmail, String bodyText) {
        validateConfiguration();

        EmailDispatch dispatch = resolveDispatch(medicoNome, destinationEmail);

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(formatFromAddress());
        message.setTo(dispatch.to());
        message.setSubject(dispatch.subject());
        message.setText(bodyText);

        mailSender.send(message);

        log.info("Finance receipt dispatched to {} (doctor: {}, original destination: {})",
                dispatch.to(),
                medicoNome,
                destinationEmail);
    }

    private EmailDispatch resolveDispatch(String medicoNome, String destinationEmail) {
        if (!financeiroTestMode) {
            return new EmailDispatch(destinationEmail, "Recibo Financeiro");
        }

        String subject = "[TESTE FINANCEIRO] Recibo para: " + medicoNome;
        return new EmailDispatch(financeiroDeveloperEmail, subject);
    }

    private String formatFromAddress() {
        return financeiroFromName + " <" + financeiroFromEmail + ">";
    }

    private void validateConfiguration() {
        if (!StringUtils.hasText(financeiroFromEmail) || !StringUtils.hasText(financeiroFromName)) {
            throw new IllegalStateException("Configuração SMTP financeira inválida: app.financeiro.smtp.from-email e app.financeiro.smtp.from-name são obrigatórios.");
        }

        if (financeiroTestMode && !StringUtils.hasText(financeiroDeveloperEmail)) {
            throw new IllegalStateException("app.financeiro.test-mode=true exige app.financeiro.dev-email com o e-mail do desenvolvedor Victor.");
        }
    }

    private record EmailDispatch(String to, String subject) {
    }
}
