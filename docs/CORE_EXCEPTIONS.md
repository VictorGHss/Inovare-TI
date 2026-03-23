**Core Exceptions**

Pacote: `api/src/main/java/br/dev/ctrls/inovareti/core/exception`

Resumo das exceções customizadas usadas pela aplicação e mapeamento HTTP:

- `NotFoundException` — estende `RuntimeException`. Anotada com `@ResponseStatus(HttpStatus.NOT_FOUND)`; usada quando um recurso não é encontrado (HTTP 404).
- `ConflictException` — mapeada para HTTP 409 (`@ResponseStatus(HttpStatus.CONFLICT)`); usada para conflitos de unicidade ou duplicidade de dados.
- `BadRequestException` — mapeada para HTTP 400 (`@ResponseStatus(HttpStatus.BAD_REQUEST)`); usada para requisições inválidas ou dados incorretos.
- `FileSizeLimitExceededException` — exceção custom para limitar uploads; tratada pelo `GlobalExceptionHandler` e mapeada para HTTP 413 (Payload Too Large).

Como são usados:
- As exceções são lançadas nas camadas de domínio/serviços quando validações de negócio falham ou quando um recurso não existe.
- `GlobalExceptionHandler` (em `config`) mapeia alguns desses tipos para `ProblemDetail` com estruturas legíveis e codificadas com os status HTTP apropriados.

Recomendações:
- Usar mensagens de erro padronizadas e, quando aplicável, incluir códigos de erro ou referências para rastreamento.
- Validar limites de upload na configuração do servidor e refletir o mesmo limite em `application.properties`.
