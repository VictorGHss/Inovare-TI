package br.dev.ctrls.inovareti.domain.appointment;

import java.util.Optional;

import org.springframework.stereotype.Repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.NoResultException;
import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class AppointmentDoctorMappingRepositoryCustomImpl implements AppointmentDoctorMappingRepositoryCustom {

    private final EntityManager entityManager;

    @Override
    public Optional<AppointmentDoctorMapping> findByProfissionalIdLocked(String profissionalId) {
        try {
            AppointmentDoctorMapping mapping = entityManager.createQuery(
                    "SELECT a FROM AppointmentDoctorMapping a WHERE a.profissionalId = :pid", 
                    AppointmentDoctorMapping.class)
                .setParameter("pid", profissionalId)
                .setLockMode(LockModeType.PESSIMISTIC_READ)
                .getSingleResult();
            return Optional.of(mapping);
        } catch (NoResultException e) {
            return Optional.empty();
        }
    }
}
