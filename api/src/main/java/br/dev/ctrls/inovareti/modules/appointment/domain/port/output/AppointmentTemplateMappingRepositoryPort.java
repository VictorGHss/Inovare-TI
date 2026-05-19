package br.dev.ctrls.inovareti.modules.appointment.domain.port.output;

import java.util.Collection;
import java.util.List;

import br.dev.ctrls.inovareti.modules.appointment.domain.model.AppointmentTemplateMapping;

public interface AppointmentTemplateMappingRepositoryPort {

    List<AppointmentTemplateMapping> findByTemplateNameIgnoreCaseOrderByPlaceholderIndexAsc(String name);

    List<AppointmentTemplateMapping> findByTemplateNameOrderByPlaceholderIndexAsc(String templateName);

    void deleteAllByTemplateName(String templateName);

    void deleteAll(Collection<AppointmentTemplateMapping> mappings);

    List<AppointmentTemplateMapping> findAll();

    AppointmentTemplateMapping save(AppointmentTemplateMapping mapping);

    void saveAll(List<AppointmentTemplateMapping> mappings);
}
