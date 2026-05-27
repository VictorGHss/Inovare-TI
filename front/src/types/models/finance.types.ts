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
  ticketsByMonth: MetricDTO[];
  inventorySummary: InventorySummaryDTO;
  totalAssets: number;
  assetsInUse: number;
  assetsInStock: number;
}

export type FinanceReceiptStatus = 'SENT' | 'HISTORICO' | 'PENDING_RETRY' | 'FAILED' | 'SKIPPED_DUPLICATE';

export interface FinanceReceipt {
  id: string;
  parcelaId: string;
  commercialNumber?: string | null;
  referenceCode?: string | null;
  displayIdentifier?: string | null;
  originalRecipientEmail: string;
  status: FinanceReceiptStatus;
  processedAt: string;
  payload: Record<string, unknown> | null;
}

export interface FinanceAlert {
  id: string;
  alertType?: string;
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

export interface UpdateDoctorMappingDTO {
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
  integrationActive?: boolean;
  lastUpdatedAt?: string | null;
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

export interface ExecuteFinanceAutomationNowParams {
  dataInicio: string;
  dataFim: string;
}

export interface FinanceAutomationExecutionResponse {
  status: 'ok' | 'warning' | 'erro';
  message: string;
  durationMs: number;
  errors: string[];
  noAttachmentWarnings: number;
  mappingWarnings: number;
}
