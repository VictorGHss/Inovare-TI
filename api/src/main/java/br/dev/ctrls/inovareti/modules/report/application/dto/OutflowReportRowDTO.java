package br.dev.ctrls.inovareti.modules.report.application.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * DTO unificado para representar uma linha de relatório de saídas e manutenções (Contrato de 8 colunas).
 */
public record OutflowReportRowDTO(
    String itemType,
    String item,
    Integer quantity,
    String requester,
    String userLocation,
    String userSector,
    BigDecimal totalPrice,
    LocalDateTime deliveryDate
) {}
