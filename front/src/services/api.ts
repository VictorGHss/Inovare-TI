// Instância do Axios configurada para a API do Inovare TI
import axios from 'axios';

// Attachment in ticket response
export interface AttachmentResponse {
  id: string;
  originalFilename: string;
  fileUrl: string;
  fileType: string;
}

// Espelha exatamente o TicketResponseDTO retornado pelo backend
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
  receives_it_notifications: boolean;
}

export interface UpdateUserDto {
  name: string;
  email: string;
  role: 'ADMIN' | 'TECHNICIAN' | 'USER';
  sectorId: string;
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

const api = axios.create({
  baseURL: import.meta.env.VITE_API_URL,
});

// Interceptor: injeta o token JWT em todas as requisições autenticadas
api.interceptors.request.use((config) => {
  const token = localStorage.getItem('@InovareTI:token');
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

// Busca todos os tickets do usuário autenticado
export async function getTickets(): Promise<Ticket[]> {
  const { data } = await api.get<Ticket[]>('/api/tickets');
  return data;
}

// Busca todas as categorias de chamado disponíveis
export async function getTicketCategories(): Promise<TicketCategory[]> {
  const { data } = await api.get<TicketCategory[]>('/api/ticket-categories');
  return data;
}

// Busca todos os itens de inventário disponíveis
export async function getItems(): Promise<Item[]> {
  const { data } = await api.get<Item[]>('/api/items');
  return data;
}

// Busca um item de inventário específico pelo UUID
export async function getItemById(id: string): Promise<Item> {
  const { data } = await api.get<Item>(`/api/items/${id}`);
  return data;
}

// Busca todos os lotes de estoque de um item específico
export async function getItemBatches(id: string): Promise<Batch[]> {
  const { data } = await api.get<Batch[]>(`/api/items/${id}/batches`);
  return data;
}

export async function getItemOutMovements(id: string): Promise<StockMovement[]> {
  const { data } = await api.get<StockMovement[]>(`/api/items/${id}/movements/out`);
  return data;
}

// Busca todas as categorias de item de inventário
export async function getItemCategories(): Promise<ItemCategory[]> {
  const { data } = await api.get<ItemCategory[]>('/api/item-categories');
  return data;
}

export async function createItemCategory(dto: CreateItemCategoryDto): Promise<ItemCategory> {
  const { data } = await api.post<ItemCategory>('/api/item-categories', dto);
  return data;
}

// Cria um novo item de inventário
export async function createItem(dto: CreateItemDto): Promise<Item> {
  const { data } = await api.post<Item>('/api/items', dto);
  return data;
}

// Registra um lote de entrada de estoque para um item
export async function addBatch(itemId: string, dto: CreateBatchDto): Promise<Batch> {
  const { data } = await api.post<Batch>(`/api/items/${itemId}/batches`, dto);
  return data;
}

// Cria um novo chamado
export async function createTicket(dto: CreateTicketDto): Promise<Ticket> {
  const { data } = await api.post<Ticket>('/api/tickets', dto);
  return data;
}

// Busca um chamado específico pelo UUID
export async function getTicketById(id: string): Promise<Ticket> {
  const { data } = await api.get<Ticket>(`/api/tickets/${id}`);
  return data;
}

// Resolve um chamado existente pelo UUID
export async function resolveTicket(id: string, request: ResolveTicketRequest): Promise<Ticket> {
  const { data } = await api.patch<Ticket>(`/api/tickets/${id}/resolve`, request);
  return data;
}

// Assume um chamado para o usuário autenticado e muda para IN_PROGRESS
export async function claimTicket(id: string): Promise<Ticket> {
  const { data } = await api.patch<Ticket>(`/api/tickets/${id}/claim`);
  return data;
}

// Transfere um chamado para outro usuário
export async function transferTicket(id: string, userId: string): Promise<Ticket> {
  const { data } = await api.patch<Ticket>(`/api/tickets/${id}/transfer/${userId}`);
  return data;
}

// Busca todos os usuários cadastrados (requer ADMIN)
export async function getUsers(): Promise<User[]> {
  const { data } = await api.get<User[]>('/api/users');
  return data;
}

// Cria um novo usuário (requer ADMIN)
export async function createUser(dto: CreateUserDto): Promise<User> {
  const { data } = await api.post<User>('/api/users', dto);
  return data;
}

// Atualiza dados de um usuário (requer ADMIN)
export async function updateUser(id: string, dto: UpdateUserDto): Promise<User> {
  const { data } = await api.put<User>(`/api/users/${id}`, dto);
  return data;
}

// Redefine a senha de um usuário para o padrão Mudar@123 (requer ADMIN)
export async function resetUserPassword(id: string): Promise<void> {
  await api.post(`/api/users/${id}/reset-password`);
}

// Busca todos os setores cadastrados (requer ADMIN)
export async function getSectors(): Promise<Sector[]> {
  const { data } = await api.get<Sector[]>('/api/sectors');
  return data;
}

// Cria um novo setor (requer ADMIN)
export async function createSector(dto: CreateSectorDto): Promise<Sector> {
  const { data } = await api.post<Sector>('/api/sectors', dto);
  return data;
}

export async function getAssets(params?: GetAssetsParams): Promise<Asset[]> {
  const sanitizedParams = Object.fromEntries(
    Object.entries(params ?? {}).filter(([, value]) => value !== undefined && value !== null && value !== ''),
  );

  const { data } = await api.get<Asset[]>('/api/assets', {
    params: sanitizedParams,
  });
  return data;
}

export async function getAssetById(id: string): Promise<Asset> {
  const { data } = await api.get<Asset>(`/api/assets/${id}`);
  return data;
}

export async function getAssetsByUser(userId: string): Promise<Asset[]> {
  const { data } = await api.get<Asset[]>(`/api/assets/user/${userId}`);
  return data;
}

export async function getAssetCategories(): Promise<AssetCategory[]> {
  const { data } = await api.get<AssetCategory[]>('/api/asset-categories');
  return data;
}

export async function createAssetCategory(dto: CreateAssetCategoryDto): Promise<AssetCategory> {
  const { data } = await api.post<AssetCategory>('/api/asset-categories', dto);
  return data;
}

export async function createAsset(dto: CreateAssetDto): Promise<Asset> {
  const { data } = await api.post<Asset>('/api/assets', dto);
  return data;
}

export async function updateAsset(id: string, dto: CreateAssetDto): Promise<Asset> {
  const { data } = await api.patch<Asset>(`/api/assets/${id}`, dto);
  return data;
}

export async function deleteAsset(id: string): Promise<void> {
  await api.delete(`/api/assets/${id}`);
}

// Asset Maintenance methods
export async function getAssetMaintenances(assetId: string): Promise<AssetMaintenance[]> {
  const { data } = await api.get<AssetMaintenance[]>(`/api/assets/${assetId}/maintenances`);
  return data;
}

export async function createAssetMaintenance(assetId: string, dto: CreateAssetMaintenanceData): Promise<AssetMaintenance> {
  const { data } = await api.post<AssetMaintenance>(`/api/assets/${assetId}/maintenances`, dto);
  return data;
}

export async function transferAsset(assetId: string, dto: TransferAssetData): Promise<Asset> {
  const { data } = await api.patch<Asset>(`/api/assets/${assetId}/transfer`, dto);
  return data;
}

// Faz upload de um anexo para um chamado específico
export async function uploadTicketAttachment(ticketId: string, file: File): Promise<TicketAttachment> {
  const formData = new FormData();
  formData.append('file', file);
  
  const { data } = await api.post<TicketAttachment>(`/api/tickets/${ticketId}/attachments`, formData, {
    headers: {
      'Content-Type': 'multipart/form-data',
    },
  });
  return data;
}

// Lista todos os anexos de um chamado específico
export async function getTicketAttachments(ticketId: string): Promise<TicketAttachment[]> {
  const { data } = await api.get<TicketAttachment[]>(`/api/tickets/${ticketId}/attachments`);
  return data;
}

// Interface para dashboard analytics
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

// Comentário de ticket
export interface TicketComment {
  id: string;
  content: string;
  authorId: string;
  authorName: string;
  createdAt: string;
}

// Notificação de usuario
export interface Notification {
  id: string;
  title: string;
  message: string;
  isRead: boolean;
  link: string | null;
  createdAt: string;
}

// Busca métricas agregadas do dashboard
export async function getDashboardAnalytics(): Promise<DashboardAnalyticsDTO> {
  const { data } = await api.get<DashboardAnalyticsDTO>('/api/analytics/dashboard');
  return data;
}

export async function resetInitialPassword(
  payload: ResetInitialPasswordRequestDTO,
): Promise<AuthResponseDTO> {
  const { data } = await api.post<AuthResponseDTO>('/api/auth/reset-initial-password', payload);
  return data;
}

export async function changePassword(currentPassword: string, newPassword: string): Promise<void> {
  const payload: ChangePasswordRequestDTO = { currentPassword, newPassword };
  await api.put('/api/users/me/password', payload);
}

export async function generate2FA(): Promise<TwoFactorGenerateResponseDTO> {
  const { data } = await api.post<TwoFactorGenerateResponseDTO>('/api/auth/2fa/generate');
  return data;
}

export async function verify2FA(code: string): Promise<AuthResponseDTO> {
  const payload: TwoFactorVerifyRequestDTO = { code };
  const { data } = await api.post<AuthResponseDTO>('/api/auth/2fa/verify', payload);
  return data;
}

// Solicita o código de recuperação do 2FA — código enviado ao Discord do usuário
export async function request2FAReset(): Promise<void> {
  await api.post('/api/auth/2fa/reset-request');
}

// Confirma a recuperação do 2FA com código + senha atual; retorna novo JWT com 2FA limpo
export async function confirm2FAReset(code: string, password: string): Promise<AuthResponseDTO> {
  const { data } = await api.post<AuthResponseDTO>('/api/auth/2fa/reset-confirm', { code, password });
  return data;
}

// Reseta o 2FA de outro usuário diretamente (somente ADMIN)
export async function adminReset2FA(userId: string): Promise<void> {
  await api.patch(`/api/users/${userId}/2fa/reset`);
}

export async function getVaultItems(): Promise<VaultItem[]> {
  const { data } = await api.get<VaultItem[]>('/api/vault');
  return data;
}

export async function createVaultItem(
  payload: VaultCreateItemRequestDTO,
  file?: File | null,
): Promise<VaultItem> {
  const formData = new FormData();
  formData.append('payload', JSON.stringify(payload));

  if (file) {
    formData.append('file', file);
  }

  const { data } = await api.post<VaultItem>('/api/vault', formData, {
    headers: {
      'Content-Type': 'multipart/form-data',
    },
  });

  return data;
}

export async function updateVaultItem(
  vaultItemId: string,
  payload: VaultUpdateItemRequestDTO,
  file?: File | null,
): Promise<VaultItem> {
  const formData = new FormData();
  formData.append('payload', JSON.stringify(payload));

  if (file) {
    formData.append('file', file);
  }

  const { data } = await api.patch<VaultItem>(`/api/vault/${vaultItemId}`, formData, {
    headers: {
      'Content-Type': 'multipart/form-data',
    },
  });

  return data;
}

export async function deleteVaultItem(vaultItemId: string): Promise<void> {
  await api.delete(`/api/vault/${vaultItemId}`);
}

export async function shareVaultItem(vaultItemId: string, userId: string): Promise<void> {
  await api.post(`/api/vault/${vaultItemId}/share`, { userId });
}

export async function getVaultItemSecret(vaultItemId: string): Promise<VaultSecretResponseDTO> {
  const { data } = await api.get<VaultSecretResponseDTO>(`/api/vault/${vaultItemId}/secret`);
  return data;
}

export async function getVaultItemFileBlob(vaultItemId: string): Promise<Blob> {
  const { data } = await api.get(`/api/vault/${vaultItemId}/file`, {
    responseType: 'blob',
  });
  return data;
}

// Exporta relatório de tickets em Excel
export async function exportTicketsReport(): Promise<Blob> {
  const { data } = await api.get('/api/reports/tickets', {
    responseType: 'blob',
  });
  return data;
}

// Exporta relatório de entradas de estoque (compras)
export async function exportInventoryEntriesReport(): Promise<Blob> {
  const { data } = await api.get('/api/reports/inventory/entries', {
    responseType: 'blob',
  });
  return data;
}

// Exporta relatório de saídas de estoque (consumo)
export async function exportInventoryExitsReport(): Promise<Blob> {
  const { data } = await api.get('/api/reports/inventory/exits', {
    responseType: 'blob',
  });
  return data;
}

// Adiciona um novo comentário a um chamado
export async function addTicketComment(ticketId: string, content: string): Promise<TicketComment> {
  const { data } = await api.post<TicketComment>(`/api/tickets/${ticketId}/comments`, { content });
  return data;
}

// Lista todos os comentários de um chamado
export async function getTicketComments(ticketId: string): Promise<TicketComment[]> {
  const { data } = await api.get<TicketComment[]>(`/api/tickets/${ticketId}/comments`);
  return data;
}

// ==================== KNOWLEDGE BASE (Articles) ====================

// Busca todos os artigos da base de conhecimento
export async function getArticles(): Promise<Article[]> {
  const { data } = await api.get<Article[]>('/api/articles');
  return data;
}

// Busca um artigo específico pelo ID
export async function getArticleById(id: string): Promise<Article> {
  const { data } = await api.get<Article>(`/api/articles/${id}`);
  return data;
}

// Cria um novo artigo (requer ADMIN ou TECHNICIAN)
export async function createArticle(dto: CreateArticleDto): Promise<Article> {
  const { data } = await api.post<Article>('/api/articles', dto);
  return data;
}

export async function updateArticle(id: string, dto: CreateArticleDto): Promise<Article> {
  const { data } = await api.put<Article>(`/api/articles/${id}`, dto);
  return data;
}

// Busca artigos por título (para Ticket Deflection)
export async function searchArticles(query: string): Promise<ArticleSearchResult[]> {
  if (!query || query.trim().length === 0) {
    return [];
  }
  const { data } = await api.get<ArticleSearchResult[]>('/api/articles/search', {
    params: { query: query.trim() },
  });
  return data;
}

// ==================== GENERIC FILE UPLOAD ====================

// Faz upload genérico de um arquivo (imagem, documento, etc.)
export async function uploadGenericFile(file: File): Promise<GenericAttachmentResponse> {
  const formData = new FormData();
  formData.append('file', file);
  
  const { data } = await api.post<GenericAttachmentResponse>('/api/attachments/upload', formData, {
    headers: {
      'Content-Type': 'multipart/form-data',
    },
  });
  return data;
}

// ==================== NOTIFICATIONS ====================

// Busca todas as notificações do usuário autenticado (lidas e não lidas)
export async function getNotifications(): Promise<Notification[]> {
  const { data } = await api.get<Notification[]>('/api/notifications');
  return data;
}

// Busca todas as notificações não lidas do usuário autenticado
export async function getUnreadNotifications(): Promise<Notification[]> {
  const { data } = await api.get<Notification[]>('/api/notifications/unread');
  return data;
}

// Marca uma notificação como lida
export async function markNotificationAsRead(id: string): Promise<Notification> {
  const { data } = await api.patch<Notification>(`/api/notifications/${id}/read`);
  return data;
}

// ==================== INVOICE UPLOAD (ASSETS & BATCHES) ====================

/**
 * Faz upload de uma nota fiscal (PDF ou imagem) para um ativo.
 * @param assetId UUID do Asset
 * @param file    Arquivo a fazer upload (PDF, JPG, PNG)
 * @returns       Asset atualizado com informações de nota fiscal
 */
export async function uploadAssetInvoice(assetId: string, file: File): Promise<Asset> {
  const formData = new FormData();
  formData.append('file', file);

  const { data } = await api.post<Asset>(`/api/assets/${assetId}/invoice`, formData, {
    headers: {
      'Content-Type': 'multipart/form-data',
    },
  });
  return data;
}

/**
 * Faz download de uma nota fiscal de um ativo.
 * @param assetId UUID do Asset
 * @returns       Blob com o arquivo (PDF ou imagem)
 */
export async function downloadAssetInvoice(assetId: string): Promise<Blob> {
  const { data } = await api.get(`/api/assets/${assetId}/invoice`, {
    responseType: 'blob',
  });
  return data;
}

/**
 * Faz upload de uma nota fiscal (PDF ou imagem) para um lote de estoque.
 * @param itemId  UUID do Item
 * @param batchId UUID do StockBatch
 * @param file    Arquivo a fazer upload (PDF, JPG, PNG)
 * @returns       Lote atualizado com informações de nota fiscal
 */
export async function uploadBatchInvoice(
  itemId: string,
  batchId: string,
  file: File
): Promise<Batch> {
  const formData = new FormData();
  formData.append('file', file);

  const { data } = await api.post<Batch>(
    `/api/items/${itemId}/batches/${batchId}/invoice`,
    formData,
    {
      headers: {
        'Content-Type': 'multipart/form-data',
      },
    }
  );
  return data;
}

/**
 * Faz download de uma nota fiscal de um lote de estoque.
 * @param itemId  UUID do Item
 * @param batchId UUID do StockBatch
 * @returns       Blob com o arquivo (PDF ou imagem)
 */
export async function downloadBatchInvoice(itemId: string, batchId: string): Promise<Blob> {
  const { data } = await api.get(`/api/items/${itemId}/batches/${batchId}/invoice`, {
    responseType: 'blob',
  });
  return data;
}

export interface SystemSetting {
  id: string;
  value: string;
  description: string | null;
}

export type UpdateSystemSettingsPayload = Record<string, string>;

export async function getSystemSettings(): Promise<SystemSetting[]> {
  const { data } = await api.get<SystemSetting[]>('/api/admin/settings');
  return data;
}

export async function updateSystemSettings(payload: UpdateSystemSettingsPayload): Promise<SystemSetting[]> {
  const { data } = await api.put<SystemSetting[]>('/api/admin/settings', payload);
  return data;
}

// ============================================================================
// ADMIN / BULK IMPORT
// ============================================================================

export interface ImportResult {
  success: boolean;
  usersCreated: number;
  sectorsCreated: number;
  assetsCreated: number;
  categoriesCreated: number;
  errors: string[];
}

export async function logQrScan(scannedPath: string): Promise<void> {
  await api.post('/api/audit-logs/qr-scan', { scannedPath });
}

/**
 * Imports users and assets from CSV file.
 */
export async function importCsv(file: File): Promise<ImportResult> {
  const formData = new FormData();
  formData.append('file', file);
  
  const response = await api.post<ImportResult>('/api/admin/import/csv', formData, {
    headers: {
      'Content-Type': 'multipart/form-data',
    },
  });
  return response.data;
}

// ==================== AUDIT LOGS ====================

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

// Busca logs de auditoria com filtros opcionais (requer ADMIN)
export async function getAuditLogs(params?: GetAuditLogsParams): Promise<AuditLogPage> {
  const sanitized = Object.fromEntries(
    Object.entries(params ?? {}).filter(([, v]) => v !== undefined && v !== null && v !== ''),
  );
  const { data } = await api.get<AuditLogPage>('/api/audit-logs', { params: sanitized });
  return data;
}

export default api;

