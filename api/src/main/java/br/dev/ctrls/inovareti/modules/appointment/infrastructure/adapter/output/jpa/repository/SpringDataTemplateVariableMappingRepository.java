package br.dev.ctrls.inovareti.modules.appointment.infrastructure.adapter.output.jpa.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import br.dev.ctrls.inovareti.modules.appointment.domain.model.AppointmentCategory;
import br.dev.ctrls.inovareti.modules.appointment.infrastructure.adapter.output.jpa.entity.TemplateVariableMappingEntity;

public interface SpringDataTemplateVariableMappingRepository extends JpaRepository<TemplateVariableMappingEntity, UUID> {

    @Query("SELECT e FROM TemplateVariableMappingEntity e WHERE e.config.category = :category ORDER BY e.placeholderIndex ASC")
    List<TemplateVariableMappingEntity> findByCategory(@Param("category") AppointmentCategory category);

    List<TemplateVariableMappingEntity> findByConfigCategoryOrderByPlaceholderIndexAsc(AppointmentCategory category);
}