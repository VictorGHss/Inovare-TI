package br.dev.ctrls.inovareti.config;

import java.util.concurrent.Executors;

import org.springframework.boot.autoconfigure.task.TaskExecutionAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.core.task.support.TaskExecutorAdapter;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Configuração de Processamento Assíncrono com suporte a Virtual Threads (Java 21).
 * Esta configuração garante que os métodos anotados com @Async sejam executados
 * utilizando Virtual Threads, evitando o bloqueio de threads de plataforma do Sistema Operacional.
 */
@Configuration
@EnableAsync
public class AsyncConfiguration {

    /**
     * Redefine o executor padrão de tarefas assíncronas do Spring para utilizar
     * um pool baseado em Virtual Threads (uma thread virtual por tarefa).
     */
    @Bean(name = TaskExecutionAutoConfiguration.APPLICATION_TASK_EXECUTOR_BEAN_NAME)
    @Primary
    public AsyncTaskExecutor applicationTaskExecutor() {
        // TaskExecutorAdapter adapta um Executor padrão do Java para a interface AsyncTaskExecutor do Spring.
        // Executors.newVirtualThreadPerTaskExecutor() cria um executor que inicia uma nova Virtual Thread para cada tarefa.
        return new TaskExecutorAdapter(Executors.newVirtualThreadPerTaskExecutor());
    }
}
