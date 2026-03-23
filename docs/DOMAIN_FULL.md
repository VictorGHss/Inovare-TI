Domain Package — documentação completa

Este documento lista e descreve, por pacote, as classes encontradas em `api/src/main/java/br/dev/ctrls/inovareti/domain`.

Formato: Package → arquivos (descrição curta)

---

1) domain.auth
- `AuthController.java` — endpoints de autenticação (login, 2FA, reset de senha inicial).
- usecase/`LoginUseCase.java` — lógica de autenticação e emissão de token via `TokenService`.
- usecase/`AuthorizationService.java` — verificação de permissões e roles.
- usecase/`ResetInitialPasswordUseCase.java` — fluxo de reset de senha inicial.
- usecase/`TwoFactorAuthService.java` — geração/validação TOTP e fluxo 2FA.
- usecase/`TwoFactorResetService.java` — reset do TOTP do usuário.
- dto/`AuthRequestDTO`, `AuthResponseDTO`, `TwoFactor*DTO` — contratos de payload para autenticação e 2FA.

2) domain.user
- `User.java` — entidade JPA do usuário (email, senha, role, TOTP secret, discordUserId, receivesItNotifications, etc.).
- `UserRepository.java` — repositório Spring Data para `User`.
- `UserController.java` — endpoints CRUD e perfil do usuário.
- `Sector.java`, `SectorRepository.java`, `SectorController.java` — entidade e endpoints de setores.
- usecase/* — casos de uso: criar/atualizar/resetar senha/listar usuários e setores.
- dto/* — DTOs para requests/responses de usuário e setor.

3) domain.ticket
- `Ticket.java` — entidade de chamado com status, prioridade, requester, assignedTo, SLA, attachments.
- `TicketRepository`, `TicketCommentRepository`, `TicketAttachmentRepository` — repositórios JPA.
- `TicketController`, `TicketCategoryController`, `TicketCategory.java` — endpoints para gestão de chamados e categorias.
- `TicketComment.java`, `TicketAttachment.java` — entidades auxiliares.
- usecase/* — casos de uso: criação, listagem, transferência, claim, comment e resolução de tickets.
- dto/* — DTOs de request/response para tickets, comentários e anexos.
- `FileController.java` — endpoints para servir anexos de chamados.

4) domain.inventory
- `Item.java`, `ItemCategory.java`, `StockBatch.java`, `StockMovement.java` — entidades de inventário, lotes e movimentações.
- `ItemRepository`, `ItemCategoryRepository`, `StockBatchRepository`, `StockMovementRepository` — repositórios.
- `ItemController`, `ItemCategoryController` — endpoints REST para itens e categorias.
- usecase/* — casos de uso: criar item, listar lotes, registrar lote, listar categorias.
- dto/* — DTOs de requisição/resposta para itens, lotes e movimentos.
- `StockDeductionService` — lógica para deduzir estoque em operações de retirada/consumo.

5) domain.asset
- `Asset.java`, `AssetCategory.java`, `AssetMaintenance.java` — entidades de ativos e histórico de manutenção.
- `AssetRepository`, `AssetCategoryRepository`, `AssetMaintenanceRepository` — repositórios.
- `AssetController`, `AssetCategoryController` — endpoints de ativos e categorias.
- `AssetService`, `AssetMaintenanceService`, `AssetQueryService` — serviços com regras e consultas especializadas.
- dto/* — DTOs para requests/responses de ativos e manutenção.

6) domain.financeiro
- `FinanceiroController` — endpoints de operações financeiras (backfill, recibos, alertas, resumo).
- Serviços e entidades:
  - `FinanceiroOperationsService` — regras centrais de negócio financeiro.
  - `ProcessedReceipt`, `ProcessedSale`, `ProcessedReceiptRepository`, `ProcessedSaleRepository` — rastreio de recibos/vendas processadas.
  - `ProcessingAttempt`, `ProcessingAttemptRepository` — tentativas de processamento e retries.
  - `ReceiptDispatcher` — coordena envio de recibos por email/alertas.
  - `AlertService`, `SystemAlert` — geração de alertas do sistema.
  - `DoctorMappingController`, `DoctorEmailMapping` — mapeamento entre médicos e emails (integração/transformação local).
  - `EmailRetryScheduler` — reenvio agendado para emails falhos.

7) domain.financeiro.contaazul
- `ContaAzulTokenService` — gerencia OAuth2 (exchange, refresh, persistência, renovação proativa @Scheduled).
- `ContaAzulClient`, `ContaAzulPaymentsClient`, `ContaAzulPessoaClient` — clientes HTTP para endpoints ContaAzul (venda, parcelas, pessoas).
- `ContaAzulOAuthToken` (entidade) + `ContaAzulOAuthTokenRepository` — persistência de tokens.
- `ContaAzulAutomationService` — automação que consulta vendas/parcelas e aciona envio de recibos.
- `ContaAzulController` — endpoints públicos para iniciar OAuth e callbacks.
- DTOs/exceptions: `ContaAzulPaymentParcel`, `ContaAzulPessoaDTO`, `NoReceiptAvailableException`, `ContaAzulAuthException`, `ContaAzulStatus`, `SyncDoctorsResult`, `TesteEnvioRealResult`.

8) domain.notification
- `Notification.java`, `NotificationRepository`, `NotificationController` — notificação interna persistida.
- `CreateNotificationService`, `GetUnreadNotificationsUseCase`, `MarkNotificationAsReadUseCase` — serviços de criação e leitura.
- `FinanceEmailService` — envio de e-mails de recibo usando `JavaMailSender` (modo teste e validação de configuração).
- Discord subpackage (`notification/discord`): `DiscordWebhookService`, `discord/bot/*` — integração JDA para envio de DMs, escuta de eventos e linking de usuários.

9) domain.vault
- `VaultItem`, `VaultItemShare`, `VaultItemRepository`, `VaultItemShareRepository` — entidade do cofre e relacionamentos de compartilhamento.
- `VaultService` — lógica do cofre (create/list/get secret/update/delete) que usa `EncryptionService` e `LocalFileStorageService` da infra.
- `VaultController` — endpoints REST do cofre (inclui validações 2FA via `TwoFactorSessionGuard`).
- DTOs: `VaultCreateItemRequestDTO`, `VaultItemResponseDTO`, `VaultSecretResponseDTO`, `VaultUpdateItemRequestDTO`.
- `VaultItemType`, `VaultSharingType` — enums de tipo/share.

10) domain.audit
- `AuditLog` (entidade), `AuditLogRepository`, `AuditLogService` — gravação de eventos de auditoria.
- `AuditEvent`, `AuditEventListener`, `AuditAction` — construção e publicação de eventos (padrão builder), e listener registra no `AuditLog`.
- DTOs: `AuditLogResponseDTO`, `QrScanAuditRequestDTO`.
- `AuditLogController` — endpoints para consulta de logs (admin).

11) domain.reports / domain.report
- `ReportController`, `ReportService` — geração e exportação de relatórios (tickets, inventário). Use cases como `TicketReportUseCase`.
- DTOs para exportação (ex.: `TicketReportDTO`).

12) domain.settings
- `SystemSetting` (entidade), `SystemSettingRepository`, `SystemSettingService`, `SettingsController` — configuração do sistema via banco (feature toggles, valores de ambiente persistidos)
- DTOs: `SystemSettingResponseDTO`.

13) domain.admin
- CSV import helpers e serviços: `CsvImportService`, `CsvPersistenceService`, `CsvRowParser`, `CsvImportRow`, `ImportResultDTO`.
- `AdminController` — importação em massa (CSV) e operações administrativas.

14) domain.analytics
- DTOs para métricas `MetricDTO`, `InventorySummaryDTO`, `DashboardAnalyticsDTO`.
- `GetDashboardAnalyticsUseCase` — agrega métricas para dashboard (tickets, estoque, alertas).

15) domain.shared
- `FileStorageService` — interface/abstração para armazenamento de arquivos usada por múltiplos domínios.
- `InvoiceFileMetadata` — objeto auxiliar para metadados de nota fiscal.

16) domain.report (complemento)
- `ReportController`, `ReportService` — endpoints para exportar relatórios (XLSX) e agregações.

17) Outros artefatos notáveis
- DTOs espalhados (`dto/*`) para cada domínio — mapeiam payloads de request/response e simplificam controllers.
- UseCases (`usecase/*`) — padrão de separação de responsabilidades: controllers delegam a casos de uso que encapsulam regras de negócio e transações.
- Repositórios JPA para todas as entidades principais seguindo convenções Spring Data.

---

Observações gerais e recomendações
- O pacote `domain` concentra regras de negócio, integrações (ContaAzul, SMTP, Discord), persistência e validações. Ele é a fonte canônica da lógica — quaisquer alterações exigem testes de integração/contratos.
- Recomendo adicionar um documento separado `docs/DOMAIN_EXAMPLES.md` com exemplos de chamada para os principais flows (OAuth ContaAzul, enviar recibo, criar ticket, gerar relatório).
- Próximo passo sugerido: documentar detalhadamente `domain/financeiro/contaazul` (fluxo OAuth, tabela `contaazul_oauth_tokens`, endpoints públicos) e `FinanceEmailService` (configurações SMTP/Brevo e testes). Posso começar por `contaazul` se desejar.

Referência: lista completa de classes gerada automaticamente a partir do workspace (arquivo `docs/DOMAIN_FULL.md` criado).
