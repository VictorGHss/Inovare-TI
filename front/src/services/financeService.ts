import axios from 'axios';
import api from './api';
import type { ContaAzulCustomerEmailResponse, CreateDoctorMappingDTO, DashboardAnalyticsDTO, DoctorMapping, ExecuteFinanceAutomationNowParams, FinanceAlert, FinanceAutomationExecutionResponse, FinanceConnectionStatus, FinanceReceipt, FinancialSummaryDTO, SyncDoctorsResponse } from '../types/models';

// Serviço focado apenas no domínio financeiro e integrações ContaAzul.

// Busca métricas agregadas do dashboard
export async function getDashboardAnalytics(): Promise<DashboardAnalyticsDTO> {
  const { data } = await api.get<DashboardAnalyticsDTO>('/analytics/dashboard');
  return data;
}

export async function getFinanceConnectionStatus(): Promise<FinanceConnectionStatus> {
  const { data } = await api.get<FinanceConnectionStatus>('/financeiro/contaazul/status');
  return data;
}

export async function getFinanceReceipts(): Promise<FinanceReceipt[]> {
  const { data } = await api.get<FinanceReceipt[]>('/financeiro/recibos', {
    params: { page: 0, size: 1 },
  });
  return data;
}

export async function getFinanceAlerts(): Promise<FinanceAlert[]> {
  const { data } = await api.get<FinanceAlert[]>('/financeiro/alertas');
  return data;
}

export async function getFinancialSummary(): Promise<FinancialSummaryDTO | null> {
  try {
    const { data } = await api.get<FinancialSummaryDTO>('/financeiro/resumo');
    return data;
  } catch (error) {
    if (axios.isAxiosError(error) && error.response?.status === 404) {
      return null;
    }

    if (axios.isAxiosError(error) && [401, 403, 422, 500, 502, 503, 504].includes(error.response?.status ?? 0)) {
      return {
        balanceCents: 0,
        totalPendingCents: 0,
        totalPaidCents: 0,
        currency: 'BRL',
        syncedReceiptsCount: 0,
        externalServiceAvailable: false,
        integrationActive: true,
        lastUpdatedAt: null,
      };
    }

    throw error;
  }
}

export async function executeFinanceAutomationNow(
  params: ExecuteFinanceAutomationNowParams,
): Promise<FinanceAutomationExecutionResponse> {
  const { data } = await api.post<FinanceAutomationExecutionResponse>('/financeiro/autonacao/executar', null, {
    params,
  });

  return data;
}

export async function getDoctorMappings(): Promise<DoctorMapping[]> {
  const { data } = await api.get<DoctorMapping[]>('/financeiro/doctor-mappings');
  return data;
}

export async function createDoctorMapping(payload: CreateDoctorMappingDTO): Promise<DoctorMapping> {
  const { data } = await api.post<DoctorMapping>('/financeiro/doctor-mappings', payload);
  return data;
}

export async function updateDoctorMapping(id: string, payload: CreateDoctorMappingDTO): Promise<DoctorMapping> {
  const { data } = await api.put<DoctorMapping>(`/financeiro/doctor-mappings/${id}`, payload);
  return data;
}

export async function deleteDoctorMapping(id: string): Promise<void> {
  await api.delete(`/financeiro/doctor-mappings/${id}`);
}

export async function syncDoctorsBaseFromContaAzul(): Promise<SyncDoctorsResponse> {
  const { data } = await api.post<SyncDoctorsResponse>('/financeiro/medicos/sincronizar-base');
  return data;
}

export async function getContaAzulCustomerEmailById(customerId: string): Promise<ContaAzulCustomerEmailResponse> {
  const { data } = await api.get<ContaAzulCustomerEmailResponse>(`/financeiro/contaazul/customer-email/${encodeURIComponent(customerId)}`);
  return data;
}

