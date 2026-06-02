package br.dev.ctrls.inovareti.modules.finance.infrastructure.adapter.output;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import lombok.extern.slf4j.Slf4j;

/**
 * Componente puro de domínio responsável pelas regras de negócio, cálculos matemáticos,
 * conversão para centavos e classificações de ativos/passivos financeiros da Conta Azul.
 */
@Slf4j
@Service
public class ContaAzulSummaryCalculator {

    private static final ZoneOffset BRASILIA_OFFSET = ZoneOffset.ofHours(-3);
    
    private static final Set<String> ASSET_ACCOUNT_TYPES = Set.of(
        "CONTA_CORRENTE",
        "POUPANCA",
        "INVESTIMENTO",
        "APLICACAO",
        "CAIXINHA"
    );
    
    private static final Set<String> LIABILITY_ACCOUNT_TYPES = Set.of(
        "CARTAO_CREDITO",
        "CARTAO_DE_CREDITO"
    );

    /**
     * Converte um valor BigDecimal em centavos (long) arredondando HALF_UP.
     */
    public long toCents(BigDecimal value) {
        if (value == null) {
            return 0L;
        }
        return value
                .movePointRight(2)
                .setScale(0, RoundingMode.HALF_UP)
                .longValue();
    }

    /**
     * Normaliza um valor de entrada em BigDecimal para centavos (long), considerando o caminho de origem do dado.
     */
    public long normalizeAmountToCents(BigDecimal amount, String sourcePath) {
        if (amount == null) {
            return 0L;
        }

        String normalizedPath = sourcePath != null ? sourcePath.toLowerCase() : "";
        boolean explicitCents = normalizedPath.contains("centavo") || normalizedPath.contains("centavos");

        if (explicitCents) {
            // Quando o campo já indica centavos, evita multiplicação por 100
            return amount.setScale(0, RoundingMode.HALF_UP).longValue();
        }

        if (amount.scale() > 0) {
            return toCents(amount);
        }

        // Na API financeira V2 da Conta Azul, valores integrais sem escala geralmente já são retornados em centavos.
        return amount.longValue();
    }

    /**
     * Verifica se o tipo de conta financeira pertence à whitelist de ativos.
     */
    public boolean isAssetAccountType(String accountType) {
        return accountType != null && ASSET_ACCOUNT_TYPES.contains(accountType.trim().toUpperCase());
    }

    /**
     * Verifica se o tipo de conta financeira pertence à whitelist de passivos (ex: cartões de crédito).
     */
    public boolean isLiabilityAccountType(String accountType) {
        return accountType != null && LIABILITY_ACCOUNT_TYPES.contains(accountType.trim().toUpperCase());
    }

    /**
     * Regra de negócio que determina se uma conta financeira deve ser consolidada no saldo final.
     * Somente contas ativas do tipo ativo (asset) com saldo positivo entram no consolidado.
     */
    public boolean shouldIncludeAccountInBalance(String accountType, boolean active, long balanceCents) {
        return active && isAssetAccountType(accountType) && balanceCents >= 0;
    }

    /**
     * Resolve a data de última atualização no padrão de fuso horário de Brasília.
     */
    public String resolveSummaryLastUpdatedAt(String rawLastUpdatedAt) {
        OffsetDateTime parsed = parseApiDateToBrasiliaOffsetDateTime(rawLastUpdatedAt);
        if (parsed != null) {
            return parsed.withOffsetSameInstant(BRASILIA_OFFSET).toString();
        }

        return OffsetDateTime.now(BRASILIA_OFFSET).toString();
    }

    /**
     * Utilitário para parsear datas retornadas pela API para OffsetDateTime de Brasília.
     */
    public OffsetDateTime parseApiDateToBrasiliaOffsetDateTime(String rawDate) {
        if (!StringUtils.hasText(rawDate)) {
            return null;
        }

        String trimmed = rawDate.trim();

        try {
            if (trimmed.endsWith("Z") || trimmed.endsWith("z")) {
                return Instant.parse(trimmed).atOffset(BRASILIA_OFFSET);
            }
        } catch (DateTimeParseException ignored) {
        }

        try {
            return OffsetDateTime.parse(trimmed).withOffsetSameInstant(BRASILIA_OFFSET);
        } catch (DateTimeParseException ignored) {
        }

        try {
            LocalDateTime localDateTime = LocalDateTime.parse(trimmed);
            return localDateTime.atOffset(BRASILIA_OFFSET);
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }
}

