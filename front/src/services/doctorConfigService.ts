import api, { buildApiUrl } from './api';

const BASE_URL = buildApiUrl('/v1/doctors/configurations');

/**
 * Represents the configuration for a doctor, including credentials for
 * the GerAcesso turnstile system and the Blip attendance queue.
 *
 * Field names must match exactly with the backend DoctorConfiguration domain model.
 */
export interface DoctorConfiguration {
  feegowProfissionalId: number;
  doctorName: string;
  gerAcessoMatricula: string;
  gerAcessoCpf: string;
  blipQueueId: string;
  blipQueueName: string;
  displayTimeOffsetMinutes?: number;
  advanceNoticeDays?: number;
}

/**
 * Fetches all doctor configurations from the backend.
 */
export async function getAll(): Promise<DoctorConfiguration[]> {
  const { data } = await api.get<DoctorConfiguration[]>(BASE_URL);
  return data ?? [];
}

/**
 * Saves (creates or updates) a doctor configuration.
 * The backend performs an upsert based on feegowProfissionalId (the primary key).
 *
 * @param config - The doctor configuration to save.
 * @returns The saved configuration as returned by the server.
 */
export async function save(config: DoctorConfiguration): Promise<DoctorConfiguration> {
  const { data } = await api.post<DoctorConfiguration>(BASE_URL, config);
  return data;
}

/**
 * Deletes a doctor configuration by the Feegow professional ID.
 *
 * @param feegowProfissionalId - The numeric ID of the Feegow professional.
 */
export async function deleteDoctorConfig(feegowProfissionalId: number): Promise<void> {
  await api.delete(`${BASE_URL}/${feegowProfissionalId}`);
}
