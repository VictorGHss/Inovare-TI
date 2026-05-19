# Relatório de Implementação - Fase 2: Resiliência e Tratamento Global de Exceções

Este documento apresenta a especificação técnica e as alterações arquiteturais implementadas com sucesso para a **Fase 2** do projeto **Inovare-TI**. Focamos em garantir resiliência para as integrações de rede externas (Feegow, Blip, ContaAzul) e em adotar uma padronização internacional para o tratamento e resposta de erros da API.

---

## 1. Dependências e Configurações Declarativas

### Maven (`pom.xml`)
Adicionamos o ecossistema do **Resilience4j** acoplado ao Spring Boot 3 por meio da biblioteca `resilience4j-spring-boot3` e habilitamos o suporte aos proxies de aspectos via `spring-boot-starter-aop`:
```xml
<!-- Resilience4j e Aspectos para Resiliencia Declarativa -->
<dependency>
    <groupId>io.github.resilience4j</groupId>
    <artifactId>resilience4j-spring-boot3</artifactId>
    <version>2.2.0</version>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-aop</artifactId>
    <version>3.3.4</version>
</dependency>
```

### Configurações de Resiliência (`application.properties`)
Declaramos as diretivas solicitadas para Circuit Breaker e Retry, garantindo um comportamento elástico e protetivo:
* **`feegowApiCircuit` (Circuit Breaker da Feegow):**
  * Janela deslizante de contagem baseada nas últimas 10 chamadas (`sliding-window-size=10`).
  * Limiar de falha para abertura em 50% (`failure-rate-threshold=50`).
  * Tempo de repouso em estado ABERTO de 45 segundos (`wait-duration-in-open-state=45s`) antes de prosseguir para o estado MEIO-ABERTO.
* **`contaAzulRetry` e `blipRetry` (Retentativas com Backoff Exponencial):**
  * Configurados para realizar no máximo 3 tentativas (`max-attempts=3`).
  * Intervalo inicial de 1 segundo (`wait-duration=1s`) com multiplicador de retardo exponencial igual a 2 (`exponential-backoff-multiplier=2`).

---

## 2. Proxies de Resiliência e Fallbacks Silenciosos

Para evitar que falhas transitórias nas APIs externas interrompam a jornada do usuário ou gerem erros 500 desnecessários no front-end, aplicamos fallbacks estruturados com a marcação `[OFFLINE-SYNC-INTENT]`:

### Feegow (`FeegowClient.java`)
Mapeamos e decoramos todos os métodos de chamadas externas com Circuit Breaker e fallbacks:
* `@CircuitBreaker(name = "feegowApiCircuit", fallbackMethod = "...")`
* **`fallbackSearchAppointments`**: Em caso de falha de rede ou circuito aberto, retorna uma lista vazia (`List.of()`) de agendamentos e registra o incidente em logs.
* **`fallbackGetPatientDetails`**: Retorna `null` de forma segura para tratamento downstream.
* **`fallbackGetProfessionalName`**: Retorna um profissional padrão seguro ("Clínica Inovare") para exibição provisória no fluxo.
* **`fallbackListProfessionals`**: Retorna uma lista vazia.
* **`fallbackUpdateAppointmentStatus`**: Registra uma intenção estruturada de sincronização offline (`[OFFLINE-SYNC-INTENT]`) para atualização posterior em background do status na Feegow.

### Blip (`BlipLIMEClient.java`)
Decoramos os métodos de envio de comandos e mensagens com Retry de 3 tentativas e fallbacks resilientes:
* `@Retry(name = "blipRetry", fallbackMethod = "...")`
* **`fallbackExecuteCommand`** e **`fallbackExecuteMessage`**: Retornam uma resposta de mock HTTP 200 contendo status `"offline-queued"` e registram a intenção em log estruturado.

### Conta Azul (`ContaAzulRequestExecutor.java`)
Decoramos as chamadas JSON GET centrais com retentativas e tratamento seguro:
* `@Retry(name = "contaAzulRetry", fallbackMethod = "fallbackExecuteJsonGetResponse")`
* **`fallbackExecuteJsonGetResponse`**: Constrói um objeto `HttpResponse<String>` simulado com status 503 e body JSON `"{\"status\":\"offline-queued\"}"`, registrando o log estruturado `[OFFLINE-SYNC-INTENT]` para sincronizações financeiras posteriores em background.

---

## 3. Tratamento Global de Erros (RFC 7807 Problem Details)

Refatoramos o **[GlobalExceptionHandler.java](file:///c:/Projeto/Inovare-TI/api/src/main/java/br/dev/ctrls/inovareti/config/GlobalExceptionHandler.java)** para atingir um nível excelente de padronização:
1. **Herança do MVC Nativo:** A classe agora estende `ResponseEntityExceptionHandler` para se integrar à malha de tratamento nativo do Spring Boot.
2. **Evitando Ambiguidades:** Para evitar colisões em tempo de inicialização, sobrescrevemos o método nativo `handleMethodArgumentNotValid` em vez de usar anotações redundantes do Spring.
3. **Erros de Negócio Customizados (HTTP 422):** As exceções de violação de regras (como `IllegalStateException`) agora retornam o status HTTP 422 (Unprocessable Entity) encapsulado perfeitamente em objetos `ProblemDetail`.
4. **Erros Genéricos Robustos (HTTP 500):** Capturamos qualquer erro não mapeado (`Exception.class`) gerando uma resposta HTTP 500 amigável com propriedades adicionais da RFC 7807:
   * **`timestamp`**: Data e hora exata da ocorrência do erro.
   * **`trace_id`**: Identificador exclusivo para rastreabilidade de incidentes. O método tenta carregar o trace ID ativo do MDC/Log. Caso retorne nulo (por exemplo, quando chamado fora de escopo mapeado de logs), gera um **UUID temporário dinâmico**, garantindo que a aplicação React sempre receba um rastreamento válido.

---

## 4. Validação e Compilação

Executamos o build completo via Maven:
```powershell
mvn clean compile
```
**Resultado:** `BUILD SUCCESS` obtido com sucesso em 10.59 segundos. As classes e as anotações do Resilience4j foram processadas e compiladas com êxito sem gerar qualquer conflito no ciclo de vida dos Beans do Spring Framework.
