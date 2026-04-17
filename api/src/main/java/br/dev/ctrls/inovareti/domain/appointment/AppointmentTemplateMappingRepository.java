package br.dev.ctrls.inovareti.domain.appointment;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AppointmentTemplateMappingRepository extends JpaRepository<AppointmentTemplateMapping, UUID> {

    List<AppointmentTemplateMapping> findByTemplateNameOrderByPlaceholderIndexAsc(String templateName);

    List<AppointmentTemplateMapping> findByTemplateNameIgnoreCaseOrderByPlaceholderIndexAsc(String templateName);

    void deleteAllByTemplateName(String templateName);
}