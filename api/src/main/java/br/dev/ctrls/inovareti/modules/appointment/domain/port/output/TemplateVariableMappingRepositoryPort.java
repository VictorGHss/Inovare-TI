package br.dev.ctrls.inovareti.modules.appointment.domain.port.output;
import br.dev.ctrls.inovareti.modules.appointment.domain.port.output.TemplateVariableMappingRepositoryPort;


import java.util.List;

import br.dev.ctrls.inovareti.modules.appointment.domain.model.AppointmentCategory;
import br.dev.ctrls.inovareti.modules.appointment.domain.model.TemplateVariableMapping;

public interface TemplateVariableMappingRepositoryPort {

    List<TemplateVariableMapping> findByConfigCategoryOrderByPlaceholderIndexAsc(AppointmentCategory category);

    TemplateVariableMapping save(TemplateVariableMapping mapping);

    void deleteAll(Iterable<? extends TemplateVariableMapping> entities);
}
