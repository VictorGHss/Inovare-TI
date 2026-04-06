export interface AttachmentResponse {
  id: string;
  originalFilename: string;
  fileUrl: string;
  fileType: string;
}

export interface Ticket {
  id: string;            // UUID do chamado
  title: string;
  description: string | null;
  anydeskCode: string | null;
  status: 'OPEN' | 'IN_PROGRESS' | 'RESOLVED';
  priority: 'LOW' | 'NORMAL' | 'HIGH' | 'URGENT';
  requesterId: string;
  requesterName: string;
  assignedToId: string | null;
  assignedToName: string | null;
  categoryId: string;
  categoryName: string;
  requestedItemId: string | null;
  requestedItemName: string | null;
  requestedQuantity: number | null;
  isFromDiscord: boolean;
  slaDeadline: string | null;
  createdAt: string;
  closedAt: string | null;
  attachments: AttachmentResponse[];
}

export interface TicketCategory {
  // UUID retornado pelo backend como string
  id: string;
  name: string;
}

export interface Item {
  // UUID retornado pelo backend como string
  id: string;
  name: string;
  itemCategoryId: string;
  itemCategoryName: string;
  currentStock: number;
  specifications: Record<string, unknown> | null;
}

export interface ItemCategory {
  id: string;
  name: string;
  isConsumable: boolean;
}

export interface CreateAssetCategoryDto {
  name: string;
}

export interface CreateItemCategoryDto {
  name: string;
  isConsumable?: boolean;
}

export interface Asset {
  id: string;
  userId: string | null;
  assignedToName: string | null;
  name: string;
  patrimonyCode: string;
  categoryId?: string | null;
  categoryName?: string | null;
  specifications: string | null;
  createdAt: string;
  invoiceFileName?: string;
  invoiceContentType?: string;
  invoiceFilePath?: string;
}

export type AssetFilterStatus = 'ALL' | 'IN_USE' | 'IN_STOCK';

export type AssetSortBy = 'createdAt' | 'maintenanceCount';

export interface GetAssetsParams {
  categoryId?: string;
  status?: AssetFilterStatus;
  sortBy?: AssetSortBy;
}

export interface CreateAssetDto {
  userId?: string;
  name: string;
  patrimonyCode: string;
  categoryId?: string;
  specifications?: string;
  quantity?: number;
}

export interface StockMovement {
  id: string;
  itemId: string;
  type: 'IN' | 'OUT';
  quantity: number;
  reference: string;
  date: string;
}

export interface AssetCategory {
  id: string;
  name: string;
}

export interface ResolveTicketRequest {
  resolutionNotes?: string;
  assetIdToDeliver?: string;
  inventoryItemIdToDeliver?: string;
  quantityToDeliver?: number;
  newAssetToDeliver?: {
    userId: string;
    name: string;
    patrimonyCode: string;
    categoryId: string;
    specifications?: string;
  };
}

export interface CreateItemDto {
  itemCategoryId: string;
  name: string;
  specifications?: Record<string, unknown>;
}

export interface CreateBatchDto {
  quantity: number;
  unitPrice: number;
  brand?: string;
  supplier?: string;
  purchaseReason?: string;
}

export interface Batch {
  id: string;
  originalQuantity: number;
  remainingQuantity: number;
  unitPrice: number;
  entryDate: string; // ISO string
  invoiceFileName?: string;
  invoiceContentType?: string;
  invoiceFilePath?: string;
}

export interface User {
  id: string;
  name: string;
  email: string;
  role: 'ADMIN' | 'TECHNICIAN' | 'USER';
  sectorId: string;
  sectorName: string;
  location: string;
  discordUserId: string | null;
  contaAzulId: string | null;
  receives_it_notifications: boolean;
}

export interface Sector {
  id: string;
  name: string;
}

export interface TicketAttachment {
  id: string;
  originalFilename: string;
  storedFilename: string;
  fileType: string;
  ticketId: string;
  uploadedAt: string;
}

export interface CreateUserDto {
  name: string;
  email: string;
  password: string;
  role: 'ADMIN' | 'TECHNICIAN' | 'USER';
  sectorId: string;
  location?: string;
  discordUserId?: string;
  contaAzulId?: string;
  receives_it_notifications: boolean;
}

export interface UpdateUserDto {
  name: string;
  email: string;
  role: 'ADMIN' | 'TECHNICIAN' | 'USER';
  sectorId: string;
  contaAzulId?: string;
  receives_it_notifications: boolean;
}

export interface CreateSectorDto {
  name: string;
}

export interface CreateTicketDto {
  title: string;
  description: string;
  anydeskCode?: string;
  priority: 'LOW' | 'NORMAL' | 'HIGH' | 'URGENT';
  // UUID da categoria (string) — alinhado com TicketRequestDTO do backend
  categoryId: string;
  // Campos preenchidos apenas para chamados do tipo REQUEST
  requestedItemId?: string;
  requestedQuantity?: number;
}

export interface Article {
  id: string;
  title: string;
  content: string;
  authorId: string;
  authorName: string;
  tags: string | null;
  status: 'DRAFT' | 'PUBLISHED';
  createdAt: string;
  updatedAt: string | null;
}

export type ArticleStatus = 'DRAFT' | 'PUBLISHED';

export interface CreateArticleDto {
  title: string;
  content: string;
  tags?: string;
  status?: ArticleStatus;
}

export interface AssetMaintenance {
  id: string;
  assetId: string;
  maintenanceDate: string;
  type: 'Preventiva' | 'Corretiva' | 'Upgrade' | 'Transferência';
  description: string | null;
  cost: number | null;
  technicianName: string;
  technicianEmail: string;
}

export interface CreateAssetMaintenanceData {
  maintenanceDate: string;
  type: 'PREVENTIVE' | 'CORRECTIVE' | 'UPGRADE';
  description?: string;
  cost?: number | null;
}

export interface TransferAssetData {
  newUserId: string | null;
  reason: string;
}

export interface GenericAttachmentResponse {
  url: string;
}

export interface ArticleSearchResult {
  id: string;
  title: string;
}

export interface ReportSchedule {
  id: string;
  reportType: string; // 'tickets' | 'entries' | 'exits'
  targetUserId: string | null;
  sendEmail: boolean;
  sendDiscord: boolean;
  scheduleDay: number;
  isActive: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface MetricDTO {
  name: string;
  value: number;
}

export interface InventorySummaryDTO {
  totalItems: number;
  lowStockItems: number;
  outOfStockItems: number;
  receivedItemsCount: number;
}

export interface DashboardAnalyticsDTO {
  totalOpenTickets: number;
  totalInProgressTickets: number;
  totalResolvedTickets: number;
  lowStockItemsCount: number;
  totalTickets: number;
  totalClosedTickets: number;
  ticketsByStatus: MetricDTO[];
  ticketsByCategory: MetricDTO[];
  ticketsBySector: MetricDTO[];
  ticketsByRequester: MetricDTO[];
  inventorySummary: InventorySummaryDTO;
  totalAssets: number;
  assetsInUse: number;
  assetsInStock: number;
}

export type FinanceReceiptStatus = 'SENT' | 'HISTORICO' | 'PENDING_RETRY' | 'FAILED' | 'SKIPPED_DUPLICATE';

export interface FinanceReceipt {
  id: string;
  parcelaId: string;
  originalRecipientEmail: string;
  status: FinanceReceiptStatus;
  processedAt: string;
  payload: Record<string, unknown> | null;
}

export interface FinanceAlert {
  id: string;
  title: string;
  details: string;
  resolved: boolean;
  createdAt: string;
  context: Record<string, unknown> | null;
}

export interface FinanceConnectionStatus {
  authorized: boolean;
  expiresAt: string | null;
  refreshedAt: string | null;
}

export interface DoctorMapping {
  id: string;
  userId: string | null;
  userContaAzulId: string | null;
  doctorName: string | null;
  contaAzulCustomerUuid: string;
  doctorEmail: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface CreateDoctorMappingDTO {
  userId?: string;
  doctorName?: string;
  contaAzulCustomerUuid: string;
  doctorEmail?: string;
}

export interface ContaAzulCustomerCheckResponse {
  email: string;
  customerId: string | null;
  message?: string;
}

export interface ContaAzulCustomerEmailResponse {
  customerId: string;
  email: string | null;
}

export interface SyncDoctorsResponse {
  novos: number;
  atualizados: number;
}

export interface FinancialSummaryDTO {
  balanceCents: number;
  totalPendingCents: number;
  totalPaidCents: number;
  currency: string;
  syncedReceiptsCount: number;
  externalServiceAvailable?: boolean;
}

export interface FinancialTransactionLineDTO {
  transactionId: string;
  date: string;
  targetType: 'DOCTOR' | 'SECTOR';
  destination: string;
  item: string;
  quantity: number;
  amountCents: number;
}

export interface AuthResponseDTO {
  status: 'AUTHENTICATED' | 'PASSWORD_RESET_REQUIRED';
  token: string | null;
  tempToken: string | null;
  userId: string | null;
  user: User | null;
}

export interface ResetInitialPasswordRequestDTO {
  tempToken: string;
  userId: string;
  newPassword: string;
}

export interface ChangePasswordRequestDTO {
  currentPassword: string;
  newPassword: string;
}

export interface TwoFactorGenerateResponseDTO {
  qrCodeBase64: string;
  otpauthUrl: string;
}

export interface TwoFactorVerifyRequestDTO {
  code: string;
}

export interface VaultItem {
  id: string;
  title: string;
  description: string | null;
  itemType: 'CREDENTIAL' | 'DOCUMENT' | 'NOTE';
  filePath: string | null;
  ownerId: string;
  sharingType: 'PRIVATE' | 'ALL_TECH_ADMIN' | 'CUSTOM';
  createdAt: string;
  updatedAt: string;
}

export interface VaultCreateItemRequestDTO {
  title: string;
  description?: string;
  itemType: 'CREDENTIAL' | 'DOCUMENT' | 'NOTE';
  secretContent?: string;
  sharingType: 'PRIVATE' | 'ALL_TECH_ADMIN' | 'CUSTOM';
  sharedWithUserIds?: string[];
}

export interface VaultUpdateItemRequestDTO {
  title: string;
  description?: string;
  itemType: 'CREDENTIAL' | 'DOCUMENT' | 'NOTE';
  secretContent?: string;
  sharingType: 'PRIVATE' | 'ALL_TECH_ADMIN' | 'CUSTOM';
  sharedWithUserIds?: string[];
}

export interface VaultSecretResponseDTO {
  itemId: string;
  secretContent: string;
}

export interface TicketComment {
  id: string;
  content: string;
  authorId: string;
  authorName: string;
  createdAt: string;
}

export interface Notification {
  id: string;
  title: string;
  message: string;
  isRead: boolean;
  link: string | null;
  createdAt: string;
}

export interface ExecuteFinanceAutomationNowParams {
  dataInicio: string;
  dataFim: string;
}

export interface SystemSetting {
  id: string;
  value: string;
  description: string | null;
}

export type UpdateSystemSettingsPayload = Record<string, string>;

export interface AdminConfig {
  smtpFromEmail: string;
  smtpFromName: string;
  discordBotEnabled: boolean;
  discordWebhookPresent: boolean;
  discordWebhookStatus?: string;
}

export interface ImportResult {
  success: boolean;
  usersCreated: number;
  sectorsCreated: number;
  assetsCreated: number;
  categoriesCreated: number;
  errors: string[];
}

export type AuditAction =
  | 'VAULT_LOGIN_SUCCESS'
  | 'VAULT_LOGIN_FAILURE'
  | 'VAULT_SECRET_VIEW'
  | 'VAULT_FILE_VIEW'
  | 'VAULT_ITEM_CREATE'
  | 'VAULT_ITEM_VIEW'
  | 'VAULT_ITEM_EDIT'
  | 'VAULT_ITEM_DELETE'
  | 'VAULT_AUTH_SUCCESS'
  | 'VAULT_AUTH_FAIL'
  | 'LOGIN_SUCCESS'
  | 'LOGIN_FAILURE'
  | 'TWO_FACTOR_RESET'
  | 'TWO_FACTOR_ADMIN_RESET'
  | 'USER_2FA_ADMIN_RESET'
  | 'TICKET_OPEN'
  | 'TICKET_ASSIGN'
  | 'TICKET_TRANSFER'
  | 'TICKET_RESOLVE'
  | 'INVENTORY_BATCH_ENTRY'
  | 'INVENTORY_ITEM_CREATE'
  | 'STOCK_BATCH_CREATE'
  | 'ITEM_CREATE'
  | 'ASSET_CREATE'
  | 'ASSET_EDIT'
  | 'ASSET_INVOICE_ATTACH'
  | 'QR_SCAN'
  | 'ASSET_QR_SCAN'
  | 'KB_ARTICLE_DRAFT_CREATE'
  | 'KB_ARTICLE_PUBLISH'
  | 'KB_ARTICLE_EDIT'
  | 'ARTICLE_POST_PUBLIC'
  | 'ARTICLE_POST_DRAFT'
  | 'ARTICLE_EDIT'
  | 'SECTOR_CREATE'
  | 'USER_CREATE'
  | 'USER_UPDATE'
  | 'USER_EDIT'
  | 'USER_PASSWORD_RESET'
  | 'USER_PERMISSION_CHANGE'
  | 'USER_PASSWORD_ADMIN_RESET'
  | 'PROFILE_PASSWORD_CHANGE';

export interface AuditLog {
  id: string;
  userId: string | null;
  userName: string | null;
  action: AuditAction;
  resourceType: string | null;
  resourceId: string | null;
  details: string | null;
  ipAddress: string | null;
  createdAt: string;
}

export interface AuditLogPage {
  content: AuditLog[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
  first: boolean;
  last: boolean;
}

export interface GetAuditLogsParams {
  userId?: string;
  action?: AuditAction;
  startDate?: string;
  endDate?: string;
  page?: number;
  size?: number;
}
