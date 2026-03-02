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

## 🔲 Fase 3 — Autenticação JWT (Próxima)

- [ ] Criar endpoint `POST /api/auth/login` que retorna `accessToken` (JWT)
- [ ] Criar `JwtService`: geração e validação de tokens
- [ ] Criar `JwtAuthenticationFilter`: intercepta requests e valida Bearer token
- [ ] Atualizar `SecurityConfig` para proteger rotas (exceto `/api/auth/**`)
- [ ] Implementar `UserDetails` / `UserDetailsService` para integrar com Spring Security
- [ ] Criar endpoint `POST /api/auth/refresh` (token de renovação)

---

## 🔲 Fase 4 — Módulo de Chamados (Helpdesk)

- [ ] Entidade `Ticket` (id, title, description, status, priority, category, openedBy, assignedTo, sla)
- [ ] Enum `TicketStatus`: `OPEN`, `IN_PROGRESS`, `RESOLVED`, `CLOSED`
- [ ] Enum `TicketPriority`: `LOW`, `MEDIUM`, `HIGH`, `CRITICAL`
- [ ] Use Cases: `CreateTicketUseCase`, `AssignTicketUseCase`, `ResolveTicketUseCase`, `ListTicketsUseCase`
- [ ] `TicketController` com filtros por status, prioridade e categoria

---

## 🔲 Fase 5 — Módulo de Inventário

- [ ] Entidade `Item` (id, serial, model, category, assignedTo, location, status, purchaseDate)
- [ ] Enum `ItemStatus`: `AVAILABLE`, `IN_USE`, `MAINTENANCE`, `DECOMMISSIONED`
- [ ] Use Cases: `CreateItemUseCase`, `AssignItemUseCase`, `ListItemsUseCase`
- [ ] `ItemController`

---

## 🔲 Fase 6 — Integrações e Extras

- [ ] Autenticação 2FA via TOTP (`totpSecret` já modelado na entidade `User`)
- [ ] Integração com Discord (notificações de chamados via webhook/bot)
- [ ] Flyway: escrever migrações DDL para todas as tabelas
- [ ] Testes de integração com `@SpringBootTest` + Testcontainers (PostgreSQL)
