package br.dev.ctrls.inovareti.modules.appointment.infrastructure.adapter.output.jpa.adapter;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import br.dev.ctrls.inovareti.modules.appointment.domain.model.AppointmentTemplateMapping;
import br.dev.ctrls.inovareti.modules.appointment.domain.port.output.AppointmentTemplateMappingRepositoryPort;
import br.dev.ctrls.inovareti.modules.appointment.infrastructure.adapter.output.jpa.entity.AppointmentTemplateMappingEntity;
import br.dev.ctrls.inovareti.modules.appointment.infrastructure.adapter.output.jpa.repository.SpringDataAppointmentTemplateMappingRepository;
import lombok.RequiredArgsConstructor;

/**
 * Este é um Adaptador de Saída que implementa a Porta de Repositório do Domínio 
 * para fazer a ponte com o Spring Data JPA para os mapeamentos de templates de agendamento.
 */
@Component
@RequiredArgsConstructor
public class AppointmentTemplateMappingRepositoryAdapter implements AppointmentTemplateMappingRepositoryPort {

    private final SpringDataAppointmentTemplateMappingRepository springDataRepository;

    @Override
    public List<AppointmentTemplateMapping> findByTemplateNameIgnoreCaseOrderByPlaceholderIndexAsc(String name) {
        return springDataRepository.findByTemplateNameIgnoreCaseOrderByPlaceholderIndexAsc(name).stream()
                .map(AppointmentTemplateMappingEntity::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public List<AppointmentTemplateMapping> findByTemplateNameOrderByPlaceholderIndexAsc(String templateName) {
        return springDataRepository.findByTemplateNameOrderByPlaceholderIndexAsc(templateName).stream()
                .map(AppointmentTemplateMappingEntity::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void deleteAllByTemplateName(String templateName) {
        springDataRepository.deleteAllByTemplateName(templateName);
    }

    @Override
    @Transactional
    public void deleteAll(Collection<AppointmentTemplateMapping> mappings) {
        List<AppointmentTemplateMappingEntity> entities = mappings.stream()
                .map(AppointmentTemplateMappingEntity::fromDomain)
                .collect(Collectors.toList());
        springDataRepository.deleteAll(entities);
    }

    @Override
    public List<AppointmentTemplateMapping> findAll() {
        return springDataRepository.findAll().stream()
                .map(AppointmentTemplateMappingEntity::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public AppointmentTemplateMapping save(AppointmentTemplateMapping mapping) {
        AppointmentTemplateMappingEntity entity = AppointmentTemplateMappingEntity.fromDomain(mapping);
        AppointmentTemplateMappingEntity saved = springDataRepository.save(entity);
        return saved.toDomain();
    }

    @Override
    @Transactional
    public void saveAll(List<AppointmentTemplateMapping> mappings) {
        List<AppointmentTemplateMappingEntity> entities = mappings.stream()
                .map(AppointmentTemplateMappingEntity::fromDomain)
                .collect(Collectors.toList());
        springDataRepository.saveAll(entities);
    }
}
