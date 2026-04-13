package br.dev.ctrls.inovareti.domain.appointment.usecase;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import br.dev.ctrls.inovareti.core.exception.NotFoundException;
import br.dev.ctrls.inovareti.domain.appointment.AppointmentCategory;
import br.dev.ctrls.inovareti.domain.appointment.AppointmentConfig;
import br.dev.ctrls.inovareti.domain.appointment.AppointmentConfigRepository;
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

    private final AppointmentConfigRepository appointmentConfigRepository;

    /**
     * Atualiza o template_id associado a uma categoria
     * @param category Categoria (CONFIRMATION, NUDGE_1, NUDGE_FINAL)
     * @param templateId ID do template no Blip
     * @return Configuração atualizada
     */
    @Transactional
    public AppointmentConfig execute(AppointmentCategory category, String templateId) {
        AppointmentConfig config = appointmentConfigRepository.findByCategory(category)
                .orElseThrow(() -> new NotFoundException("Configuração não encontrada para categoria " + category));

        config.setTemplateId(templateId);
        AppointmentConfig updated = appointmentConfigRepository.save(config);

        log.info("Configuração de template atualizada. category={}, templateId={}", category, templateId);
        return updated;
    }
}
