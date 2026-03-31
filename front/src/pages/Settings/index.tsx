import { useEffect, useMemo, useState } from 'react';
import { Save, Globe, User as UserIcon, Settings as SettingsIcon, Clock3, Pencil, Trash2, Plus, Bell, Moon, Languages, Check, X, Calendar } from 'lucide-react';
import { toast } from 'react-toastify';

import { useAuth } from '../../contexts/AuthContext';
import {
  createAssetCategory,
  createItemCategory,
  getAssetCategories,
  getItemCategories,
  getSystemSettings,
  updateSystemSettings,
  getReportSchedules,
  createReportSchedule,
  deleteReportSchedule,
  updateReportSchedule,
  deleteItemCategory,
  deleteAssetCategory,
  type AssetCategory,
  type ItemCategory,
  type ReportSchedule,
  type SystemSetting,
  type UpdateSystemSettingsPayload,
  getUsers,
  type User,
  getAdminConfig,
  type AdminConfig,
} from '../../services/api';
import PageHero from '../../components/PageHero';

type TabType = 'system' | 'profile' | 'integration';

const inputClassName =
  'w-full rounded-lg border border-slate-200 bg-slate-50 px-3.5 py-2.5 text-sm text-slate-800 shadow-none placeholder-slate-400 focus:outline-none focus:ring-2 focus:ring-brand-primary/30 focus:border-brand-primary focus:bg-white transition-all';

const friendlyLabels: Record<string, string> = {
  SLA_URGENT_HOURS: 'SLA — Chamados Urgentes (Horas)',
  ATTACHMENT_SIZE_LIMIT_MB: 'Limite de Tamanho de Anexo (MB)',
};

function getFriendlyLabel(settingKey: string): string {
  return friendlyLabels[settingKey] ?? settingKey.replaceAll('_', ' ');
}

export default function Settings() {
  const { user } = useAuth();
  const [activeTab, setActiveTab] = useState<TabType>(user?.role === 'ADMIN' ? 'system' : 'profile');
  const [settings, setSettings] = useState<SystemSetting[]>([]);
  const [values, setValues] = useState<Record<string, string>>({});
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [assetCategories, setAssetCategories] = useState<AssetCategory[]>([]);
  const [itemCategories, setItemCategories] = useState<ItemCategory[]>([]);
  const [assetCategoryName, setAssetCategoryName] = useState('');
  const [itemCategoryName, setItemCategoryName] = useState('');
  const [savingAssetCategory, setSavingAssetCategory] = useState(false);
  const [savingItemCategory, setSavingItemCategory] = useState(false);
  const [deletingItemCategoryId, setDeletingItemCategoryId] = useState<string | null>(null);
  const [deletingAssetCategoryId, setDeletingAssetCategoryId] = useState<string | null>(null);

  const [adminConfig, setAdminConfig] = useState<AdminConfig | null>(null);

  const [newSettingKey, setNewSettingKey] = useState('');
  const [newSettingValue, setNewSettingValue] = useState('');

  const [reportSchedules, setReportSchedules] = useState<ReportSchedule[]>([]);
  const [schedulesLoading, setSchedulesLoading] = useState(false);
  const [usersList, setUsersList] = useState<User[]>([]);
  const [newSchedulePayload, setNewSchedulePayload] = useState<Partial<ReportSchedule>>({
    reportType: 'exits',
    targetUserId: null,
    sendEmail: true,
    sendDiscord: false,
    scheduleDay: 12,
    isActive: true,
  });

  const [editingScheduleId, setEditingScheduleId] = useState<string | null>(null);
  const [editingPayload, setEditingPayload] = useState<Partial<ReportSchedule> | null>(null);

  const [emailNotifications, setEmailNotifications] = useState(true);
  const [darkTheme, setDarkTheme] = useState(false);
  const [language, setLanguage] = useState<'pt-BR' | 'en'>('pt-BR');

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
          setAdminConfig(cfg ?? null);
        } catch (e) {
          console.warn('Failed to load admin config', e);
          setAdminConfig(null);
        }
        try {
          const [schedules, users] = await Promise.all([getReportSchedules(), getUsers()]);
          setReportSchedules(Array.isArray(schedules) ? schedules : []);
          setUsersList(Array.isArray(users) ? users : []);
        } catch (e) {
          console.warn('Failed to load report schedules or users', e);
          setReportSchedules([]);
          setUsersList([]);
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

  useEffect(() => {
    async function loadCategories() {
      if (!isAdmin) return;
      try {
        const [assetData, itemData] = await Promise.all([getAssetCategories(), getItemCategories()]);
        setAssetCategories(Array.isArray(assetData) ? assetData : []);
        setItemCategories(Array.isArray(itemData) ? itemData : []);
      } catch {
        toast.error('Erro ao carregar categorias de configuração.');
        setAssetCategories([]);
        setItemCategories([]);
      }
    }
    loadCategories();
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

  async function refreshCategories() {
    const [assetData, itemData] = await Promise.all([getAssetCategories(), getItemCategories()]);
    setAssetCategories(assetData);
    setItemCategories(itemData);
  }

  async function handleCreateAssetCategory() {
    const normalizedName = assetCategoryName.trim();
    if (!normalizedName) { toast.error('Digite um nome para a categoria de equipamentos.'); return; }
    setSavingAssetCategory(true);
    try {
      await createAssetCategory({ name: normalizedName });
      setAssetCategoryName('');
      await refreshCategories();
      toast.success('Categoria de equipamentos adicionada com sucesso.');
    } catch (error) {
      toast.error(getApiErrorMessage(error, 'Erro ao adicionar categoria de equipamentos.'));
    } finally {
      setSavingAssetCategory(false);
    }
  }

  async function handleCreateItemCategory() {
    const normalizedName = itemCategoryName.trim();
    if (!normalizedName) { toast.error('Digite um nome para a categoria de inventário.'); return; }
    setSavingItemCategory(true);
    try {
      await createItemCategory({ name: normalizedName, isConsumable: true });
      setItemCategoryName('');
      await refreshCategories();
      toast.success('Categoria de inventário adicionada com sucesso.');
    } catch (error) {
      toast.error(getApiErrorMessage(error, 'Erro ao adicionar categoria de inventário.'));
    } finally {
      setSavingItemCategory(false);
    }
  }

  async function handleDeleteItemCategory(id: string) {
    if (!confirm('Tem certeza que deseja excluir esta categoria de inventário? Esta ação não pode ser desfeita.')) return;
    setDeletingItemCategoryId(id);
    try {
      await deleteItemCategory(id);
      await refreshCategories();
      toast.success('Categoria de inventário excluída com sucesso.');
    } catch (error) {
      toast.error(getApiErrorMessage(error, 'Erro ao excluir categoria de inventário.'));
    } finally {
      setDeletingItemCategoryId(null);
    }
  }

  async function handleCreateSchedule() {
    if (!newSchedulePayload.reportType) { toast.error('Selecione o tipo de relatório.'); return; }
    setSchedulesLoading(true);
    try {
      const created = await createReportSchedule(newSchedulePayload as Partial<ReportSchedule>);
      setReportSchedules((prev) => (prev ?? []).concat(created));
      toast.success('Agendamento criado com sucesso.');
      setNewSchedulePayload({ reportType: 'exits', targetUserId: null, sendEmail: true, sendDiscord: false, scheduleDay: 12, isActive: true });
    } catch (error) {
      toast.error(getApiErrorMessage(error, 'Erro ao criar agendamento.'));
    } finally {
      setSchedulesLoading(false);
    }
  }

  function startEditSchedule(s: ReportSchedule) {
    setEditingScheduleId(s.id);
    setEditingPayload({ reportType: s.reportType, targetUserId: s.targetUserId, sendEmail: s.sendEmail, sendDiscord: s.sendDiscord, scheduleDay: s.scheduleDay, isActive: s.isActive });
  }

  async function handleSaveEditedSchedule() {
    if (!editingScheduleId || !editingPayload) return;
    setSchedulesLoading(true);
    try {
      const updated = await updateReportSchedule(editingScheduleId, editingPayload as Partial<ReportSchedule>);
      setReportSchedules((prev) => (prev ?? []).map((s) => (s.id === updated.id ? updated : s)));
      toast.success('Agendamento atualizado com sucesso.');
      setEditingScheduleId(null);
      setEditingPayload(null);
    } catch (error) {
      toast.error(getApiErrorMessage(error, 'Erro ao atualizar agendamento.'));
    } finally {
      setSchedulesLoading(false);
    }
  }

  function cancelEdit() {
    setEditingScheduleId(null);
    setEditingPayload(null);
  }

  async function handleAddSetting() {
    const key = newSettingKey.trim();
    if (!key) { toast.error('Digite a chave da configuração (ex: SLA_URGENT_HOURS).'); return; }
    setSaving(true);
    try {
      const payload: UpdateSystemSettingsPayload = { [key]: newSettingValue ?? '' };
      const updated = await updateSystemSettings(payload);
      setSettings(updated);
      setValues(updated.reduce<Record<string, string>>((acc, setting) => { acc[setting.id] = setting.value; return acc; }, {}));
      setNewSettingKey('');
      setNewSettingValue('');
      toast.success('Configuração adicionada com sucesso.');
    } catch (error) {
      toast.error(getApiErrorMessage(error, 'Erro ao adicionar configuração.'));
    } finally {
      setSaving(false);
    }
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

  async function handleDeleteSchedule(id: string) {
    setSchedulesLoading(true);
    try {
      await deleteReportSchedule(id);
      setReportSchedules((prev) => (prev ?? []).filter((s) => s.id !== id));
      toast.success('Agendamento removido com sucesso.');
    } catch (error) {
      toast.error(getApiErrorMessage(error, 'Erro ao remover agendamento.'));
    } finally {
      setSchedulesLoading(false);
    }
  }

  async function handleToggleScheduleActive(schedule: ReportSchedule) {
    setSchedulesLoading(true);
    try {
      const payload = { active: !schedule.isActive } as unknown as Partial<ReportSchedule>;
      const updated = await updateReportSchedule(schedule.id, payload);
      setReportSchedules((prev) => (prev ?? []).map((s) => (s.id === updated.id ? updated : s)));
      toast.success(updated.isActive ? 'Agendamento ativado.' : 'Agendamento desativado.');
    } catch (error) {
      toast.error(getApiErrorMessage(error, 'Erro ao atualizar agendamento.'));
    } finally {
      setSchedulesLoading(false);
    }
  }

  if (!isAdmin && (activeTab === 'system' || activeTab === 'integration')) {
    return (
      <main className="w-full max-w-full px-4 sm:px-6 lg:px-8 py-8">
        <div className="bg-white rounded-2xl border border-slate-200 shadow-sm p-12 text-center">
          <div className="w-12 h-12 bg-slate-100 rounded-full flex items-center justify-center mx-auto mb-4">
            <SettingsIcon size={22} className="text-slate-400" />
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
    ...(isAdmin ? [{ id: 'integration' as TabType, label: 'Integração', icon: SettingsIcon }] : []),
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
                  <h2 className="text-sm font-semibold text-slate-900">Parâmetros Globais</h2>
                  <p className="text-xs text-slate-500 mt-0.5">Configurações gerais do sistema.</p>
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
                    className="inline-flex items-center gap-2 bg-brand-primary hover:bg-primary-hover disabled:opacity-40 disabled:cursor-not-allowed text-white text-sm font-semibold px-4 py-2 rounded-lg transition-colors"
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
                    <Clock3 size={16} className="text-amber-500" />
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
                    className="inline-flex items-center gap-2 bg-brand-primary hover:bg-primary-hover disabled:opacity-40 text-white text-sm font-semibold px-4 py-2 rounded-lg transition-colors"
                  >
                    <Save size={14} />
                    Salvar SLAs
                  </button>
                </div>
              </div>

              {/* Adicionar configuração */}
              <div className="bg-white rounded-2xl border border-slate-200 overflow-hidden">
                <div className="px-6 py-4 border-b border-slate-100">
                  <h2 className="text-sm font-semibold text-slate-900">Adicionar Configuração</h2>
                  <p className="text-xs text-slate-500 mt-0.5">Crie uma nova chave de configuração manualmente.</p>
                </div>
                <div className="px-6 py-4">
                  <div className="grid grid-cols-1 md:grid-cols-3 gap-3 items-end">
                    <div>
                      <label className="block text-xs font-medium text-slate-600 mb-1.5">Chave</label>
                      <input type="text" placeholder="ex: SLA_URGENT_HOURS" value={newSettingKey} onChange={(e) => setNewSettingKey(e.target.value)} className={inputClassName} />
                    </div>
                    <div>
                      <label className="block text-xs font-medium text-slate-600 mb-1.5">Valor</label>
                      <input type="text" placeholder="Valor" value={newSettingValue} onChange={(e) => setNewSettingValue(e.target.value)} className={inputClassName} />
                    </div>
                    <button
                      onClick={handleAddSetting}
                      disabled={saving}
                      className="inline-flex items-center justify-center gap-2 bg-brand-primary hover:bg-primary-hover disabled:opacity-40 text-white text-sm font-semibold px-4 py-2.5 rounded-lg transition-colors"
                    >
                      <Plus size={14} />
                      Adicionar
                    </button>
                  </div>
                </div>
              </div>

              {/* Agendamentos */}
              <div className="bg-white rounded-2xl border border-slate-200 overflow-hidden">
                <div className="px-6 py-4 border-b border-slate-100 flex items-center gap-3">
                  <div className="w-8 h-8 rounded-lg bg-blue-50 flex items-center justify-center shrink-0">
                    <Calendar size={16} className="text-blue-500" />
                  </div>
                  <div>
                    <h2 className="text-sm font-semibold text-slate-900">Agendamentos de Relatórios</h2>
                    <p className="text-xs text-slate-500 mt-0.5">Configure envios automáticos de relatórios mensais.</p>
                  </div>
                </div>

                {/* Lista de agendamentos */}
                <div className="divide-y divide-slate-100">
                  {Array.isArray(reportSchedules) && reportSchedules.length === 0 ? (
                    <div className="px-6 py-8 text-center">
                      <p className="text-sm text-slate-400">Nenhum agendamento encontrado.</p>
                    </div>
                  ) : (
                    Array.isArray(reportSchedules) && reportSchedules.map((s) => {
                      const usr = Array.isArray(usersList) ? usersList.find((u) => u.id === s.targetUserId) : undefined;

                      if (editingScheduleId === s.id) {
                        return (
                          <div key={s.id} className="px-6 py-4 bg-slate-50">
                            <div className="grid grid-cols-1 md:grid-cols-3 gap-3 items-end">
                              <div>
                                <label className="block text-xs font-medium text-slate-600 mb-1.5">Tipo de Relatório</label>
                                <select value={editingPayload?.reportType ?? 'exits'} onChange={(e) => setEditingPayload((prev) => ({ ...(prev ?? {}), reportType: e.target.value }))} className={inputClassName}>
                                  <option value="exits">Saídas de Estoque</option>
                                  <option value="entries">Entradas de Estoque</option>
                                  <option value="tickets">Histórico de Chamados</option>
                                </select>
                              </div>
                              <div>
                                <label className="block text-xs font-medium text-slate-600 mb-1.5">Destinatário</label>
                                <select value={editingPayload?.targetUserId ?? ''} onChange={(e) => setEditingPayload((prev) => ({ ...(prev ?? {}), targetUserId: e.target.value || null }))} className={inputClassName}>
                                  <option value="">Selecione usuário (opcional)</option>
                                  {Array.isArray(usersList) && usersList.map((u) => (
                                    <option key={u.id} value={u.id}>{u.name} ({u.email})</option>
                                  ))}
                                </select>
                              </div>
                              <div>
                                <label className="block text-xs font-medium text-slate-600 mb-1.5">Dia do Mês</label>
                                <input type="number" min={1} max={31} value={editingPayload?.scheduleDay ?? s.scheduleDay} onChange={(e) => setEditingPayload((prev) => ({ ...(prev ?? {}), scheduleDay: Number(e.target.value) }))} className={inputClassName} />
                              </div>
                              <div className="md:col-span-3">
                                <p className="text-xs text-slate-400">Dias 29, 30 e 31 serão processados no último dia útil de meses mais curtos.</p>
                              </div>
                              <div className="flex items-center gap-4">
                                <label className="flex items-center gap-2 text-sm text-slate-700 cursor-pointer">
                                  <input type="checkbox" checked={!!editingPayload?.sendEmail} onChange={(e) => setEditingPayload((prev) => ({ ...(prev ?? {}), sendEmail: e.target.checked }))} className="rounded" />
                                  E-mail
                                </label>
                                <label className="flex items-center gap-2 text-sm text-slate-700 cursor-pointer">
                                  <input type="checkbox" checked={!!editingPayload?.sendDiscord} onChange={(e) => setEditingPayload((prev) => ({ ...(prev ?? {}), sendDiscord: e.target.checked }))} className="rounded" />
                                  Discord
                                </label>
                              </div>
                              <div className="md:col-span-2 flex gap-2 justify-end">
                                <button onClick={handleSaveEditedSchedule} disabled={schedulesLoading} className="inline-flex items-center gap-1.5 bg-brand-primary hover:bg-primary-hover text-white text-sm font-medium px-4 py-2 rounded-lg">
                                  <Check size={13} /> Salvar
                                </button>
                                <button onClick={cancelEdit} className="inline-flex items-center gap-1.5 border border-slate-200 text-slate-600 text-sm font-medium px-4 py-2 rounded-lg hover:bg-slate-100 transition-colors">
                                  <X size={13} /> Cancelar
                                </button>
                              </div>
                            </div>
                          </div>
                        );
                      }

                      return (
                        <div key={s.id} className="px-6 py-4 flex flex-col sm:flex-row sm:items-center gap-3">
                          <div className="flex-1 min-w-0">
                            <div className="flex items-center gap-2">
                              <span className="text-sm font-medium text-slate-800 capitalize">{s.reportType}</span>
                              <span className="text-slate-300">·</span>
                              <span className="text-sm text-slate-500">Dia {s.scheduleDay}</span>
                              <span className={`inline-flex items-center gap-1 text-xs font-medium px-2 py-0.5 rounded-full ${s.isActive ? 'bg-emerald-50 text-emerald-700' : 'bg-slate-100 text-slate-500'}`}>
                                <span className={`w-1.5 h-1.5 rounded-full ${s.isActive ? 'bg-emerald-500' : 'bg-slate-400'}`} />
                                {s.isActive ? 'Ativo' : 'Inativo'}
                              </span>
                            </div>
                            <p className="text-xs text-slate-400 mt-0.5">
                              {[s.sendEmail && 'E-mail', s.sendDiscord && 'Discord'].filter(Boolean).join(' + ')}
                              {usr ? ` — ${usr.name}` : s.targetUserId ? ` — ${s.targetUserId}` : ''}
                            </p>
                          </div>
                          <div className="flex items-center gap-1 shrink-0">
                            <button
                              onClick={() => handleToggleScheduleActive(s)}
                              title={s.isActive ? 'Desativar' : 'Ativar'}
                              className={`px-3 py-1.5 text-xs font-medium rounded-lg border transition-colors ${s.isActive ? 'border-slate-200 text-slate-600 hover:bg-slate-50' : 'border-emerald-200 text-emerald-700 hover:bg-emerald-50'}`}
                            >
                              {s.isActive ? 'Desativar' : 'Ativar'}
                            </button>
                            <button onClick={() => startEditSchedule(s)} className="p-2 rounded-lg hover:bg-slate-100 transition-colors">
                              <Pencil size={14} className="text-slate-500" />
                            </button>
                            <button onClick={() => handleDeleteSchedule(s.id)} disabled={schedulesLoading} className="p-2 rounded-lg hover:bg-red-50 transition-colors">
                              <Trash2 size={14} className="text-red-500" />
                            </button>
                          </div>
                        </div>
                      );
                    })
                  )}
                </div>

                {/* Novo agendamento */}
                <div className="px-6 py-5 bg-slate-50 border-t border-slate-100">
                  <p className="text-xs font-semibold text-slate-600 uppercase tracking-wide mb-3">Novo Agendamento</p>
                  <div className="grid grid-cols-1 md:grid-cols-3 gap-3 items-end">
                    <div>
                      <label className="block text-xs font-medium text-slate-600 mb-1.5">Tipo de Relatório</label>
                      <select value={newSchedulePayload.reportType} onChange={(e) => setNewSchedulePayload((prev) => ({ ...prev, reportType: e.target.value }))} className={inputClassName}>
                        <option value="exits">Saídas de Estoque</option>
                        <option value="entries">Entradas de Estoque</option>
                        <option value="tickets">Histórico de Chamados</option>
                      </select>
                    </div>
                    <div>
                      <label className="block text-xs font-medium text-slate-600 mb-1.5">Destinatário</label>
                      <select value={newSchedulePayload.targetUserId ?? ''} onChange={(e) => setNewSchedulePayload((prev) => ({ ...prev, targetUserId: e.target.value || null }))} className={inputClassName}>
                        <option value="">Selecione usuário (opcional)</option>
                        {Array.isArray(usersList) && usersList.map((u) => (
                          <option key={u.id} value={u.id}>{u.name} ({u.email})</option>
                        ))}
                      </select>
                    </div>
                    <div>
                      <label className="block text-xs font-medium text-slate-600 mb-1.5">Dia do Mês</label>
                      <input type="number" min={1} max={31} value={newSchedulePayload.scheduleDay ?? 12} onChange={(e) => setNewSchedulePayload((prev) => ({ ...prev, scheduleDay: Number(e.target.value) }))} className={inputClassName} />
                    </div>
                    <div className="md:col-span-3">
                      <p className="text-xs text-slate-400">Dias 29, 30 e 31 serão processados no último dia útil de meses mais curtos.</p>
                    </div>
                    <div className="flex items-center gap-4">
                      <label className="flex items-center gap-2 text-sm text-slate-700 cursor-pointer">
                        <input type="checkbox" checked={newSchedulePayload.sendEmail ?? true} onChange={(e) => setNewSchedulePayload((prev) => ({ ...prev, sendEmail: e.target.checked }))} className="rounded" />
                        E-mail
                      </label>
                      <label className="flex items-center gap-2 text-sm text-slate-700 cursor-pointer">
                        <input type="checkbox" checked={newSchedulePayload.sendDiscord ?? false} onChange={(e) => setNewSchedulePayload((prev) => ({ ...prev, sendDiscord: e.target.checked }))} className="rounded" />
                        Discord
                      </label>
                    </div>
                    <div className="md:col-span-2 flex justify-end">
                      <button
                        onClick={handleCreateSchedule}
                        disabled={schedulesLoading}
                        className="inline-flex items-center gap-2 bg-brand-primary hover:bg-primary-hover disabled:opacity-40 text-white text-sm font-semibold px-4 py-2.5 rounded-lg transition-colors"
                      >
                        <Plus size={14} />
                        Criar Agendamento
                      </button>
                    </div>
                  </div>
                </div>
              </div>

              {/* Categorias */}
              <div className="grid grid-cols-1 lg:grid-cols-2 gap-5">
                {/* Categorias de Equipamentos */}
                <div className="bg-white rounded-2xl border border-slate-200 overflow-hidden">
                  <div className="px-5 py-4 border-b border-slate-100">
                    <h3 className="text-sm font-semibold text-slate-900">Categorias de Equipamentos</h3>
                    <p className="text-xs text-slate-400 mt-0.5">Ativos físicos do CMDB.</p>
                  </div>
                  <div className="px-5 py-4">
                    <div className="flex flex-wrap gap-2 min-h-[40px]">
                      {!Array.isArray(assetCategories) || assetCategories.length === 0 ? (
                        <span className="text-xs text-slate-400">Nenhuma categoria cadastrada.</span>
                      ) : (
                        assetCategories.map((category) => (
                          <span key={category.id} className="inline-flex items-center gap-1.5 bg-slate-100 text-slate-700 text-xs font-medium px-3 py-1.5 rounded-full">
                            {category.name}
                            <button
                              onClick={async () => {
                                if (!confirm('Tem certeza que deseja excluir esta categoria de equipamentos?')) return;
                                setDeletingAssetCategoryId(category.id);
                                try {
                                  await deleteAssetCategory(category.id);
                                  await refreshCategories();
                                  toast.success('Categoria de equipamentos excluída com sucesso.');
                                } catch (error) {
                                  toast.error(getApiErrorMessage(error, 'Erro ao excluir categoria de equipamentos.'));
                                } finally {
                                  setDeletingAssetCategoryId(null);
                                }
                              }}
                              disabled={deletingAssetCategoryId === category.id}
                              className="hover:text-red-500 transition-colors disabled:opacity-50"
                            >
                              <X size={12} />
                            </button>
                          </span>
                        ))
                      )}
                    </div>
                    <div className="mt-4 flex gap-2">
                      <input
                        type="text"
                        value={assetCategoryName}
                        onChange={(event) => setAssetCategoryName(event.target.value)}
                        className={inputClassName}
                        placeholder="Nova categoria..."
                        maxLength={100}
                      />
                      <button
                        type="button"
                        onClick={handleCreateAssetCategory}
                        disabled={savingAssetCategory}
                        className="shrink-0 inline-flex items-center gap-1.5 bg-brand-primary hover:bg-primary-hover disabled:opacity-40 text-white text-sm font-semibold px-3.5 py-2.5 rounded-lg transition-colors"
                      >
                        <Plus size={14} />
                        {savingAssetCategory ? 'Adicionando...' : 'Adicionar'}
                      </button>
                    </div>
                  </div>
                </div>

                {/* Categorias de Inventário */}
                <div className="bg-white rounded-2xl border border-slate-200 overflow-hidden">
                  <div className="px-5 py-4 border-b border-slate-100">
                    <h3 className="text-sm font-semibold text-slate-900">Categorias de Inventário</h3>
                    <p className="text-xs text-slate-400 mt-0.5">Materiais e itens de estoque.</p>
                  </div>
                  <div className="px-5 py-4">
                    <div className="flex flex-wrap gap-2 min-h-[40px]">
                      {!Array.isArray(itemCategories) || itemCategories.length === 0 ? (
                        <span className="text-xs text-slate-400">Nenhuma categoria cadastrada.</span>
                      ) : (
                        itemCategories.map((category) => (
                          <span key={category.id} className="inline-flex items-center gap-1.5 bg-slate-100 text-slate-700 text-xs font-medium px-3 py-1.5 rounded-full">
                            {category.name}
                            <button
                              onClick={() => handleDeleteItemCategory(category.id)}
                              disabled={deletingItemCategoryId === category.id}
                              className="hover:text-red-500 transition-colors disabled:opacity-50"
                            >
                              <X size={12} />
                            </button>
                          </span>
                        ))
                      )}
                    </div>
                    <div className="mt-4 flex gap-2">
                      <input
                        type="text"
                        value={itemCategoryName}
                        onChange={(event) => setItemCategoryName(event.target.value)}
                        className={inputClassName}
                        placeholder="Nova categoria..."
                        maxLength={100}
                      />
                      <button
                        type="button"
                        onClick={handleCreateItemCategory}
                        disabled={savingItemCategory}
                        className="shrink-0 inline-flex items-center gap-1.5 bg-brand-primary hover:bg-primary-hover disabled:opacity-40 text-white text-sm font-semibold px-3.5 py-2.5 rounded-lg transition-colors"
                      >
                        <Plus size={14} />
                        {savingItemCategory ? 'Adicionando...' : 'Adicionar'}
                      </button>
                    </div>
                  </div>
                </div>
              </div>
            </>
          )}
        </div>
      )}

      {/* ── TAB: PERFIL ── */}
      {activeTab === 'profile' && (
        <div className="space-y-3 max-w-2xl">
          {/* Notificações */}
          <div className="bg-white rounded-2xl border border-slate-200 px-5 py-4 flex items-center justify-between">
            <div className="flex items-center gap-3">
              <div className="w-8 h-8 rounded-lg bg-blue-50 flex items-center justify-center shrink-0">
                <Bell size={15} className="text-blue-500" />
              </div>
              <div>
                <p className="text-sm font-medium text-slate-800">Notificações por E-mail</p>
                <p className="text-xs text-slate-400 mt-0.5">Receba atualizações de chamados e tarefas por e-mail</p>
              </div>
            </div>
            <label className="relative inline-flex items-center cursor-pointer">
              <input type="checkbox" checked={emailNotifications} onChange={(e) => setEmailNotifications(e.target.checked)} className="sr-only peer" />
              <div className="w-10 h-5 bg-slate-200 peer-focus:outline-none peer-focus:ring-2 peer-focus:ring-brand-primary/30 rounded-full peer peer-checked:after:translate-x-full after:content-[''] after:absolute after:top-[2px] after:left-[2px] after:bg-white after:rounded-full after:h-4 after:w-4 after:transition-all peer-checked:bg-brand-primary shadow-inner" />
            </label>
          </div>

          {/* Tema Escuro */}
          <div className="bg-white rounded-2xl border border-slate-200 px-5 py-4 flex items-center justify-between">
            <div className="flex items-center gap-3">
              <div className="w-8 h-8 rounded-lg bg-slate-100 flex items-center justify-center shrink-0">
                <Moon size={15} className="text-slate-600" />
              </div>
              <div>
                <p className="text-sm font-medium text-slate-800">Tema Escuro</p>
                <p className="text-xs text-slate-400 mt-0.5">Ative o modo escuro para melhor visualização noturna</p>
              </div>
            </div>
            <label className="relative inline-flex items-center cursor-pointer">
              <input type="checkbox" checked={darkTheme} onChange={(e) => setDarkTheme(e.target.checked)} className="sr-only peer" />
              <div className="w-10 h-5 bg-slate-200 peer-focus:outline-none peer-focus:ring-2 peer-focus:ring-brand-primary/30 rounded-full peer peer-checked:after:translate-x-full after:content-[''] after:absolute after:top-[2px] after:left-[2px] after:bg-white after:rounded-full after:h-4 after:w-4 after:transition-all peer-checked:bg-brand-primary shadow-inner" />
            </label>
          </div>

          {/* Idioma */}
          <div className="bg-white rounded-2xl border border-slate-200 px-5 py-4 flex flex-col sm:flex-row sm:items-center gap-4">
            <div className="flex items-center gap-3 flex-1">
              <div className="w-8 h-8 rounded-lg bg-violet-50 flex items-center justify-center shrink-0">
                <Languages size={15} className="text-violet-500" />
              </div>
              <div>
                <p className="text-sm font-medium text-slate-800">Idioma</p>
                <p className="text-xs text-slate-400 mt-0.5">Escolha seu idioma preferido para a interface</p>
              </div>
            </div>
            <select value={language} onChange={(e) => setLanguage(e.target.value as 'pt-BR' | 'en')} className="w-full sm:w-44 rounded-lg border border-slate-200 bg-slate-50 px-3.5 py-2.5 text-sm text-slate-800 focus:outline-none focus:ring-2 focus:ring-brand-primary/30 focus:border-brand-primary focus:bg-white transition-all">
              <option value="pt-BR">Português (BR)</option>
              <option value="en">English</option>
            </select>
          </div>

          <div className="flex justify-end pt-2">
            <button
              type="button"
              onClick={handleSavePreferences}
              className="inline-flex items-center gap-2 bg-brand-primary hover:bg-primary-hover text-white text-sm font-semibold px-4 py-2.5 rounded-lg transition-colors"
            >
              <Save size={14} />
              Salvar Preferências
            </button>
          </div>
        </div>
      )}

      {/* ── TAB: INTEGRAÇÃO ── */}
      {activeTab === 'integration' && isAdmin && (
        <div className="space-y-5 max-w-3xl">
          <div className="bg-white rounded-2xl border border-slate-200 overflow-hidden">
            <div className="px-6 py-4 border-b border-slate-100">
              <h3 className="text-sm font-semibold text-slate-900">Ambiente e Integrações</h3>
              <p className="text-xs text-slate-500 mt-0.5">Valores e indicadores das integrações do sistema (somente leitura).</p>
            </div>

            {adminConfig ? (
              <div className="divide-y divide-slate-100">
                {/* SMTP From Email */}
                <div className="px-6 py-4 flex flex-col sm:flex-row sm:items-center gap-3">
                  <div className="flex-1">
                    <p className="text-xs font-medium text-slate-500 uppercase tracking-wide">SMTP From Email</p>
                  </div>
                  <div className="w-full sm:w-64">
                    <input type="text" value={adminConfig.smtpFromEmail ?? ''} readOnly className={`${inputClassName} cursor-default`} />
                  </div>
                </div>

                {/* SMTP From Name */}
                <div className="px-6 py-4 flex flex-col sm:flex-row sm:items-center gap-3">
                  <div className="flex-1">
                    <p className="text-xs font-medium text-slate-500 uppercase tracking-wide">SMTP From Name</p>
                  </div>
                  <div className="w-full sm:w-64">
                    <input type="text" value={adminConfig.smtpFromName ?? ''} readOnly className={`${inputClassName} cursor-default`} />
                  </div>
                </div>

                {/* Discord */}
                <div className="px-6 py-4 flex flex-col sm:flex-row sm:items-center gap-4">
                  <div className="flex-1">
                    <p className="text-xs font-medium text-slate-500 uppercase tracking-wide">Discord</p>
                  </div>
                  <div className="flex items-center gap-3">
                    <span className={`inline-flex items-center gap-1.5 text-xs font-medium px-2.5 py-1 rounded-full ${adminConfig.discordBotEnabled ? 'bg-emerald-50 text-emerald-700' : 'bg-slate-100 text-slate-500'}`}>
                      <span className={`w-1.5 h-1.5 rounded-full ${adminConfig.discordBotEnabled ? 'bg-emerald-500' : 'bg-slate-400'}`} />
                      Bot {adminConfig.discordBotEnabled ? 'ativado' : 'desativado'}
                    </span>
                    <span className={`inline-flex items-center gap-1.5 text-xs font-medium px-2.5 py-1 rounded-full ${adminConfig.discordWebhookPresent ? 'bg-emerald-50 text-emerald-700' : 'bg-amber-50 text-amber-700'}`}>
                      <span className={`w-1.5 h-1.5 rounded-full ${adminConfig.discordWebhookPresent ? 'bg-emerald-500' : 'bg-amber-500'}`} />
                      Webhook {adminConfig.discordWebhookPresent ? 'presente' : 'não configurado'}
                    </span>
                  </div>
                </div>
              </div>
            ) : (
              <div className="px-6 py-8 text-center">
                <p className="text-sm text-slate-400">Nenhuma configuração de integração disponível.</p>
              </div>
            )}
          </div>
        </div>
      )}
    </main>
  );
}
