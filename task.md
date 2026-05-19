# Lista de Tarefas - Refatoração HikariCP e Virtual Threads

- [x] Configuração de Virtual Threads (Java 21)
  - [x] Adicionar `spring.threads.virtual.enabled=true` em `application.properties`
  - [x] Criar classe `AsyncConfiguration.java` no pacote `br.dev.ctrls.inovareti.config`
  - [x] Remover classe `AsyncConfig.java` obsoleta
- [x] Refatoração das Fronteiras Transacionais (Componente 2)
  - [x] `IngestAppointmentsUseCase.java`: Isolar transações HTTP fora do escopo `@Transactional` usando `TransactionTemplate`
  - [x] `SendAppointmentTemplateUseCase.java`: Otimizar métodos de disparo de templates Blip com transações microscópicas
  - [x] `HandleBlipWebhookUseCase.java`: Desacoplar fluxo de webhook de webhook HTTP das transações ativas do banco
- [x] Verificação e Compilação
  - [x] Executar `mvn clean compile` para testar integridade e compilação
  - [x] Gerar walkthrough.md das alterações realizadas
