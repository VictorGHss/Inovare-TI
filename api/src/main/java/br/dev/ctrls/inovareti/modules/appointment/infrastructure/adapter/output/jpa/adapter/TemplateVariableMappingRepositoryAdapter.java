package br.dev.ctrls.inovareti.modules.appointment.infrastructure.adapter.output.jpa.adapter;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import br.dev.ctrls.inovareti.modules.appointment.domain.model.AppointmentCategory;
import br.dev.ctrls.inovareti.modules.appointment.domain.model.TemplateVariableMapping;
import br.dev.ctrls.inovareti.modules.appointment.domain.port.output.TemplateVariableMappingRepositoryPort;
import br.dev.ctrls.inovareti.modules.appointment.infrastructure.adapter.output.jpa.entity.TemplateVariableMappingEntity;
import br.dev.ctrls.inovareti.modules.appointment.infrastructure.adapter.output.jpa.repository.SpringDataTemplateVariableMappingRepository;
import lombok.RequiredArgsConstructor;

/**
 * Este é um Adaptador de Saída que implementa a Porta de Repositório do Domínio 
 * para fazer a ponte com o Spring Data JPA para as variáveis de templates.
 */
@Component
@RequiredArgsConstructor
public class TemplateVariableMappingRepositoryAdapter implements TemplateVariableMappingRepositoryPort {

    private final SpringDataTemplateVariableMappingRepository springDataRepository;

    @Override
    public List<TemplateVariableMapping> findByConfigCategoryOrderByPlaceholderIndexAsc(AppointmentCategory category) {
        return springDataRepository.findByConfigCategoryOrderByPlaceholderIndexAsc(category).stream()
                .map(entity -> entity.toDomain())
                .collect(Collectors.toList());
    }

    @Override
    public TemplateVariableMapping save(TemplateVariableMapping mapping) {
        TemplateVariableMappingEntity entity = TemplateVariableMappingEntity.fromDomain(mapping);
        TemplateVariableMappingEntity saved = springDataRepository.save(entity);
        return saved.toDomain();
    }

    @Override
    @Transactional
    public void deleteAll(Iterable<? extends TemplateVariableMapping> entities) {
        if (entities == null) return;
        List<TemplateVariableMappingEntity> jpaEntities = StreamSupport.stream(entities.spliterator(), false)
                .map(TemplateVariableMappingEntity::fromDomain)
                .collect(Collectors.toList());
        springDataRepository.deleteAll(jpaEntities);
    }
}
