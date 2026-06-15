package br.dev.ctrls.inovareti.config;

import java.util.concurrent.Executors;

import org.springframework.boot.autoconfigure.task.TaskExecutionAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.core.task.support.TaskExecutorAdapter;
import org.springframework.scheduling.annotation.EnableAsync;

import br.dev.ctrls.inovareti.infrastructure.shared.config.MdcTaskDecorator;

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
     * um pool baseado em Virtual Threads (uma thread virtual por tarefa),
     * vinculando o MdcTaskDecorator para a propagação de contexto do MDC (como Trace ID).
     *
     * @param mdcTaskDecorator O decorador de tarefas injetado para gerenciar o MDC nas threads.
     * @return O executor de tarefas assíncronas configurado.
     */
    @Bean(name = TaskExecutionAutoConfiguration.APPLICATION_TASK_EXECUTOR_BEAN_NAME)
    @Primary
    public AsyncTaskExecutor applicationTaskExecutor(MdcTaskDecorator mdcTaskDecorator) {
        // TaskExecutorAdapter adapta um Executor padrão do Java para a interface AsyncTaskExecutor do Spring.
        // Executors.newVirtualThreadPerTaskExecutor() cria um executor que inicia uma nova Virtual Thread para cada tarefa.
        TaskExecutorAdapter adapter = new TaskExecutorAdapter(Executors.newVirtualThreadPerTaskExecutor());
        
        // Vincula o decorator para copiar e sanitizar o MDC nas threads secundárias assíncronas
        adapter.setTaskDecorator(mdcTaskDecorator);
        
        return adapter;
    }
}
