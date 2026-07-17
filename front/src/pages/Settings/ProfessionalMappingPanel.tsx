import { useEffect, useMemo, useState } from 'react';
import { Loader2, Save, Trash2 } from 'lucide-react';
import { toast } from 'react-toastify';
import SearchableDropdown from '@/components/common/SearchableDropdown';

import { getApiErrorMessage } from '../../lib/apiError';
import {
  getFeegowProfessionals,
  getMappings,
  getBlipQueuesList,
  syncMappings,
  type FeegowProfessional,
  type DoctorMapping,
  type BlipQueue,
} from '../../services/doctorMappingService';
import {
  getAll as getAllConfigs,
  save as saveConfig,
  type DoctorConfiguration,
} from '../../services/doctorConfigService';

interface MergedDoctorMapping extends DoctorMapping {
  blip_queue_id?: string;
  itsm_user_id?: string;
  discord_webhook_url?: string;
  external_wa_link?: string;
  profissional_nome?: string;
  is_external?: boolean;
  ignore_auto_schedule?: boolean;
  
  // Consolidated GerAcesso fields
  gerAcessoMatricula?: string;
  gerAcessoCpf?: string;
}

export default function ProfessionalMappingPanel() {
  const [professionals, setProfessionals] = useState<FeegowProfessional[]>([]);
  const [mappings, setMappings] = useState<MergedDoctorMapping[]>([]);
  const [blipQueues, setBlipQueues] = useState<BlipQueue[]>([]);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [syncingData, setSyncingData] = useState(false);
  const [showInactive, setShowInactive] = useState(false);

  const professionalsById = useMemo(() => {
    const map = new Map<string, FeegowProfessional>();
    professionals.forEach((p) => {
      const id = String(p.id ?? '').trim();
      if (!id) return;
      if (!map.has(id)) {
        map.set(id, p);
      }
    });
    return map;
  }, [professionals]);

  const missingIdCount = useMemo(() => {
    return mappings.filter((m) => !String(m.profissionalId ?? '').trim()).length;
  }, [mappings]);

  const filteredMappings = useMemo(() => {
    return mappings.filter((row) => {
      const isInactive = row.blipQueueId === 'inactive';
      if (showInactive) return true;
      return !isInactive;
    });
  }, [mappings, showInactive]);

  const hasBlipQueues = blipQueues.length > 0;

  async function loadData() {
    try {
      setLoading(true);
      const [pros, dbMappings, dbConfigs, queues] = await Promise.all([
        getFeegowProfessionals(),
        getMappings(),
        getAllConfigs(),
        getBlipQueuesList(),
      ]);

      setProfessionals(Array.isArray(pros) ? pros : []);
      const queueList = Array.isArray(queues) ? queues : [];
      setBlipQueues(queueList);

      const mappingById = new Map<string, MergedDoctorMapping>();
      if (Array.isArray(dbMappings)) {
        (dbMappings as MergedDoctorMapping[]).forEach((m) => mappingById.set(String(m.profissionalId), m));
      }

      const configById = new Map<string, DoctorConfiguration>();
      if (Array.isArray(dbConfigs)) {
        dbConfigs.forEach((c) => configById.set(String(c.feegowProfissionalId), c));
      }

      // Collect all unique IDs from Feegow, mappings and configs
      const allProIds = new Set<string>();
      (Array.isArray(pros) ? pros : []).forEach((p) => allProIds.add(String(p.id)));
      if (Array.isArray(dbMappings)) {
        dbMappings.forEach((m) => { if (m.profissionalId) allProIds.add(String(m.profissionalId)); });
      }
      if (Array.isArray(dbConfigs)) {
        dbConfigs.forEach((c) => { if (c.feegowProfissionalId) allProIds.add(String(c.feegowProfissionalId)); });
      }

      const merged: MergedDoctorMapping[] = Array.from(allProIds).map((proId) => {
        const p = professionalsById.get(proId) || (Array.isArray(pros) ? (pros as FeegowProfessional[]).find(pr => String(pr.id) === proId) : undefined);
        const m = mappingById.get(proId);
        const c = configById.get(proId);

        return {
          id: m?.id,
          profissionalId: proId,
          blipQueueId: m?.blipQueueId || m?.blip_queue_id || c?.blipQueueId || '',
          itsmUserId: m?.itsmUserId || m?.itsm_user_id || '',
          discordWebhookUrl: m?.discordWebhookUrl || m?.discord_webhook_url || '',
          profissionalNome: m?.profissionalNome || m?.profissional_nome || c?.doctorName || p?.name || `Sem nome (ID ${proId})`,
          ignoreAutoSchedule: m?.ignoreAutoSchedule ?? m?.ignore_auto_schedule ?? false,
          gerAcessoMatricula: c?.gerAcessoMatricula || '',
          gerAcessoCpf: c?.gerAcessoCpf || '',
        } as MergedDoctorMapping;
      });

      setMappings(merged);
    } catch (error) {
      toast.error(getApiErrorMessage(error, 'Falha ao carregar profissionais ou mapeamentos.'));
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    void loadData();
  }, []);

  async function handleSyncData() {
    try {
      setSyncingData(true);
      await loadData();
      toast.success('Dados sincronizados com sucesso.');
    } catch (error) {
      toast.error(getApiErrorMessage(error, 'Falha ao sincronizar dados.'));
    } finally {
      setSyncingData(false);
    }
  }

  const hasChanges = useMemo(() => true, []);

  function updateField(profissionalId: string, field: keyof MergedDoctorMapping, value: string | boolean) {
    setMappings((current) =>
      current.map((m) =>
        String(m.profissionalId) === String(profissionalId) ? { ...m, [field]: value } : m
      )
    );
  }

  async function handleSave() {
    try {
      setSaving(true);

      // 1. Save appointment doctor mappings
      const mappingPayload = mappings.map((m) => ({
        profissionalId: String(m.profissionalId),
        blipQueueId: String(m.blipQueueId ?? '').trim(),
        itsmUserId: String(m.itsmUserId ?? '').trim(),
        discordWebhookUrl: String(m.discordWebhookUrl ?? '').trim(),
        profissionalNome: String(m.profissionalNome ?? '').trim(),
        ignoreAutoSchedule: Boolean(m.ignoreAutoSchedule),
      }));

      // 2. Save doctor configs (GerAcesso credentials)
      const configPayloads = mappings
        .filter((m) => m.profissionalId && !isNaN(Number(m.profissionalId)))
        .map((m) => ({
          feegowProfissionalId: Number(m.profissionalId),
          doctorName: String(m.profissionalNome ?? '').trim(),
          gerAcessoMatricula: String(m.gerAcessoMatricula ?? '').trim(),
          gerAcessoCpf: String(m.gerAcessoCpf ?? '').replaceAll(/\D/g, '').trim(),
          blipQueueId: String(m.blipQueueId ?? '').trim(),
          blipQueueName: blipQueues.find((q) => q.id === m.blipQueueId)?.name || '',
        }));

      // Fire both save calls
      const [mappingResp] = await Promise.all([
        syncMappings(mappingPayload),
        ...configPayloads.map((cfg) => saveConfig(cfg)),
      ]);

      const status = mappingResp?.status ?? 0;
      if (status >= 200 && status < 300) {
        toast.success('Mapeamentos e credenciais salvos com sucesso.');
        void loadData(); // Reload from DB to confirm values
      } else {
        const fakeError = { response: { data: mappingResp?.data } } as unknown;
        const reason = getApiErrorMessage(fakeError, mappingResp?.data?.reason ?? mappingResp?.statusText ?? `HTTP ${status}`);
        toast.error(reason);
      }
    } catch (error) {
      toast.error(getApiErrorMessage(error, 'Falha ao salvar mapeamentos e credenciais de médicos.'));
    } finally {
      setSaving(false);
    }
  }

  return (
    <section className="w-full rounded-2xl border border-slate-200 bg-white shadow-sm overflow-hidden">
      <header className="border-b border-slate-100 px-6 py-5">
        <div className="flex items-center justify-between">
          <div>
            <h2 className="text-base font-bold text-slate-900">Mapeamento de Profissionais (Feegow)</h2>
            <p className="mt-0.5 text-xs text-slate-500">Edite fila, credenciais de catraca (GerAcesso) e configuracoes do profissional.</p>
          </div>

          <div className="flex gap-2">
            <button
              type="button"
              onClick={() => {
                void handleSyncData();
              }}
              disabled={syncingData || loading}
              className="inline-flex items-center gap-2 rounded-xl bg-brand-primary px-4 py-2.5 text-sm font-semibold text-white shadow-sm transition-colors hover:bg-brand-primary-dark disabled:cursor-not-allowed disabled:opacity-60"
            >
              {syncingData ? <Loader2 size={15} className="animate-spin" /> : null}
              {syncingData ? 'Sincronizando...' : 'Sincronizar Dados'}
            </button>

            <button
              type="button"
              onClick={() => {
                void handleSave();
              }}
              disabled={saving || loading || !hasChanges}
              className="inline-flex items-center gap-2 rounded-xl bg-brand-primary px-4 py-2.5 text-sm font-semibold text-white shadow-sm transition-colors hover:bg-brand-primary-dark disabled:cursor-not-allowed disabled:opacity-60"
            >
              {saving ? <Loader2 size={15} className="animate-spin" /> : <Save size={15} />}
              {saving ? 'Salvando...' : 'Salvar Mapeamentos'}
            </button>
          </div>
        </div>
      </header>

      <div className="flex flex-wrap items-center justify-between border-b border-slate-100 bg-slate-50/70 px-6 py-3 text-xs text-slate-600 gap-4">
        <div className="space-y-1">
          {missingIdCount > 0 ? (
            <p>Há {missingIdCount} profissional(is) sem ID da Feegow. Essas linhas serão ignoradas ao salvar.</p>
          ) : null}
          {!hasBlipQueues ? (
            <p>Filas do Blip indisponíveis no momento. Verifique a integração do Blip.</p>
          ) : null}
        </div>
        <label className="inline-flex items-center gap-2 cursor-pointer font-semibold text-slate-700 bg-white px-3 py-1.5 rounded-lg border border-slate-200 shadow-sm transition-all hover:bg-slate-50">
          <input
            type="checkbox"
            className="rounded border-slate-300 text-brand-primary focus:ring-brand-primary"
            checked={showInactive}
            onChange={(e) => setShowInactive(e.target.checked)}
          />
          Mostrar profissionais inativos / duplicados
        </label>
      </div>

      <div className="overflow-x-auto">
        <table className="min-w-full text-sm">
          <thead>
            <tr className="border-b border-slate-200 bg-slate-50">
              {['ID', 'Nome Feegow', 'Fila Blip', 'CPF Catraca', 'Matrícula Catraca', 'Ignorar auto', 'Ações'].map((col) => (
                <th key={col} className="px-4 py-3 text-left text-xs font-semibold uppercase tracking-wider text-slate-500">{col}</th>
              ))}
            </tr>
          </thead>

          <tbody className="divide-y divide-slate-100">
            {loading ? (
              <tr>
                <td colSpan={7} className="px-4 py-10 text-center text-sm text-slate-400">Carregando profissionais...</td>
              </tr>
            ) : filteredMappings.length === 0 ? (
              <tr>
                <td colSpan={7} className="px-4 py-10 text-center text-sm text-slate-400">Nenhum profissional encontrado.</td>
              </tr>
            ) : (
              filteredMappings.map((row, idx) => {
                const profissionalId = String(row.profissionalId ?? '').trim();
                const professional = profissionalId ? professionalsById.get(profissionalId) : undefined;
                const feegowName = professional?.name?.trim() ?? '';
                const resolvedFeegowName = feegowName
                  ? feegowName
                  : profissionalId
                    ? `Sem nome (ID ${profissionalId})`
                    : 'ID ausente';
                const isMissingId = !profissionalId;
                const isInactiveRow = row.blipQueueId === 'inactive';

                const rowOptions = [
                  { id: '', name: 'Nenhuma fila selecionada' },
                  { id: 'inactive', name: '🚫 Inativo (Ocultar/Duplicado)' },
                  ...blipQueues.map((q) => ({ id: q.id, name: q.name })),
                ];

                return (
                  <tr key={`${row.profissionalId}-${idx}`} className={`hover:bg-slate-50/80 transition-colors ${isInactiveRow ? 'bg-slate-100/50 opacity-70' : ''}`}>
                    <td className={`px-4 py-3 align-middle font-mono text-xs ${isMissingId ? 'text-rose-600' : 'text-slate-650'}`}>
                      {isMissingId ? 'ID ausente' : profissionalId}
                    </td>
                    <td className="px-4 py-3 align-middle">
                      <span className="font-semibold text-slate-800">{row.profissionalNome || resolvedFeegowName}</span>
                    </td>

                    <td className="px-4 py-3 align-middle min-w-[200px]">
                      <SearchableDropdown
                        options={rowOptions}
                        value={row.blipQueueId || ''}
                        onChange={(val) => updateField(row.profissionalId, 'blipQueueId', val)}
                        placeholder="Selecionar fila do Blip"
                        disabled={!hasBlipQueues}
                      />
                    </td>

                    <td className="px-4 py-3 align-middle w-36">
                      <input
                        type="text"
                        maxLength={11}
                        placeholder="CPF (só dígitos)"
                        value={row.gerAcessoCpf || ''}
                        onChange={(e) => updateField(row.profissionalId, 'gerAcessoCpf', e.target.value.replace(/\D/g, ''))}
                        className="w-full rounded-lg border border-slate-200 px-2 py-1 text-xs text-slate-800 focus:outline-none focus:ring-1 focus:ring-[#feb56c]"
                      />
                    </td>

                    <td className="px-4 py-3 align-middle w-32">
                      <input
                        type="text"
                        placeholder="Matrícula"
                        value={row.gerAcessoMatricula || ''}
                        onChange={(e) => updateField(row.profissionalId, 'gerAcessoMatricula', e.target.value)}
                        className="w-full rounded-lg border border-slate-200 px-2 py-1 text-xs text-slate-800 focus:outline-none focus:ring-1 focus:ring-[#feb56c]"
                      />
                    </td>

                    <td className="px-4 py-3 align-middle text-center">
                      <input
                        type="checkbox"
                        className="rounded border-slate-300 text-brand-primary focus:ring-brand-primary"
                        checked={Boolean(row.ignoreAutoSchedule)}
                        onChange={(e) => updateField(row.profissionalId, 'ignoreAutoSchedule', e.target.checked)}
                      />
                    </td>

                    <td className="px-4 py-3 align-middle">
                      {isInactiveRow ? (
                        <button
                          type="button"
                          onClick={() => {
                            updateField(row.profissionalId, 'blipQueueId', '');
                            toast.info('Restaurado. Clique em "Salvar Mapeamentos" para gravar.');
                          }}
                          className="inline-flex items-center gap-1.5 rounded-lg bg-emerald-50 px-2.5 py-1 text-xs font-semibold text-emerald-700 hover:bg-emerald-100 transition-colors shadow-sm"
                        >
                          Restaurar
                        </button>
                      ) : (
                        <button
                          type="button"
                          onClick={() => {
                            updateField(row.profissionalId, 'blipQueueId', 'inactive');
                            toast.info('Marcado como Inativo. Clique em "Salvar Mapeamentos" para gravar.');
                          }}
                          className="inline-flex items-center gap-1.5 rounded-lg bg-rose-50 px-2.5 py-1 text-xs font-semibold text-rose-700 hover:bg-rose-100 transition-colors shadow-sm"
                        >
                          <Trash2 size={13} />
                          Inativar
                        </button>
                      )}
                    </td>
                  </tr>
                );
              })
            )}
          </tbody>
        </table>
      </div>
    </section>
  );
}
