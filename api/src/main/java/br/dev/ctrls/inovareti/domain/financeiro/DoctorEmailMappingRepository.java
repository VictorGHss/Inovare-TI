package br.dev.ctrls.inovareti.domain.financeiro;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface DoctorEmailMappingRepository extends JpaRepository<DoctorEmailMapping, UUID> {

    Optional<DoctorEmailMapping> findByContaAzulCustomerUuid(String contaAzulCustomerUuid);
}