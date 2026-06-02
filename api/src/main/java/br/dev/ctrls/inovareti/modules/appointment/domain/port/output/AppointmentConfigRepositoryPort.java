package br.dev.ctrls.inovareti.modules.appointment.domain.port.output;
import br.dev.ctrls.inovareti.modules.appointment.domain.port.output.AppointmentConfigRepositoryPort;


import java.util.List;
import java.util.Optional;
import java.util.UUID;

import br.dev.ctrls.inovareti.modules.appointment.domain.model.AppointmentCategory;
import br.dev.ctrls.inovareti.modules.appointment.domain.model.AppointmentConfig;

public interface AppointmentConfigRepositoryPort {

    Optional<AppointmentConfig> findByCategory(AppointmentCategory category);

    Optional<AppointmentConfig> findById(UUID id);

    List<AppointmentConfig> findAll();

    AppointmentConfig save(AppointmentConfig config);
}
