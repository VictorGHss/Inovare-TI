package br.dev.ctrls.inovareti.modules.asset.application.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import br.dev.ctrls.inovareti.modules.asset.domain.model.AssetMaintenance;
import jakarta.validation.constraints.NotNull;

public record AssetMaintenanceRequestDTO(

        @NotNull(message = "Maintenance date is required.")
        LocalDate maintenanceDate,

        @NotNull(message = "Maintenance type is required.")
        AssetMaintenance.MaintenanceType type,

        String description,

        BigDecimal cost,

        UUID ticketId

) {}
