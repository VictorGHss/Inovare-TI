package br.dev.ctrls.inovareti.modules.finance.application.service;

import io.micrometer.observation.annotation.Observed;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import br.dev.ctrls.inovareti.modules.communication.infrastructure.config.FinanceMailProperties;
import br.dev.ctrls.inovareti.modules.communication.infrastructure.config.SpringMailProperties;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.util.HtmlUtils;
import org.springframework.web.client.RestTemplate;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * ServiÃ§o responsÃ¡vel por toda a comunicaÃ§Ã£o SMTP de recibos da Conta Azul.
 *
 * Responsabilidades:
 * - montar assunto/corpo de e-mail (template);
 * - preparar anexo PDF;
 * - disparar via JavaMail.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Observed
public class ReceiptEmailService {

    private static final Pattern SALE_NUMBER_PATTERN = Pattern.compile("(?i)(?:numero_venda|numero|venda)\\s*[:#-]?\\s*(\\d{3,})");
    private static final String FINANCEIRO_FIXED_FROM = "administrativo@inovare.med.br";

    private final JavaMailSender mailSender;
    private final RestTemplate restTemplate;
    private final FinanceMailProperties properties;
    private final SpringMailProperties springMailProperties;


    public void sendReceiptForRealSaleTest(
            String doctorName,
            String destinationEmail,
            String saleId,
            byte[] pdfBytes) {
        sendReceiptEmailWithPdf(
                doctorName,
                destinationEmail,
                saleId,
                pdfBytes,
                "recibo-venda-" + saleId + ".pdf");
    }

    public void sendReceiptForBaixa(
            String doctorName,
            String destinationEmail,
            String saleId,
            String baixaId,
            byte[] pdfBytes) {
        sendReceiptEmailWithPdf(
                doctorName,
                destinationEmail,
                StringUtils.hasText(saleId) ? saleId : baixaId,
                pdfBytes,
                "recibo-quitacao-baixa-" + baixaId + ".pdf");
    }

    /**
     * Faz download do binÃ¡rio de recibo garantindo Authorization Bearer no GET.
     */
    public byte[] downloadReceiptBinary(String receiptUrl, String accessToken) {
        if (!StringUtils.hasText(receiptUrl)) {
            return new byte[0];
        }

        if (!StringUtils.hasText(accessToken)) {
            throw new IllegalStateException("Token de acesso da Conta Azul Ã© obrigatÃ³rio para baixar o recibo.");
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken.trim());
        headers.setAccept(List.of(MediaType.APPLICATION_PDF, MediaType.APPLICATION_OCTET_STREAM));

        ResponseEntity<byte[]> response = restTemplate.exchange(
                receiptUrl.trim(),
                HttpMethod.GET,
                new HttpEntity<>(headers),
                byte[].class);

        return response.getBody() != null ? response.getBody() : new byte[0];
    }

    private void sendReceiptEmailWithPdf(
            String doctorName,
            String destinationEmail,
            String saleIdentifier,
            byte[] pdfBytes,
            String attachmentFileName) {
        validateConfiguration();
        // Normaliza o identificador para exibir somente o nÃºmero de venda no conteÃºdo enviado.
        String saleNumber = resolveSaleNumber(saleIdentifier);

        EmailDispatch dispatch = resolveDispatch(doctorName, destinationEmail, saleNumber);
        MimeMessage message = mailSender.createMimeMessage();

        try {
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            // PolÃ­tica de seguranÃ§a: remetente financeiro fixo para evitar variaÃ§Ãµes indevidas no SMTP.
            helper.setFrom(FINANCEIRO_FIXED_FROM);
            helper.setTo(dispatch.to());
            helper.setSubject(dispatch.subject());
            helper.setText(buildEmailBodyHtml(doctorName, saleNumber), true);

            if (pdfBytes != null && pdfBytes.length > 0) {
                helper.addAttachment(
                        StringUtils.hasText(attachmentFileName) ? attachmentFileName : "recibo.pdf",
                        new ByteArrayResource(pdfBytes),
                        "application/pdf");
            }
        } catch (MessagingException ex) {
            throw new IllegalStateException("Falha ao montar mensagem SMTP de recibo.", ex);
        }

        try {
            // Blindagem do fluxo financeiro: falha SMTP nÃ£o deve interromper sincronizaÃ§Ã£o de parcelas.
            mailSender.send(message);
            log.info("Recibo enviado por e-mail para {} (destino original: {})", dispatch.to(), destinationEmail);
        } catch (MailException e) {
            log.error("Falha ao enviar e-mail de recibo para {}. Processamento seguirÃ¡ sem crash.", dispatch.to(), e);
        }
    }

    private EmailDispatch resolveDispatch(String doctorName, String destinationEmail, String saleNumber) {
        String subject = buildEmailSubject(doctorName, saleNumber);
        boolean testMode = isTestModeEnabled();
        String originalEmail = StringUtils.hasText(destinationEmail) ? destinationEmail.trim() : "";
        String devEmail = StringUtils.hasText(properties.getDevEmail()) ? properties.getDevEmail().trim() : "";
        String destinoFinal = testMode ? devEmail : originalEmail;

        if (testMode) {
            log.warn("MODO DE TESTE ATIVO: Redirecionando e-mail de {} para {}", originalEmail, devEmail);
        }

        if (!StringUtils.hasText(destinoFinal)) {
            throw new IllegalStateException("DestinatÃ¡rio final de e-mail estÃ¡ vazio apÃ³s resoluÃ§Ã£o do modo de teste.");
        }

        return new EmailDispatch(destinoFinal, subject);
    }

    private String buildEmailSubject(String doctorName, String saleNumber) {
        String resolvedDoctorName = StringUtils.hasText(doctorName) ? doctorName.trim() : "Cliente";
        return "Recibo de QuitaÃ§Ã£o - Inovare TI - " + resolvedDoctorName + " - Venda " + saleNumber;
    }

    private String buildEmailBodyHtml(String doctorName, String saleIdentifier) {
        String resolvedDoctorName = StringUtils.hasText(doctorName) ? doctorName.trim() : "Cliente";
        String resolvedSaleIdentifier = StringUtils.hasText(saleIdentifier) ? saleIdentifier.trim() : "N/D";

        String safeDoctorName = HtmlUtils.htmlEscape(resolvedDoctorName);
        String safeSaleIdentifier = HtmlUtils.htmlEscape(resolvedSaleIdentifier);

        return "<html><body>"
                + "<p>Prezado(a) " + safeDoctorName + ",</p>"
                + "<p>Confirmamos o recebimento do valor referente Ã  Venda " + safeSaleIdentifier
                + ". O seu recibo de quitaÃ§Ã£o jÃ¡ estÃ¡ disponÃ­vel e segue em anexo a este e-mail.</p>"
            + "<p>Atenciosamente,<br/>Administrativo Inovare</p>"
                + "</body></html>";
    }

    private void validateConfiguration() {
        if (!StringUtils.hasText(properties.getSmtp().getFromEmail()) || !StringUtils.hasText(properties.getSmtp().getFromName())) {
            throw new IllegalStateException(
                    "ConfiguraÃ§Ã£o SMTP financeira invÃ¡lida: app.financeiro.smtp.from-email e app.financeiro.smtp.from-name sÃ£o obrigatÃ³rios.");
        }

        // Blindagem obrigatÃ³ria: envio financeiro deve ocorrer apenas com SSL puro na porta 465.
        boolean smtpSslEnable = Boolean.parseBoolean(springMailProperties.getProperties().getOrDefault("mail.smtp.ssl.enable", "false"));
        if (!smtpSslEnable || springMailProperties.getPort() != 465) {
            throw new IllegalStateException(
                    "ConfiguraÃ§Ã£o SMTP insegura para recibos: exige SSL habilitado e porta 465.");
        }

        if (isTestModeEnabled() && !StringUtils.hasText(properties.getDevEmail())) {
            throw new IllegalStateException(
                    "app.financeiro.test-mode=true exige app.financeiro.dev-email com o e-mail do desenvolvedor Victor.");
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

    private String resolveSaleNumber(String saleIdentifier) {
        if (!StringUtils.hasText(saleIdentifier)) {
            return "N/D";
        }

        String normalized = saleIdentifier.trim();
        if (normalized.matches("\\d{3,}")) {
            return normalized;
        }

        Matcher matcher = SALE_NUMBER_PATTERN.matcher(normalized);
        if (matcher.find()) {
            return matcher.group(1);
        }

        // Evita exibir UUID no e-mail quando nÃºmero de venda nÃ£o estiver disponÃ­vel.
        return "N/D";
    }

    private record EmailDispatch(String to, String subject) {
    }
}



