package br.dev.ctrls.inovareti.modules.finance.infrastructure.adapter.output;

import br.dev.ctrls.inovareti.modules.finance.domain.port.ProcessedSaleRepository;

import java.util.concurrent.ConcurrentHashMap;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import br.dev.ctrls.inovareti.modules.finance.domain.model.ProcessedSale;
import br.dev.ctrls.inovareti.modules.finance.domain.port.ProcessedSaleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Componente responsÃƒÆ’Ã‚Â¡vel pelo controle de concorrÃƒÆ’Ã‚Âªncia e locks locais/banco
 * para evitar o processamento duplicado de recibos.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReceiptConcurrencyHandler {

    private final ProcessedSaleRepository processedSaleRepository;
    private final ConcurrentHashMap<String, Boolean> activeLocks = new ConcurrentHashMap<>();

    /**
     * Verifica se o recibo jÃƒÆ’Ã‚Â¡ foi processado anteriormente (persiste no banco).
     */
    public boolean isAlreadyProcessed(String baixaId) {
        if (!StringUtils.hasText(baixaId)) {
            return false;
        }
        return processedSaleRepository.existsBySaleId(baixaId.trim());
    }

    /**
     * Adquire um lock em memÃƒÆ’Ã‚Â³ria para evitar concorrÃƒÆ’Ã‚Âªncia local imediata.
     * Retorna true se o lock foi adquirido com sucesso, false caso contrÃƒÆ’Ã‚Â¡rio.
     */
    public boolean acquireLock(String baixaId) {
        if (!StringUtils.hasText(baixaId)) {
            return false;
        }
        return activeLocks.putIfAbsent(baixaId.trim(), Boolean.TRUE) == null;
    }

    /**
     * Libera o lock em memÃƒÆ’Ã‚Â³ria.
     */
    public void releaseLock(String baixaId) {
        if (StringUtils.hasText(baixaId)) {
            activeLocks.remove(baixaId.trim());
        }
    }

    /**
     * Registra o recibo como processado no banco de dados, tratando possÃƒÆ’Ã‚Â­veis colisÃƒÆ’Ã‚Âµes de concorrÃƒÆ’Ã‚Âªncia.
     */
    public void markAsProcessed(String saleId, String successMessage, String duplicateMessage) {
        if (!StringUtils.hasText(saleId)) {
            return;
        }

        String trimmedId = saleId.trim();
        if (processedSaleRepository.existsBySaleId(trimmedId)) {
            if (StringUtils.hasText(duplicateMessage)) {
                log.debug(duplicateMessage, trimmedId);
            }
            return;
        }

        try {
            processedSaleRepository.save(ProcessedSale.builder().saleId(trimmedId).build());
            if (StringUtils.hasText(successMessage)) {
                log.info(successMessage, trimmedId);
            }
        } catch (DataIntegrityViolationException ex) {
            if (StringUtils.hasText(duplicateMessage)) {
                log.debug(duplicateMessage, trimmedId);
            }
        }
    }
}


