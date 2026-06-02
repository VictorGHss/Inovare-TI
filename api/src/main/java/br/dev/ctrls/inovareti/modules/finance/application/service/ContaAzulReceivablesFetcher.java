package br.dev.ctrls.inovareti.modules.finance.application.service;

import br.dev.ctrls.inovareti.modules.finance.domain.model.ContaAzulStatus;
import br.dev.ctrls.inovareti.modules.finance.domain.model.ReceivableParcelRef;
import br.dev.ctrls.inovareti.modules.finance.domain.model.ReceivedParcelsResult;
import br.dev.ctrls.inovareti.modules.finance.domain.model.ReceivablesPageData;
import br.dev.ctrls.inovareti.modules.finance.infrastructure.adapter.output.ContaAzulRestClientAdapter;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Componente responsável pela listagem paginada de parcelas recebidas/quitadas do Conta Azul.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ContaAzulReceivablesFetcher {

    private static final int SUMMARY_PAGE_SIZE = 100;
    private static final int SUMMARY_MAX_PAGES = 30;
    private static final LocalDate DIAGNOSTIC_PAYMENT_DATE_FROM = LocalDate.of(2026, 1, 1);

    private final ContaAzulRestClientAdapter restClientAdapter;

    /**
     * Consulta parcelas marcadas como RECEBIDO ou QUITADO a partir do Conta Azul no período configurado.
     */
    public ReceivedParcelsResult fetchReceivedParcels(String accessToken) {
        Map<String, ReceivableParcelRef> parcelMap = new java.util.LinkedHashMap<>();
        OffsetDateTime latestUpdate = null;

        LocalDate hoje = LocalDate.now();
        LocalDate inicioMesAtual = hoje.withDayOfMonth(1);
        log.info("Consultando parcelas RECEBIDO no período {} até {}.", inicioMesAtual, hoje);

        latestUpdate = collectReceivedParcelsByStatusAndRange(
            accessToken, ContaAzulStatus.RECEBIDO, inicioMesAtual, hoje, parcelMap, latestUpdate);

        if (parcelMap.isEmpty()) {
            log.warn("Nenhuma parcela RECEBIDO no mês atual. Reexecutando diagnóstico.");
            latestUpdate = collectReceivedParcelsByStatusAndRange(
                accessToken, ContaAzulStatus.RECEBIDO, DIAGNOSTIC_PAYMENT_DATE_FROM, hoje, parcelMap, latestUpdate);
        }

        if (parcelMap.isEmpty()) {
            log.warn("Nenhuma parcela com status RECEBIDO. Tentando fallback com status QUITADO.");
            latestUpdate = collectReceivedParcelsByStatusAndRange(
                accessToken, ContaAzulStatus.QUITADO, inicioMesAtual, hoje, parcelMap, latestUpdate);
        }

        if (parcelMap.isEmpty()) {
            log.warn("Nenhuma parcela QUITADO no mês atual. Reexecutando QUITADO.");
            latestUpdate = collectReceivedParcelsByStatusAndRange(
                accessToken, ContaAzulStatus.QUITADO, DIAGNOSTIC_PAYMENT_DATE_FROM, hoje, parcelMap, latestUpdate);
        }

        return new ReceivedParcelsResult(
            new ArrayList<>(parcelMap.values()),
            true,
            latestUpdate != null ? latestUpdate.toString() : null);
    }

    private OffsetDateTime collectReceivedParcelsByStatusAndRange(
            String accessToken,
            String status,
            LocalDate dataPagamentoDe,
            LocalDate dataPagamentoAte,
            Map<String, ReceivableParcelRef> parcelMap,
            OffsetDateTime latestUpdate) {
        
        for (int page = 1; page <= SUMMARY_MAX_PAGES; page++) {
            ReceivablesPageData pageData = restClientAdapter.fetchReceivablesPageByPaymentDate(
                accessToken, status, page, dataPagamentoDe, dataPagamentoAte);

            if (pageData.latestUpdate() != null
                    && (latestUpdate == null || pageData.latestUpdate().toInstant().isAfter(latestUpdate.toInstant()))) {
                latestUpdate = pageData.latestUpdate();
            }

            if (pageData.parcels().isEmpty()) {
                break;
            }

            for (ReceivableParcelRef currentParcel : pageData.parcels()) {
                parcelMap.merge(
                        currentParcel.parcelaId(),
                        currentParcel,
                    (existing, incoming) -> org.springframework.util.StringUtils.hasText(existing.displayIdentifier()) ? existing : incoming);
            }

            if (pageData.parcels().size() < SUMMARY_PAGE_SIZE) {
                break;
            }
        }
        return latestUpdate;
    }
}
