export interface Item {
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
  entryDate: string;
  invoiceFileName?: string;
  invoiceContentType?: string;
  invoiceFilePath?: string;
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

export interface ReportSchedule {
  id: string;
  reportType: string;
  targetUserId: string | null;
  sendEmail: boolean;
  sendDiscord: boolean;
  scheduleDay: number;
  isActive: boolean;
  createdAt: string;
  updatedAt: string;
}
