package br.dev.ctrls.inovareti.modules.appointment.application.usecase;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import br.dev.ctrls.inovareti.modules.appointment.domain.model.AppointmentCategory;
import br.dev.ctrls.inovareti.modules.appointment.domain.model.AppointmentConfig;
import br.dev.ctrls.inovareti.modules.appointment.domain.port.output.AppointmentConfigRepositoryPort;
import br.dev.ctrls.inovareti.modules.appointment.infrastructure.config.AppointmentMotorProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Caso de uso para atualizar configurações de templates de agendamentos
 * Permite associar um template específico do Blip a uma categoria de comunicação
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UpdateAppointmentConfigUseCase {

    private final AppointmentConfigRepositoryPort appointmentConfigRepository;
    private final AppointmentMotorProperties appointmentMotorProperties;

    /**
     * Atualiza o template_id associado a uma categoria
     * @param category Categoria (CONFIRMATION, NUDGE_1, NUDGE_FINAL)
     * @param templateName Nome do template no Blip
     * @return Configuração atualizada
     */
    @Transactional
    public AppointmentConfig execute(AppointmentCategory category, String templateName) {
        if (!StringUtils.hasText(templateName)) {
            throw new IllegalArgumentException("templateName é obrigatório");
        }

        AppointmentConfig config = appointmentConfigRepository.findByCategory(category)
            .orElseGet(() -> AppointmentConfig.builder()
                .category(category)
                .templateId(templateName)
                .timingHours(defaultTimingHours(category))
                .build());

        config.setTemplateId(templateName);
        AppointmentConfig updated = appointmentConfigRepository.save(config);

        log.info("Configuração de template atualizada. category={}, templateName={}", category, templateName);
        return updated;
    }

    private int defaultTimingHours(AppointmentCategory category) {
        return switch (category) {
            case CONFIRMATION -> 0;
            case NUDGE_1 -> appointmentMotorProperties.getNudge1WaitHours();
            case NUDGE_FINAL -> appointmentMotorProperties.getNudgeFinalWaitHours();
        };
    }
}
