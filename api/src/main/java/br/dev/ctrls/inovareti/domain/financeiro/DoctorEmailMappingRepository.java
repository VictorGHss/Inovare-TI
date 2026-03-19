package br.dev.ctrls.inovareti.domain.financeiro;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DoctorEmailMappingRepository extends JpaRepository<DoctorEmailMapping, UUID> {

    @EntityGraph(attributePaths = "user")
    Optional<DoctorEmailMapping> findByContaAzulCustomerUuid(String contaAzulCustomerUuid);

    @EntityGraph(attributePaths = "user")
    @Query("""
            select m
            from DoctorEmailMapping m
            left join fetch m.user
            where lower(trim(m.contaAzulCustomerUuid)) = lower(trim(:customerUuid))
            """)
    Optional<DoctorEmailMapping> findByContaAzulCustomerUuidNormalized(@Param("customerUuid") String customerUuid);

    @EntityGraph(attributePaths = "user")
    java.util.List<DoctorEmailMapping> findAllByOrderByDoctorNameAsc();
}