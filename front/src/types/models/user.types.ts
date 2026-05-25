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

export interface Notification {
  id: string;
  title: string;
  message: string;
  isRead: boolean;
  link: string | null;
  createdAt: string;
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
  discordWebhookUrlPresent?: boolean;
  discordBotTokenPresent?: boolean;
  contaAzulClientIdPresent?: boolean;
  contaAzulClientSecretPresent?: boolean;
  feegowApiKeyPresent?: boolean;
  feegowUnitIdPresent?: boolean;
  blipApiKeyPresent?: boolean;
  blipBotIdPresent?: boolean;
  blipWebhookTokenPresent?: boolean;
  blipWebhookSecretPresent?: boolean;
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

export type AuditSeverity = 'INFO' | 'WARN' | 'ERROR';

export interface GetAuditLogsParams {
  userId?: string;
  action?: AuditAction;
  severity?: AuditSeverity;
  search?: string;
  startDate?: string;
  endDate?: string;
  page?: number;
  size?: number;
}
