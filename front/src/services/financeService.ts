import axios from 'axios';
import api from './api';
import type { ContaAzulCustomerEmailResponse, CreateDoctorMappingDTO, DashboardAnalyticsDTO, DoctorMapping, ExecuteFinanceAutomationNowParams, FinanceAlert, FinanceAutomationExecutionResponse, FinanceConnectionStatus, FinanceReceipt, FinancialSummaryDTO, SyncDoctorsResponse } from '../types/models';

// Serviço focado apenas no domínio financeiro e integrações ContaAzul.

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
  const { data } = await api.post<FinanceAutomationExecutionResponse>('/api/financeiro/autonacao/executar', null, {
    params,
  });

  return data;
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

