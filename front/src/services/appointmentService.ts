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

export interface AppointmentTemplateMappingItem {
  placeholderIndex: number;
  feegowFieldName: string;
}

export interface SaveAppointmentTemplateMappingsRequest {
  templateName: string;
  mappings: AppointmentTemplateMappingItem[];
}

export interface SaveAppointmentTemplateMappingsResponse {
  status: 'success' | 'error';
  templateName?: string;
  savedMappings?: number;
  message?: string;
}

export interface AppointmentTemplateMappingResponse {
  templateName: string;
  placeholderIndex: number;
  feegowFieldName: string;
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

export async function getFeegowFields(): Promise<string[]> {
  const { data } = await api.get<string[]>('/api/v1/appointments/config/feegow-fields');
  return data;
}

export async function saveAppointmentTemplateMappings(
  payload: SaveAppointmentTemplateMappingsRequest,
): Promise<SaveAppointmentTemplateMappingsResponse> {
  const { data } = await api.post<SaveAppointmentTemplateMappingsResponse>('/api/v1/appointments/config/template-mappings', payload);
  return data;
}

export async function getAppointmentTemplateMappings(
  templateName: string,
): Promise<AppointmentTemplateMappingResponse[]> {
  const { data } = await api.get<AppointmentTemplateMappingResponse[]>('/api/v1/appointments/config/template-mappings', {
    params: { templateName },
  });
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
