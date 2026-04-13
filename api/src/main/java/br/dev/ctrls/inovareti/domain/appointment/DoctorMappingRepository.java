package br.dev.ctrls.inovareti.domain.appointment;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface DoctorMappingRepository extends JpaRepository<DoctorMapping, UUID> {

    Optional<DoctorMapping> findByProfissionalId(String profissionalId);
}
