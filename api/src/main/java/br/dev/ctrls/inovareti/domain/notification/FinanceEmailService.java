package br.dev.ctrls.inovareti.domain.notification;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import br.dev.ctrls.inovareti.modules.communication.infrastructure.config.FinanceMailProperties;
import br.dev.ctrls.inovareti.modules.communication.infrastructure.config.SpringMailProperties;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.util.HtmlUtils;

import br.dev.ctrls.inovareti.modules.finance.domain.model.ContaAzulPaymentParcel;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class FinanceEmailService {

    private static final Pattern SALE_NUMBER_PATTERN = Pattern.compile("(?i)(?:numero_venda|numero|venda)\\s*[:#-]?\\s*(\\d{3,})");

    private final JavaMailSender mailSender;
    private final FinanceMailProperties properties;
    private final SpringMailProperties springMailProperties;

    public void sendReceiptEmail(String medicoNome, String destinationEmail, String bodyText) {
        sendReceiptEmailWithPdf(medicoNome, destinationEmail, bodyText, null, null, null);
    }

    public void sendReceiptEmail(String medicoNome, String destinationEmail, String bodyText, String saleNumber) {
        sendReceiptEmailWithPdf(medicoNome, destinationEmail, bodyText, null, null, saleNumber);
    }

    public void sendReceiptEmail(ContaAzulPaymentParcel parcela) {
        String saleNumber = resolveSaleNumber(parcela.saleNumber(), null, null);
        String bodyText = "Olá " + parcela.medicoNome()
                + ", este é um disparo de teste de recibo financeiro. Referente à Venda " + saleNumber + ".";
        sendReceiptEmail(parcela.medicoNome(), parcela.recipientEmail(), bodyText, saleNumber);
    }

    public void sendReceiptEmailWithPdf(
            String medicoNome,
            String destinationEmail,
            String bodyText,
            byte[] pdfBytes,
            String attachmentFileName) {
        sendReceiptEmailWithPdf(medicoNome, destinationEmail, bodyText, pdfBytes, attachmentFileName, null);
    }

    public void sendReceiptEmailWithPdf(
            String medicoNome,
            String destinationEmail,
            String bodyText,
            byte[] pdfBytes,
            String attachmentFileName,
            String saleNumberHint) {
        validateConfiguration();
        // Prioriza número explícito da venda para impedir uso de UUID no assunto/corpo.
        String saleNumber = resolveSaleNumber(saleNumberHint, bodyText, attachmentFileName);

        EmailDispatch dispatch = resolveDispatch(medicoNome, destinationEmail, saleNumber);

        MimeMessage message = mailSender.createMimeMessage();
        try {
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            // O Skymail rejeita remetente divergente do usuário autenticado; força From estrito.
            helper.setFrom(resolveStrictFromEmail());
            helper.setTo(dispatch.to());
            helper.setSubject(dispatch.subject());
            helper.setText(buildEmailBodyHtml(medicoNome, saleNumber), true);

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

    private EmailDispatch resolveDispatch(String medicoNome, String destinationEmail, String saleNumber) {
        String resolvedName = StringUtils.hasText(medicoNome) ? medicoNome.trim() : "Cliente";
        String subject = "Recibo de Quitação - Inovare TI - " + resolvedName + " - Venda " + saleNumber;
        boolean testMode = isTestModeEnabled();
        String originalEmail = StringUtils.hasText(destinationEmail) ? destinationEmail.trim() : "";
        String devEmail = StringUtils.hasText(properties.getDevEmail()) ? properties.getDevEmail().trim() : "";
        String destinoFinal = testMode ? devEmail : originalEmail;

        if (testMode) {
            log.warn("MODO DE TESTE ATIVO: Redirecionando e-mail de {} para {}", originalEmail, devEmail);
        }

        if (!StringUtils.hasText(destinoFinal)) {
            throw new IllegalStateException("Destinatário final de e-mail está vazio após resolução do modo de teste.");
        }

        return new EmailDispatch(destinoFinal, subject);
    }

    private String resolveStrictFromEmail() {
        if (StringUtils.hasText(springMailProperties.getUsername())) {
            String normalizedSmtpUser = springMailProperties.getUsername().trim();
            if (!normalizedSmtpUser.equalsIgnoreCase(properties.getSmtp().getFromEmail().trim())) {
                log.warn(
                        "app.financeiro.smtp.from-email difere de spring.mail.username. Usando usuário SMTP como remetente para evitar rejeição do provedor.");
            }
            return normalizedSmtpUser;
        }

        return properties.getSmtp().getFromEmail().trim();
    }

    private String buildEmailBodyHtml(String medicoNome, String saleNumber) {
        String resolvedName = StringUtils.hasText(medicoNome) ? medicoNome.trim() : "Cliente";

        String safeName = HtmlUtils.htmlEscape(resolvedName);
        String safeSaleNumber = HtmlUtils.htmlEscape(saleNumber);

        return "<html><body>"
                + "<p>Prezado(a) " + safeName + ",</p>"
                + "<p>Confirmamos o recebimento do valor referente à Venda " + safeSaleNumber
                + ". O seu recibo de quitação já está disponível e segue em anexo a este e-mail.</p>"
            + "<p>Atenciosamente,<br/>Administrativo Inovare</p>"
                + "</body></html>";
    }

    private String resolveSaleNumber(String saleNumberHint, String bodyText, String attachmentFileName) {
        if (StringUtils.hasText(saleNumberHint) && saleNumberHint.trim().matches("\\d{3,}")) {
            return saleNumberHint.trim();
        }

        String fromBody = extractSaleNumber(bodyText);
        if (StringUtils.hasText(fromBody)) {
            return fromBody;
        }

        String fromFileName = extractSaleNumber(attachmentFileName);
        if (StringUtils.hasText(fromFileName)) {
            return fromFileName;
        }

        return "N/D";
    }

    private String extractSaleNumber(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }

        String normalized = value.trim();
        if (normalized.matches("\\d{3,}")) {
            return normalized;
        }

        Matcher matcher = SALE_NUMBER_PATTERN.matcher(normalized);
        return matcher.find() ? matcher.group(1) : null;
    }

    private void validateConfiguration() {
        if (!StringUtils.hasText(properties.getSmtp().getFromEmail()) || !StringUtils.hasText(properties.getSmtp().getFromName())) {
            throw new IllegalStateException("Configuração SMTP financeira inválida: app.financeiro.smtp.from-email e app.financeiro.smtp.from-name são obrigatórios.");
        }

        if (isTestModeEnabled() && !StringUtils.hasText(properties.getDevEmail())) {
            throw new IllegalStateException("app.financeiro.test-mode=true exige app.financeiro.dev-email com o e-mail do desenvolvedor Victor.");
        }
    }

    private boolean isTestModeEnabled() {
        if (!StringUtils.hasText(properties.getTestMode())) {
            return false;
        }

        String normalized = properties.getTestMode().trim();
        if (normalized.length() >= 2 && normalized.startsWith("\"") && normalized.endsWith("\"")) {
            normalized = normalized.substring(1, normalized.length() - 1).trim();
        }

        return "true".equalsIgnoreCase(normalized)
                || "1".equals(normalized)
                || "yes".equalsIgnoreCase(normalized)
                || "on".equalsIgnoreCase(normalized)
                || "sim".equalsIgnoreCase(normalized);
    }

    private record EmailDispatch(String to, String subject) {
    }
}

