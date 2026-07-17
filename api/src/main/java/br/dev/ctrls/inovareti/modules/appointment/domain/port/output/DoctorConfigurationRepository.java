package br.dev.ctrls.inovareti.modules.appointment.domain.port.output;

import java.util.List;
import java.util.Optional;
import br.dev.ctrls.inovareti.modules.appointment.domain.model.DoctorConfiguration;

/**
 * Porta de repositório de domínio para configurações de médicos.
 * Define os métodos padrão de CRUD em conformidade com as regras da arquitetura limpa.
 */
public interface DoctorConfigurationRepository {

    Optional<DoctorConfiguration> findById(Long id);

    List<DoctorConfiguration> findAll();

    DoctorConfiguration save(DoctorConfiguration config);

    void delete(DoctorConfiguration config);

    void deleteById(Long id);
}
