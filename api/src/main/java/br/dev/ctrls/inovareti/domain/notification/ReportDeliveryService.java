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

@Service
@RequiredArgsConstructor
@Slf4j
public class ReportDeliveryService {

    private final JavaMailSender mailSender;

    @Value("${app.financeiro.test-mode:false}")
    private boolean testMode;

    @Value("${app.financeiro.dev-email:}")
    private String developerEmail;

    @Value("${app.financeiro.smtp.from-email:}")
    private String fromEmail;

    @Value("${app.financeiro.smtp.from-name:}")
    private String fromName;

    public void sendReportEmail(
            String recipientName,
            String destinationEmail,
            String subject,
            String bodyText,
            byte[] attachmentBytes,
            String attachmentFileName,
            String contentType) {

        validateConfiguration();

        String to = resolveRecipient(destinationEmail);

        MimeMessage message = mailSender.createMimeMessage();
        try {
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(formatFromAddress());
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(bodyText, false);

            if (attachmentBytes != null && attachmentBytes.length > 0) {
                helper.addAttachment(attachmentFileName, new ByteArrayResource(attachmentBytes), contentType);
            }
        } catch (MessagingException ex) {
            throw new IllegalStateException("Falha ao montar mensagem SMTP de relatório.", ex);
        }

        mailSender.send(message);

        log.info("Report dispatched to {} (original destination: {})", to, destinationEmail);
    }

    private String resolveRecipient(String destinationEmail) {
        if (!testMode) return destinationEmail;
        if (!StringUtils.hasText(developerEmail)) {
            throw new IllegalStateException("app.financeiro.test-mode=true exige app.financeiro.dev-email configurado");
        }
        return developerEmail;
    }

    private String formatFromAddress() {
        return fromName + " <" + fromEmail + ">";
    }

    private void validateConfiguration() {
        if (!StringUtils.hasText(fromEmail) || !StringUtils.hasText(fromName)) {
            throw new IllegalStateException("Configuração SMTP inválida: app.financeiro.smtp.from-email e app.financeiro.smtp.from-name são obrigatórios.");
        }
    }
}
