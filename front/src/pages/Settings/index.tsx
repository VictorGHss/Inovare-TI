import { useEffect, useMemo, useState } from 'react';
import { Save, Globe, User as UserIcon, Clock3 } from 'lucide-react';
import { toast } from 'react-toastify';

import { useAuth } from '../../contexts/AuthContext';
import { getSystemSettings, getAdminConfig, updateSystemSettings } from '../../services/inventoryService';
import type { SystemSetting, UpdateSystemSettingsPayload } from '../../types/models';
import PageHero from '../../components/PageHero';
import ReportSchedulesSection from './ReportSchedulesSection';
import AppointmentControlPanel from './AppointmentControlPanel';

type TabType = 'system' | 'profile';

const inputClassName =
  'w-full rounded-xl border border-slate-200 bg-white px-3.5 py-2.5 text-sm text-slate-800 shadow-sm placeholder-slate-400 focus:outline-none focus:ring-2 focus:ring-brand-primary/30 focus:border-brand-primary transition-all';

const friendlyLabels: Record<string, string> = {
  SLA_URGENT_HOURS: 'SLA — Chamados Urgentes (Horas)',
  ATTACHMENT_SIZE_LIMIT_MB: 'Limite de Tamanho de Anexo (MB)',
};

function getFriendlyLabel(settingKey: string): string {
  return friendlyLabels[settingKey] ?? settingKey.replaceAll('_', ' ');
}

function WebhookBadge({ status }: { status: string }) {
  let text = 'Desconhecido';
  let cls = 'bg-amber-100 text-amber-700';
  switch (status) {
    case 'PRESENT':
      text = 'Presente';
      cls = 'bg-emerald-100 text-emerald-700';
      break;
    case 'INVALID':
      text = 'Erro/Inválido';
      cls = 'bg-red-100 text-red-700';
      break;
    case 'MISSING':
      text = 'Ausente';
      cls = 'bg-slate-100 text-slate-500';
      break;
    default:
      text = 'Desconhecido';
      cls = 'bg-amber-100 text-amber-700';
  }
  return <span className={`${cls} text-xs font-medium px-2 py-1 rounded`}>{text}</span>;
}

export default function Settings() {
  const { user } = useAuth();
  const [activeTab, setActiveTab] = useState<TabType>(user?.role === 'ADMIN' ? 'system' : 'profile');
  const [settings, setSettings] = useState<SystemSetting[]>([]);
  const [values, setValues] = useState<Record<string, string>>({});
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [discordWebhookStatus, setDiscordWebhookStatus] = useState<string | null>(null);
  

  

  

  const isAdmin = user?.role === 'ADMIN';

  useEffect(() => {
    async function loadSettings() {
      if (!isAdmin) {
        setLoading(false);
        return;
      }
      setLoading(true);
      try {
        const data = await getSystemSettings();
        const safeSettings = Array.isArray(data) ? data : [];
        setSettings(safeSettings);
        setValues(
          safeSettings.reduce<Record<string, string>>((acc, setting) => {
            acc[setting.id] = setting.value;
            return acc;
          }, {}),
        );
        try {
          const cfg = await getAdminConfig();
          setDiscordWebhookStatus(cfg.discordWebhookStatus ?? (cfg.discordWebhookPresent ? 'PRESENT' : 'MISSING'));
        } catch {
          // Em caso de falha ao obter configuração de admin, considera webhook desconhecido
          setDiscordWebhookStatus(null);
        }
        // Agendamentos são carregados pelo componente ReportSchedulesSection
      } catch {
        toast.error('Erro ao carregar configurações globais.');
        setSettings([]);
        setValues({});
      } finally {
        setLoading(false);
      }
    }
    loadSettings();
  }, [isAdmin]);

  // categorias e integrações removidas — simplificamos para SLA e Agendamentos

  const hasChanges = useMemo(() => {
    return settings.some((setting) => values[setting.id] !== setting.value);
  }, [settings, values]);

  async function handleSave() {
    if (!isAdmin) return;
    const payload: UpdateSystemSettingsPayload = settings.reduce((acc, setting) => {
      acc[setting.id] = values[setting.id] ?? '';
      return acc;
    }, {} as UpdateSystemSettingsPayload);
    setSaving(true);
    try {
      const updated = await updateSystemSettings(payload);
      setSettings(updated);
      setValues(
        updated.reduce<Record<string, string>>((acc, setting) => {
          acc[setting.id] = setting.value;
          return acc;
        }, {}),
      );
      toast.success('Configurações salvas com sucesso.');
    } catch {
      toast.error('Erro ao salvar configurações.');
    } finally {
      setSaving(false);
    }
  }

  function handleSavePreferences() {
    toast.success('Preferências salvas com sucesso.');
  }

  function isProblemDetail(obj: unknown): obj is { detail?: string } {
    return typeof obj === 'object' && obj !== null && 'detail' in (obj as Record<string, unknown>) && typeof (obj as Record<string, unknown>).detail === 'string';
  }

  function getApiErrorMessage(error: unknown, fallbackMessage: string): string {
    if (typeof error === 'object' && error !== null) {
      const maybeResponse = error as { response?: { data?: unknown } };
      const data = maybeResponse.response?.data;
      if (isProblemDetail(data)) return data.detail ?? fallbackMessage;
      if (typeof data === 'string' && data.includes('<html')) return 'Resposta inesperada do servidor (HTML). Verifique proxy/NGINX.';
    }
    return fallbackMessage;
  }


  const slaKeys = useMemo(() => settings.filter((s) => s.id && s.id.startsWith('SLA_')), [settings]);

  async function handleSaveSLA() {
    if (!isAdmin) return;
    const payload: UpdateSystemSettingsPayload = {};
    slaKeys.forEach((s) => { payload[s.id] = values[s.id] ?? ''; });
    setSaving(true);
    try {
      const updated = await updateSystemSettings(payload);
      setSettings(updated);
      setValues(updated.reduce<Record<string, string>>((acc, setting) => { acc[setting.id] = setting.value; return acc; }, {}));
      toast.success('SLAs salvos com sucesso.');
    } catch (error) {
      toast.error(getApiErrorMessage(error, 'Erro ao salvar SLAs.'));
    } finally {
      setSaving(false);
    }
  }

  

  if (!isAdmin && activeTab === 'system') {
    return (
      <main className="w-full max-w-full px-4 sm:px-6 lg:px-8 py-8">
        <div className="bg-white rounded-2xl border border-slate-200 shadow-sm p-12 text-center">
          <div className="w-12 h-12 bg-slate-100 rounded-full flex items-center justify-center mx-auto mb-4">
            <Globe size={22} className="text-slate-400" />
          </div>
          <p className="text-sm font-medium text-slate-700">Acesso Restrito</p>
          <p className="text-xs text-slate-400 mt-1">Você não possui permissão para acessar configurações globais.</p>
        </div>
      </main>
    );
  }

  const tabs = [
    ...(isAdmin ? [{ id: 'system' as TabType, label: 'Sistema', icon: Globe }] : []),
    { id: 'profile' as TabType, label: 'Perfil', icon: UserIcon },
  ];

  return (
    <main className="w-full max-w-full px-4 sm:px-6 lg:px-8 py-8">
      <PageHero
        eyebrow="Sistema"
        title="Configurações"
        description="Ajuste parâmetros globais e personalize preferências para adaptar o ambiente ao fluxo da equipe."
      />

      {/* Tab Navigation */}
      <div className="flex items-center gap-1 mb-6 bg-slate-100 rounded-xl p-1 w-fit">
        {tabs.map(({ id, label, icon: Icon }) => (
          <button
            key={id}
            onClick={() => setActiveTab(id)}
            className={`flex items-center gap-2 px-4 py-2 text-sm font-medium rounded-lg transition-all ${
              activeTab === id
                ? 'bg-white text-slate-900 shadow-sm'
                : 'text-slate-500 hover:text-slate-700'
            }`}
          >
            <Icon size={15} />
            {label}
          </button>
        ))}
      </div>

      {/* ── TAB: SISTEMA ── */}
      {activeTab === 'system' && isAdmin && (
        <div className="space-y-5">
          {loading ? (
            <div className="space-y-3">
              <div className="h-20 rounded-2xl bg-slate-100 animate-pulse" />
              <div className="h-20 rounded-2xl bg-slate-100 animate-pulse" />
              <div className="h-20 rounded-2xl bg-slate-100 animate-pulse" />
            </div>
          ) : settings.length === 0 ? (
            <div className="bg-white rounded-2xl border border-slate-200 p-8 text-center">
              <p className="text-sm text-slate-500">Nenhuma configuração global encontrada.</p>
            </div>
          ) : (
            <>
              {/* Configurações gerais */}
              <div className="bg-white rounded-2xl border border-slate-200 overflow-hidden">
                <div className="px-6 py-4 border-b border-slate-100">
                  <div className="flex items-center justify-between">
                    <div>
                      <h2 className="text-sm font-semibold text-slate-900">Parâmetros Globais</h2>
                      <p className="text-xs text-slate-500 mt-0.5">Configurações gerais do sistema.</p>
                    </div>
                    <div>
                      {discordWebhookStatus && (
                        <WebhookBadge status={discordWebhookStatus} />
                      )}
                    </div>
                  </div>
                </div>
                <div className="divide-y divide-slate-100">
                  {Array.isArray(settings) && settings.map((setting) => (
                    <div key={setting.id} className="px-6 py-4 flex flex-col lg:flex-row lg:items-center gap-3">
                      <div className="flex-1 min-w-0">
                        <p className="text-sm font-medium text-slate-800">{getFriendlyLabel(setting.id)}</p>
                        <p className="text-xs text-slate-400 mt-0.5 truncate">{setting.description ?? 'Sem descrição disponível.'}</p>
                      </div>
                      <div className="w-full lg:w-48 shrink-0">
                        <input
                          type="text"
                          value={values[setting.id] ?? ''}
                          onChange={(event) => setValues((prev) => ({ ...prev, [setting.id]: event.target.value }))}
                          className={inputClassName}
                          placeholder="Valor"
                        />
                      </div>
                    </div>
                  ))}
                </div>
                <div className="px-6 py-4 bg-slate-50 border-t border-slate-100 flex justify-end">
                  <button
                    type="button"
                    disabled={saving || !hasChanges}
                    onClick={handleSave}
                    className="inline-flex items-center gap-2 rounded-xl bg-brand-primary px-4 py-2 text-sm font-semibold text-white transition-colors hover:bg-brand-primary-dark disabled:cursor-not-allowed disabled:opacity-40"
                  >
                    <Save size={14} />
                    {saving ? 'Salvando...' : 'Salvar Configurações'}
                  </button>
                </div>
              </div>

              {/* SLA */}
              <div className="bg-white rounded-2xl border border-slate-200 overflow-hidden">
                  <div className="px-6 py-4 border-b border-slate-100 flex items-center gap-3">
                  <div className="w-8 h-8 rounded-lg bg-amber-50 flex items-center justify-center shrink-0">
                    <Clock3 size={16} className="text-[#feb56c]" />
                  </div>
                  <div>
                    <h2 className="text-sm font-semibold text-slate-900">Configurações de SLA</h2>
                    <p className="text-xs text-slate-500 mt-0.5">Defina os prazos de atendimento para cada nível de prioridade.</p>
                  </div>
                </div>
                <div className="divide-y divide-slate-100">
                  {Array.isArray(slaKeys) && slaKeys.length === 0 ? (
                    <div className="px-6 py-6 text-sm text-slate-500 text-center">Nenhuma configuração de SLA encontrada.</div>
                  ) : (
                    Array.isArray(slaKeys) && slaKeys.map((s) => (
                      <div key={s.id} className="px-6 py-4 flex items-center gap-4">
                        <div className="flex-1 min-w-0">
                          <p className="text-sm font-medium text-slate-800">{getFriendlyLabel(s.id)}</p>
                          <p className="text-xs text-slate-400 mt-0.5">{s.description ?? 'Sem descrição.'}</p>
                        </div>
                        <div className="w-32 shrink-0">
                          <input
                            type="number"
                            min={0}
                            value={values[s.id] ?? ''}
                            onChange={(e) => setValues((prev) => ({ ...prev, [s.id]: e.target.value }))}
                            className={inputClassName}
                          />
                        </div>
                      </div>
                    ))
                  )}
                </div>
                <div className="px-6 py-4 bg-slate-50 border-t border-slate-100 flex justify-end">
                  <button
                    onClick={handleSaveSLA}
                    disabled={saving}
                    className="inline-flex items-center gap-2 rounded-xl bg-brand-primary px-4 py-2 text-sm font-semibold text-white transition-colors hover:bg-brand-primary-dark disabled:opacity-40"
                  >
                    <Save size={14} />
                    Salvar SLAs
                  </button>
                </div>
              </div>

              {/* Adicionar configuração removida — foco em SLAs e Agendamentos */}

              {/* Agendamentos (extraído para componente separado) */}
              <ReportSchedulesSection />
              <AppointmentControlPanel />
            </>
          )}
        </div>
      )}

      {/* ── TAB: PERFIL ── */}
      {activeTab === 'profile' && (
        <div className="space-y-3 max-w-2xl">
          <div className="bg-white rounded-2xl border border-slate-200 px-6 py-6">
            <div className="flex items-center gap-4">
              <div className="w-10 h-10 rounded-full bg-[#fff6ee] flex items-center justify-center">
                <UserIcon size={20} className="text-[#feb56c]" />
              </div>
              <div>
                <p className="text-sm font-semibold text-slate-900">Perfil</p>
                <p className="text-xs text-slate-400 mt-0.5">Informações básicas da sua conta</p>
              </div>
            </div>

            <div className="mt-4 grid grid-cols-1 md:grid-cols-2 gap-3">
              <div>
                <label className="block text-xs font-medium text-slate-600 mb-1.5">Nome</label>
                <input type="text" readOnly value={user?.name ?? ''} className={inputClassName} />
              </div>
              <div>
                <label className="block text-xs font-medium text-slate-600 mb-1.5">E-mail</label>
                <input type="text" readOnly value={user?.email ?? ''} className={inputClassName} />
              </div>
            </div>

            <div className="flex justify-end mt-4">
              <button
                type="button"
                onClick={handleSavePreferences}
                className="inline-flex items-center gap-2 rounded-xl bg-brand-primary px-4 py-2.5 text-sm font-semibold text-white transition-colors hover:bg-brand-primary-dark"
              >
                <Save size={14} />
                Salvar Preferências
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Integração removida - simplificação da página de Settings */}
    </main>
  );
}

