package br.dev.ctrls.inovareti.modules.appointment.infrastructure.adapter.output.jpa.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import br.dev.ctrls.inovareti.modules.appointment.domain.model.AppointmentCategory;
import br.dev.ctrls.inovareti.modules.appointment.infrastructure.adapter.output.jpa.entity.AppointmentTemplateMappingEntity;

public interface SpringDataAppointmentTemplateMappingRepository extends JpaRepository<AppointmentTemplateMappingEntity, UUID> {

    List<AppointmentTemplateMappingEntity> findByCategory(AppointmentCategory category);

    List<AppointmentTemplateMappingEntity> findByTemplateNameIgnoreCaseOrderByPlaceholderIndexAsc(String templateName);

    List<AppointmentTemplateMappingEntity> findByTemplateNameOrderByPlaceholderIndexAsc(String templateName);

    @Transactional
    void deleteAllByTemplateName(String templateName);
}