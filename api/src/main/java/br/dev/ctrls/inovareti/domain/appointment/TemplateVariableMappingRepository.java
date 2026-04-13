package br.dev.ctrls.inovareti.domain.appointment;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TemplateVariableMappingRepository extends JpaRepository<TemplateVariableMapping, UUID> {

    List<TemplateVariableMapping> findByConfigCategoryOrderByPlaceholderIndexAsc(AppointmentCategory category);
}
