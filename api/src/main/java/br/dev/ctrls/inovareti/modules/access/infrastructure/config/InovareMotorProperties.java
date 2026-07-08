package br.dev.ctrls.inovareti.modules.access.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

/**
 * Propriedades de configuração do motor de agendamentos e controle de acesso integrado.
 * Comentários em PT-BR como requerido pelas Regras de Ouro.
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "inovare.motor")
public class InovareMotorProperties {

    /**
     * Flag indicando se o motor de agendamentos/acesso está operando em modo de teste.
     */
    private boolean testMode;

    /**
     * Lista de IDs de médicos elegíveis em ambiente de produção.
     */
    private List<Long> prodDoctorIds;

    /**
     * Lista estrita de IDs de médicos permitidos para testes manuais.
     */
    private List<Long> testDoctorIds;
}
