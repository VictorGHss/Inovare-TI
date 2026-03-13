package br.dev.ctrls.inovareti.domain.notification;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;

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
        sendReceiptEmailWithPdf(medicoNome, destinationEmail, bodyText, null, null);
    }

    public void sendReceiptEmailWithPdf(
            String medicoNome,
            String destinationEmail,
            String bodyText,
            byte[] pdfBytes,
            String attachmentFileName) {
        validateConfiguration();

        EmailDispatch dispatch = resolveDispatch(medicoNome, destinationEmail);

        MimeMessage message = mailSender.createMimeMessage();
        try {
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(formatFromAddress());
            helper.setTo(dispatch.to());
            helper.setSubject(dispatch.subject());
            helper.setText(bodyText, false);

            if (pdfBytes != null && pdfBytes.length > 0) {
                String resolvedFileName = StringUtils.hasText(attachmentFileName)
                        ? attachmentFileName
                        : "recibo.pdf";

                helper.addAttachment(
                        resolvedFileName,
                        new ByteArrayResource(pdfBytes),
                        "application/pdf");
            }
        } catch (MessagingException ex) {
            throw new IllegalStateException("Falha ao montar mensagem SMTP de recibo.", ex);
        }

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
