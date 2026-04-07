package br.dev.ctrls.inovareti.domain.financeiro.contaazul;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.thymeleaf.ITemplateEngine;
import org.thymeleaf.context.Context;

import com.fasterxml.jackson.databind.JsonNode;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class InternalReceiptService {

    private static final Locale PT_BR = Locale.forLanguageTag("pt-BR");
    private static final DateTimeFormatter DATE_DISPLAY = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter DATE_FULL = DateTimeFormatter.ofPattern("dd 'de' MMMM 'de' yyyy", PT_BR);

    private final ITemplateEngine templateEngine;
    private final JsonSafeReader jsonSafeReader;

    public byte[] generateReceipt(JsonNode settlement, String doctorName, String doctorCpfCnpj) {
        try {
            BigDecimal netValue = resolveNetValue(settlement);
            LocalDate paymentDate = resolvePaymentDate(settlement);
            String referenceId = resolveReferenceId(settlement);
            String parcela = resolveParcelaDescription(settlement, referenceId);
            String valueInWords = NumberToWords.toBrazilianCurrencyWords(netValue);

            Context context = new Context(PT_BR);
            context.setVariable("doctorName", resolveDoctorName(doctorName));
            context.setVariable("doctorDocument", resolveDoctorDocument(doctorCpfCnpj, settlement));
            context.setVariable("amountValue", formatCurrency(netValue));
            context.setVariable("amountInWords", valueInWords);
            context.setVariable("parcela", parcela);
            context.setVariable("referenceId", referenceId);
            context.setVariable("paymentDate", paymentDate.format(DATE_DISPLAY));
            context.setVariable("issueDate", LocalDate.now().format(DATE_FULL));
            context.setVariable("city", "Ponta Grossa/PR");

            String html = templateEngine.process("recibo_interno", context);

            try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                PdfRendererBuilder builder = new PdfRendererBuilder();
                builder.useFastMode();
                builder.withHtmlContent(html, null);
                builder.toStream(output);
                builder.run();
                return output.toByteArray();
            }
        } catch (RuntimeException ex) {
            log.error("Falha em tempo de execução ao gerar recibo interno em PDF.", ex);
            throw new IllegalStateException("Falha ao gerar recibo interno em PDF.", ex);
        } catch (java.io.IOException ex) {
            log.error("Falha ao gerar recibo interno em PDF.", ex);
            throw new IllegalStateException("Falha ao gerar recibo interno em PDF.", ex);
        }
    }

    private BigDecimal resolveNetValue(JsonNode settlement) {
        String rawValue = jsonSafeReader.readText(
                settlement,
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

    private String resolveReferenceId(JsonNode settlement) {
        String referenceId = jsonSafeReader.readText(
                settlement,
                "evento.referencia.id",
                "evento_financeiro.referencia.id",
                "referencia.id",
                "venda_id",
                "sale_id",
                "origem.venda_id");

        if (StringUtils.hasText(referenceId)) {
            return referenceId.trim();
        }

        return "N/D";
    }

    private String resolveParcelaDescription(JsonNode settlement, String referenceId) {
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

    private String resolveDoctorName(String doctorName) {
        return StringUtils.hasText(doctorName) ? doctorName.trim() : "Profissional";
    }

    private String resolveDoctorDocument(String doctorCpfCnpj, JsonNode settlement) {
        if (StringUtils.hasText(doctorCpfCnpj)) {
            return doctorCpfCnpj.trim();
        }

        String fromSettlement = jsonSafeReader.readText(
                settlement,
                "cliente.cpf_cnpj",
                "customer.cpf_cnpj",
                "pessoa.cpf_cnpj",
                "referencia.cpf_cnpj",
                "cpf_cnpj");

        if (StringUtils.hasText(fromSettlement)) {
            return fromSettlement.trim();
        }

        return "Nao informado";
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
        return formatter.format(value);
    }
}
