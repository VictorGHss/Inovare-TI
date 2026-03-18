package br.dev.ctrls.inovareti.domain.financeiro;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DoctorEmailMappingRepository extends JpaRepository<DoctorEmailMapping, UUID> {

    @EntityGraph(attributePaths = "user")
    Optional<DoctorEmailMapping> findByContaAzulCustomerUuid(String contaAzulCustomerUuid);

    @EntityGraph(attributePaths = "user")
    java.util.List<DoctorEmailMapping> findAllByOrderByDoctorNameAsc();
}