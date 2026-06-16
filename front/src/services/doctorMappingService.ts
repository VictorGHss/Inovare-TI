import api, { buildApiUrl } from './api';
import type { AxiosResponse } from 'axios';

const APPOINTMENT_ADMIN_MAPPINGS_URL = buildApiUrl('/v1/appointments/admin/mappings');
const APPOINTMENT_ADMIN_SYNC_MAPPINGS_URL = buildApiUrl('/v1/appointments/admin/sync-mappings');
const APPOINTMENT_ADMIN_DEBUG_QUEUES_URL = buildApiUrl('/v1/appointments/admin/debug-queues');
const APPOINTMENT_PROFESSIONALS_URL = buildApiUrl('/v1/appointments/professionals');
const APPOINTMENT_BLIP_QUEUES_URL = buildApiUrl('/v1/appointments/blip/queues');
const APPOINTMENT_ADMIN_UPSERT_MAPPING_URL = buildApiUrl('/v1/appointments/admin/doctor-mapping');

export interface DoctorMapping {
  id?: string;
  profissionalId: string;
  blipQueueId: string;
  itsmUserId: string;
  discordWebhookUrl: string;
  profissionalNome?: string;
  ignoreAutoSchedule?: boolean;
}

export interface SyncMappingsResponse {
  status: 'success' | 'error';
  received?: number;
  created?: number;
  updated?: number;
  skipped?: number;
  skippedItems?: Array<Record<string, unknown>>;
  reason?: string;
}

export type BlipDebugQueuesPayload = Record<string, unknown>;

export interface FeegowProfessional {
  id: string;
  name: string;
}

export interface BlipQueue {
  id: string;
  name: string;
}

export function extractQueueNamesFromBlipDebugPayload(payload: BlipDebugQueuesPayload): string[] {
  const names: string[] = [];
  
  try {
    const resource = payload.resource as Record<string, unknown> | undefined;
    if (!resource) {
      console.warn('Nenhum resource encontrado no payload do Blip.');
      return names;
    }
    
    const items = resource.items as Array<Record<string, unknown>> | undefined;
    if (!Array.isArray(items)) {
      console.warn('Items não é um array no resource do Blip.', resource);
      return names;
    }
    
    items.forEach((item) => {
      if (item && typeof item === 'object') {
        const name = item.name as unknown;
        if (typeof name === 'string' && name.trim().length > 0) {
          names.push(name.trim());
        }
      }
    });
  } catch (error) {
    console.error('Erro ao extrair nomes de filas do Blip:', error);
  }
  
  const uniqueNames = Array.from(new Set(names)).sort((a, b) => a.localeCompare(b, 'pt-BR'));
  console.log('Filas processadas para o select:', uniqueNames);
  return uniqueNames;
}

export async function getMappings(params?: { search?: string; page?: number; size?: number }): Promise<DoctorMapping[]> {
  const { data } = await api.get<DoctorMapping[]>(APPOINTMENT_ADMIN_MAPPINGS_URL, { params });
  if (params?.search) {
    const q = params.search.toLowerCase();
    return data.filter(m => 
      (m.profissionalNome && m.profissionalNome.toLowerCase().includes(q)) ||
      (m.profissionalId && m.profissionalId.toLowerCase().includes(q))
    ).slice(0, params.size ?? 15);
  }
  return data;
}

export async function syncMappings(data: DoctorMapping[]): Promise<AxiosResponse<SyncMappingsResponse>> {
  return await api.patch<SyncMappingsResponse>(APPOINTMENT_ADMIN_SYNC_MAPPINGS_URL, data);
}

export async function getBlipQueuesDebug(): Promise<BlipDebugQueuesPayload> {
  const { data } = await api.get<BlipDebugQueuesPayload>(APPOINTMENT_ADMIN_DEBUG_QUEUES_URL);
  return data ?? {};
}

export async function getFeegowProfessionals(): Promise<FeegowProfessional[]> {
  const { data } = await api.get<FeegowProfessional[]>(APPOINTMENT_PROFESSIONALS_URL);
  return data ?? [];
}

export async function getBlipQueuesList(): Promise<BlipQueue[]> {
  const { data } = await api.get<BlipQueue[]>(APPOINTMENT_BLIP_QUEUES_URL);
  return data ?? [];
}

export interface UpsertMappingResponse {
  status: 'success' | 'error';
  profissionalId?: string;
}

export async function updateDoctorMapping(mapping: Partial<DoctorMapping>): Promise<UpsertMappingResponse> {
  const { data } = await api.patch<UpsertMappingResponse>(APPOINTMENT_ADMIN_UPSERT_MAPPING_URL, mapping);
  return data;
}

export async function deleteMappingById(id: string): Promise<void> {
  await api.delete(buildApiUrl(`/v1/appointments/mapping/${id}`));
}
