import axios from 'axios';
import api from './api';
import type { AdminConfig, Article, ArticleSearchResult, Asset, AssetCategory, AssetMaintenance, AuditLogPage, Batch, ContaAzulCustomerEmailResponse, CreateArticleDto, CreateAssetCategoryDto, CreateAssetDto, CreateAssetMaintenanceData, CreateBatchDto, CreateDoctorMappingDTO, CreateItemCategoryDto, CreateItemDto, DashboardAnalyticsDTO, DoctorMapping, ExecuteFinanceAutomationNowParams, FinanceAlert, FinanceConnectionStatus, FinanceReceipt, FinancialSummaryDTO, FinancialTransactionLineDTO, GenericAttachmentResponse, GetAssetsParams, GetAuditLogsParams, Item, ItemCategory, Notification, ReportSchedule, StockMovement, SyncDoctorsResponse, SystemSetting, TransferAssetData, UpdateSystemSettingsPayload, VaultCreateItemRequestDTO, VaultItem, VaultSecretResponseDTO, VaultUpdateItemRequestDTO } from '../types/domain';

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

export async function getReportSchedules(): Promise<ReportSchedule[]> {
  const { data } = await api.get<ReportSchedule[]>('/api/report-schedules');
  return data;
}

export async function createReportSchedule(payload: Partial<ReportSchedule>): Promise<ReportSchedule> {
  const { data } = await api.post<ReportSchedule>('/api/report-schedules', payload);
  return data;
}

export async function updateReportSchedule(id: string, payload: Partial<ReportSchedule>): Promise<ReportSchedule> {
  const { data } = await api.put<ReportSchedule>(`/api/report-schedules/${id}`, payload);
  return data;
}

export async function deleteReportSchedule(id: string): Promise<void> {
  await api.delete(`/api/report-schedules/${id}`);
}

export async function triggerReportScheduleTest(id: string): Promise<void> {
  await api.post(`/api/report-schedules/${id}/trigger-test`);
}

// Deleção de categorias (frontend chama essas rotas — backend deve expor DELETE /api/item-categories/{id} e /api/asset-categories/{id})
export async function deleteItemCategory(id: string): Promise<void> {
  await api.delete(`/api/item-categories/${id}`);
}

export async function deleteAssetCategory(id: string): Promise<void> {
  await api.delete(`/api/asset-categories/${id}`);
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

export async function getFinancialTransactions(params?: { startDate?: string; endDate?: string }): Promise<FinancialTransactionLineDTO[]> {
  const { data } = await api.get<FinancialTransactionLineDTO[]>('/api/financial/transactions', { params });
  return data;
}

// Busca métricas agregadas do dashboard
export async function getDashboardAnalytics(): Promise<DashboardAnalyticsDTO> {
  const { data } = await api.get<DashboardAnalyticsDTO>('/api/analytics/dashboard');
  return data;
}

export async function getFinanceConnectionStatus(): Promise<FinanceConnectionStatus> {
  const { data } = await api.get<FinanceConnectionStatus>('/api/financeiro/contaazul/status');
  return data;
}

export async function getFinanceReceipts(): Promise<FinanceReceipt[]> {
  const { data } = await api.get<FinanceReceipt[]>('/api/financeiro/recibos');
  return data;
}

export async function getFinanceAlerts(): Promise<FinanceAlert[]> {
  const { data } = await api.get<FinanceAlert[]>('/api/financeiro/alertas');
  return data;
}

export async function getFinancialSummary(): Promise<FinancialSummaryDTO | null> {
  try {
    const { data } = await api.get<FinancialSummaryDTO>('/api/financeiro/resumo');
    return data;
  } catch (error) {
    if (axios.isAxiosError(error) && error.response?.status === 404) {
      return null;
    }

    throw error;
  }
}

export async function executeFinanceAutomationNow(params: ExecuteFinanceAutomationNowParams): Promise<void> {
  await api.post('/api/financeiro/autonacao/executar', null, {
    params,
  });
}

export async function getDoctorMappings(): Promise<DoctorMapping[]> {
  const { data } = await api.get<DoctorMapping[]>('/api/financeiro/doctor-mappings');
  return data;
}

export async function createDoctorMapping(payload: CreateDoctorMappingDTO): Promise<DoctorMapping> {
  const { data } = await api.post<DoctorMapping>('/api/financeiro/doctor-mappings', payload);
  return data;
}

export async function deleteDoctorMapping(id: string): Promise<void> {
  await api.delete(`/api/financeiro/doctor-mappings/${id}`);
}

export async function syncDoctorsBaseFromContaAzul(): Promise<SyncDoctorsResponse> {
  const { data } = await api.post<SyncDoctorsResponse>('/api/financeiro/medicos/sincronizar-base');
  return data;
}

export async function getContaAzulCustomerEmailById(customerId: string): Promise<ContaAzulCustomerEmailResponse> {
  const { data } = await api.get<ContaAzulCustomerEmailResponse>(`/api/financeiro/contaazul/customer-email/${encodeURIComponent(customerId)}`);
  return data;
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
export async function exportTicketsReport(params?: { startDate?: string; endDate?: string }): Promise<Blob> {
  const { data } = await api.get('/api/reports/tickets', {
    params,
    responseType: 'blob',
  });
  return data;
}

// Exporta relatório de entradas de estoque (compras)
export async function exportInventoryEntriesReport(params?: { startDate?: string; endDate?: string }): Promise<Blob> {
  const { data } = await api.get('/api/reports/inventory/entries', {
    params,
    responseType: 'blob',
  });
  return data;
}

// Exporta relatório de saídas de estoque (consumo)
export async function exportInventoryExitsReport(params?: { startDate?: string; endDate?: string }): Promise<Blob> {
  const { data } = await api.get('/api/reports/inventory/exits', {
    params,
    responseType: 'blob',
  });
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

export async function getAdminConfig(): Promise<AdminConfig> {
  const { data } = await api.get<AdminConfig>('/admin/config');
  return data;
}

export async function getSystemSettings(): Promise<SystemSetting[]> {
  const { data } = await api.get<SystemSetting[]>('/api/admin/settings');
  return data;
}

export async function updateSystemSettings(payload: UpdateSystemSettingsPayload): Promise<SystemSetting[]> {
  const { data } = await api.put<SystemSetting[]>('/api/admin/settings', payload);
  return data;
}

export async function logQrScan(scannedPath: string): Promise<void> {
  await api.post('/api/audit-logs/qr-scan', { scannedPath });
}

// Busca logs de auditoria com filtros opcionais (requer ADMIN)
export async function getAuditLogs(params?: GetAuditLogsParams): Promise<AuditLogPage> {
  const sanitized = Object.fromEntries(
    Object.entries(params ?? {}).filter(([, v]) => v !== undefined && v !== null && v !== ''),
  );
  const { data } = await api.get<AuditLogPage>('/api/audit-logs', { params: sanitized });
  return data;
}
