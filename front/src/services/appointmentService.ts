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

export async function getAppointmentMotorConfig(): Promise<AppointmentMotorConfig> {
  const { data } = await api.get<AppointmentMotorConfig>('/api/v1/appointments/motor-config');
  return data;
}

export async function triggerAppointmentMotorManual(): Promise<AppointmentManualTriggerResponse> {
  const { data } = await api.post<AppointmentManualTriggerResponse>('/api/v1/appointments/trigger-manual');
  return data;
}
