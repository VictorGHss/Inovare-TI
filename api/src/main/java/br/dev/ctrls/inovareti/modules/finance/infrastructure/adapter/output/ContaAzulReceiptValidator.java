package br.dev.ctrls.inovareti.modules.finance.infrastructure.adapter.output;

import br.dev.ctrls.inovareti.modules.finance.infrastructure.adapter.output.ContaAzulClient;

import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import br.dev.ctrls.inovareti.modules.finance.domain.model.DoctorEmailMapping;
import br.dev.ctrls.inovareti.modules.finance.domain.port.DoctorEmailMappingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Componente de validaÃƒÂ§ÃƒÂ£o de domÃƒÂ­nio responsÃƒÂ¡vel por verificar a integridade da venda/baixa
 * e a consistÃƒÂªncia dos mapeamentos e metadados de e-mail dos profissionais.
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
     * @return O resultado da validaÃƒÂ§ÃƒÂ£o contendo status e dados resolvidos ou mensagens de erro.
     */
    public ValidationResult validate(ContaAzulClient.SaleItem sale) {
        if (sale == null) {
            return new ValidationResult(false, null, null, null, "NULL_SALE", "Item de venda nulo recebido para validaÃƒÂ§ÃƒÂ£o.");
        }

        // 1. Validar se a parcela possui identificador bÃƒÂ¡sico consistente
        if (!StringUtils.hasText(sale.parcelaId())) {
            return new ValidationResult(false, null, null, null, "MISSING_PARCELA_ID", "A parcela nÃƒÂ£o possui parcelaId.");
        }

        // 2. Validar e normalizar o customerUuid
        String customerUuid = normalizeUuid(sale.customerUuid());
        if (!StringUtils.hasText(customerUuid)) {
            return new ValidationResult(false, null, null, null, "CUSTOMER_UUID_MISSING", "O recibo nÃƒÂ£o possui customer UUID vÃƒÂ¡lido.");
        }

        // 3. Localizar o mapeamento do mÃƒÂ©dico pelo UUID
        Optional<DoctorEmailMapping> mappingOpt = findDoctorMappingByCustomerUuid(customerUuid);
        if (mappingOpt.isEmpty()) {
            String errorMsg = String.format("Cadastro faltando para o mÃƒÂ©dico: %s | UUID API='%s' | UUID normalizado='%s'",
                    StringUtils.hasText(sale.customerName()) ? sale.customerName() : "(nome indisponÃƒÂ­vel)",
                    sale.customerUuid(),
                    customerUuid);
            return new ValidationResult(false, customerUuid, null, null, "MAPPING_NOT_FOUND", errorMsg);
        }

        DoctorEmailMapping mapping = mappingOpt.get();

        // 4. Resolver o e-mail de destino (direto ou do usuÃƒÂ¡rio associado) e validar sua presenÃƒÂ§a
        String recipientEmail = resolveRecipientEmail(mapping);
        if (!StringUtils.hasText(recipientEmail)) {
            String errorMsg = String.format("Mapeamento sem e-mail de destino (user/fallback) para customer UUID %s.", customerUuid);
            return new ValidationResult(false, customerUuid, mapping, null, "EMAIL_MISSING", errorMsg);
        }

        return new ValidationResult(true, customerUuid, mapping, recipientEmail, null, null);
    }

    /**
     * Resolve o e-mail de destino do mapeamento do mÃƒÂ©dico.
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
     * Resolve o nome amigÃƒÂ¡vel do mÃƒÂ©dico com base no mapeamento e nome do cliente da API.
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
     * Localiza o mapeamento de mÃƒÂ©dico pelo customer UUID normalizado ou direto.
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
     * Normaliza UUID removendo espaÃƒÂ§os e convertendo para minÃƒÂºsculas.
     */
    public String normalizeUuid(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String normalized = value.replaceAll("\\s+", "").toLowerCase();
        return StringUtils.hasText(normalized) ? normalized : null;
    }

    /**
     * Registro com os resultados detalhados da validaÃƒÂ§ÃƒÂ£o de uma parcela de recibo.
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


