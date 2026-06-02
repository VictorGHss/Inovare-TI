package br.dev.ctrls.inovareti.modules.appointment.application.service;

import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;
import br.dev.ctrls.inovareti.modules.appointment.domain.model.BlipUserIdentityReconciliation;
import br.dev.ctrls.inovareti.modules.appointment.domain.port.output.BlipClientPort;
import br.dev.ctrls.inovareti.modules.appointment.domain.port.output.BlipUserIdentityReconciliationRepositoryPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Serviço responsável por reconciliar identidades do Blip e purificar números telefônicos.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BlipIdentityReconciler {

    private final BlipUserIdentityReconciliationRepositoryPort blipUserIdentityReconciliationRepository;
    private final BlipClientPort blipClientPort;

    /**
     * Resolve e reconcilia a identidade recebida do Blip, buscando no banco local ou na API do Blip.
     */
    public String resolveAndReconcileIdentity(String originalIdentity, String metadataBsuid) {
        if (originalIdentity == null || originalIdentity.isBlank()) {
            return "";
        }
        
        String identity = originalIdentity.trim();
        String localPart = identity;
        if (identity.contains("@")) {
            localPart = identity.substring(0, identity.indexOf('@'));
        }
        
        boolean isGuidOrUser = localPart.matches(".*[a-zA-Z\\-].*");
        if (!isGuidOrUser) {
            return purifyPhoneNumber(identity);
        }
        
        String blipGuid = localPart;
        
        Optional<BlipUserIdentityReconciliation> existing = 
                blipUserIdentityReconciliationRepository.findByBlipGuid(blipGuid);
        if (existing.isPresent()) {
            log.info("[RECONCILIATION] Correspondência de identidade em cache local: GUID={} -> Telefone={}", 
                blipGuid, existing.get().getPhoneNumber());
            return existing.get().getPhoneNumber();
        }
        
        String resolvedPhone = null;
        String bsuid = metadataBsuid != null ? metadataBsuid.trim() : null;
        
        if (bsuid != null && !bsuid.isBlank() && !bsuid.matches(".*[a-zA-Z\\-].*")) {
            resolvedPhone = purifyPhoneNumber(bsuid);
            log.info("[RECONCILIATION] Identidade reconciliada via metadado BSUID: GUID={} -> Telefone={}", 
                blipGuid, resolvedPhone);
        }
        
        if (resolvedPhone == null || resolvedPhone.isBlank()) {
            try {
                Map<String, Object> profileResponse = blipClientPort.getContactProfile(identity);
                if (profileResponse != null && profileResponse.containsKey("resource")) {
                    Object resource = profileResponse.get("resource");
                    if (resource instanceof Map<?, ?> resourceMap) {
                        Object phoneObj = resourceMap.get("phoneNumber");
                        if (phoneObj == null) {
                            phoneObj = resourceMap.get("cellPhoneNumber");
                        }
                        if (phoneObj != null) {
                            resolvedPhone = purifyPhoneNumber(phoneObj.toString());
                            log.info("[RECONCILIATION] Perfil consultado na API do Blip: GUID={} -> Telefone={}", 
                                blipGuid, resolvedPhone);
                        }
                        
                        Object extrasObj = resourceMap.get("extras");
                        if (extrasObj instanceof Map<?, ?> extrasMap) {
                            Object bsuidObj = extrasMap.get("bsuid");
                            if (bsuidObj == null) {
                                bsuidObj = extrasMap.get("wa.bsuid");
                            }
                            if (bsuidObj != null) {
                                bsuid = bsuidObj.toString().trim();
                            }
                        }
                    }
                }
            } catch (Exception ex) {
                log.error("[RECONCILIATION] Falha ao consultar o perfil do contato no Blip para {}: {}", 
                    identity, ex.getMessage(), ex);
            }
        }
        
        if (resolvedPhone != null && !resolvedPhone.isBlank()) {
            try {
                BlipUserIdentityReconciliation newReconciliation = 
                    BlipUserIdentityReconciliation.builder()
                        .blipGuid(blipGuid)
                        .bsuid(bsuid)
                        .phoneNumber(resolvedPhone)
                        .build();
                blipUserIdentityReconciliationRepository.save(newReconciliation);
                log.info("[RECONCILIATION] Novo mapeamento de identidade salvo: GUID={} -> Telefone={} (BSUID={})", 
                    blipGuid, resolvedPhone, bsuid);
            } catch (Exception ex) {
                log.warn("[RECONCILIATION] Falha ao salvar reconciliação no banco local (pode ser inserção concorrente): {}", 
                    ex.getMessage());
            }
            return resolvedPhone;
        }
        
        log.warn("[RECONCILIATION] Não foi possível reconciliar o GUID do WhatsApp {} para nenhum número telefônico.", blipGuid);
        return "";
    }

    /**
     * Purifica o número de telefone removendo formatações desnecessárias.
     */
    public String purifyPhoneNumber(String originalPhone) {
        if (originalPhone == null || originalPhone.isBlank()) {
            return "";
        }
        
        String trimmed = originalPhone.trim();
        if (trimmed.contains("@")) {
            trimmed = trimmed.substring(0, trimmed.indexOf('@')).trim();
        }
        
        String digitsOnly = trimmed.replaceAll("\\D", "");
        if (digitsOnly.isBlank()) {
            return "";
        }
        
        if (digitsOnly.startsWith("55")) {
            return "+" + digitsOnly;
        }
        
        return "+55" + digitsOnly;
    }
}
