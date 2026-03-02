# TODO — Inovare TI

Acompanhamento das tarefas de desenvolvimento por fase.

---

## ✅ Fase 1 — Entidades e Repositórios (Concluída)

- [x] Entidade `Sector` + `SectorRepository`
- [x] Entidade `User` + `UserRole` (enum) + `UserRepository`
- [x] Entidade `TicketCategory` + `TicketCategoryRepository`
- [x] Entidade `ItemCategory` + `ItemCategoryRepository`
- [x] Documentação inicial em `DATABASE.md`

---

## ✅ Fase 2 — CRUD Básico + Segurança Inicial (Concluída)

- [x] `SecurityConfig`: todas as rotas abertas (`permitAll`), CSRF desabilitado, `PasswordEncoder` BCrypt
- [x] `GlobalExceptionHandler`: erros 400, 404 e 409 no padrão RFC 7807
- [x] `NotFoundException` e `ConflictException` em `core/exception/`
- [x] Setores: DTOs, `CreateSectorUseCase`, `ListAllSectorsUseCase`, `SectorController`
- [x] Categorias de Chamado: DTOs, `CreateTicketCategoryUseCase`, `ListAllTicketCategoriesUseCase`, `TicketCategoryController`
- [x] Categorias de Item: DTOs, `CreateItemCategoryUseCase`, `ListAllItemCategoriesUseCase`, `ItemCategoryController`
- [x] Usuários: DTOs, `CreateUserUseCase`, `ListAllUsersUseCase`, `UserController`
- [x] `API_DOCS.md` atualizado com todos os endpoints

---

## ✅ Fase 3 — Inventário: Items e Lotes de Estoque (Concluída)

- [x] Entidade `Item` (com `specifications` como `jsonb` via `@JdbcTypeCode(SqlTypes.JSON)`)
- [x] Entidade `StockBatch` (lote de entrada de estoque)
- [x] `ItemRepository` (com `findAllWithCategory()` JOIN FETCH)
- [x] `StockBatchRepository`
- [x] DTOs: `ItemRequestDTO`, `ItemResponseDTO`, `StockBatchRequestDTO`, `StockBatchResponseDTO`
- [x] `CreateItemUseCase` (estoque inicial = 0)
- [x] `RegisterStockBatchUseCase` (cria lote + atualiza `currentStock` atomicamente)
- [x] `ItemController`: `POST /api/items`, `POST /api/items/{id}/batches`
- [x] `DATABASE.md` e `API_DOCS.md` atualizados

---

## ✅ Fase 4 — Motor de Chamados + Baixa de Estoque (Concluída)

- [x] Enum `TicketStatus`: `OPEN`, `IN_PROGRESS`, `RESOLVED`, `CLOSED`
- [x] Enum `TicketPriority`: `LOW`, `NORMAL`, `HIGH`, `URGENT`
- [x] Entidade `Ticket` com todos os relacionamentos (User, TicketCategory, Item)
- [x] `TicketRepository` com `findAllWithRelations()` JOIN FETCH
- [x] DTOs: `TicketRequestDTO`, `TicketResponseDTO`
- [x] `CreateTicketUseCase` (slaDeadline calculado, status inicial OPEN)
- [x] `CloseTicketUseCase` (baixa de estoque atômica via `@Transactional`)
- [x] `TicketController`: `POST /api/tickets`, `PATCH /api/tickets/{id}/close`
- [x] `GlobalExceptionHandler`: handler para `IllegalStateException` → HTTP 422
- [x] `DATABASE.md` e `API_DOCS.md` atualizados

---

## 🔲 Fase 5 — Autenticação JWT

- [ ] Criar endpoint `POST /api/auth/login` que retorna `accessToken` (JWT)
- [ ] Criar `JwtService`: geração e validação de tokens
- [ ] Criar `JwtAuthenticationFilter`: intercepta requests e valida Bearer token
- [ ] Atualizar `SecurityConfig` para proteger rotas (exceto `/api/auth/**`)
- [ ] Implementar `UserDetails` / `UserDetailsService` para integrar com Spring Security
- [ ] Criar endpoint `POST /api/auth/refresh` (token de renovação)

---

## 🔲 Fase 6 — Módulo de Chamados: Funcionalidades Avançadas

- [ ] `AssignTicketUseCase`: atribuir técnico a um chamado
- [ ] `ResolveTicketUseCase`: mover para status `RESOLVED`
- [ ] Filtros no `TicketController` por status, prioridade, categoria e técnico

---

## 🔲 Fase 7 — Integrações e Extras

- [ ] Autenticação 2FA via TOTP (`totpSecret` já modelado na entidade `User`)
- [ ] Integração com Discord (notificações de chamados via webhook/bot)
- [ ] Flyway: escrever migrações DDL para todas as tabelas
- [ ] Testes de integração com `@SpringBootTest` + Testcontainers (PostgreSQL)