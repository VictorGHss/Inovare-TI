package br.dev.ctrls.inovareti.modules.report.application.dto;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * DTO que representa o relatório de despesas agregadas e rateadas de TI por Setor (Centro de Custo).
 * Retorna os custos de manutenção física e o consumo de insumos, ambos já divididos
 * igualmente entre a quantidade de médicos ativos do consultório/setor.
 */
public record SectorFinancialReportDTO(
    UUID sectorId,
    String sectorName,
    BigDecimal maintenanceCost,      // Custo de manutenção (CMDB) rateado por médicos ativos
    BigDecimal consumableCost,       // Custo de insumos de inventário rateado por médicos ativos
    BigDecimal totalCost,            // Soma dos custos de manutenção e insumos rateados
    long activeDoctorsCount,         // Quantidade de médicos ativos vinculados (para transparência)
    BigDecimal rawMaintenanceCost,   // Custo de manutenção bruto (sem divisão)
    BigDecimal rawConsumableCost     // Custo de insumos bruto (sem divisão)
) {}
