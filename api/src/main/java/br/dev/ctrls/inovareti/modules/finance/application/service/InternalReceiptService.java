package br.dev.ctrls.inovareti.modules.finance.application.service;

import io.micrometer.observation.annotation.Observed;

import br.dev.ctrls.inovareti.modules.finance.infrastructure.adapter.output.JsonSafeReader;
import br.dev.ctrls.inovareti.modules.finance.infrastructure.adapter.output.NumberToWords;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;
import org.springframework.util.StringUtils;
import org.thymeleaf.ITemplateEngine;
import org.thymeleaf.context.Context;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.MissingNode;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
@Observed
public class InternalReceiptService {

    private static final Locale PT_BR = Locale.forLanguageTag("pt-BR");
    private static final DateTimeFormatter DATE_DISPLAY = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter DATE_FULL = DateTimeFormatter.ofPattern("dd 'de' MMMM 'de' yyyy", PT_BR);
    private static final String LOGO_RESOURCE_PATH = "static/inovare-logo.png";

    private final ITemplateEngine templateEngine;
    private final JsonSafeReader jsonSafeReader;
    private volatile String cachedLogoDataUri;
    private volatile boolean logoResolutionDone;

    public byte[] generateReceipt(JsonNode settlement, String doctorName, String doctorCpfCnpj, String saleDescription) {
        try {
            BigDecimal netValue = resolveNetValue(settlement);
            LocalDate paymentDate = resolvePaymentDate(settlement);
            String referenceId = resolveReferenceId(settlement, saleDescription);
            String parcela = resolveParcelaDescription(settlement, saleDescription, referenceId);
            String valueInWords = NumberToWords.toBrazilianCurrencyWords(netValue);
            String htmlLogoBase64 = resolveLogoDataUri();
            String doctorDocument = resolveDoctorDocument(doctorCpfCnpj);

            if ("Documento nÃ£o cadastrado".equals(doctorDocument)) {
                log.info("Recibo interno gerado para mÃ©dico sem CPF/CNPJ cadastrado: {}", resolveDoctorName(doctorName));
            } else {
                log.info("Recibo interno gerado para mÃ©dico com CPF/CNPJ cadastrado: {}", resolveDoctorName(doctorName));
            }

            Context context = new Context(Locale.forLanguageTag("pt-BR"));
            context.setVariable("doctorName", resolveDoctorName(doctorName));
            context.setVariable("doctorDocument", doctorDocument);
            context.setVariable("amountValue", formatCurrency(netValue));
            context.setVariable("amountInWords", valueInWords);
            context.setVariable("parcela", parcela);
            context.setVariable("referenceId", referenceId);
            context.setVariable("paymentDate", paymentDate.format(DATE_DISPLAY));
            context.setVariable("issueDate", LocalDate.now().format(DATE_FULL));
            context.setVariable("city", "Ponta Grossa-PR");
            context.setVariable("logoBase64", htmlLogoBase64);

            String html = templateEngine.process("recibo_interno", context);
            String htmlUtf8 = new String(html.getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8);

            try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                PdfRendererBuilder builder = new PdfRendererBuilder();
                builder.useFastMode().withHtmlContent(htmlUtf8, "");
                builder.toStream(output);
                builder.run();
                return output.toByteArray();
            }
        } catch (RuntimeException ex) {
            log.error("Falha em tempo de execuÃ§Ã£o ao gerar recibo interno em PDF.", ex);
            throw new IllegalStateException("Falha ao gerar recibo interno em PDF.", ex);
        } catch (java.io.IOException ex) {
            log.error("Falha ao gerar recibo interno em PDF.", ex);
            throw new IllegalStateException("Falha ao gerar recibo interno em PDF.", ex);
        }
    }

    private BigDecimal resolveNetValue(JsonNode settlement) {
        JsonNode settlementData = settlement == null ? MissingNode.getInstance() : settlement;

        double valorLiquido = settlementData.path("valor_composicao").path("valor_liquido").asDouble(0.0);
        if (valorLiquido == 0.0d) {
            valorLiquido = settlementData.path("valor_bruto").asDouble(0.0);
        }

        if (valorLiquido != 0.0d) {
            return BigDecimal.valueOf(valorLiquido).setScale(2, RoundingMode.HALF_UP);
        }

        String rawValue = jsonSafeReader.readText(
                settlementData,
                "valor_composicao.valor_liquido",
                "valor_bruto",
                "valor_liquido",
                "valor.liquido",
                "valorLiquido",
                "evento.valor_liquido",
                "evento.valor.liquido",
                "resumo.valor_liquido",
                "liquid_amount",
                "net_amount",
                "amount.net");

        if (!StringUtils.hasText(rawValue)) {
            return BigDecimal.ZERO;
        }

        return parseCurrencyValue(rawValue);
    }

    private LocalDate resolvePaymentDate(JsonNode settlement) {
        String rawDate = jsonSafeReader.readText(
                settlement,
                "data_pagamento",
                "pagamento.data",
                "data_baixa",
                "evento.data_pagamento",
                "paid_at",
                "created_at",
                "data");

        if (!StringUtils.hasText(rawDate)) {
            return LocalDate.now();
        }

        String normalized = rawDate.trim();
        List<DateTimeFormatter> dateOnlyFormats = List.of(
                DateTimeFormatter.ISO_LOCAL_DATE,
                DateTimeFormatter.ofPattern("dd/MM/yyyy"),
                DateTimeFormatter.ofPattern("yyyy/MM/dd"));

        for (DateTimeFormatter formatter : dateOnlyFormats) {
            try {
                return LocalDate.parse(normalized, formatter);
            } catch (DateTimeParseException ignored) {
            }
        }

        try {
            return OffsetDateTime.parse(normalized).toLocalDate();
        } catch (DateTimeParseException ignored) {
        }

        try {
            return LocalDateTime.parse(normalized, DateTimeFormatter.ISO_DATE_TIME).toLocalDate();
        } catch (DateTimeParseException ignored) {
        }

        return LocalDate.now();
    }

    private String resolveReferenceId(JsonNode settlement, String saleDescription) {
        String referenceId = jsonSafeReader.readText(
                settlement,
                "id_referencia",
                "evento.referencia.id",
                "evento_financeiro.referencia.id",
                "referencia.id",
                "venda_id",
                "sale_id",
                "origem.venda_id");

        if (StringUtils.hasText(referenceId)) {
            return referenceId.trim();
        }

        if (StringUtils.hasText(saleDescription)) {
            return saleDescription.trim();
        }

        return "N/D";
    }

    private String resolveParcelaDescription(JsonNode settlement, String saleDescription, String referenceId) {
        if (StringUtils.hasText(saleDescription)) {
            return saleDescription.trim();
        }

        String parcela = jsonSafeReader.readText(
                settlement,
                "descricao",
                "evento.descricao",
                "historico",
                "referencia.descricao",
                "parcela.descricao");

        if (StringUtils.hasText(parcela)) {
            return parcela.trim();
        }

        return "Parcela vinculada a referencia " + referenceId;
    }

    private String resolveLogoDataUri() {
        if (logoResolutionDone) {
            return cachedLogoDataUri;
        }

        synchronized (this) {
            if (logoResolutionDone) {
                return cachedLogoDataUri;
            }

            ClassPathResource logoResource = new ClassPathResource(LOGO_RESOURCE_PATH);
            if (!logoResource.exists()) {
                log.warn("Logo interna nao encontrada em classpath:{}", LOGO_RESOURCE_PATH);
                cachedLogoDataUri = null;
                logoResolutionDone = true;
                return cachedLogoDataUri;
            }

            try (var inputStream = logoResource.getInputStream()) {
                byte[] logoBytes = StreamUtils.copyToByteArray(inputStream);
                if (logoBytes.length == 0) {
                    log.warn("Logo interna encontrada, mas sem bytes em classpath:{}", LOGO_RESOURCE_PATH);
                    cachedLogoDataUri = null;
                    logoResolutionDone = true;
                    return cachedLogoDataUri;
                }

                cachedLogoDataUri = Base64.getEncoder().encodeToString(logoBytes);
                logoResolutionDone = true;
                return cachedLogoDataUri;
            } catch (java.io.IOException ex) {
                log.warn("Falha ao carregar logo interna para base64.", ex);
                cachedLogoDataUri = null;
                logoResolutionDone = true;
                return cachedLogoDataUri;
            }
        }
    }

    private String resolveDoctorName(String doctorName) {
        return StringUtils.hasText(doctorName) ? doctorName.trim() : "Profissional";
    }

    private String resolveDoctorDocument(String doctorCpfCnpj) {
        if (StringUtils.hasText(doctorCpfCnpj)) {
            return doctorCpfCnpj.trim();
        }

        return "Documento nÃ£o cadastrado";
    }

    private BigDecimal parseCurrencyValue(String rawValue) {
        String cleaned = rawValue.trim().replaceAll("[^0-9,.-]", "");
        if (!StringUtils.hasText(cleaned)) {
            return BigDecimal.ZERO;
        }

        String normalized = cleaned;
        if (normalized.contains(",") && normalized.contains(".")) {
            normalized = normalized.replace(".", "").replace(",", ".");
        } else if (normalized.contains(",")) {
            normalized = normalized.replace(",", ".");
        }

        try {
            return new BigDecimal(normalized);
        } catch (NumberFormatException ex) {
            log.warn("Valor monetario invalido no settlement: {}. Assumindo zero.", rawValue);
            return BigDecimal.ZERO;
        }
    }

    private String formatCurrency(BigDecimal value) {
        NumberFormat formatter = NumberFormat.getCurrencyInstance(PT_BR);
        return formatter.format(Objects.requireNonNullElse(value, BigDecimal.ZERO));
    }
}



