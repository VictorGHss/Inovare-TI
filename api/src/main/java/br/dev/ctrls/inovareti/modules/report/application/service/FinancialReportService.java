package br.dev.ctrls.inovareti.modules.report.application.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.dev.ctrls.inovareti.modules.report.application.dto.SectorFinancialReportDTO;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Serviço responsável por gerar os relatórios de rateio financeiro de TI por Setor (Centro de Custo).
 * Aplica a regra de negócio da Clínica Inovare: divisão igualitária dos custos operacionais (manutenções e consumíveis)
 * entre a quantidade de médicos ativos associados ao setor correspondente.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FinancialReportService {

    @PersistenceContext
    private final EntityManager entityManager;

    /**
     * Gera a consolidação financeira de gastos de TI no período, agrupados e rateados por Setor.
     *
     * @param startDate Data inicial do intervalo de pesquisa.
     * @param endDate   Data final do intervalo de pesquisa.
     * @return Lista com a agregação e rateio de cada setor no período.
     */
    @Transactional(readOnly = true)
    @SuppressWarnings("unchecked")
    public List<SectorFinancialReportDTO> generateReport(LocalDate startDate, LocalDate endDate) {
        log.info("[FINANCE-REPORT] Iniciando consolidação e rateio financeiro de TI no período de {} até {}", startDate, endDate);

        // 1. Busca todos os setores e a respectiva contagem de médicos ativos vinculados (appointment_doctor_mapping)
        String sectorsQuery = 
            "SELECT CAST(s.id AS VARCHAR) as sector_id, s.name as sector_name, COUNT(adm.id) as active_doctors " +
            "FROM sectors s " +
            "LEFT JOIN users u ON u.sector_id = s.id AND u.active = true " +
            "LEFT JOIN appointment_doctor_mapping adm ON adm.itsm_user_id = u.id " +
            "GROUP BY s.id, s.name";

        List<Object[]> sectorsRaw = entityManager.createNativeQuery(sectorsQuery).getResultList();

        // Mapeamento local temporário para a consolidação
        Map<UUID, SectorData> sectorsMap = new HashMap<>();
        for (Object[] row : sectorsRaw) {
            UUID id = UUID.fromString((String) row[0]);
            String name = (String) row[1];
            long doctorsCount = ((Number) row[2]).longValue();
            sectorsMap.put(id, new SectorData(id, name, doctorsCount));
        }

        // 2. Agrega os custos de manutenção física (CMDB) por setor
        String maintenanceQuery = 
            "SELECT CAST(s.id AS VARCHAR) as sector_id, COALESCE(SUM(am.cost), 0) as total_maintenance " +
            "FROM asset_maintenances am " +
            "JOIN assets a ON am.asset_id = a.id " +
            "JOIN asset_users au ON a.id = au.asset_id " +
            "JOIN users u ON au.user_id = u.id " +
            "JOIN sectors s ON u.sector_id = s.id " +
            "WHERE am.maintenance_date BETWEEN :start AND :end " +
            "GROUP BY s.id";

        List<Object[]> maintenanceRaw = entityManager.createNativeQuery(maintenanceQuery)
                .setParameter("start", startDate)
                .setParameter("end", endDate)
                .getResultList();

        for (Object[] row : maintenanceRaw) {
            UUID id = UUID.fromString((String) row[0]);
            BigDecimal cost = new BigDecimal(row[1].toString());
            SectorData data = sectorsMap.get(id);
            if (data != null) {
                data.rawMaintenance = cost;
            }
        }

        // 3. Agrega os custos de consumo de insumos (baixo físico FIFO) por setor
        LocalDateTime startDateTime = startDate.atStartOfDay();
        LocalDateTime endDateTime = endDate.atTime(LocalTime.MAX);

        String consumableQuery = 
            "SELECT CAST(s.id AS VARCHAR) as sector_id, COALESCE(SUM(sm.quantity * sm.unit_price_at_time), 0) as total_consumable " +
            "FROM stock_movements sm " +
            "JOIN users u ON sm.recipient_user_id = u.id " +
            "JOIN sectors s ON u.sector_id = s.id " +
            "WHERE sm.type = 'OUT' AND sm.date BETWEEN :start AND :end " +
            "GROUP BY s.id";

        List<Object[]> consumableRaw = entityManager.createNativeQuery(consumableQuery)
                .setParameter("start", startDateTime)
                .setParameter("end", endDateTime)
                .getResultList();

        for (Object[] row : consumableRaw) {
            UUID id = UUID.fromString((String) row[0]);
            BigDecimal cost = new BigDecimal(row[1].toString());
            SectorData data = sectorsMap.get(id);
            if (data != null) {
                data.rawConsumable = cost;
            }
        }

        // 4. Aplica a regra de rateio e calcula o valor final
        List<SectorFinancialReportDTO> report = new ArrayList<>();
        for (SectorData data : sectorsMap.values()) {
            long doctors = data.doctorsCount;
            
            // Verificação Defensiva contra Divisão por Zero (ArithmeticException):
            // Se o setor não tiver médicos cadastrados ativos no mapeamento, o rateio cai
            // em fallback e atribui 100% do custo bruto (divisão por 1).
            BigDecimal divisor = doctors > 0 ? BigDecimal.valueOf(doctors) : BigDecimal.ONE;

            BigDecimal rateatedMaintenance = data.rawMaintenance.divide(divisor, 2, RoundingMode.HALF_UP);
            BigDecimal rateatedConsumable = data.rawConsumable.divide(divisor, 2, RoundingMode.HALF_UP);
            BigDecimal totalRateated = rateatedMaintenance.add(rateatedConsumable);

            report.add(new SectorFinancialReportDTO(
                data.id,
                data.name,
                rateatedMaintenance,
                rateatedConsumable,
                totalRateated,
                doctors,
                data.rawMaintenance,
                data.rawConsumable
            ));
        }

        log.info("[FINANCE-REPORT] Relatório financeiro por setor gerado com sucesso. Setores analisados: {}", report.size());
        return report;
    }

    /**
     * Classe utilitária estática para agregação temporária dos custos brutos.
     */
    private static class SectorData {
        final UUID id;
        final String name;
        final long doctorsCount;
        BigDecimal rawMaintenance = BigDecimal.ZERO;
        BigDecimal rawConsumable = BigDecimal.ZERO;

        SectorData(UUID id, String name, long doctorsCount) {
            this.id = id;
            this.name = name;
            this.doctorsCount = doctorsCount;
        }
    }
}
