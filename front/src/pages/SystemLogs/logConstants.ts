import type { AuditAction, AuditSeverity } from '../../types/models';

export const ACTION_LABEL_OVERRIDES: Partial<Record<AuditAction, string>> = {
  VAULT_LOGIN_SUCCESS: 'Cofre: Login 2FA com Sucesso',
  VAULT_LOGIN_FAILURE: 'Cofre: Falha de Login 2FA',
  VAULT_SECRET_VIEW: 'Visualizou Segredo',
  VAULT_FILE_VIEW: 'Visualizou Arquivo',
  VAULT_ITEM_CREATE: 'Criou Item no Cofre',
  VAULT_ITEM_VIEW: 'Visualizou Item no Cofre',
  VAULT_ITEM_EDIT: 'Editou Item no Cofre',
  VAULT_ITEM_DELETE: 'Removeu Item no Cofre',
  VAULT_AUTH_SUCCESS: 'Autenticacao 2FA Bem-sucedida',
  VAULT_AUTH_FAIL: 'Falha na Autenticacao 2FA',
  LOGIN_SUCCESS: 'Login com Sucesso',
  LOGIN_FAILURE: 'Falha de Login',
  TWO_FACTOR_RESET: 'Reset 2FA (proprio)',
  TWO_FACTOR_ADMIN_RESET: 'Reset 2FA (Admin)',
  USER_2FA_ADMIN_RESET: 'Reset 2FA (Admin)',
  TICKET_OPEN: 'Chamado Aberto',
  TICKET_ASSIGN: 'Chamado Atribuido',
  TICKET_TRANSFER: 'Chamado Transferido',
  TICKET_RESOLVE: 'Chamado Resolvido',
  INVENTORY_BATCH_ENTRY: 'Inventario: Entrada de Lote',
  INVENTORY_ITEM_CREATE: 'Inventario: Criacao de Item',
  STOCK_BATCH_CREATE: 'Inventario: Entrada de Lote',
  ITEM_CREATE: 'Inventario: Criacao de Item',
  ASSET_CREATE: 'Ativo Criado',
  ASSET_EDIT: 'Ativo Editado',
  ASSET_INVOICE_ATTACH: 'NF Anexada ao Ativo',
  QR_SCAN: 'Leitura de QR Code',
  ASSET_QR_SCAN: 'Escaneou QR Code de Ativo',
  KB_ARTICLE_DRAFT_CREATE: 'Base de Conhecimento: Rascunho',
  KB_ARTICLE_PUBLISH: 'Base de Conhecimento: Publicacao',
  KB_ARTICLE_EDIT: 'Base de Conhecimento: Edicao',
  ARTICLE_POST_PUBLIC: 'Base de Conhecimento: Publicacao',
  ARTICLE_POST_DRAFT: 'Base de Conhecimento: Rascunho',
  ARTICLE_EDIT: 'Base de Conhecimento: Edicao',
  SECTOR_CREATE: 'Gestao: Criacao de Setor',
  USER_CREATE: 'Gestao: Criacao de Usuario',
  USER_UPDATE: 'Gestao: Edicao de Usuario',
  USER_EDIT: 'Gestao: Edicao de Usuario',
  USER_PASSWORD_RESET: 'Gestao: Reset de Senha',
  USER_PASSWORD_ADMIN_RESET: 'Gestao: Reset de Senha',
  USER_PERMISSION_CHANGE: 'Alteracao de Permissao',
  PROFILE_PASSWORD_CHANGE: 'Alterou a propria senha',
};

export function toFriendlyAuditAction(action: AuditAction): string {
  const override = ACTION_LABEL_OVERRIDES[action];
  if (override) {
    return override;
  }

  if (action.startsWith('TICKET_')) {
    return `Chamado: ${action.replace('TICKET_', '').replaceAll('_', ' ')}`;
  }

  if (
    action.startsWith('INVENTORY_')
    || action === 'STOCK_BATCH_CREATE'
    || action === 'ITEM_CREATE'
  ) {
    return `Inventario: ${action
      .replace('INVENTORY_', '')
      .replace('STOCK_BATCH_CREATE', 'ENTRADA DE LOTE')
      .replace('ITEM_CREATE', 'CRIACAO DE ITEM')
      .replaceAll('_', ' ')}`;
  }

  return action.replaceAll('_', ' ');
}

export const ACTION_BADGE_CLASS: Record<AuditAction, string> = {
  VAULT_LOGIN_SUCCESS: 'bg-brand-secondary/40 text-brand-primary-dark',
  VAULT_LOGIN_FAILURE: 'bg-red-100 text-red-600',
  VAULT_SECRET_VIEW: 'bg-brand-secondary/40 text-brand-primary-dark',
  VAULT_FILE_VIEW: 'bg-brand-secondary/40 text-brand-primary-dark',
  VAULT_ITEM_CREATE: 'bg-orange-100 text-orange-700',
  VAULT_ITEM_VIEW: 'bg-brand-secondary/40 text-brand-primary-dark',
  VAULT_ITEM_EDIT: 'bg-orange-100 text-orange-700',
  VAULT_ITEM_DELETE: 'bg-red-100 text-red-600',
  VAULT_AUTH_SUCCESS: 'bg-brand-secondary/40 text-brand-primary-dark',
  VAULT_AUTH_FAIL: 'bg-red-100 text-red-600',
  LOGIN_SUCCESS: 'bg-brand-secondary/40 text-brand-primary-dark',
  LOGIN_FAILURE: 'bg-red-100 text-red-600',
  TWO_FACTOR_RESET: 'bg-amber-100 text-amber-700',
  TWO_FACTOR_ADMIN_RESET: 'bg-orange-100 text-orange-700',
  USER_2FA_ADMIN_RESET: 'bg-orange-100 text-orange-700',
  TICKET_OPEN: 'bg-orange-100 text-orange-700',
  TICKET_ASSIGN: 'bg-brand-secondary/40 text-brand-primary-dark',
  TICKET_TRANSFER: 'bg-amber-100 text-amber-700',
  TICKET_RESOLVE: 'bg-brand-secondary/40 text-brand-primary-dark',
  INVENTORY_BATCH_ENTRY: 'bg-amber-100 text-amber-700',
  INVENTORY_ITEM_CREATE: 'bg-brand-secondary/40 text-brand-primary-dark',
  STOCK_BATCH_CREATE: 'bg-amber-100 text-amber-700',
  ITEM_CREATE: 'bg-brand-secondary/40 text-brand-primary-dark',
  ASSET_CREATE: 'bg-brand-secondary/40 text-brand-primary-dark',
  ASSET_EDIT: 'bg-orange-100 text-orange-700',
  ASSET_INVOICE_ATTACH: 'bg-brand-secondary/40 text-brand-primary-dark',
  QR_SCAN: 'bg-brand-secondary/40 text-brand-primary-dark',
  ASSET_QR_SCAN: 'bg-brand-secondary/40 text-brand-primary-dark',
  KB_ARTICLE_DRAFT_CREATE: 'bg-slate-100 text-slate-700',
  KB_ARTICLE_PUBLISH: 'bg-brand-secondary/40 text-brand-primary-dark',
  KB_ARTICLE_EDIT: 'bg-orange-100 text-orange-700',
  ARTICLE_POST_PUBLIC: 'bg-brand-secondary/40 text-brand-primary-dark',
  ARTICLE_POST_DRAFT: 'bg-slate-100 text-slate-700',
  ARTICLE_EDIT: 'bg-orange-100 text-orange-700',
  SECTOR_CREATE: 'bg-brand-secondary/40 text-brand-primary-dark',
  USER_CREATE: 'bg-brand-secondary/40 text-brand-primary-dark',
  USER_UPDATE: 'bg-orange-100 text-orange-700',
  USER_EDIT: 'bg-orange-100 text-orange-700',
  USER_PASSWORD_RESET: 'bg-red-100 text-red-700',
  USER_PERMISSION_CHANGE: 'bg-amber-100 text-amber-700',
  USER_PASSWORD_ADMIN_RESET: 'bg-red-100 text-red-700',
  PROFILE_PASSWORD_CHANGE: 'bg-amber-100 text-amber-700',
};

export const SEVERITY_BADGE_CLASS: Record<AuditSeverity, string> = {
  INFO: 'bg-brand-secondary/40 text-brand-primary-dark',
  WARN: 'bg-amber-100 text-amber-700',
  ERROR: 'bg-red-100 text-red-600',
};

export function getAuditSeverity(action: AuditAction): AuditSeverity {
  if (
    action.includes('FAILURE')
    || action.endsWith('_FAIL')
    || action === 'USER_PASSWORD_RESET'
    || action === 'USER_PASSWORD_ADMIN_RESET'
  ) {
    return 'ERROR';
  }

  if (
    action.includes('RESET')
    || action === 'USER_PERMISSION_CHANGE'
    || action === 'VAULT_ITEM_DELETE'
    || action === 'TICKET_TRANSFER'
  ) {
    return 'WARN';
  }

  return 'INFO';
}

export const ALL_ACTIONS: AuditAction[] = [
  'VAULT_LOGIN_SUCCESS',
  'VAULT_LOGIN_FAILURE',
  'VAULT_SECRET_VIEW',
  'VAULT_FILE_VIEW',
  'VAULT_ITEM_CREATE',
  'VAULT_ITEM_VIEW',
  'VAULT_ITEM_EDIT',
  'VAULT_ITEM_DELETE',
  'VAULT_AUTH_SUCCESS',
  'VAULT_AUTH_FAIL',
  'LOGIN_SUCCESS',
  'LOGIN_FAILURE',
  'TWO_FACTOR_RESET',
  'TWO_FACTOR_ADMIN_RESET',
  'USER_2FA_ADMIN_RESET',
  'TICKET_OPEN',
  'TICKET_ASSIGN',
  'TICKET_TRANSFER',
  'TICKET_RESOLVE',
  'INVENTORY_BATCH_ENTRY',
  'INVENTORY_ITEM_CREATE',
  'STOCK_BATCH_CREATE',
  'ITEM_CREATE',
  'ASSET_CREATE',
  'ASSET_EDIT',
  'ASSET_INVOICE_ATTACH',
  'QR_SCAN',
  'ASSET_QR_SCAN',
  'KB_ARTICLE_DRAFT_CREATE',
  'KB_ARTICLE_PUBLISH',
  'KB_ARTICLE_EDIT',
  'ARTICLE_POST_PUBLIC',
  'ARTICLE_POST_DRAFT',
  'ARTICLE_EDIT',
  'SECTOR_CREATE',
  'USER_CREATE',
  'USER_UPDATE',
  'USER_EDIT',
  'USER_PASSWORD_RESET',
  'USER_PERMISSION_CHANGE',
  'USER_PASSWORD_ADMIN_RESET',
  'PROFILE_PASSWORD_CHANGE',
];
