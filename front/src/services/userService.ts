import axios from 'axios';
import api from './api';
import type { AuthResponseDTO, ChangePasswordRequestDTO, ContaAzulCustomerCheckResponse, CreateSectorDto, CreateUserDto, ImportResult, ResetInitialPasswordRequestDTO, Sector, TwoFactorGenerateResponseDTO, TwoFactorVerifyRequestDTO, UpdateUserDto, User, Page } from '../types/models';

// Busca todos os usuários cadastrados (requer ADMIN)
export async function getUsers(): Promise<User[]> {
  const { data } = await api.get<User[]>('/users');
  return data;
}

// Retorna usuários paginados (simulado no cliente com suporte a busca e ordenação)
export async function getAllUsers(params?: { page: number; size: number; search?: string; sort?: 'name-asc' | 'name-desc' | 'sector-asc' }): Promise<Page<User>> {
  const allUsers = await getUsers();
  const search = params?.search?.toLowerCase() || '';
  let filteredUsers = allUsers;
  
  if (search.trim()) {
    filteredUsers = allUsers.filter(u => 
      (u.name && u.name.toLowerCase().includes(search)) || 
      (u.email && u.email.toLowerCase().includes(search)) ||
      (u.sectorName && u.sectorName.toLowerCase().includes(search))
    );
  }

  const sort = params?.sort || 'name-asc';
  filteredUsers.sort((a, b) => {
    if (sort === 'name-asc') {
      return (a.name ?? '').localeCompare(b.name ?? '');
    } else if (sort === 'name-desc') {
      return (b.name ?? '').localeCompare(a.name ?? '');
    } else if (sort === 'sector-asc') {
      return (a.sectorName ?? '').localeCompare(b.sectorName ?? '');
    }
    return 0;
  });

  const page = params?.page ?? 0;
  const size = params?.size ?? 15;
  const start = page * size;
  const end = start + size;
  const totalPages = Math.ceil(filteredUsers.length / size);
  return {
    content: filteredUsers.slice(start, end),
    totalPages,
    totalElements: filteredUsers.length,
    size,
    number: page,
    first: page === 0,
    last: page >= totalPages - 1,
  };
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

export interface GetSectorsParams {
  page?: number;
  size?: number;
  activeOnly?: boolean;
  search?: string;
  sort?: 'name-asc' | 'name-desc';
}

// Busca todos os setores cadastrados (requer ADMIN) - Suporta paginação simulada ou retorno simples
export async function getSectors(activeOnly?: boolean): Promise<Sector[]>;
export async function getSectors(params: GetSectorsParams): Promise<Page<Sector>>;
export async function getSectors(paramsOrActiveOnly?: GetSectorsParams | boolean): Promise<any> {
  let activeOnly = false;
  let page: number | undefined;
  let size: number | undefined;
  let search = '';
  let sort = 'name-asc';

  if (typeof paramsOrActiveOnly === 'boolean') {
    activeOnly = paramsOrActiveOnly;
  } else if (paramsOrActiveOnly && typeof paramsOrActiveOnly === 'object') {
    activeOnly = !!paramsOrActiveOnly.activeOnly;
    page = paramsOrActiveOnly.page;
    size = paramsOrActiveOnly.size;
    search = paramsOrActiveOnly.search || '';
    sort = paramsOrActiveOnly.sort || 'name-asc';
  }

  const { data } = await api.get<Sector[]>('/sectors', {
    params: { activeOnly, search: search || undefined }
  });

  let filteredData = data;
  if (search.trim()) {
    const q = search.toLowerCase();
    filteredData = data.filter((s) => s.name?.toLowerCase().includes(q));
  }

  filteredData.sort((a, b) => {
    if (sort === 'name-asc') {
      return (a.name ?? '').localeCompare(b.name ?? '');
    } else {
      return (b.name ?? '').localeCompare(a.name ?? '');
    }
  });

  if (page !== undefined && size !== undefined) {
    const start = page * size;
    const end = start + size;
    const totalPages = Math.ceil(filteredData.length / size);
    return {
      content: filteredData.slice(start, end),
      totalPages,
      totalElements: filteredData.length,
      size,
      number: page,
      first: page === 0,
      last: page >= totalPages - 1,
    };
  }

  return filteredData;
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

