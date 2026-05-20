package br.dev.ctrls.inovareti.domain.financeiro.contaazul;

import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import br.dev.ctrls.inovareti.domain.financeiro.DoctorEmailMapping;
import br.dev.ctrls.inovareti.domain.financeiro.DoctorEmailMappingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Componente de validação de domínio responsável por verificar a integridade da venda/baixa
 * e a consistência dos mapeamentos e metadados de e-mail dos profissionais.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ContaAzulReceiptValidator {

    private final DoctorEmailMappingRepository doctorEmailMappingRepository;

    /**
     * Valida os metadados e integridade de um item de venda.
     *
     * @param sale O item de venda a ser validado.
     * @return O resultado da validação contendo status e dados resolvidos ou mensagens de erro.
     */
    public ValidationResult validate(ContaAzulClient.SaleItem sale) {
        if (sale == null) {
            return new ValidationResult(false, null, null, null, "NULL_SALE", "Item de venda nulo recebido para validação.");
        }

        // 1. Validar se a parcela possui identificador básico consistente
        if (!StringUtils.hasText(sale.parcelaId())) {
            return new ValidationResult(false, null, null, null, "MISSING_PARCELA_ID", "A parcela não possui parcelaId.");
        }

        // 2. Validar e normalizar o customerUuid
        String customerUuid = normalizeUuid(sale.customerUuid());
        if (!StringUtils.hasText(customerUuid)) {
            return new ValidationResult(false, null, null, null, "CUSTOMER_UUID_MISSING", "O recibo não possui customer UUID válido.");
        }

        // 3. Localizar o mapeamento do médico pelo UUID
        Optional<DoctorEmailMapping> mappingOpt = findDoctorMappingByCustomerUuid(customerUuid);
        if (mappingOpt.isEmpty()) {
            String errorMsg = String.format("Cadastro faltando para o médico: %s | UUID API='%s' | UUID normalizado='%s'",
                    StringUtils.hasText(sale.customerName()) ? sale.customerName() : "(nome indisponível)",
                    sale.customerUuid(),
                    customerUuid);
            return new ValidationResult(false, customerUuid, null, null, "MAPPING_NOT_FOUND", errorMsg);
        }

        DoctorEmailMapping mapping = mappingOpt.get();

        // 4. Resolver o e-mail de destino (direto ou do usuário associado) e validar sua presença
        String recipientEmail = resolveRecipientEmail(mapping);
        if (!StringUtils.hasText(recipientEmail)) {
            String errorMsg = String.format("Mapeamento sem e-mail de destino (user/fallback) para customer UUID %s.", customerUuid);
            return new ValidationResult(false, customerUuid, mapping, null, "EMAIL_MISSING", errorMsg);
        }

        return new ValidationResult(true, customerUuid, mapping, recipientEmail, null, null);
    }

    /**
     * Resolve o e-mail de destino do mapeamento do médico.
     */
    public String resolveRecipientEmail(DoctorEmailMapping mapping) {
        if (mapping == null) {
            return null;
        }
        if (mapping.getUser() != null && StringUtils.hasText(mapping.getUser().getEmail())) {
            return mapping.getUser().getEmail();
        }
        return mapping.getDoctorEmail();
    }

    /**
     * Resolve o nome amigável do médico com base no mapeamento e nome do cliente da API.
     */
    public String resolveDoctorName(DoctorEmailMapping mapping, String customerName) {
        if (mapping == null) {
            return StringUtils.hasText(customerName) ? customerName : "Profissional";
        }
        if (mapping.getUser() != null && StringUtils.hasText(mapping.getUser().getName())) {
            return mapping.getUser().getName();
        }
        if (StringUtils.hasText(mapping.getDoctorName())) {
            return mapping.getDoctorName();
        }
        return StringUtils.hasText(customerName) ? customerName : "Profissional";
    }

    /**
     * Localiza o mapeamento de médico pelo customer UUID normalizado ou direto.
     */
    public Optional<DoctorEmailMapping> findDoctorMappingByCustomerUuid(String customerUuidFromParcel) {
        if (!StringUtils.hasText(customerUuidFromParcel)) {
            return Optional.empty();
        }

        String normalizedParcelCustomerUuid = normalizeUuid(customerUuidFromParcel);

        Optional<DoctorEmailMapping> normalizedMatch = doctorEmailMappingRepository
                .findByContaAzulCustomerUuidNormalized(normalizedParcelCustomerUuid);
        if (normalizedMatch.isPresent()) {
            return normalizedMatch;
        }

        Optional<DoctorEmailMapping> direct = doctorEmailMappingRepository
                .findByContaAzulCustomerUuid(normalizedParcelCustomerUuid);
        if (direct.isPresent()) {
            return direct;
        }

        return doctorEmailMappingRepository.findAllByOrderByDoctorNameAsc().stream()
                .filter(mapping -> StringUtils.hasText(mapping.getContaAzulCustomerUuid()))
                .filter(mapping -> normalizedParcelCustomerUuid.equals(normalizeUuid(mapping.getContaAzulCustomerUuid())))
                .findFirst();
    }

    /**
     * Normaliza UUID removendo espaços e convertendo para minúsculas.
     */
    public String normalizeUuid(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String normalized = value.replaceAll("\\s+", "").toLowerCase();
        return StringUtils.hasText(normalized) ? normalized : null;
    }

    /**
     * Registro com os resultados detalhados da validação de uma parcela de recibo.
     */
    public record ValidationResult(
            boolean valid,
            String customerUuid,
            DoctorEmailMapping mapping,
            String recipientEmail,
            String errorType,
            String errorMessage
    ) {
        public boolean isNotValid() {
            return !valid;
        }
    }
}
