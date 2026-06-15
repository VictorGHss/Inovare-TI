import api from './api';
import type { Page, AdminConfig, Article, ArticleSearchResult, Asset, AssetCategory, AssetMaintenance, AuditLogPage, Batch, CreateArticleDto, CreateAssetCategoryDto, CreateAssetDto, CreateAssetMaintenanceData, CreateBatchDto, CreateItemCategoryDto, CreateItemDto, CreateTicketCategoryDto, FinancialTransactionLineDTO, GenericAttachmentResponse, GetAssetsParams, GetAuditLogsParams, Item, ItemCategory, Notification, StockMovement, SystemSetting, TicketCategoryResponse, TransferAssetData, UpdateSystemSettingsPayload } from '../types/models';

// Serviço centralizado para operações de inventário, ativos e relatórios operacionais.

export interface GetItemsParams {
  sortField?: 'name' | 'currentStock' | 'oldestBatchEntryDate';
  sortDirection?: 'ASC' | 'DESC';
  lowStockOnly?: boolean;
  page?: number;
  size?: number;
  search?: string;
}

// Busca todos os itens de inventário disponíveis
export async function getItems(params?: GetItemsParams): Promise<Page<Item>> {
  const { data } = await api.get<Page<Item>>('/items', {
    params,
  });
  return data;
}

// Busca todos os itens obsoletos (End of Life) de forma paginada
export async function getObsoleteItems(params?: { page?: number }): Promise<Page<Item>> {
  const { data } = await api.get<Page<Item>>('/items/obsolete', {
    params,
  });
  return data;
}

// Busca um item de inventário específico pelo UUID
export async function getItemById(id: string): Promise<Item> {
  const { data } = await api.get<Item>(`/items/${id}`);
  return data;
}

// Busca todos os lotes de estoque de um item específico
export async function getItemBatches(id: string): Promise<Batch[]> {
  const { data } = await api.get<Batch[]>(`/items/${id}/batches`);
  return data;
}

export async function getItemOutMovements(id: string): Promise<StockMovement[]> {
  const { data } = await api.get<StockMovement[]>(`/items/${id}/movements/out`);
  return data;
}

// Busca todas as categorias de item de inventário
export async function getItemCategories(): Promise<ItemCategory[]> {
  const { data } = await api.get<ItemCategory[]>('/item-categories');
  return data;
}

export async function createItemCategory(dto: CreateItemCategoryDto): Promise<ItemCategory> {
  const { data } = await api.post<ItemCategory>('/item-categories', dto);
  return data;
}

export async function deleteItemCategory(id: string): Promise<void> {
  await api.delete(`/item-categories/${id}`);
}

// Cria um novo item de inventário
export async function createItem(dto: CreateItemDto): Promise<Item> {
  const { data } = await api.post<Item>('/items', dto);
  return data;
}

// Registra um lote de entrada de estoque para um item
export async function addBatch(itemId: string, dto: CreateBatchDto): Promise<Batch> {
  const { data } = await api.post<Batch>(`/items/${itemId}/batches`, dto);
  return data;
}

export async function getAssets(params?: GetAssetsParams): Promise<Page<Asset>> {
  const sanitizedParams = Object.fromEntries(
    Object.entries(params ?? {}).filter(([, value]) => value !== undefined && value !== null && value !== ''),
  );

  const { data } = await api.get<Page<Asset>>('/assets', {
    params: sanitizedParams,
  });
  return data;
}

export async function getAssetById(id: string): Promise<Asset> {
  const { data } = await api.get<Asset>(`/assets/${id}`);
  return data;
}

export async function getAssetsByUser(userId: string): Promise<Asset[]> {
  const { data } = await api.get<Asset[]>(`/assets/user/${userId}`);
  return data;
}

export async function getAssetCategories(): Promise<AssetCategory[]> {
  const { data } = await api.get<AssetCategory[]>('/asset-categories');
  return data;
}

export async function createAssetCategory(dto: CreateAssetCategoryDto): Promise<AssetCategory> {
  const { data } = await api.post<AssetCategory>('/asset-categories', dto);
  return data;
}

export async function deleteAssetCategory(id: string): Promise<void> {
  await api.delete(`/asset-categories/${id}`);
}

export async function createAsset(dto: CreateAssetDto): Promise<Asset> {
  const { data } = await api.post<Asset>('/assets', dto);
  return data;
}

export async function updateAsset(id: string, dto: CreateAssetDto): Promise<Asset> {
  const { data } = await api.patch<Asset>(`/assets/${id}`, dto);
  return data;
}

export async function deleteAsset(id: string): Promise<void> {
  await api.delete(`/assets/${id}`);
}

// Asset Maintenance methods
export async function getAssetMaintenances(assetId: string): Promise<AssetMaintenance[]> {
  const { data } = await api.get<AssetMaintenance[]>(`/assets/${assetId}/maintenances`);
  return data;
}

export async function createAssetMaintenance(assetId: string, dto: CreateAssetMaintenanceData): Promise<AssetMaintenance> {
  const { data } = await api.post<AssetMaintenance>(`/assets/${assetId}/maintenances`, dto);
  return data;
}

export async function transferAsset(assetId: string, dto: TransferAssetData): Promise<Asset> {
  const { data } = await api.patch<Asset>(`/assets/${assetId}/transfer`, dto);
  return data;
}

// Lista transações de consumo interno por período.
export async function getFinancialTransactions(params?: { startDate?: string; endDate?: string }): Promise<FinancialTransactionLineDTO[]> {
  const { data } = await api.get<FinancialTransactionLineDTO[]>('/financial/transactions', { params });
  return data;
}

// ==================== KNOWLEDGE BASE (Articles) ====================

// Busca todos os artigos da base de conhecimento
export async function getArticles(): Promise<Article[]> {
  const { data } = await api.get<Article[]>('/articles');
  return data;
}

// Busca um artigo específico pelo ID
export async function getArticleById(id: string): Promise<Article> {
  const { data } = await api.get<Article>(`/articles/${id}`);
  return data;
}

// Cria um novo artigo (requer ADMIN ou TECHNICIAN)
export async function createArticle(dto: CreateArticleDto): Promise<Article> {
  const { data } = await api.post<Article>('/articles', dto);
  return data;
}

export async function updateArticle(id: string, dto: CreateArticleDto): Promise<Article> {
  const { data } = await api.put<Article>(`/articles/${id}`, dto);
  return data;
}

// Busca artigos por título (para Ticket Deflection)
export async function searchArticles(query: string): Promise<ArticleSearchResult[]> {
  if (!query || query.trim().length === 0) {
    return [];
  }
  const { data } = await api.get<ArticleSearchResult[]>('/articles/search', {
    params: { query: query.trim() },
  });
  return data;
}

// ==================== GENERIC FILE UPLOAD ====================

// Faz upload genérico de um arquivo (imagem, documento, etc.)
export async function uploadGenericFile(file: File): Promise<GenericAttachmentResponse> {
  const formData = new FormData();
  formData.append('file', file);
  
  const { data } = await api.post<GenericAttachmentResponse>('/attachments/upload', formData, {
    headers: {
      'Content-Type': 'multipart/form-data',
    },
  });
  return data;
}

// ==================== NOTIFICATIONS ====================

// Busca todas as notificações do usuário autenticado (lidas e não lidas)
export async function getNotifications(): Promise<Notification[]> {
  const { data } = await api.get<Notification[]>('/notifications');
  return data;
}

// Busca todas as notificações não lidas do usuário autenticado
export async function getUnreadNotifications(): Promise<Notification[]> {
  const { data } = await api.get<Notification[]>('/notifications/unread');
  return data;
}

// Marca uma notificação como lida
export async function markNotificationAsRead(id: string): Promise<Notification> {
  const { data } = await api.patch<Notification>(`/notifications/${id}/read`);
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

  const { data } = await api.post<Asset>(`/assets/${assetId}/invoice`, formData, {
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
  const { data } = await api.get(`/assets/${assetId}/invoice`, {
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
    `/items/${itemId}/batches/${batchId}/invoice`,
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
  const { data } = await api.get(`/items/${itemId}/batches/${batchId}/invoice`, {
    responseType: 'blob',
  });
  return data;
}

export async function getAdminConfig(): Promise<AdminConfig> {
  const { data } = await api.get<AdminConfig>('/admin/config');
  return data;
}

export async function getSystemSettings(): Promise<SystemSetting[]> {
  const { data } = await api.get<SystemSetting[]>('/admin/settings');
  return data;
}

export async function updateSystemSettings(payload: UpdateSystemSettingsPayload): Promise<SystemSetting[]> {
  const { data } = await api.put<SystemSetting[]>('/admin/settings', payload);
  return data;
}

export async function logQrScan(scannedPath: string): Promise<void> {
  await api.post('/audit-logs/qr-scan', { scannedPath });
}

// Busca logs de auditoria com filtros opcionais (requer ADMIN)
export async function getAuditLogs(params?: GetAuditLogsParams): Promise<AuditLogPage> {
  const sanitized = Object.fromEntries(
    Object.entries(params ?? {}).filter(([, v]) => v !== undefined && v !== null && v !== ''),
  );
  const { data } = await api.get<AuditLogPage>('/audit-logs', { params: sanitized });
  return data;
}

// ==================== TICKET CATEGORIES ====================

/** Lista todas as categorias de chamado cadastradas. */
export async function getTicketCategories(): Promise<TicketCategoryResponse[]> {
  const { data } = await api.get<TicketCategoryResponse[]>('/ticket-categories');
  return data;
}

/** Cria uma nova categoria de chamado com SLA base em horas. */
export async function createTicketCategory(dto: CreateTicketCategoryDto): Promise<TicketCategoryResponse> {
  const { data } = await api.post<TicketCategoryResponse>('/ticket-categories', dto);
  return data;
}

/** Exclui uma categoria de chamado pelo UUID. Retorna 409 se houver tickets vinculados. */
export async function deleteTicketCategory(id: string): Promise<void> {
  await api.delete(`/ticket-categories/${id}`);
}
