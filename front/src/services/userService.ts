import axios from 'axios';
import api from './api';
import type { AuthResponseDTO, ChangePasswordRequestDTO, ContaAzulCustomerCheckResponse, CreateSectorDto, CreateUserDto, ImportResult, ResetInitialPasswordRequestDTO, Sector, TwoFactorGenerateResponseDTO, TwoFactorVerifyRequestDTO, UpdateUserDto, User } from '../types/models';

// Busca todos os usuários cadastrados (requer ADMIN)
export async function getUsers(): Promise<User[]> {
  const { data } = await api.get<User[]>('/users');
  return data;
}

// Cria um novo usuário (requer ADMIN)
export async function createUser(dto: CreateUserDto): Promise<User> {
  const { data } = await api.post<User>('/users', dto);
  return data;
}

// Atualiza dados de um usuário (requer ADMIN)
export async function updateUser(id: string, dto: UpdateUserDto): Promise<User> {
  const { data } = await api.put<User>(`/users/${id}`, dto);
  return data;
}

// Redefine a senha de um usuário para o padrão Mudar@123 (requer ADMIN)
export async function resetUserPassword(id: string): Promise<void> {
  await api.post(`/users/${id}/reset-password`);
}

// Busca todos os setores cadastrados (requer ADMIN)
export async function getSectors(activeOnly?: boolean): Promise<Sector[]> {
  const { data } = await api.get<Sector[]>('/sectors', {
    params: activeOnly !== undefined ? { activeOnly } : undefined
  });
  return data;
}

// Cria um novo setor (requer ADMIN)
export async function createSector(dto: CreateSectorDto): Promise<Sector> {
  const { data } = await api.post<Sector>('/sectors', dto);
  return data;
}

// Atualiza o nome de um setor (requer ADMIN)
export async function updateSector(id: string, dto: CreateSectorDto): Promise<Sector> {
  const { data } = await api.put<Sector>(`/sectors/${id}`, dto);
  return data;
}

// Alterna o estado de ativação de um setor (requer ADMIN)
export async function toggleSectorActive(id: string): Promise<Sector> {
  const { data } = await api.patch<Sector>(`/sectors/${id}/toggle-active`);
  return data;
}

export async function checkContaAzulCustomerByEmail(email: string): Promise<ContaAzulCustomerCheckResponse> {
  try {
    const { data } = await api.get<ContaAzulCustomerCheckResponse>(`/financeiro/contaazul/check-customer/${encodeURIComponent(email)}`);

    if (!data?.customerId) {
      return {
        email,
        customerId: null,
        message: 'Médico não localizado',
      };
    }

    return {
      ...data,
      message: data.message ?? undefined,
    };
  } catch (error) {
    if (axios.isAxiosError(error) && error.response?.status === 404) {
      return {
        email,
        customerId: null,
        message: 'Médico não localizado',
      };
    }

    throw error;
  }
}

export async function resetInitialPassword(
  payload: ResetInitialPasswordRequestDTO,
): Promise<AuthResponseDTO> {
  const { data } = await api.post<AuthResponseDTO>('/auth/reset-initial-password', payload);
  return data;
}

export async function changePassword(currentPassword: string, newPassword: string): Promise<void> {
  const payload: ChangePasswordRequestDTO = { currentPassword, newPassword };
  await api.put('/users/me/password', payload);
}

export async function generate2FA(): Promise<TwoFactorGenerateResponseDTO> {
  const { data } = await api.post<TwoFactorGenerateResponseDTO>('/auth/2fa/generate');
  return data;
}

export async function verify2FA(code: string): Promise<AuthResponseDTO> {
  const payload: TwoFactorVerifyRequestDTO = { code };
  const { data } = await api.post<AuthResponseDTO>('/auth/2fa/verify', payload);
  return data;
}

// Solicita o código de recuperação do 2FA — código enviado ao Discord do usuário
export async function request2FAReset(): Promise<void> {
  await api.post('/auth/2fa/reset-request');
}

// Confirma a recuperação do 2FA com código + senha atual; retorna novo JWT com 2FA limpo
export async function confirm2FAReset(code: string, password: string): Promise<AuthResponseDTO> {
  const { data } = await api.post<AuthResponseDTO>('/auth/2fa/reset-confirm', { code, password });
  return data;
}

// Reseta o 2FA de outro usuário diretamente (somente ADMIN)
export async function adminReset2FA(userId: string): Promise<void> {
  await api.patch(`/users/${userId}/2fa/reset`);
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

