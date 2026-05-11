package br.dev.ctrls.inovareti.domain.appointment;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AppointmentDoctorMappingRepository extends JpaRepository<AppointmentDoctorMapping, UUID>, AppointmentDoctorMappingRepositoryCustom {

    Optional<AppointmentDoctorMapping> findByProfissionalId(String profissionalId);
}