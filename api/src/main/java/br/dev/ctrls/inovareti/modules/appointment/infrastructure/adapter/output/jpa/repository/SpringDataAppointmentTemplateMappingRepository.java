package br.dev.ctrls.inovareti.modules.appointment.infrastructure.adapter.output.jpa.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import br.dev.ctrls.inovareti.modules.appointment.domain.model.AppointmentCategory;
import br.dev.ctrls.inovareti.modules.appointment.infrastructure.adapter.output.jpa.entity.AppointmentTemplateMappingEntity;

public interface SpringDataAppointmentTemplateMappingRepository extends JpaRepository<AppointmentTemplateMappingEntity, UUID> {

    @Query("SELECT m FROM AppointmentTemplateMappingEntity m " +
           "WHERE m.templateName = (SELECT c.templateId FROM AppointmentConfigEntity c WHERE c.category = :category) " +
           "ORDER BY m.placeholderIndex ASC")
    List<AppointmentTemplateMappingEntity> findByCategory(@Param("category") AppointmentCategory category);

    List<AppointmentTemplateMappingEntity> findByTemplateNameIgnoreCaseOrderByPlaceholderIndexAsc(String templateName);

    List<AppointmentTemplateMappingEntity> findByTemplateNameOrderByPlaceholderIndexAsc(String templateName);

    @Transactional
    void deleteAllByTemplateName(String templateName);
}