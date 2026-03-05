package br.dev.ctrls.inovareti.domain.asset.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import br.dev.ctrls.inovareti.domain.asset.AssetMaintenance;
import jakarta.validation.constraints.NotNull;

public record AssetMaintenanceRequestDTO(

        @NotNull(message = "Maintenance date is required.")
        LocalDate maintenanceDate,

        @NotNull(message = "Maintenance type is required.")
        AssetMaintenance.MaintenanceType type,

        String description,

        BigDecimal cost

) {}
