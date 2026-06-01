package br.dev.ctrls.inovareti.modules.finance.application.service;

import br.dev.ctrls.inovareti.modules.finance.application.service.InternalReceiptService;
import br.dev.ctrls.inovareti.modules.finance.infrastructure.adapter.output.ContaAzulClient;

import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import br.dev.ctrls.inovareti.modules.finance.domain.model.DoctorEmailMapping;
import br.dev.ctrls.inovareti.modules.finance.domain.port.DoctorEmailMappingRepository;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * ServiÃƒÂ§o de aplicaÃƒÂ§ÃƒÂ£o encarregado pela lÃƒÂ³gica de fallback de emissÃƒÂ£o de recibos.
 * Quando a API do Conta Azul falha em entregar o anexo, aciona a geraÃƒÂ§ÃƒÂ£o interna
 * local do PDF usando metadados e persistÃƒÂªncia de documentos.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InternalReceiptEmissionService {

    private static final String DOCUMENT_FALLBACK_UNDER_REVIEW = "CPF/CNPJ sob consulta";

    private final ContaAzulClient contaAzulClient;
    private final InternalReceiptService internalReceiptService;
    private final DoctorEmailMappingRepository doctorEmailMappingRepository;

    /**
     * Gera o PDF de recibo interno Inovare (fallback) para a baixa informada.
     *
     * @param baixaId Identificador da baixa de pagamento.
     * @param doctorName Nome do mÃƒÂ©dico/profissional.
     * @param mapping O mapeamento de e-mail do mÃƒÂ©dico.
     * @param customerUuidFromSale UUID do cliente na venda.
     * @param saleDescription DescriÃƒÂ§ÃƒÂ£o formatada da venda/parcela.
     * @return O vetor de bytes do PDF gerado.
     */
    public byte[] generateInternalReceiptPdf(
            String baixaId,
            String doctorName,
            DoctorEmailMapping mapping,
            String customerUuidFromSale,
            String saleDescription) {
        JsonNode settlementNode = null;

        try {
            settlementNode = contaAzulClient.getSettlementDetails(baixaId).orElse(null);
        } catch (RuntimeException ex) {
            log.warn("Nao foi possivel obter detalhes da baixa {} para montagem completa do recibo interno.", baixaId, ex);
        }

        String documentForReceipt = resolveDoctorDocumentForReceipt(mapping, customerUuidFromSale, settlementNode, baixaId);

        byte[] pdfBytes = internalReceiptService.generateReceipt(
                settlementNode,
                doctorName,
                documentForReceipt,
                saleDescription);
        if (pdfBytes == null || pdfBytes.length == 0) {
            throw new IllegalStateException("Recibo interno gerado sem conteudo para baixa " + baixaId + ".");
        }

        return pdfBytes;
    }

    /**
     * Resolve o CPF/CNPJ do mÃƒÂ©dico. Tenta buscar no mapping local ou faz a consulta na API da Conta Azul.
     */
    private String resolveDoctorDocumentForReceipt(
            DoctorEmailMapping mapping,
            String customerUuidFromSale,
            JsonNode settlementNode,
            String baixaId) {
        String mappedDocument = mapping != null ? mapping.getDoctorCpfCnpj() : null;
        if (mappedDocument != null) {
            mappedDocument = mappedDocument.trim();
        }
        if (mappedDocument != null && !mappedDocument.isBlank()) {
            return mappedDocument;
        }

        String doctorName = mapping != null ? mapping.getDoctorName() : "(mÃƒÂ©dico nÃƒÂ£o identificado)";
        log.warn("CPF/CNPJ nao encontrado no doctor_email_mapping para o medico {} (baixa {}).", doctorName, baixaId);

        if (StringUtils.hasText(customerUuidFromSale)) {
            Optional<String> apiDocument = contaAzulClient.fetchPersonDocumentById(customerUuidFromSale.trim());
            if (apiDocument.isPresent()) {
                String resolvedDocument = apiDocument.get().trim();
                persistDoctorDocumentOnMapping(mapping, resolvedDocument, customerUuidFromSale, baixaId);
                log.info("Documento obtido via API da pessoa {} para baixa {}.", customerUuidFromSale, baixaId);
                return resolvedDocument;
            }
            log.warn("Nao foi possivel obter documento via API para cliente_id da venda {} (baixa {}).",
                    customerUuidFromSale,
                    baixaId);
        }

        String clientIdFromSettlement = extractClientIdFromSettlement(settlementNode);
        if (StringUtils.hasText(clientIdFromSettlement)) {
            Optional<String> apiDocument = contaAzulClient.fetchPersonDocumentById(clientIdFromSettlement);
            if (apiDocument.isPresent()) {
                String resolvedDocument = apiDocument.get().trim();
                persistDoctorDocumentOnMapping(mapping, resolvedDocument, clientIdFromSettlement, baixaId);
                log.info("Documento obtido via API da pessoa {} para baixa {}.", clientIdFromSettlement, baixaId);
                return resolvedDocument;
            }

            log.warn("Nao foi possivel obter documento via API para cliente_id {} (baixa {}). Usando fallback.",
                    clientIdFromSettlement,
                    baixaId);
        } else {
            log.warn("cliente_id nao encontrado no JSON da baixa {}. Mantendo documento em fallback interno.", baixaId);
        }

        return DOCUMENT_FALLBACK_UNDER_REVIEW;
    }

    /**
     * Persiste o CPF/CNPJ resolvido no mapeamento de mÃƒÂ©dico existente no banco.
     */
    private void persistDoctorDocumentOnMapping(
            DoctorEmailMapping mapping,
            String doctorDocument,
            String clientId,
            String baixaId) {
        if (mapping == null || !StringUtils.hasText(doctorDocument)) {
            return;
        }

        if (StringUtils.hasText(mapping.getDoctorCpfCnpj())
                && doctorDocument.trim().equalsIgnoreCase(mapping.getDoctorCpfCnpj().trim())) {
            return;
        }

        try {
            mapping.setDoctorCpfCnpj(doctorDocument.trim());
            doctorEmailMappingRepository.save(mapping);
            log.info("CPF/CNPJ atualizado no doctor_email_mapping via API da pessoa {} (baixa {}).", clientId, baixaId);
        } catch (RuntimeException ex) {
            log.warn("Falha ao persistir CPF/CNPJ no doctor_email_mapping para cliente_id {}.", clientId, ex);
        }
    }

    /**
     * Extrai o identificador do cliente a partir do JSON de baixa.
     */
    private String extractClientIdFromSettlement(JsonNode settlementNode) {
        if (settlementNode == null || settlementNode.isNull()) {
            return null;
        }

        String clientId = null;
        if (settlementNode.path("cliente_id").isValueNode()) {
            clientId = settlementNode.path("cliente_id").asText();
        }

        if (!StringUtils.hasText(clientId) && settlementNode.path("cliente").path("id").isValueNode()) {
            clientId = settlementNode.path("cliente").path("id").asText();
        }

        if (!StringUtils.hasText(clientId) && settlementNode.path("customer").path("id").isValueNode()) {
            clientId = settlementNode.path("customer").path("id").asText();
        }

        if (!StringUtils.hasText(clientId) && settlementNode.path("pessoa").path("id").isValueNode()) {
            clientId = settlementNode.path("pessoa").path("id").asText();
        }

        if (clientId == null || clientId.isBlank()) {
            return null;
        }

        return clientId.trim();
    }
}


