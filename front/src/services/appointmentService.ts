import api from './api';
import type { BlipDeliveryFailure, PaginatedResponse } from '../types/models';

export interface AppointmentMotorConfig {
  enabled: boolean;
  testMode: boolean;
  testDoctorId: string;
  mode: 'TEST' | 'PROD';
}

export interface AppointmentManualTriggerResponse {
  status: 'success';
  messages_sent: number;
  mode: 'TEST' | 'PROD';
}

export async function getAppointmentMotorConfig(): Promise<AppointmentMotorConfig> {
  const { data } = await api.get<AppointmentMotorConfig>('/v1/appointments/motor-config');
  return data;
}

export async function triggerAppointmentMotorManual(): Promise<AppointmentManualTriggerResponse> {
  const { data } = await api.post<AppointmentManualTriggerResponse>('/v1/appointments/trigger-manual');
  return data;
}

export interface Doctor {
  id: string;
  name: string;
}

export async function getDoctors(params?: { search?: string; page?: number; size?: number }): Promise<Doctor[]> {
  const { data } = await api.get<Doctor[]>('/v1/appointments/professionals', { params });
  if (params?.search) {
    const q = params.search.toLowerCase();
    return data.filter(d => d.name && d.name.toLowerCase().includes(q)).slice(0, params.size ?? 15);
  }
  return data.slice(0, params?.size ?? 15);
}

export interface FetchBlipFailuresParams {
  page?: number;
  size?: number;
  appointmentId?: string;
  category?: string;
}

/**
 * Consome o endpoint GET /v1/audit/blip-failures para obter as falhas de entrega.
 *
 * @param params Filtros opcionais para a busca paginada.
 * @returns Lista paginada de falhas de entrega registradas.
 */
export async function getBlipDeliveryFailures(params?: FetchBlipFailuresParams): Promise<PaginatedResponse<BlipDeliveryFailure>> {
  const { data } = await api.get<PaginatedResponse<BlipDeliveryFailure>>('/v1/audit/blip-failures', { params });
  return data;
}

