package br.dev.ctrls.inovareti.modules.appointment.infrastructure.adapter.output.jpa.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import br.dev.ctrls.inovareti.modules.appointment.infrastructure.adapter.output.jpa.entity.AppointmentDoctorMappingEntity;

import jakarta.persistence.LockModeType;

public interface SpringDataAppointmentDoctorMappingRepository
        extends JpaRepository<AppointmentDoctorMappingEntity, UUID>,
                SpringDataAppointmentDoctorMappingRepositoryCustom {

    Optional<AppointmentDoctorMappingEntity> findByProfissionalId(String profissionalId);

    @Override
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT m FROM AppointmentDoctorMappingEntity m WHERE m.profissionalId = :profissionalId")
    Optional<AppointmentDoctorMappingEntity> findByProfissionalIdLocked(@Param("profissionalId") String profissionalId);
}