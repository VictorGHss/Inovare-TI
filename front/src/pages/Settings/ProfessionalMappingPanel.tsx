import { useEffect, useMemo, useState } from 'react';
import { Loader2, Save } from 'lucide-react';
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
            profissionalId: String(p.id ?? ''),
            blipQueueId: existing?.blipQueueId ?? '',
            itsmUserId: existing?.itsmUserId ?? '',
            discordWebhookUrl: existing?.discordWebhookUrl ?? '',
            externalWaLink: existing?.externalWaLink ?? '',
            profissionalNome: existing?.profissionalNome ?? p.name ?? '',
            isExternal: existing?.isExternal ?? false,
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
          profissionalId: String(p.id ?? ''),
          blipQueueId: existing?.blipQueueId ?? '',
          itsmUserId: existing?.itsmUserId ?? '',
          discordWebhookUrl: existing?.discordWebhookUrl ?? '',
          externalWaLink: existing?.externalWaLink ?? '',
          profissionalNome: existing?.profissionalNome ?? p.name ?? '',
          isExternal: existing?.isExternal ?? false,
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

  function updateField(index: number, field: keyof DoctorMapping, value: string | boolean) {
    setMappings((current) => current.map((m, i) => (i === index ? { ...m, [field]: value } : m)));
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

      <div className="overflow-x-auto">
        <table className="min-w-full text-sm">
          <thead>
            <tr className="border-b border-slate-200 bg-slate-50">
              {['ID', 'Nome Feegow', 'Display Name', 'Fila Blip', 'Is External', 'WA Link'].map((col) => (
                <th key={col} className="px-4 py-3 text-left text-xs font-semibold uppercase tracking-wider text-slate-500">{col}</th>
              ))}
            </tr>
          </thead>

          <tbody className="divide-y divide-slate-100">
            {loading ? (
              <tr>
                <td colSpan={6} className="px-4 py-10 text-center text-sm text-slate-400">Carregando profissionais...</td>
              </tr>
            ) : mappings.length === 0 ? (
              <tr>
                <td colSpan={6} className="px-4 py-10 text-center text-sm text-slate-400">Nenhum profissional encontrado.</td>
              </tr>
            ) : (
              mappings.map((row, idx) => (
                <tr key={`${row.profissionalId}-${idx}`} className="hover:bg-slate-50/80 transition-colors">
                  <td className="px-4 py-3 align-middle">{row.profissionalId}</td>
                  <td className="px-4 py-3 align-middle">{professionals.find((p) => String(p.id) === String(row.profissionalId))?.name ?? ''}</td>
                  <td className="px-4 py-3 align-middle">
                    <input value={row.profissionalNome} onChange={(e) => updateField(idx, 'profissionalNome', e.target.value)} className={inlineInputClass} />
                  </td>

                  <td className="px-4 py-3 align-middle">
                    <select value={row.blipQueueId} onChange={(e) => updateField(idx, 'blipQueueId', e.target.value)} className={`${inlineInputClass} border border-slate-200 bg-white`}>
                      <option value="">Selecionar fila do Blip</option>
                      {blipQueues.map((q) => (
                        <option key={q.id} value={q.id}>{q.name}</option>
                      ))}
                    </select>
                  </td>

                  <td className="px-4 py-3 align-middle">
                    <input type="checkbox" checked={Boolean(row.isExternal)} onChange={(e) => updateField(idx, 'isExternal', e.target.checked)} />
                  </td>

                  <td className="px-4 py-3 align-middle">
                    <input value={row.externalWaLink} onChange={(e) => updateField(idx, 'externalWaLink', e.target.value)} className={inlineInputClass} />
                  </td>
                </tr>
              ))
            )}
          </tbody>
        </table>
      </div>
    </section>
  );
}
