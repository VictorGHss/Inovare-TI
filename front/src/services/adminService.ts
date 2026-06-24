import api from './api';

export interface BackupInfo {
  filename: string;
  sizeBytes: number;
  lastModified: string;
}

/**
 * Busca a lista de backups disponíveis.
 */
export async function getBackups(): Promise<BackupInfo[]> {
  const { data } = await api.get<BackupInfo[]>('/admin/backups');
  return data;
}

/**
 * Dispara manualmente um novo backup de banco de dados.
 */
export async function triggerBackup(): Promise<void> {
  await api.post('/admin/backups/trigger');
}

/**
 * Faz o download físico de um arquivo de backup específico.
 */
export async function downloadBackup(filename: string): Promise<Blob> {
  const { data } = await api.get(`/admin/backups/download/${filename}`, {
    responseType: 'blob',
  });
  return data;
}

/**
 * Remove um arquivo de backup físico do disco do servidor.
 */
export async function deleteBackup(filename: string): Promise<void> {
  await api.delete(`/admin/backups/${filename}`);
}
