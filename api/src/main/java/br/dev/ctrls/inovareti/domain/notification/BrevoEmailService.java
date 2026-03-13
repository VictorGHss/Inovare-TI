package br.dev.ctrls.inovareti.domain.notification;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class BrevoEmailService {

    private final RestTemplate restTemplate;

    @Value("${app.financeiro.test-mode:false}")
    private boolean financeiroTestMode;

    @Value("${app.financeiro.dev-email:}")
    private String financeiroDeveloperEmail;

    @Value("${app.brevo.api-url:https://api.brevo.com/v3/smtp/email}")
    private String brevoApiUrl;

    @Value("${app.brevo.api-key:}")
    private String brevoApiKey;

    @Value("${app.brevo.sender.email:no-reply@inovareti.local}")
    private String senderEmail;

    @Value("${app.brevo.sender.name:Inovare TI}")
    private String senderName;

    public void sendReceiptEmail(String originalRecipient, String subject, String htmlContent) {
        validateBrevoConfiguration();

        ReceiptDispatch dispatch = resolveDispatchForFinancialMode(originalRecipient, subject);

        Map<String, Object> body = Map.of(
                "sender", Map.of("name", senderName, "email", senderEmail),
                "to", List.of(Map.of("email", dispatch.finalRecipient())),
                "subject", dispatch.finalSubject(),
                "htmlContent", htmlContent
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("api-key", brevoApiKey);

        restTemplate.postForEntity(brevoApiUrl, new HttpEntity<>(body, headers), Void.class);

        log.info("Brevo receipt dispatched to {} (original recipient: {})",
                dispatch.finalRecipient(),
                originalRecipient);
    }

    private ReceiptDispatch resolveDispatchForFinancialMode(String originalRecipient, String subject) {
        if (!financeiroTestMode) {
            return new ReceiptDispatch(originalRecipient, subject);
        }

        if (!StringUtils.hasText(financeiroDeveloperEmail)) {
            throw new IllegalStateException(
                    "app.financeiro.test-mode=true exige app.financeiro.dev-email configurado.");
        }

        String markedSubject = "[FINANCEIRO TESTE][destinatário original: "
                + originalRecipient
                + "] "
                + subject;

        return new ReceiptDispatch(financeiroDeveloperEmail, markedSubject);
    }

    private void validateBrevoConfiguration() {
        if (!StringUtils.hasText(brevoApiKey)) {
            throw new IllegalStateException("BREVO api-key não configurada em app.brevo.api-key.");
        }
    }

    private record ReceiptDispatch(String finalRecipient, String finalSubject) {
    }
}