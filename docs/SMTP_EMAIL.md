**SMTP Email (envio via Spring Mail)**

- **Resumo:** O projeto usa `spring-boot-starter-mail` (JavaMailSender) para envio de e-mails via SMTP. Não usamos Brevo para envio — Brevo está apenas disponível como alternativa via API se desejado.

- **Propriedades (exemplo `application.properties`):

```
spring.mail.host=smtp.example.com
spring.mail.port=587
spring.mail.username=your-smtp-user
spring.mail.password=your-smtp-password
spring.mail.properties.mail.smtp.auth=true
spring.mail.properties.mail.smtp.starttls.enable=true
spring.mail.from=suporte@exemplo.com
```

- **Comportamento:** O Spring cria um `JavaMailSender` autoconfigurado quando as propriedades acima são definidas. O envio é feito por componentes que recebem `JavaMailSender` via injeção e constroem um `SimpleMailMessage` ou `MimeMessage` (para HTML/attachments).

- **Exemplo simples (service):**

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

- **HTML / anexos:** use `MimeMessage` / `MimeMessageHelper` para conteúdo HTML e anexos.

- **Ambiente de testes:**
  - Para testes unitários, `JavaMailSender` pode ser mockado com Mockito.
  - Para testes de integração locais, use um servidor SMTP de desenvolvimento (MailHog, Papercut, Docker image `mailhog/mailhog`) e aponte `spring.mail.host`/`port` para ele.

- **Segurança / credenciais:**
  - Nunca comitar credenciais reais em `application.properties`. Use variáveis de ambiente ou um cofre (Vault).
  - Recomenda-se restringir a conta SMTP a apenas envio (sem acesso a caixas de entrada sensíveis).

- **Observabilidade:**
  - Logue o resultado do envio (success/failure) e capture falhas de `MailException` para alertas.

- **Notas específicas do projeto:**
  - O projeto já usa propriedades `spring.mail.*` em `api/src/main/resources/application.properties`.
  - Há também configurações `app.financeiro.smtp.*` usadas para remetente/nomes; alinhe esses valores com `spring.mail.*` conforme necessário.
