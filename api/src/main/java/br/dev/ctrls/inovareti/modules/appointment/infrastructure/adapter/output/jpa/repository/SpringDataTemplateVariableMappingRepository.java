package br.dev.ctrls.inovareti.modules.appointment.infrastructure.adapter.output.jpa.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import br.dev.ctrls.inovareti.modules.appointment.domain.model.AppointmentCategory;
import br.dev.ctrls.inovareti.modules.appointment.infrastructure.adapter.output.jpa.entity.TemplateVariableMappingEntity;

public interface SpringDataTemplateVariableMappingRepository extends JpaRepository<TemplateVariableMappingEntity, UUID> {

    List<TemplateVariableMappingEntity> findByCategory(AppointmentCategory category);

    List<TemplateVariableMappingEntity> findByConfigCategoryOrderByPlaceholderIndexAsc(AppointmentCategory category);
}