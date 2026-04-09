package br.dev.ctrls.inovareti.domain.financeiro.contaazul;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.core.io.ByteArrayResource;
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
 * Serviço responsável por toda a comunicação SMTP de recibos da Conta Azul.
 *
 * Responsabilidades:
 * - montar assunto/corpo de e-mail (template);
 * - preparar anexo PDF;
 * - disparar via JavaMail.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReceiptEmailService {

    private final JavaMailSender mailSender;
    private final RestTemplate restTemplate;

    @Value("${app.financeiro.test-mode}")
    private boolean financeiroTestMode;

    @Value("${app.financeiro.dev-email}")
    private String financeiroDeveloperEmail;

    @Value("${app.financeiro.smtp.from-email:administrativo@inovare.med.br}")
    private String financeiroFromEmail;

    @Value("${app.financeiro.smtp.from-name:Administrativo Inovare}")
    private String financeiroFromName;

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
     * Faz download do binário de recibo garantindo Authorization Bearer no GET.
     */
    public byte[] downloadReceiptBinary(String receiptUrl, String accessToken) {
        if (!StringUtils.hasText(receiptUrl)) {
            return new byte[0];
        }

        if (!StringUtils.hasText(accessToken)) {
            throw new IllegalStateException("Token de acesso da Conta Azul é obrigatório para baixar o recibo.");
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

        EmailDispatch dispatch = resolveDispatch(doctorName, destinationEmail);
        MimeMessage message = mailSender.createMimeMessage();

        try {
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            // Skymail exige remetente exatamente igual ao e-mail configurado para envio financeiro.
            helper.setFrom(financeiroFromEmail.trim());
            helper.setTo(dispatch.to());
            helper.setSubject(dispatch.subject());
            helper.setText(buildEmailBodyHtml(doctorName, saleIdentifier), true);

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
            // Blindagem do fluxo financeiro: falha SMTP não deve interromper sincronização de parcelas.
            mailSender.send(message);
            log.info("Recibo enviado por e-mail para {} (destino original: {})", dispatch.to(), destinationEmail);
        } catch (Exception e) {
            log.error("Falha ao enviar e-mail de recibo para {}. Processamento seguirá sem crash.", dispatch.to(), e);
        }
    }

    private EmailDispatch resolveDispatch(String doctorName, String destinationEmail) {
        String subject = buildEmailSubject(doctorName);
        if (!financeiroTestMode) {
            return new EmailDispatch(destinationEmail, subject);
        }

        return new EmailDispatch(financeiroDeveloperEmail, subject);
    }

    private String buildEmailSubject(String doctorName) {
        String resolvedDoctorName = StringUtils.hasText(doctorName) ? doctorName.trim() : "Cliente";
        return "Recibo de Quitação - Inovare TI - " + resolvedDoctorName;
    }

    private String buildEmailBodyHtml(String doctorName, String saleIdentifier) {
        String resolvedDoctorName = StringUtils.hasText(doctorName) ? doctorName.trim() : "Cliente";
        String resolvedSaleIdentifier = StringUtils.hasText(saleIdentifier) ? saleIdentifier.trim() : "N/D";

        String safeDoctorName = HtmlUtils.htmlEscape(resolvedDoctorName);
        String safeSaleIdentifier = HtmlUtils.htmlEscape(resolvedSaleIdentifier);

        return "<html><body>"
                + "<p>Prezado(a) " + safeDoctorName + ",</p>"
                + "<p>Confirmamos o recebimento do valor referente à Venda " + safeSaleIdentifier
                + ". O seu recibo de quitação já está disponível e segue em anexo a este e-mail.</p>"
                + "<p>Agradecemos a confiança.</p>"
                + "<p>Atenciosamente,<br/>Equipe Administrativa<br/>Inovare Serviços de Saúde</p>"
                + "</body></html>";
    }

    private void validateConfiguration() {
        if (!StringUtils.hasText(financeiroFromEmail) || !StringUtils.hasText(financeiroFromName)) {
            throw new IllegalStateException(
                    "Configuração SMTP financeira inválida: app.financeiro.smtp.from-email e app.financeiro.smtp.from-name são obrigatórios.");
        }

        if (financeiroTestMode && !StringUtils.hasText(financeiroDeveloperEmail)) {
            throw new IllegalStateException(
                    "app.financeiro.test-mode=true exige app.financeiro.dev-email com o e-mail do desenvolvedor Victor.");
        }
    }

    private record EmailDispatch(String to, String subject) {
    }
}
