package br.dev.ctrls.inovareti.modules.settings.domain.port.output;

import java.util.List;
import java.util.Optional;
import br.dev.ctrls.inovareti.modules.settings.domain.model.SystemSetting;

/**
 * Porta de saída que define os métodos de consulta e persistência para configurações do sistema (SystemSetting).
 */
public interface SystemSettingRepositoryPort {
    /**
     * Retorna todas as configurações ordenadas pelo identificador em ordem ascendente.
     */
    List<SystemSetting> findAllByOrderByIdAsc();

    /**
     * Busca uma configuração específica por seu identificador.
     */
    Optional<SystemSetting> findById(String id);

    /**
     * Salva ou atualiza uma configuração do sistema.
     */
    SystemSetting save(SystemSetting systemSetting);
}
