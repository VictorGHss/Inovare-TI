import { useEffect, useMemo, useState } from 'react';
import { Save, Globe, User as UserIcon, Clock3, MessageSquare, DollarSign, Activity, MessageCircle, ArrowLeft, Settings2, Database, Tag, FileText, Share2 } from 'lucide-react';
import { toast } from 'react-toastify';
import { motion, AnimatePresence } from 'framer-motion';

import { useAuth } from '../../contexts/AuthContext';
import { getSystemSettings, getAdminConfig, updateSystemSettings } from '../../services/inventoryService';
import FinancialTwoFactorChallenge from '../../components/FinancialTwoFactorChallenge';
import type { SystemSetting, UpdateSystemSettingsPayload } from '../../types/models';
import PageHero from '../../components/PageHero';
import ReportSchedulesSection from './ReportSchedulesSection';
import AppointmentControlPanel from './AppointmentControlPanel';
import ProfessionalMappingPanel from './ProfessionalMappingPanel';
import CategoriesSection from './CategoriesSection';
import BackupsSection from './BackupsSection';

type TabType = 'system' | 'profile';
type SubSectionType = 'menu' | 'integrations' | 'system-params' | 'sla' | 'reports' | 'feegow' | 'categories' | 'backups';

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

function PresenceBadge({ present }: { present?: boolean }) {
  const isPresent = Boolean(present);
  const text = isPresent ? 'Configurado' : 'Ausente';
  const cls = isPresent ? 'bg-emerald-100 text-emerald-700' : 'bg-slate-100 text-slate-500';
  return <span className={`${cls} text-xs font-medium px-2 py-1 rounded`}>{text}</span>;
}

export default function Settings() {
  const { user, isTwoFactorVerified } = useAuth();
  const [activeTab, setActiveTab] = useState<TabType>(user?.role === 'ADMIN' ? 'system' : 'profile');
  const [activeSubSection, setActiveSubSection] = useState<SubSectionType>('menu');
  const [settings, setSettings] = useState<SystemSetting[]>([]);
  const [values, setValues] = useState<Record<string, string>>({});
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [discordWebhookStatus, setDiscordWebhookStatus] = useState<string | null>(null);
  const [adminConfig, setAdminConfig] = useState<AdminConfig | null>(null);

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
          setAdminConfig(cfg);
          setDiscordWebhookStatus(cfg.discordWebhookStatus ?? (cfg.discordWebhookPresent ? 'PRESENT' : 'MISSING'));
        } catch {
          setDiscordWebhookStatus(null);
          setAdminConfig(null);
        }
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

  const subSections = [
    {
      id: 'integrations',
      title: 'Integrações',
      desc: 'Conecte Discord, Conta Azul, Feegow e Blip.',
      icon: Share2,
      color: 'bg-indigo-50/70 text-indigo-700 border-indigo-100/50 hover:bg-indigo-50 hover:border-indigo-200',
    },
    {
      id: 'system-params',
      title: 'Parâmetros Globais',
      desc: 'Ajuste limites de anexos e chaves do sistema.',
      icon: Settings2,
      color: 'bg-blue-50/70 text-blue-700 border-blue-100/50 hover:bg-blue-50 hover:border-blue-200',
    },
    {
      id: 'sla',
      title: 'Prazos de SLA',
      desc: 'Defina limites de atendimento por prioridade.',
      icon: Clock3,
      color: 'bg-amber-50/70 text-amber-700 border-amber-100/50 hover:bg-amber-50 hover:border-amber-200',
    },
    {
      id: 'reports',
      title: 'Agendamento de Relatórios',
      desc: 'Programe envios automáticos de relatórios.',
      icon: FileText,
      color: 'bg-emerald-50/70 text-emerald-700 border-emerald-100/50 hover:bg-emerald-50 hover:border-emerald-200',
    },
    {
      id: 'feegow',
      title: 'Mapeamento Feegow / Blip',
      desc: 'Vincule profissionais às filas de atendimento do Blip.',
      icon: MessageCircle,
      color: 'bg-cyan-50/70 text-cyan-700 border-cyan-100/50 hover:bg-cyan-50 hover:border-cyan-200',
    },
    {
      id: 'categories',
      title: 'Categorias do Sistema',
      desc: 'Cadastre categorias de itens e ativos do inventário.',
      icon: Tag,
      color: 'bg-purple-50/70 text-purple-700 border-purple-100/50 hover:bg-purple-50 hover:border-purple-200',
    },
    {
      id: 'backups',
      title: 'Backups do Sistema',
      desc: 'Gere snapshots, baixe ZIPs ou delete backups.',
      icon: Database,
      color: 'bg-rose-50/70 text-rose-700 border-rose-100/50 hover:bg-rose-50 hover:border-rose-200',
    },
  ] as const;

  const subSectionTitles: Record<SubSectionType, string> = {
    menu: 'Painel do Sistema',
    integrations: 'Integrações do Ecossistema',
    'system-params': 'Parâmetros Globais',
    sla: 'Configurações de SLA',
    reports: 'Agendamento de Relatórios',
    feegow: 'Mapeamento Feegow / Blip',
    categories: 'Categorias do Sistema',
    backups: 'Backups do Sistema',
  };

  const subSectionDescriptions: Record<SubSectionType, string> = {
    menu: 'Gerencie todas as facetas administrativas e integrativas da plataforma.',
    integrations: 'Configure integrações com Discord, Conta Azul, Feegow e Blip.',
    'system-params': 'Ajuste limites de tamanho de anexo, e-mails e chaves globais.',
    sla: 'Defina os prazos de atendimento (horas) para chamados com base no nível de prioridade.',
    reports: 'Monitore e configure a geração automática de relatórios gerenciais.',
    feegow: 'Gerencie chaves do WhatsApp e associe médicos às filas do Blip.',
    categories: 'Configure e gerencie categorias de itens e de ativos.',
    backups: 'Gere backups de banco de dados, baixe snapshots de segurança ou delete antigos.',
  };

  const renderBackHeader = () => (
    <div className="mb-6 flex items-center gap-3">
      <button
        type="button"
        onClick={() => setActiveSubSection('menu')}
        className="inline-flex h-10 w-10 items-center justify-center rounded-xl border border-slate-200 bg-white text-slate-600 shadow-sm transition-all hover:bg-slate-50 hover:text-slate-800 hover:scale-105 active:scale-95"
      >
        <ArrowLeft size={18} />
      </button>
      <div>
        <h2 className="text-base font-bold text-slate-900">{subSectionTitles[activeSubSection]}</h2>
        <p className="text-xs text-slate-500">{subSectionDescriptions[activeSubSection]}</p>
      </div>
    </div>
  );

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
            onClick={() => {
              setActiveTab(id);
              if (id === 'profile') setActiveSubSection('menu');
            }}
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
        <div className="space-y-6">
          {loading ? (
            <div className="space-y-3">
              <div className="h-20 rounded-2xl bg-slate-100 animate-pulse" />
              <div className="h-20 rounded-2xl bg-slate-100 animate-pulse" />
              <div className="h-20 rounded-2xl bg-slate-100 animate-pulse" />
            </div>
          ) : (
            <AnimatePresence mode="wait">
              {activeSubSection === 'menu' && (
                <motion.div
                  key="menu"
                  initial={{ opacity: 0, y: 15 }}
                  animate={{ opacity: 1, y: 0 }}
                  exit={{ opacity: 0, y: -15 }}
                  transition={{ duration: 0.2 }}
                  className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6"
                >
                  {subSections.map(({ id, title, desc, icon: Icon, color }) => (
                    <motion.button
                      key={id}
                      onClick={() => setActiveSubSection(id)}
                      whileHover={{ scale: 1.02, translateY: -4 }}
                      whileTap={{ scale: 0.98 }}
                      className={`flex flex-col text-left p-6 rounded-2xl border bg-white shadow-sm hover:shadow-md transition-all cursor-pointer group relative overflow-hidden`}
                    >
                      <div className="flex items-center gap-4 mb-3">
                        <div className={`w-12 h-12 rounded-xl ${color.split(' ')[0]} ${color.split(' ')[1]} flex items-center justify-center shrink-0 shadow-sm transition-transform group-hover:scale-110`}>
                          <Icon size={22} />
                        </div>
                        <h3 className="text-sm font-bold text-slate-800 group-hover:text-brand-primary transition-colors">{title}</h3>
                      </div>
                      <p className="text-xs text-slate-500 leading-relaxed pr-4">{desc}</p>
                      
                      <div className="absolute bottom-4 right-4 text-slate-350 opacity-0 group-hover:opacity-100 group-hover:translate-x-1 transition-all">
                        <ArrowLeft size={16} className="rotate-180" />
                      </div>
                    </motion.button>
                  ))}
                </motion.div>
              )}

              {activeSubSection === 'integrations' && !isTwoFactorVerified && (
                <FinancialTwoFactorChallenge onClose={() => setActiveSubSection('menu')} />
              )}

              {activeSubSection === 'integrations' && isTwoFactorVerified && (
                <motion.div
                  key="integrations"
                  initial={{ opacity: 0, scale: 0.98 }}
                  animate={{ opacity: 1, scale: 1 }}
                  exit={{ opacity: 0, scale: 0.98 }}
                  transition={{ duration: 0.2 }}
                >
                  {renderBackHeader()}
                  
                  <div className="grid grid-cols-1 xl:grid-cols-2 gap-6">
                    {/* Card: Discord */}
                    <div className="rounded-2xl border border-indigo-100 bg-indigo-50/10 p-5 flex flex-col justify-between transition-all hover:shadow-sm">
                      <div>
                        <div className="flex items-center justify-between mb-4">
                          <div className="flex items-center gap-3">
                            <div className="w-10 h-10 rounded-xl bg-indigo-100 flex items-center justify-center text-indigo-600 shrink-0 shadow-sm">
                              <MessageSquare size={20} />
                            </div>
                            <div>
                              <h3 className="text-sm font-bold text-slate-800">Discord</h3>
                              <p className="text-xs text-slate-400">Notificações e alertas</p>
                            </div>
                          </div>
                          <PresenceBadge present={adminConfig?.discordWebhookPresent || adminConfig?.discordWebhookUrlPresent} />
                        </div>
                        <div className="space-y-2 mt-2">
                          <div className="flex items-center justify-between">
                            <span className="text-xs text-slate-500">Webhook URL</span>
                            <PresenceBadge present={adminConfig?.discordWebhookUrlPresent ?? adminConfig?.discordWebhookPresent} />
                          </div>
                          <div className="flex items-center justify-between">
                            <span className="text-xs text-slate-500">Token do Bot</span>
                            <PresenceBadge present={adminConfig?.discordBotTokenPresent} />
                          </div>
                          <div className="flex items-center justify-between">
                            <span className="text-xs text-slate-500">Status do Webhook</span>
                            <WebhookBadge status={discordWebhookStatus ?? 'UNKNOWN'} />
                          </div>
                        </div>
                        <p className="mt-3 text-[11px] text-slate-400">Somente leitura. Valores configurados via ENV.</p>
                      </div>
                    </div>

                    {/* Card: Conta Azul */}
                    <div className="rounded-2xl border border-blue-100 bg-blue-50/10 p-5 flex flex-col justify-between transition-all hover:shadow-sm">
                      <div>
                        <div className="flex items-center justify-between mb-4">
                          <div className="flex items-center gap-3">
                            <div className="w-10 h-10 rounded-xl bg-blue-100 flex items-center justify-center text-blue-600 shrink-0 shadow-sm">
                              <DollarSign size={20} />
                            </div>
                            <div>
                              <h3 className="text-sm font-bold text-slate-800">Conta Azul</h3>
                              <p className="text-xs text-slate-400">ERP e conciliacao financeira</p>
                            </div>
                          </div>
                          <PresenceBadge present={adminConfig?.contaAzulClientIdPresent && adminConfig?.contaAzulClientSecretPresent} />
                        </div>
                        <div className="space-y-2 mt-2">
                          <div className="flex items-center justify-between">
                            <span className="text-xs text-slate-500">Client ID</span>
                            <PresenceBadge present={adminConfig?.contaAzulClientIdPresent} />
                          </div>
                          <div className="flex items-center justify-between">
                            <span className="text-xs text-slate-500">Client Secret</span>
                            <PresenceBadge present={adminConfig?.contaAzulClientSecretPresent} />
                          </div>
                          <div className="flex items-center justify-between">
                            <span className="text-xs text-slate-500">Backup (ENV)</span>
                            <span className="text-xs text-slate-400">Gerenciado no servidor</span>
                          </div>
                        </div>
                        <p className="mt-3 text-[11px] text-slate-400">Somente leitura. Valores configurados via ENV.</p>
                      </div>
                    </div>

                    {/* Card: Feegow */}
                    <div className="rounded-2xl border border-emerald-100 bg-emerald-50/10 p-5 flex flex-col justify-between transition-all hover:shadow-sm">
                      <div>
                        <div className="flex items-center justify-between mb-4">
                          <div className="flex items-center gap-3">
                            <div className="w-10 h-10 rounded-xl bg-emerald-100 flex items-center justify-center text-emerald-600 shrink-0 shadow-sm">
                              <Activity size={20} />
                            </div>
                            <div>
                              <h3 className="text-sm font-bold text-slate-800">Feegow</h3>
                              <p className="text-xs text-slate-400">Consultas e prontuarios medicos</p>
                            </div>
                          </div>
                          <PresenceBadge present={adminConfig?.feegowApiKeyPresent && adminConfig?.feegowUnitIdPresent} />
                        </div>
                        <div className="space-y-2 mt-2">
                          <div className="flex items-center justify-between">
                            <span className="text-xs text-slate-500">API Key</span>
                            <PresenceBadge present={adminConfig?.feegowApiKeyPresent} />
                          </div>
                          <div className="flex items-center justify-between">
                            <span className="text-xs text-slate-500">ID da Unidade</span>
                            <PresenceBadge present={adminConfig?.feegowUnitIdPresent} />
                          </div>
                          <div className="flex items-center justify-between">
                            <span className="text-xs text-slate-500">Backup (ENV)</span>
                            <span className="text-xs text-slate-400">Gerenciado no servidor</span>
                          </div>
                        </div>
                        <p className="mt-3 text-[11px] text-slate-400">Somente leitura. Valores configurados via ENV.</p>
                      </div>
                    </div>

                    {/* Card: Blip */}
                    <div className="rounded-2xl border border-cyan-100 bg-cyan-50/10 p-5 flex flex-col justify-between transition-all hover:shadow-sm">
                      <div>
                        <div className="flex items-center justify-between mb-4">
                          <div className="flex items-center gap-3">
                            <div className="w-10 h-10 rounded-xl bg-cyan-100 flex items-center justify-center text-cyan-600 shrink-0 shadow-sm">
                              <MessageCircle size={20} />
                            </div>
                            <div>
                              <h3 className="text-sm font-bold text-slate-800">Blip</h3>
                              <p className="text-xs text-slate-400">WhatsApp e atendimento automatizado</p>
                            </div>
                          </div>
                          <PresenceBadge present={adminConfig?.blipApiKeyPresent && adminConfig?.blipBotIdPresent} />
                        </div>
                        <div className="space-y-2 mt-2">
                          <div className="flex items-center justify-between">
                            <span className="text-xs text-slate-500">API Key</span>
                            <PresenceBadge present={adminConfig?.blipApiKeyPresent} />
                          </div>
                          <div className="flex items-center justify-between">
                            <span className="text-xs text-slate-500">ID do Bot</span>
                            <PresenceBadge present={adminConfig?.blipBotIdPresent} />
                          </div>
                          <div className="flex items-center justify-between">
                            <span className="text-xs text-slate-500">Webhook Token</span>
                            <PresenceBadge present={adminConfig?.blipWebhookTokenPresent} />
                          </div>
                          <div className="flex items-center justify-between">
                            <span className="text-xs text-slate-500">Webhook Secret</span>
                            <PresenceBadge present={adminConfig?.blipWebhookSecretPresent} />
                          </div>
                          <div className="flex items-center justify-between">
                            <span className="text-xs text-slate-500">Backup (ENV)</span>
                            <span className="text-xs text-slate-400">Gerenciado no servidor</span>
                          </div>
                        </div>
                        <p className="mt-3 text-[11px] text-slate-400">Somente leitura. Valores configurados via ENV.</p>
                      </div>
                    </div>
                  </div>
                </motion.div>
              )}

              {activeSubSection === 'system-params' && (
                <motion.div
                  key="system-params"
                  initial={{ opacity: 0, scale: 0.98 }}
                  animate={{ opacity: 1, scale: 1 }}
                  exit={{ opacity: 0, scale: 0.98 }}
                  transition={{ duration: 0.2 }}
                >
                  {renderBackHeader()}
                  
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
                    <div className="divide-y divide-slate-100 bg-white">
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
                </motion.div>
              )}

              {activeSubSection === 'sla' && (
                <motion.div
                  key="sla"
                  initial={{ opacity: 0, scale: 0.98 }}
                  animate={{ opacity: 1, scale: 1 }}
                  exit={{ opacity: 0, scale: 0.98 }}
                  transition={{ duration: 0.2 }}
                >
                  {renderBackHeader()}
                  
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
                    <div className="divide-y divide-slate-100 bg-white">
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
                </motion.div>
              )}

              {activeSubSection === 'reports' && (
                <motion.div
                  key="reports"
                  initial={{ opacity: 0, scale: 0.98 }}
                  animate={{ opacity: 1, scale: 1 }}
                  exit={{ opacity: 0, scale: 0.98 }}
                  transition={{ duration: 0.2 }}
                >
                  {renderBackHeader()}
                  <ReportSchedulesSection />
                </motion.div>
              )}

              {activeSubSection === 'feegow' && (
                <motion.div
                  key="feegow"
                  initial={{ opacity: 0, scale: 0.98 }}
                  animate={{ opacity: 1, scale: 1 }}
                  exit={{ opacity: 0, scale: 0.98 }}
                  transition={{ duration: 0.2 }}
                  className="space-y-6"
                >
                  {renderBackHeader()}
                  <AppointmentControlPanel />
                  <ProfessionalMappingPanel />
                </motion.div>
              )}

              {activeSubSection === 'categories' && (
                <motion.div
                  key="categories"
                  initial={{ opacity: 0, scale: 0.98 }}
                  animate={{ opacity: 1, scale: 1 }}
                  exit={{ opacity: 0, scale: 0.98 }}
                  transition={{ duration: 0.2 }}
                >
                  {renderBackHeader()}
                  <CategoriesSection />
                </motion.div>
              )}

              {activeSubSection === 'backups' && (
                <motion.div
                  key="backups"
                  initial={{ opacity: 0, scale: 0.98 }}
                  animate={{ opacity: 1, scale: 1 }}
                  exit={{ opacity: 0, scale: 0.98 }}
                  transition={{ duration: 0.2 }}
                >
                  {renderBackHeader()}
                  <BackupsSection />
                </motion.div>
              )}
            </AnimatePresence>
          )}
        </div>
      )}

      {/* ── TAB: PERFIL ── */}
      {activeTab === 'profile' && (
        <div className="space-y-3 max-w-2xl">
          <div className="bg-white rounded-2xl border border-slate-200 px-6 py-6 shadow-sm">
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
    </main>
  );
}

