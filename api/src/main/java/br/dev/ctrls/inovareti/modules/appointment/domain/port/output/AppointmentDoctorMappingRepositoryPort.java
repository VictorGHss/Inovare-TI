package br.dev.ctrls.inovareti.modules.appointment.domain.port.output;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import br.dev.ctrls.inovareti.modules.appointment.domain.model.AppointmentDoctorMapping;

public interface AppointmentDoctorMappingRepositoryPort {

    Optional<AppointmentDoctorMapping> findByProfissionalId(String profissionalId);

    Optional<AppointmentDoctorMapping> findByProfissionalIdLocked(String profissionalId);

    Optional<AppointmentDoctorMapping> findById(UUID id);

    boolean existsById(UUID id);

    List<AppointmentDoctorMapping> findAll();

    AppointmentDoctorMapping save(AppointmentDoctorMapping mapping);

    void delete(AppointmentDoctorMapping mapping);

    void deleteById(UUID id);

    void deleteAll(List<AppointmentDoctorMapping> mappings);
}