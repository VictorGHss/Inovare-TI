import { useEffect, useMemo, useState } from 'react';
import { Loader2, Save, Trash2 } from 'lucide-react';
import { toast } from 'react-toastify';

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

const inlineInputClass =
  'w-full rounded-lg border border-transparent bg-transparent px-2 py-1.5 text-sm text-slate-700 transition-all focus:border-brand-primary/40 focus:bg-white focus:outline-none focus:ring-2 focus:ring-brand-primary/25';

export default function ProfessionalMappingPanel() {
  const [professionals, setProfessionals] = useState<FeegowProfessional[]>([]);
  const [mappings, setMappings] = useState<DoctorMapping[]>([]);
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

  useEffect(() => {
    async function load() {
      try {
        setLoading(true);
        const [pros, dbMappings, queues] = await Promise.all([
          getFeegowProfessionals(),
          getMappings(),
          getBlipQueuesList(),
        ]);

        setProfessionals(Array.isArray(pros) ? pros : []);
        setBlipQueues(Array.isArray(queues) ? queues : []);

        const mappingById = new Map<string, DoctorMapping>();
        if (Array.isArray(dbMappings)) {
          (dbMappings as DoctorMapping[]).forEach((m) => mappingById.set(String(m.profissionalId), m));
        }

        const merged: DoctorMapping[] = (Array.isArray(pros) ? pros : []).map((p) => {
          const existing = mappingById.get(String(p.id));
          return {
            id: existing?.id,
            profissionalId: String(p.id ?? ''),
            blipQueueId: existing?.blipQueueId ?? '',
            itsmUserId: existing?.itsmUserId ?? '',
            discordWebhookUrl: existing?.discordWebhookUrl ?? '',
            externalWaLink: existing?.externalWaLink ?? '',
            profissionalNome: existing?.profissionalNome ?? p.name ?? '',
            isExternal: existing?.isExternal ?? false,
            ignoreAutoSchedule: existing?.ignoreAutoSchedule ?? false,
          } as DoctorMapping;
        });

        setMappings(merged);
      } catch (error) {
        toast.error(getApiErrorMessage(error, 'Falha ao carregar profissionais ou mapeamentos.'));
      } finally {
        setLoading(false);
      }
    }

    void load();
  }, []);

  async function handleSyncData() {
    try {
      setSyncingData(true);
      const [pros, queues, dbMappings] = await Promise.all([
        getFeegowProfessionals(),
        getBlipQueuesList(),
        getMappings(),
      ]);

      setProfessionals(Array.isArray(pros) ? pros : []);
      setBlipQueues(Array.isArray(queues) ? queues : []);

      const mappingById = new Map<string, DoctorMapping>();
      if (Array.isArray(dbMappings)) {
        (dbMappings as DoctorMapping[]).forEach((m) => mappingById.set(String(m.profissionalId), m));
      }

      const merged: DoctorMapping[] = (Array.isArray(pros) ? pros : []).map((p) => {
        const existing = mappingById.get(String(p.id));
        return {
          id: existing?.id,
          profissionalId: String(p.id ?? ''),
          blipQueueId: existing?.blipQueueId ?? '',
          itsmUserId: existing?.itsmUserId ?? '',
          discordWebhookUrl: existing?.discordWebhookUrl ?? '',
          externalWaLink: existing?.externalWaLink ?? '',
          profissionalNome: existing?.profissionalNome ?? p.name ?? '',
          isExternal: existing?.isExternal ?? false,
          ignoreAutoSchedule: existing?.ignoreAutoSchedule ?? false,
        } as DoctorMapping;
      });

      setMappings(merged);
      toast.success('Dados sincronizados com sucesso.');
    } catch (error) {
      toast.error(getApiErrorMessage(error, 'Falha ao sincronizar dados.'));
    } finally {
      setSyncingData(false);
    }
  }

  const hasChanges = useMemo(() => true, []); // allow save for simplicity

  function updateField(profissionalId: string, field: keyof DoctorMapping, value: string | boolean) {
    setMappings((current) => current.map((m) => (String(m.profissionalId) === String(profissionalId) ? { ...m, [field]: value } : m)));
  }

  async function handleSave() {
    try {
      setSaving(true);
      const payload = mappings.map((m) => ({
        profissionalId: String(m.profissionalId),
        blipQueueId: String(m.blipQueueId ?? '').trim(),
        itsmUserId: String(m.itsmUserId ?? '').trim(),
        discordWebhookUrl: String(m.discordWebhookUrl ?? '').trim(),
        externalWaLink: String(m.externalWaLink ?? '').trim(),
        profissionalNome: String(m.profissionalNome ?? '').trim(),
        isExternal: Boolean(m.isExternal),
        ignoreAutoSchedule: Boolean(m.ignoreAutoSchedule),
      }));
      try {
        const resp = await syncMappings(payload);
        const status = resp?.status ?? 0;
        if (status >= 200 && status < 300) {
          toast.success('Mapeamentos salvos com sucesso.');
        } else {
          // If the API client returns a non-2xx response without throwing, ensure we show the real API message
          const fakeError = { response: { data: resp?.data } } as unknown;
          const reason = getApiErrorMessage(fakeError, resp?.data?.reason ?? resp?.statusText ?? `HTTP ${status}`);
          toast.error(reason);
        }
      } catch (err) {
        // axios throws for non-2xx; extract server message when available and show as error
        toast.error(getApiErrorMessage(err, 'Falha ao salvar mapeamentos.'));
      }
    } catch (error) {
      toast.error(getApiErrorMessage(error, 'Falha ao salvar mapeamentos.'));
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
            <p className="mt-0.5 text-xs text-slate-500">Edite exibição, fila e rota por profissional.</p>
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

      {(missingIdCount > 0 || !hasBlipQueues || true) && (
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
      )}

      <div className="overflow-x-auto">
        <table className="min-w-full text-sm">
          <thead>
            <tr className="border-b border-slate-200 bg-slate-50">
              {['ID', 'Nome Feegow', 'Display Name', 'Fila Blip', 'Ignorar auto', 'Is External', 'WA Link', 'Ações'].map((col) => (
                <th key={col} className="px-4 py-3 text-left text-xs font-semibold uppercase tracking-wider text-slate-500">{col}</th>
              ))}
            </tr>
          </thead>

          <tbody className="divide-y divide-slate-100">
            {loading ? (
              <tr>
                <td colSpan={8} className="px-4 py-10 text-center text-sm text-slate-400">Carregando profissionais...</td>
              </tr>
            ) : filteredMappings.length === 0 ? (
              <tr>
                <td colSpan={8} className="px-4 py-10 text-center text-sm text-slate-400">Nenhum profissional encontrado.</td>
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

                return (
                  <tr key={`${row.profissionalId}-${idx}`} className={`hover:bg-slate-50/80 transition-colors ${isInactiveRow ? 'bg-slate-100/50 opacity-70' : ''}`}>
                    <td className={`px-4 py-3 align-middle ${isMissingId ? 'text-rose-600' : ''}`}>
                      {isMissingId ? 'ID ausente' : profissionalId}
                    </td>
                    <td className="px-4 py-3 align-middle font-medium text-slate-700">{resolvedFeegowName}</td>
                  <td className="px-4 py-3 align-middle">
                    <input value={row.profissionalNome} onChange={(e) => updateField(row.profissionalId, 'profissionalNome', e.target.value)} className={inlineInputClass} />
                  </td>

                  <td className="px-4 py-3 align-middle">
                    <select
                      value={row.blipQueueId ?? ''}
                      onChange={(e) => updateField(row.profissionalId, 'blipQueueId', e.target.value)}
                      disabled={!hasBlipQueues}
                      className={`${inlineInputClass} border border-slate-200 bg-white ${isInactiveRow ? 'text-rose-500 border-rose-200 bg-rose-50/20' : ''} ${!hasBlipQueues ? 'opacity-60 cursor-not-allowed' : ''}`}
                    >
                      <option value="">{hasBlipQueues ? 'Selecionar fila do Blip' : 'Filas do Blip indisponíveis'}</option>
                      <option value="inactive" className="text-rose-600 font-medium">🚫 Inativo (Ocultar/Duplicado)</option>
                      {blipQueues.map((q) => (
                        <option key={q.id} value={q.id}>{q.name}</option>
                      ))}
                    </select>
                  </td>

                  <td className="px-4 py-3 align-middle">
                    <input
                      type="checkbox"
                      className="rounded border-slate-300 text-brand-primary focus:ring-brand-primary"
                      checked={Boolean(row.ignoreAutoSchedule)}
                      onChange={(e) => updateField(row.profissionalId, 'ignoreAutoSchedule', e.target.checked)}
                    />
                  </td>

                  <td className="px-4 py-3 align-middle">
                    <input
                      type="checkbox"
                      className="rounded border-slate-300 text-brand-primary focus:ring-brand-primary"
                      checked={Boolean(row.isExternal)}
                      onChange={(e) => updateField(row.profissionalId, 'isExternal', e.target.checked)}
                    />
                  </td>

                  <td className="px-4 py-3 align-middle">
                    <input value={row.externalWaLink} onChange={(e) => updateField(row.profissionalId, 'externalWaLink', e.target.value)} className={inlineInputClass} />
                  </td>

                  <td className="px-4 py-3 align-middle">
                    {isInactiveRow ? (
                      <button
                        type="button"
                        onClick={() => {
                          updateField(row.profissionalId, 'blipQueueId', '');
                          toast.info('Restaurado. Clique em "Salvar Mapeamentos" para gravar.');
                        }}
                        className="inline-flex items-center gap-1.5 rounded-lg bg-emerald-50 px-3 py-1.5 text-xs font-semibold text-emerald-700 hover:bg-emerald-100 transition-colors shadow-sm"
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
                        className="inline-flex items-center gap-1.5 rounded-lg bg-rose-50 px-3 py-1.5 text-xs font-semibold text-rose-700 hover:bg-rose-100 transition-colors shadow-sm"
                      >
                        <Trash2 size={14} />
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
