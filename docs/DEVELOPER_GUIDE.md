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

Crie um arquivo `.env` na raiz do projeto com as variáveis abaixo (exemplo para desenvolvimento):

```env
# Banco de dados
POSTGRES_DB=inovareti
POSTGRES_USER=postgres
POSTGRES_PASSWORD=postgres

# JWT — use uma string longa e aleatória em produção
JWT_SECRET=MeuSegredoJWTSuperSegurogfdsInovareTI2025!

# Chave de criptografia para dados sensíveis
ENCRYPTION_SECRET=MyStrongDevEncryptionSecret2024!

# Discord (opcional em desenvolvimento)
DISCORD_WEBHOOK_URL=
DISCORD_BOT_TOKEN=
DISCORD_BOT_ENABLED=false

# URL do frontend (usada em redirecionamentos)
FRONTEND_URL=http://localhost:5173/
```

Observações:
- Nunca versionar segredos no repositório.
- Em produção, use Vault/Secret Manager e rotacione chaves conforme procedimento.

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
