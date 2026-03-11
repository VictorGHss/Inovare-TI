package br.dev.ctrls.inovareti.config;

import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FlywayConfig {

    // O initMethod faz a migração rodar no exato momento que o Bean nasce
    @Bean(initMethod = "migrate")
    public Flyway flyway(DataSource dataSource) {
        System.out.println(">>> [FLYWAY] INICIANDO MIGRATIONS ANTES DO HIBERNATE <<<");
        
        return Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .baselineOnMigrate(true)
                .load();
    }
}