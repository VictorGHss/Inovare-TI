package br.dev.ctrls.inovareti.domain.appointment;

import java.util.Optional;

public interface AppointmentDoctorMappingRepositoryCustom {
    Optional<AppointmentDoctorMapping> findByProfissionalIdLocked(String profissionalId);
}
