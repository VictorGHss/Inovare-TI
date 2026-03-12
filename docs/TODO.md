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

## ✅ Fase 5 — Autenticação JWT (Concluída)

- [x] Criado endpoint `POST /api/auth/login` com emissão de JWT
- [x] Implementado serviço de geração e validação de tokens
- [x] Criado filtro de autenticação Bearer Token
- [x] `SecurityConfig` atualizado para proteger rotas sensíveis
- [x] `UserDetails` / `UserDetailsService` integrados ao Spring Security
- [x] Sessão de autenticação integrada ao fluxo de 2FA via claim no JWT

---

## ✅ Fase 6 — Funcionalidades Avançadas (Concluída)

- [x] Fluxo avançado de chamados com claim, transferência e resolução
- [x] Upload e visualização de anexos
- [x] Gestão avançada de usuários e redefinição administrativa
- [x] Cofre seguro com compartilhamento e anexos protegidos por 2FA

---

## ✅ Fase 7 — Extras (Concluída)

- [x] Autenticação 2FA via TOTP com geração de QR Code
- [x] Recuperação de acesso via Discord com código temporário e senha atual
- [x] Integração com Discord para notificações operacionais e recuperação de segurança
- [x] Migrações Flyway consolidadas no schema base (`V1__init.sql`)
- [x] Revogação imediata de acesso ao Vault após reset do 2FA
- [x] Fase 7 validada e mantida como concluída após estabilização de regressões

---

## 🔲 Fase 8 — Auditoria de Qualidade e Compliance

- [x] Auditoria de tamanho: varredura de Controllers, UseCases e páginas críticas
- [x] Correção de `exhaustive-deps` no `Assets/index.tsx` (`useCallback` + deps explícitas)
- [x] Refatoração de `CsvImportService` (350 linhas → `CsvRowParser` + `CsvPersistenceService`)
- [x] Refatoração de `AssetController` (lógica de filtro extraída para `AssetQueryService`)
- [x] Componentização: `Vault/index.tsx` → 4 sub-componentes em `Vault/components/`
- [x] Componentização: `Assets/index.tsx` → `AssetTable` + `NewAssetModal`
- [ ] Implementar rastreabilidade de acessos ao Vault (leituras, resets 2FA, ações admin)
- [ ] Trilha de auditoria com usuário, ação, timestamp, origem e contexto

---

## 🔲 Fase 9 — Otimização Mobile (PWA)

- [x] Configurar `vite-plugin-pwa` no `vite.config.ts`
- [x] Criar `manifest.json` com nome "Inovare TI", cores `#ffa751`/`#ffffff`, modo `standalone`
- [x] Adicionar meta tags Apple para comportamento nativo no iPhone (sem barra do Safari)
- [x] Ajustar manifesto com `scope: /` e `start_url: /dashboard` para navegação interna no app instalado
- [x] Adicionar `display_override` com `standalone` e `window-controls-overlay`
- [x] Implementar sidebar vertical colapsável no layout autenticado (desktop fixo, mobile com Menu/X)
- [ ] Adicionar ícones PWA em resolução 192x192 e 512x512
- [ ] Testar instalação como web app em iOS e Android
- [ ] Adicionar suporte nativo a QR Code para fluxos de inventário e ativos

---

## 🔲 Fase 10 — Dashboard Premium

- [ ] Criar interface premium com animações e refinamento visual
- [ ] Aplicar identidade visual com as cores oficiais `#ffa751` e `#ffd1a3`
- [ ] Evoluir dashboard para experiência executiva e analítica
- [ ] Gráficos avançados com drill-down e filtros temporais