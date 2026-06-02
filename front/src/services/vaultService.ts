import api from './api';
import type { VaultCreateItemRequestDTO, VaultItem, VaultSecretResponseDTO, VaultUpdateItemRequestDTO } from '../types/models';

/**
 * Busca todos os itens do cofre disponíveis para o usuário autenticado.
 */
export async function getVaultItems(): Promise<VaultItem[]> {
  const { data } = await api.get<VaultItem[]>('/vault');
  return data;
}

/**
 * Cria um novo item no cofre (com suporte a upload de arquivo opcional).
 */
export async function createVaultItem(
  payload: VaultCreateItemRequestDTO,
  file?: File | null,
): Promise<VaultItem> {
  const formData = new FormData();
  formData.append('payload', JSON.stringify(payload));

  if (file) {
    formData.append('file', file);
  }

  const { data } = await api.post<VaultItem>('/vault', formData, {
    headers: {
      'Content-Type': 'multipart/form-data',
    },
  });

  return data;
}

/**
 * Atualiza um item do cofre pelo seu ID (com suporte a novo upload de arquivo opcional).
 */
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

  const { data } = await api.patch<VaultItem>(`/vault/${vaultItemId}`, formData, {
    headers: {
      'Content-Type': 'multipart/form-data',
    },
  });

  return data;
}

/**
 * Exclui fisicamente um item do cofre pelo UUID.
 */
export async function deleteVaultItem(vaultItemId: string): Promise<void> {
  await api.delete(`/vault/${vaultItemId}`);
}

/**
 * Compartilha um item específico do cofre com outro usuário pelo UUID.
 */
export async function shareVaultItem(vaultItemId: string, userId: string): Promise<void> {
  await api.post(`/vault/${vaultItemId}/share`, { userId });
}

/**
 * Obtém a senha ou o conteúdo secreto de um item do cofre (requer validação 2FA).
 */
export async function getVaultItemSecret(vaultItemId: string): Promise<VaultSecretResponseDTO> {
  const { data } = await api.get<VaultSecretResponseDTO>(`/vault/${vaultItemId}/secret`);
  return data;
}

/**
 * Faz download do anexo secreto associado a um item do cofre (retorna em formato Blob).
 */
export async function getVaultItemFileBlob(vaultItemId: string): Promise<Blob> {
  const { data } = await api.get(`/vault/${vaultItemId}/file`, {
    responseType: 'blob',
  });
  return data;
}
