package br.dev.ctrls.inovareti.modules.asset.application.service;

import io.micrometer.observation.annotation.Observed;

import br.dev.ctrls.inovareti.modules.asset.domain.model.AssetMaintenance;
import br.dev.ctrls.inovareti.modules.asset.domain.model.Asset;

import br.dev.ctrls.inovareti.modules.asset.domain.port.output.AssetRepositoryPort;

import br.dev.ctrls.inovareti.modules.asset.domain.port.output.AssetMaintenanceRepositoryPort;


import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;

import br.dev.ctrls.inovareti.core.shared.domain.model.exception.NotFoundException;
import br.dev.ctrls.inovareti.modules.asset.application.dto.AssetMaintenanceRequestDTO;
import br.dev.ctrls.inovareti.modules.asset.application.dto.AssetMaintenanceResponseDTO;
import br.dev.ctrls.inovareti.modules.finance.domain.model.FinancialTransaction;
import br.dev.ctrls.inovareti.modules.finance.domain.port.FinancialTransactionRepository;
import br.dev.ctrls.inovareti.modules.finance.domain.port.FinancialLinkRepository;
import br.dev.ctrls.inovareti.modules.user.domain.model.User;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Observed
public class AssetMaintenanceService {

    private final AssetMaintenanceRepositoryPort maintenanceRepository;
    private final AssetRepositoryPort assetRepository;
    private final FinancialTransactionRepository financialTransactionRepository;
    private final FinancialLinkRepository financialLinkRepository;
    private final br.dev.ctrls.inovareti.modules.ticket.domain.port.output.TicketRepositoryPort ticketRepository;

    public AssetMaintenanceResponseDTO create(UUID assetId, AssetMaintenanceRequestDTO request, User technician) {
        Asset asset = assetRepository.findById(assetId)
                .orElseThrow(() -> new NotFoundException("Asset not found with id: " + assetId));

        br.dev.ctrls.inovareti.modules.ticket.domain.model.Ticket ticket = null;
        if (request.ticketId() != null) {
            ticket = ticketRepository.findById(request.ticketId())
                    .orElseThrow(() -> new NotFoundException("Ticket not found with id: " + request.ticketId()));
        }

        AssetMaintenance maintenance = AssetMaintenance.builder()
                .asset(asset)
                .maintenanceDate(request.maintenanceDate())
                .type(request.type())
                .description(request.description())
                .cost(request.cost())
                .technician(technician)
                .ticket(ticket)
                .build();

        AssetMaintenance savedMaintenance = maintenanceRepository.save(maintenance);

        // Se a manutenção tiver custo maior que zero, lança o débito financeiro correspondente
        if (request.cost() != null && request.cost().compareTo(BigDecimal.ZERO) > 0) {
            FinancialTransaction.TargetType targetType = null;
            UUID targetId = null;

            if (asset.getUsers() != null && !asset.getUsers().isEmpty()) {
                User user = asset.getUsers().iterator().next();
                if (user.getContaAzulId() != null && financialLinkRepository.findByContaAzulCustomerId(user.getContaAzulId()).isPresent()) {
                    targetType = FinancialTransaction.TargetType.DOCTOR;
                    targetId = user.getId();
                } else if (user.getSector() != null) {
                    targetType = FinancialTransaction.TargetType.SECTOR;
                    targetId = user.getSector().getId();
                }
            }

            // Fallback: se o ativo não tiver colaboradores ou se o colaborador não tiver setor associado, usa o setor do técnico
            if (targetId == null && technician != null && technician.getSector() != null) {
                targetType = FinancialTransaction.TargetType.SECTOR;
                targetId = technician.getSector().getId();
            }

            if (targetId != null) {
                FinancialTransaction tx = FinancialTransaction.builder()
                        .targetType(targetType)
                        .targetId(targetId)
                        .resourceType(FinancialTransaction.ResourceType.ASSET)
                        .amount(request.cost())
                        .build();
                financialTransactionRepository.save(tx);
            }
        }

        return AssetMaintenanceResponseDTO.from(savedMaintenance);
    }

    public List<AssetMaintenanceResponseDTO> getByAssetId(UUID assetId) {
        // Valida se o ativo existe
        if (!assetRepository.existsById(assetId)) {
            throw new NotFoundException("Asset not found with id: " + assetId);
        }

        return maintenanceRepository.findByAssetIdOrderByMaintenanceDateDesc(assetId)
                .stream()
                .map(AssetMaintenanceResponseDTO::from)
                .toList();
    }

    public void createTransferLog(Asset asset, User oldUser, User newUser, String reason, User technician) {
        String oldUserName = oldUser != null ? oldUser.getName() : "Estoque da TI";
        String newUserName = newUser != null ? newUser.getName() : "Estoque da TI";

        String description = newUser != null
                ? String.format("Ativo transferido de %s para %s. Motivo: %s", oldUserName, newUserName, reason)
                : String.format("Ativo desvinculado de %s e retornado ao estoque. Motivo: %s", oldUserName, reason);

        AssetMaintenance maintenance = AssetMaintenance.builder()
                .asset(asset)
                .maintenanceDate(LocalDate.now())
                .type(AssetMaintenance.MaintenanceType.TRANSFER)
                .description(description)
                .cost(BigDecimal.ZERO)
                .technician(technician)
                .build();

        maintenanceRepository.save(maintenance);
    }
}


