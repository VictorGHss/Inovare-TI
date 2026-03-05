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

export interface Asset {
  id: string;
  userId: string;
    assignedToName: string | null;
  name: string;
  patrimonyCode: string;
  specifications: string | null;
  createdAt: string;
  invoiceFileName?: string;
  invoiceContentType?: string;
  invoiceFilePath?: string;
}

export interface CreateAssetDto {
  userId: string;
  name: string;
  patrimonyCode: string;
  specifications?: string;
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
  createdAt: string;
  updatedAt: string | null;
}

export interface CreateArticleDto {
  title: string;
  content: string;
  tags?: string;
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

// Busca todas as categorias de item de inventário
export async function getItemCategories(): Promise<ItemCategory[]> {
  const { data } = await api.get<ItemCategory[]>('/api/item-categories');
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
export async function resolveTicket(id: string): Promise<Ticket> {
  const { data } = await api.patch<Ticket>(`/api/tickets/${id}/resolve`);
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

export async function getAssets(): Promise<Asset[]> {
  const { data } = await api.get<Asset[]>('/api/assets');
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

/**
 * Imports users and assets from CSV file.
 */
export async function importCsv(file: File): Promise<ImportResult> {
  const formData = new FormData();
  formData.append('file', file);
  
  const response = await api.post<ImportResult>('/admin/import/csv', formData, {
    headers: {
      'Content-Type': 'multipart/form-data',
    },
  });
  return response.data;
}

export default api;
