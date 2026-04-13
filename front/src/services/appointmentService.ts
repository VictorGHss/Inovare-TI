import api from './api';

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

export interface BlipTemplate {
  id: string;
  name: string;
}

export interface AppointmentConfigUpdateResponse {
  status: 'success' | 'error';
  category?: string;
  templateId?: string;
  message?: string;
}

export async function getAppointmentMotorConfig(): Promise<AppointmentMotorConfig> {
  const { data } = await api.get<AppointmentMotorConfig>('/api/v1/appointments/motor-config');
  return data;
}

export async function triggerAppointmentMotorManual(): Promise<AppointmentManualTriggerResponse> {
  const { data } = await api.post<AppointmentManualTriggerResponse>('/api/v1/appointments/trigger-manual');
  return data;
}

/**
 * Busca templates aprovados disponíveis na API do Blip
 */
export async function getBlipTemplates(): Promise<BlipTemplate[]> {
  const { data } = await api.get<BlipTemplate[]>('/api/v1/appointments/config/blip-templates');
  return data;
}

/**
 * Atualiza o template associado a uma categoria de agendamento
 * @param category Categoria (CONFIRMATION, NUDGE_1, NUDGE_FINAL)
 * @param templateId ID do template a associar
 */
export async function updateAppointmentConfig(
  category: string,
  templateId: string
): Promise<AppointmentConfigUpdateResponse> {
  const { data } = await api.put<AppointmentConfigUpdateResponse>(
    `/api/v1/appointments/config/${category}`,
    { templateId }
  );
  return data;
}
