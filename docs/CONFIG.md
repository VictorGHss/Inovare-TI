**Config Package**

Resumo das principais classes do pacote `config` (backend Spring Boot).

**TokenService**: Serviço para geração e validação de JWTs usando `auth0-java-jwt`.
- Emite tokens com issuer `inovare-ti`, expiração padrão de 8 horas e suporta token de reset inicial (15 minutos).
- Claim `two_factor_verified` usado para controle de acesso a endpoints sensíveis (ex.: Vault).

**SecurityFilter**: Filtro `OncePerRequestFilter` que extrai o Bearer token do cabeçalho `Authorization`, valida via `TokenService`, carrega o `User` por e-mail e popula o `SecurityContext` com `UsernamePasswordAuthenticationToken`.
- Ignora rotas de callback/authorize do ContaAzul.
- Usa `@Lazy` no `UserRepository` para evitar ciclos de inicialização.

**SecurityConfig**: Configuração do Spring Security.
- Stateless JWT (sem sessão), CSRF desabilitado, CORS habilitado para o frontend.
- Rotas públicas: `POST /auth/login`, `POST /auth/reset-initial-password`, `GET /attachments/**`, `/financeiro/contaazul/**` e algumas rotas de teste.
- Adiciona `SecurityFilter` antes do `UsernamePasswordAuthenticationFilter`.
- Exposição de `AuthenticationManager` e bean `PasswordEncoder` (BCrypt).

**RestTemplateConfig**: Cria um bean `RestTemplate` para chamadas HTTP externas (Discord webhooks, integrações).

**JacksonConfig**: `ObjectMapper` primário configurado com `JavaTimeModule` e sem `WRITE_DATES_AS_TIMESTAMPS` (JSON ISO8601 para datas).

**GlobalExceptionHandler**: `@RestControllerAdvice` que padroniza erros no formato RFC 7807 (Problem Details).
- Trata `MethodArgumentNotValidException` (400) com mapa de campos.
- `ConflictException` -> 409, `NotFoundException` -> 404, `IllegalStateException` -> 422.
- Trata `FileSizeLimitExceededException` e `MaxUploadSizeExceededException` (413).
- Trata access denied (403) e erros genéricos (500) com log completo.

**FlywayConfig**: Força execução de migrações Flyway na inicialização e seta dependência para `entityManagerFactory`.

**DatabaseSeeder**: `CommandLineRunner` ativado em profile `dev` para popular entidades (categorias, setores, usuários, itens, lotes, tickets, assets, artigos, configurações) quando tabelas estiverem vazias.

**AnalyticsController** (localizado neste pacote): Endpoint `GET /analytics/dashboard` que usa `GetDashboardAnalyticsUseCase` e aplica isolamento por usuário, retornando métricas agregadas.

**Observações e variáveis importantes**
- Chaves/variáveis injetadas via `application.properties` / `.env`:
  - `api.security.token.secret` — usado em `TokenService` para assinar/validar JWTs.
  - Propriedades Flyway / datasource — utilizadas por `FlywayConfig`.
  - SMTP / ContaAzul / Brevo / Discord — usados por outros beans que dependem do `RestTemplate` e por services no pacote `domain`.

**Recomendações rápidas**
- Centralizar nomes de propriedades no `docs/.env.example` (já atualizado).
- Verificar limites de tamanho de upload nas configurações do servidor e alinhar mensagens no `GlobalExceptionHandler`.

Arquivo de referência: `api/src/main/java/br/dev/ctrls/inovareti/config/` (classes: `TokenService`, `SecurityFilter`, `SecurityConfig`, `RestTemplateConfig`, `JacksonConfig`, `GlobalExceptionHandler`, `FlywayConfig`, `DatabaseSeeder`, `AnalyticsController`).
