import api from './api';
import type { ReportSchedule } from '../types/models';

/**
 * Busca todos os agendamentos de relatórios automatizados cadastrados.
 */
export async function getReportSchedules(): Promise<ReportSchedule[]> {
  const { data } = await api.get<ReportSchedule[]>('/report-schedules');
  return data;
}

/**
 * Cria um novo agendamento de envio automático de relatório mensal.
 */
export async function createReportSchedule(payload: Partial<ReportSchedule>): Promise<ReportSchedule> {
  const { data } = await api.post<ReportSchedule>('/report-schedules', payload);
  return data;
}

/**
 * Atualiza um agendamento de relatório automático existente pelo UUID.
 */
export async function updateReportSchedule(id: string, payload: Partial<ReportSchedule>): Promise<ReportSchedule> {
  const { data } = await api.put<ReportSchedule>(`/report-schedules/${id}`, payload);
  return data;
}

/**
 * Remove um agendamento de relatório automático pelo UUID.
 */
export async function deleteReportSchedule(id: string): Promise<void> {
  await api.delete(`/report-schedules/${id}`);
}

/**
 * Dispara o envio imediato de um relatório de teste para o agendamento especificado.
 */
export async function triggerReportScheduleTest(id: string): Promise<void> {
  await api.post(`/report-schedules/${id}/trigger-test`);
}

/**
 * Exporta o relatório de chamados em planilha Excel (Blob).
 */
export async function exportTicketsReport(params?: { startDate?: string; endDate?: string }): Promise<Blob> {
  const { data } = await api.get('/reports/tickets', {
    params,
    responseType: 'blob',
  });
  return data;
}

/**
 * Exporta o relatório de entradas de estoque/compras em planilha Excel (Blob).
 */
export async function exportInventoryEntriesReport(params?: { startDate?: string; endDate?: string }): Promise<Blob> {
  const { data } = await api.get('/reports/inventory/entries', {
    params,
    responseType: 'blob',
  });
  return data;
}

/**
 * Exporta o relatório de saídas de estoque/consumo em planilha Excel (Blob).
 */
export async function exportInventoryExitsReport(params?: { startDate?: string; endDate?: string }): Promise<Blob> {
  const { data } = await api.get('/reports/inventory/exits', {
    params,
    responseType: 'blob',
  });
  return data;
}
