# Guia do Desenvolvedor — Inovare TI

Este documento centraliza instruções técnicas para desenvolvedores: configuração, execução, variáveis de ambiente, padrões de segurança (JWT, filtros) e envio de e-mail.

---

## Visão Geral do Módulo Backend

- Pacote principal: `api/` (Java 21 · Spring Boot 4)
- Responsabilidades: API REST, integrações, regras de negócio, persistência, segurança e observabilidade.

---

## Como compilar e rodar testes

```bash
cd api
mvn test
```

Para executar um teste específico:

```bash
mvn -Dtest=NomeDoTeste test
```

---

## Configuração do Ambiente (`.env` / `application.properties`)

Crie um arquivo `.env` na raiz do projeto com as variáveis abaixo (exemplo para desenvolvimento). Para referência use também `.env.example`.

```env
# -------------------------------
# Banco de dados
# -------------------------------
POSTGRES_DB=inovareti
POSTGRES_USER=inovareti_user
POSTGRES_PASSWORD=change_this_secure_password
POSTGRES_HOST=db
POSTGRES_PORT=5432
DB_URL=jdbc:postgresql://${POSTGRES_HOST}:${POSTGRES_PORT}/${POSTGRES_DB}

# -------------------------------
# API / Frontend
# -------------------------------
API_PORT=8085
FRONTEND_URL=http://localhost:5173

# -------------------------------
# Redis (cache / rate-limiter)
# -------------------------------
SPRING_REDIS_HOST=redis
SPRING_REDIS_PORT=6379

# -------------------------------
# Segurança e criptografia
# -------------------------------
JWT_SECRET=ReplaceWithASecureRandomString
ENCRYPTION_SECRET=ReplaceWith32ByteKeyOrBase64
VAULT_ENCRYPTION_KEY=

# -------------------------------
# Discord (opcional)
# -------------------------------
DISCORD_WEBHOOK_URL=
DISCORD_BOT_TOKEN=
DISCORD_BOT_ENABLED=false

# -------------------------------
# SMTP / Financeiro
# -------------------------------
APP_FINANCEIRO_TEST_MODE=true
APP_FINANCEIRO_DEV_EMAIL=
APP_FINANCEIRO_SMTP_FROM_EMAIL=no-reply@inovare.med.br
APP_FINANCEIRO_SMTP_FROM_NAME=Inovare TI Financeiro

# -------------------------------
# ContaAzul OAuth2
# -------------------------------
CONTAAZUL_CLIENT_ID=
CONTAAZUL_CLIENT_SECRET=
CONTAAZUL_AUTHORIZATION_URL=https://auth.contaazul.com/oauth2/authorize
CONTAAZUL_TOKEN_URL=https://auth.contaazul.com/oauth2/token

# -------------------------------
# Feature toggles
# -------------------------------
# Habilita o DatabaseSeeder quando rodar com profile `dev` (APP_SEEDER_ENABLED -> app.seeder.enabled)
APP_SEEDER_ENABLED=true
```

Observações:
- Nunca versionar segredos no repositório. Use `Vault` ou `Secrets Manager` em produção.
- O `DatabaseSeeder` é executado apenas quando a aplicação roda com o profile `dev` e quando a propriedade `app.seeder.enabled` (ou a variável de ambiente `APP_SEEDER_ENABLED`) está ativada. Por padrão `application.properties` define `app.seeder.enabled=false`.
- O `DB_URL` também pode ser fornecido diretamente (ex.: `DB_URL=jdbc:postgresql://db:5432/inovareti`). O `docker-compose.yml` mapeia a porta do Postgres para `5436` no host — para conectar localmente ao container use `localhost:5436`.

Propriedades importantes (exemplos):

- `api.security.token.secret` — segredo usado pelo `TokenService`.
- `spring.mail.*` — configuração SMTP usada por `FinanceEmailService`.
- `app.contaazul.*` — client_id/client_secret e endpoints da ContaAzul.
- `app.vault.encryption-key` — chave usada pelo `EncryptionService`.

---

## Componentes de Segurança e Autenticação

### Fluxo de Escalonamento de Privilégios (JWT + 2FA)

O sistema utiliza um fluxo de autenticação em duas etapas refletido nas claims do JWT:

**Etapa 1 (Login Comum):** O usuário fornece e-mail/senha. O `TokenService` gera um JWT padrão. Este token permite acessar chamados e inventário, mas bloqueia o acesso ao Vault e outras operações sensíveis.

**Etapa 2 (Desafio TOTP):** O usuário envia o código de 6 dígitos para `/api/auth/2fa/verify`. Se válido, o sistema emite um novo JWT contendo a claim `two_factor_verified`: `true`. Esse token passa a conceder acesso a recursos que exigem 2FA.

### O Guardião do Vault (TwoFactorSessionGuard)

O que ele faz: antes de descriptografar qualquer segredo (AES-256/GCM) o backend verifica se o JWT atual possui a claim `two_factor_verified` ativa. Além disso, em cada requisição sensível o `SecurityFilter` valida o estado do 2FA no banco para garantir revogação imediata.

Revogação: se o 2FA for resetado (via Discord ou por um Admin), o segredo TOTP no banco é apagado. Mesmo que o usuário tenha um JWT contendo `two_factor_verified=true`, o `SecurityFilter`/`TwoFactorSessionGuard` checam o estado real no banco a cada requisição sensível e invalidam o acesso imediatamente.

### `SecurityFilter`

- `OncePerRequestFilter` que extrai o Bearer token do cabeçalho `Authorization`.
- Valida via `TokenService`, carrega `User` por e-mail e popula o `SecurityContext`.
- Ignora rotas de callback/authorize da ContaAzul.

### `SecurityConfig`

- Configuração JWT stateless, CSRF desabilitado e CORS configurado para o frontend.
- Rotas públicas típicas: `POST /auth/login`, `POST /auth/reset-initial-password`, `GET /attachments/**`, `/financeiro/contaazul/**`.
- `SecurityFilter` é registrado antes de `UsernamePasswordAuthenticationFilter`.

---

## Manipulação de Erros — RFC 7807 (Problem Details)

O projeto usa `GlobalExceptionHandler` (`@RestControllerAdvice`) que padroniza respostas de erro seguindo RFC 7807 (Problem Details). Mapeamentos principais:

- `MethodArgumentNotValidException` → 400 (lista de campos e mensagens)
- `NotFoundException` → 404
- `ConflictException` → 409
- `IllegalStateException` → 422
- `FileSizeLimitExceededException` / `MaxUploadSizeExceededException` → 413
- `AccessDeniedException` → 403
- Erros inesperados → 500 (ProblemDetail com requestId quando possível)

Recomendações:
- Mensagens visíveis ao usuário devem estar sempre em português.
- Incluir códigos de erro quando útil para triagem (ex.: `ERR_CONTA_AZUL_401`).

---

## Exceções customizadas

- `NotFoundException` → HTTP 404
- `ConflictException` → HTTP 409
- `BadRequestException` → HTTP 400
- `FileSizeLimitExceededException` → HTTP 413

As exceções são lançadas nas camadas de domínio quando validações de negócio falham.

---

## SMTP / Envio de Emails

O projeto usa `spring-boot-starter-mail` (JavaMailSender). Exemplo de propriedades:

```properties
spring.mail.host=smtp.example.com
spring.mail.port=587
spring.mail.username=your-smtp-user
spring.mail.password=your-smtp-password
spring.mail.properties.mail.smtp.auth=true
spring.mail.properties.mail.smtp.starttls.enable=true
spring.mail.from=suporte@exemplo.com
```

Exemplo simples de `EmailService`:

```java
@Service
public class EmailService {
    private final JavaMailSender mailSender;

    public EmailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    public void sendPlainText(String to, String subject, String body) {
        SimpleMailMessage msg = new SimpleMailMessage();
        msg.setTo(to);
        msg.setSubject(subject);
        msg.setText(body);
        msg.setFrom("suporte@exemplo.com");
        mailSender.send(msg);
    }
}
```

Para testes locais use `MailHog` ou similar; para unit tests, mockar `JavaMailSender`.

---

## Flyway, Seeder e Observability

- `FlywayConfig` executa migrações na inicialização; `spring.jpa.hibernate.ddl-auto` = `validate`.
- `DatabaseSeeder` (profile `dev`) popula dados iniciais quando tabelas vazias.
- Métricas: Micrometer + Prometheus; expor endpoints `prometheus`/`health` via Actuator.

---

## Recomendações para Desenvolvedores

- Manter mensagens visíveis ao usuário em português.
- Evitar logs que contenham tokens completos em produção.
- Testar cenários de refresh de token e parsing defensivo nas integrações.

---

## Relatórios PDF (OpenPDF / PdfPTable)

- O serviço `ReportService.exportInventoryExitsToPdf` agora usa a biblioteca OpenPDF (API compatível com iText) e `PdfPTable` para gerar uma tabela profissional.
- Alterações principais:
    - Inclusão de logo (tenta carregar `src/main/resources/images/logo.png`, com fallback para URL pública) no cabeçalho do PDF.
    - Cabeçalho da tabela com cor da marca Inovare (`#feb56c`) e fonte em branco para contraste.
    - Uso de `PdfPTable` para garantir colunas reais, larguras fixas e alinhamentos (quantidades e valores à direita; textos à esquerda).
    - Linha `TOTAL` ao final da tabela com somatório financeiro e resumo abaixo da tabela.
    - Comentários em código mantidos em português e paginação tratada pela API de PDF.
 - Observação: se for necessário trocar a biblioteca por iText comercial, abrir discussão sobre licenciamento.
