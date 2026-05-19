package br.dev.ctrls.inovareti.modules.appointment.infrastructure.adapter.output.jpa.repository;

import java.util.Optional;

import org.springframework.stereotype.Repository;

import br.dev.ctrls.inovareti.modules.appointment.infrastructure.adapter.output.jpa.entity.AppointmentDoctorMappingEntity;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.NoResultException;
import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class SpringDataAppointmentDoctorMappingRepositoryCustomImpl implements SpringDataAppointmentDoctorMappingRepositoryCustom {

    private final EntityManager entityManager;

    @Override
    public Optional<AppointmentDoctorMappingEntity> findByProfissionalIdLocked(String profissionalId) {
        try {
            AppointmentDoctorMappingEntity mapping = entityManager.createQuery(
                    "SELECT a FROM AppointmentDoctorMappingEntity a WHERE a.profissionalId = :pid",
                    AppointmentDoctorMappingEntity.class)
                .setParameter("pid", profissionalId)
                .setLockMode(LockModeType.PESSIMISTIC_READ)
                .getSingleResult();
            return Optional.of(mapping);
        } catch (NoResultException e) {
            return Optional.empty();
        }
    }
}