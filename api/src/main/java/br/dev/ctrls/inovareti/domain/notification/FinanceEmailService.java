package br.dev.ctrls.inovareti.domain.notification;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.util.HtmlUtils;

import br.dev.ctrls.inovareti.domain.financeiro.contaazul.ContaAzulPaymentParcel;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class FinanceEmailService {

    private static final Pattern NUMERIC_IDENTIFIER_PATTERN = Pattern.compile("(\\d+)");

    private final JavaMailSender mailSender;

    @Value("${app.financeiro.test-mode}")
    private boolean financeiroTestMode;

    @Value("${app.financeiro.dev-email}")
    private String financeiroDeveloperEmail;

    @Value("${app.financeiro.smtp.from-email:administrativo@inovare.med.br}")
    private String financeiroFromEmail;

    @Value("${app.financeiro.smtp.from-name:Administrativo Inovare}")
    private String financeiroFromName;

    public void sendReceiptEmail(String medicoNome, String destinationEmail, String bodyText) {
        sendReceiptEmailWithPdf(medicoNome, destinationEmail, bodyText, null, null);
    }

    public void sendReceiptEmail(ContaAzulPaymentParcel parcela) {
        String bodyText = "Olá " + parcela.medicoNome()
                + ", este é um disparo de teste de recibo financeiro. Valor: R$ 100,00. Referente a Março/2026.";
        sendReceiptEmail(parcela.medicoNome(), parcela.recipientEmail(), bodyText);
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
            helper.setText(buildEmailBodyHtml(medicoNome, bodyText, attachmentFileName), true);

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
        String resolvedName = StringUtils.hasText(medicoNome) ? medicoNome.trim() : "Cliente";
        String subject = "Recibo de Quitação - Inovare TI - " + resolvedName;

        if (!financeiroTestMode) {
            return new EmailDispatch(destinationEmail, subject);
        }

        return new EmailDispatch(financeiroDeveloperEmail, subject);
    }

    private String formatFromAddress() {
        return financeiroFromName + " <" + financeiroFromEmail + ">";
    }

    private String buildEmailBodyHtml(String medicoNome, String bodyText, String attachmentFileName) {
        String resolvedName = StringUtils.hasText(medicoNome) ? medicoNome.trim() : "Cliente";
        String resolvedSaleId = resolveSaleIdentifier(bodyText, attachmentFileName);

        String safeName = HtmlUtils.htmlEscape(resolvedName);
        String safeSaleId = HtmlUtils.htmlEscape(resolvedSaleId);

        return "<html><body>"
                + "<p>Prezado(a) " + safeName + ",</p>"
                + "<p>Confirmamos o recebimento do valor referente à Venda " + safeSaleId
                + ". O seu recibo de quitação já está disponível e segue em anexo a este e-mail.</p>"
                + "<p>Agradecemos a confiança.</p>"
                + "<p>Atenciosamente,<br/>Equipe Administrativa<br/>Inovare Serviços de Saúde</p>"
                + "</body></html>";
    }

    private String resolveSaleIdentifier(String bodyText, String attachmentFileName) {
        String fromBody = extractNumericIdentifier(bodyText);
        if (StringUtils.hasText(fromBody)) {
            return fromBody;
        }

        String fromFileName = extractNumericIdentifier(attachmentFileName);
        if (StringUtils.hasText(fromFileName)) {
            return fromFileName;
        }

        return "N/D";
    }

    private String extractNumericIdentifier(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }

        Matcher matcher = NUMERIC_IDENTIFIER_PATTERN.matcher(value);
        return matcher.find() ? matcher.group(1) : null;
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
