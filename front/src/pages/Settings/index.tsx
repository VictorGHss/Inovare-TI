import { useEffect, useMemo, useState } from 'react';
import { Save, Globe, User as UserIcon, Settings as SettingsIcon, Clock3, Pencil, Trash2 } from 'lucide-react';
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
  'w-full rounded-lg border border-slate-100 bg-white px-4 py-3 text-sm text-slate-800 shadow-sm placeholder-slate-400 focus:outline-none focus:ring-2 focus:ring-primary focus:border-primary transition-colors transition-shadow';

const friendlyLabels: Record<string, string> = {
  SLA_URGENT_HOURS: 'SLA - Chamados Urgentes (Horas)',
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

  // Config admin (.env) — valores somente leitura vindos do backend
  const [adminConfig, setAdminConfig] = useState<AdminConfig | null>(null);

  // Adicionar nova configuração manualmente
  const [newSettingKey, setNewSettingKey] = useState('');
  const [newSettingValue, setNewSettingValue] = useState('');

  // Report schedules (admin)
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

  // Edição inline de agendamento
  const [editingScheduleId, setEditingScheduleId] = useState<string | null>(null);
  const [editingPayload, setEditingPayload] = useState<Partial<ReportSchedule> | null>(null);

  // Estado de preferências do usuário
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
          // não-fatal
          console.warn('Failed to load admin config', e);
          setAdminConfig(null);
        }
        try {
          const [schedules, users] = await Promise.all([getReportSchedules(), getUsers()]);
          setReportSchedules(Array.isArray(schedules) ? schedules : []);
          setUsersList(Array.isArray(users) ? users : []);
        } catch (e) {
          // não-fatal
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
      if (!isAdmin) {
        return;
      }

      try {
        const [assetData, itemData] = await Promise.all([
          getAssetCategories(),
          getItemCategories(),
        ]);

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
    if (!isAdmin) {
      return;
    }

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
    // Salva local (mock) - preferências ainda não são persistidas no backend
    toast.success('Preferências salvas com sucesso.');
  }

  function getApiErrorMessage(error: unknown, fallbackMessage: string): string {
    if (typeof error === 'object' && error !== null) {
      const maybeResponse = error as { response?: { data?: { detail?: string } } };
      if (maybeResponse.response?.data?.detail) {
        return maybeResponse.response.data.detail;
      }
    }

    return fallbackMessage;
  }

  async function refreshCategories() {
    const [assetData, itemData] = await Promise.all([
      getAssetCategories(),
      getItemCategories(),
    ]);

    setAssetCategories(assetData);
    setItemCategories(itemData);
  }

  async function handleCreateAssetCategory() {
    const normalizedName = assetCategoryName.trim();
    if (!normalizedName) {
      toast.error('Digite um nome para a categoria de equipamentos.');
      return;
    }

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
    if (!normalizedName) {
      toast.error('Digite um nome para a categoria de inventário.');
      return;
    }

    setSavingItemCategory(true);
    try {
      await createItemCategory({
        name: normalizedName,
        isConsumable: true,
      });
      setItemCategoryName('');
      await refreshCategories();
      toast.success('Categoria de inventário adicionada com sucesso.');
    } catch (error) {
      toast.error(getApiErrorMessage(error, 'Erro ao adicionar categoria de inventário.'));
    } finally {
      setSavingItemCategory(false);
    }
  }

  async function handleCreateSchedule() {
    if (!newSchedulePayload.reportType) {
      toast.error('Selecione o tipo de relatório.');
      return;
    }

    setSchedulesLoading(true);
    try {
      const created = await createReportSchedule(newSchedulePayload as Partial<ReportSchedule>);
      setReportSchedules((prev) => (prev ?? []).concat(created));
      toast.success('Agendamento criado com sucesso.');
      setNewSchedulePayload({
        reportType: 'exits',
        targetUserId: null,
        sendEmail: true,
        sendDiscord: false,
        scheduleDay: 12,
        isActive: true,
      });
    } catch (error) {
      toast.error(getApiErrorMessage(error, 'Erro ao criar agendamento.'));
    } finally {
      setSchedulesLoading(false);
    }
  }

  function startEditSchedule(s: ReportSchedule) {
    setEditingScheduleId(s.id);
    setEditingPayload({
      reportType: s.reportType,
      targetUserId: s.targetUserId,
      sendEmail: s.sendEmail,
      sendDiscord: s.sendDiscord,
      scheduleDay: s.scheduleDay,
      isActive: s.isActive,
    });
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
    if (!key) {
      toast.error('Digite a chave da configuração (ex: SLA_URGENT_HOURS).');
      return;
    }

    setSaving(true);
    try {
      const payload: UpdateSystemSettingsPayload = { [key]: newSettingValue ?? '' };
      const updated = await updateSystemSettings(payload);
      setSettings(updated);
      setValues(
        updated.reduce<Record<string, string>>((acc, setting) => {
          acc[setting.id] = setting.value;
          return acc;
        }, {}),
      );
      setNewSettingKey('');
      setNewSettingValue('');
      toast.success('Configuração adicionada com sucesso.');
    } catch (error) {
      toast.error(getApiErrorMessage(error, 'Erro ao adicionar configuração.'));
    } finally {
      setSaving(false);
    }
  }

  // ----- Gerenciamento de SLA -----
  const slaKeys = useMemo(() => settings.filter((s) => s.id && s.id.startsWith('SLA_')), [settings]);

  async function handleSaveSLA() {
    if (!isAdmin) return;
    const payload: UpdateSystemSettingsPayload = {};
    slaKeys.forEach((s) => {
      payload[s.id] = values[s.id] ?? '';
    });

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
      toast.success('SLAs salvos com sucesso.');
    } catch (error) {
      console.error('Erro ao salvar SLAs', error);
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
      // backend expects `active` field name
      const payload = { active: !schedule.isActive } as unknown as Partial<ReportSchedule>;
      const updated = await updateReportSchedule(schedule.id, payload);
      setReportSchedules((prev) => (prev ?? []).map((s) => (s.id === updated.id ? updated : s)));
      toast.success(updated.isActive ? 'Agendamento ativado.' : 'Agendamento desativado.');
    } catch (error) {
      console.error('Erro ao atualizar agendamento', error);
      toast.error(getApiErrorMessage(error, 'Erro ao atualizar agendamento.'));
    } finally {
      setSchedulesLoading(false);
    }
  }

  if (!isAdmin && (activeTab === 'system' || activeTab === 'integration')) {
    return (
      <main className="w-full max-w-full px-4 sm:px-6 lg:px-8 py-8">
        <div className="bg-white rounded-xl border border-slate-200 shadow-sm p-8 text-center">
          <p className="text-sm text-slate-500">Você não possui permissão para acessar configurações globais.</p>
        </div>
      </main>
    );
  }

  return (
    <main className="w-full max-w-full px-4 sm:px-6 lg:px-8 py-8">
      <PageHero
        eyebrow="Sistema"
        title="Configurações"
        description="Ajuste parâmetros globais e personalize preferências para adaptar o ambiente ao fluxo da equipe."
      />

      {/* Abas */}
      <div className="bg-white rounded-xl border border-slate-200 shadow-sm overflow-hidden">
          <div className="flex border-b border-slate-200">
            {/* Tab: Sistema (ADMIN only) */}
            {isAdmin && (
              <button
                onClick={() => setActiveTab('system')}
                className={`flex items-center gap-2 px-6 py-4 font-medium transition-colors border-b-2 ${
                  activeTab === 'system'
                    ? 'text-brand-primary border-b-brand-primary'
                    : 'text-slate-600 border-b-transparent hover:text-slate-800'
                }`}
              >
                <Globe size={18} />
                Sistema
              </button>
            )}

            {/* Tab: Perfil */}
            <button
              onClick={() => setActiveTab('profile')}
              className={`flex items-center gap-2 px-6 py-4 font-medium transition-colors border-b-2 ${
                activeTab === 'profile'
                  ? 'text-brand-primary border-b-brand-primary'
                  : 'text-slate-600 border-b-transparent hover:text-slate-800'
              }`}
            >
              <UserIcon size={18} />
              Perfil
            </button>

            {/* Tab: Integração (ADMIN only) */}
            {isAdmin && (
              <button
                onClick={() => setActiveTab('integration')}
                className={`flex items-center gap-2 px-6 py-4 font-medium transition-colors border-b-2 ${
                  activeTab === 'integration'
                    ? 'text-brand-primary border-b-brand-primary'
                    : 'text-slate-600 border-b-transparent hover:text-slate-800'
                }`}
              >
                <SettingsIcon size={18} />
                Integração
              </button>
            )}
          </div>

        {/* Tab Content */}
        <div className="p-6 sm:p-8">
          {/* TAB: SISTEMA */}
          {activeTab === 'system' && isAdmin && (
            <>
              {loading ? (
                <div className="space-y-4">
                  <div className="h-28 rounded-xl bg-slate-200 animate-pulse" />
                  <div className="h-20 rounded-xl bg-slate-200 animate-pulse" />
                </div>
              ) : settings.length === 0 ? (
                <p className="text-sm text-slate-500">Nenhuma configuração global encontrada.</p>
              ) : (
                <>
                  <div className="space-y-4">
                    {Array.isArray(settings) && settings.map((setting) => (
                      <div
                        key={setting.id}
                        className="rounded-xl border border-slate-100 bg-white px-4 py-4 grid grid-cols-1 lg:grid-cols-[1fr_180px] gap-3 lg:items-center shadow-sm"
                      >
                        <div>
                          <p className="text-sm font-semibold text-slate-800">{getFriendlyLabel(setting.id)}</p>
                          <p className="text-xs text-slate-500 mt-1">{setting.description ?? 'Sem descrição disponível.'}</p>
                        </div>
                        <input
                          type="text"
                          value={values[setting.id] ?? ''}
                          onChange={(event) =>
                            setValues((prev) => ({
                              ...prev,
                              [setting.id]: event.target.value,
                            }))
                          }
                          className={inputClassName}
                          placeholder="Digite o valor"
                        />
                      </div>
                    ))}
                  </div>

                  {/* Seção SLA */}
                  <div className="mt-6 rounded-xl border border-slate-100 bg-white p-4 shadow-sm">
                    <h3 className="text-sm font-semibold text-slate-800 flex items-center gap-2">Configurações de SLA</h3>
                    <p className="text-xs text-slate-500 mt-1">Edite os SLAs do sistema (ex: SLA_URGENT_HOURS, SLA_HIGH_HOURS).</p>
                    <div className="mt-2">
                      <span className="inline-block w-14 h-1 rounded-full bg-[#fed8b0]/20" />
                    </div>

                    <div className="mt-4 space-y-3">
                      {Array.isArray(slaKeys) && slaKeys.length === 0 ? (
                        <div className="text-xs text-slate-700">Nenhuma configuração de SLA encontrada.</div>
                      ) : (
                        Array.isArray(slaKeys) ? (
                          slaKeys.map((s) => (
                            <div key={s.id} className="rounded-lg p-3 border border-slate-100 bg-white flex items-center justify-between">
                              <div className="flex items-center gap-3">
                                <Clock3 size={18} className="text-slate-400" />
                                <div>
                                  <p className="text-sm font-semibold text-slate-800">{getFriendlyLabel(s.id)}</p>
                                  <p className="text-xs text-slate-500 mt-1">{s.description ?? 'Sem descrição.'}</p>
                                </div>
                              </div>
                              <div className="w-36">
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
                        ) : null
                      )}
                    </div>

                    <div className="mt-4 flex justify-end">
                      <button onClick={handleSaveSLA} disabled={saving} className="inline-flex items-center gap-2 bg-brand-primary hover:bg-brand-primary-dark text-white text-sm font-semibold px-4 py-2.5 rounded-lg shadow-sm transition-colors transition-shadow">
                        Salvar Alterações de SLA
                      </button>
                    </div>
                  </div>

                  <div className="mt-6 rounded-xl border border-slate-200 bg-white p-4">
                    <h3 className="text-sm font-semibold text-slate-800">Adicionar Configuração</h3>
                    <p className="text-xs text-slate-500 mt-1">Adicione uma nova chave de configuração (ex: SLA_URGENT_HOURS).</p>

                    <div className="mt-3 grid grid-cols-1 md:grid-cols-3 gap-3 items-end">
                      <input
                        type="text"
                        placeholder="Chave (ex: SLA_URGENT_HOURS)"
                        value={newSettingKey}
                        onChange={(e) => setNewSettingKey(e.target.value)}
                        className={inputClassName}
                      />

                      <input
                        type="text"
                        placeholder="Valor"
                        value={newSettingValue}
                        onChange={(e) => setNewSettingValue(e.target.value)}
                        className={inputClassName}
                      />

                      <div>
                        <button onClick={handleAddSetting} disabled={saving} className="w-full bg-brand-primary hover:bg-brand-primary-dark text-white text-sm font-semibold px-4 py-2.5 rounded-xl transition-colors">Adicionar</button>
                      </div>
                    </div>
                  </div>
                  
                  <div className="mt-8 rounded-xl border border-slate-100 bg-white p-4 shadow-sm">
                    <h3 className="text-sm font-semibold text-slate-800">Agendamentos de Relatórios</h3>
                    <p className="text-xs text-slate-500 mt-1">Configure envios automáticos de relatórios (dia do mês).</p>

                    <div className="mt-4 space-y-3">
                      {Array.isArray(reportSchedules) && reportSchedules.length === 0 ? (
                        <div className="text-xs text-slate-500">Nenhum agendamento encontrado.</div>
                      ) : (
                        Array.isArray(reportSchedules) ? reportSchedules.map((s) => {
                          const usr = Array.isArray(usersList) ? usersList.find((u) => u.id === s.targetUserId) : undefined;
                          if (editingScheduleId === s.id) {
                            return (
                              <div key={s.id} className="rounded-lg border border-slate-100 p-4 bg-white shadow-sm">
                                <div className="grid grid-cols-1 md:grid-cols-3 gap-3 items-end">
                                  <select
                                    value={editingPayload?.reportType ?? 'exits'}
                                    onChange={(e) => setEditingPayload((prev) => ({ ...(prev ?? {}), reportType: e.target.value }))}
                                    className={inputClassName}
                                  >
                                    <option value="exits">Saídas de Estoque</option>
                                    <option value="entries">Entradas de Estoque</option>
                                    <option value="tickets">Histórico de Chamados</option>
                                  </select>

                                  <select
                                    value={editingPayload?.targetUserId ?? ''}
                                    onChange={(e) => setEditingPayload((prev) => ({ ...(prev ?? {}), targetUserId: e.target.value || null }))}
                                    className={inputClassName}
                                  >
                                    <option value="">Selecione usuário (opcional)</option>
                                    {Array.isArray(usersList) && usersList.map((u) => (
                                      <option key={u.id} value={u.id}>{u.name} ({u.email})</option>
                                    ))}
                                  </select>

                                  <input
                                    type="number"
                                    min={1}
                                    max={28}
                                    value={editingPayload?.scheduleDay ?? s.scheduleDay}
                                    onChange={(e) => setEditingPayload((prev) => ({ ...(prev ?? {}), scheduleDay: Number(e.target.value) }))}
                                    className={inputClassName}
                                  />

                                  <label className="flex items-center gap-2">
                                    <input type="checkbox" checked={!!editingPayload?.sendEmail} onChange={(e) => setEditingPayload((prev) => ({ ...(prev ?? {}), sendEmail: e.target.checked }))} />
                                    <span className="text-sm">Enviar por E-mail</span>
                                  </label>

                                  <label className="flex items-center gap-2">
                                    <input type="checkbox" checked={!!editingPayload?.sendDiscord} onChange={(e) => setEditingPayload((prev) => ({ ...(prev ?? {}), sendDiscord: e.target.checked }))} />
                                    <span className="text-sm">Enviar por Discord</span>
                                  </label>

                                  <div className="md:col-span-3 flex gap-2 justify-end">
                                    <button onClick={handleSaveEditedSchedule} disabled={schedulesLoading} className="bg-brand-primary hover:bg-brand-primary-dark text-white text-sm font-semibold px-4 py-2.5 rounded-lg shadow-sm">Salvar</button>
                                    <button onClick={cancelEdit} className="text-sm text-slate-600">Cancelar</button>
                                  </div>
                                </div>
                              </div>
                            );
                          }

                          return (
                            <div key={s.id} className="rounded-lg border border-slate-100 p-4 bg-white shadow-sm flex flex-col md:flex-row md:items-center md:justify-between gap-3">
                              <div>
                                <div className="text-sm font-semibold">{s.reportType} — Dia {s.scheduleDay}</div>
                                <div className="text-xs text-slate-500">Envia: {s.sendEmail ? 'E-mail' : ''}{s.sendEmail && s.sendDiscord ? ' + ' : ''}{s.sendDiscord ? 'Discord' : ''} — Destinatário: {usr ? `${usr.name} (${usr.email})` : (s.targetUserId ?? '—')}</div>
                              </div>
                              <div className="flex items-center gap-2">
                                <label className="flex items-center gap-2">
                                  <input type="checkbox" checked={s.isActive} onChange={() => handleToggleScheduleActive(s)} className="h-4 w-4" />
                                  <span className="text-sm">Ativo</span>
                                </label>
                                <button onClick={() => startEditSchedule(s)} className="p-2 rounded-md hover:bg-slate-100"><Pencil size={14} className="text-slate-700" /></button>
                                <button onClick={() => handleDeleteSchedule(s.id)} disabled={schedulesLoading} className="p-2 rounded-md hover:bg-slate-100"><Trash2 size={14} className="text-red-600" /></button>
                              </div>
                            </div>
                          );
                        }) : null
                      )}
                    </div>

                    <div className="mt-4 grid grid-cols-1 md:grid-cols-3 gap-3 items-end">
                      <select
                        value={newSchedulePayload.reportType}
                        onChange={(e) => setNewSchedulePayload((prev) => ({ ...prev, reportType: e.target.value }))}
                        className={inputClassName}
                      >
                        <option value="exits">Saídas de Estoque</option>
                        <option value="entries">Entradas de Estoque</option>
                        <option value="tickets">Histórico de Chamados</option>
                      </select>

                      <select
                        value={newSchedulePayload.targetUserId ?? ''}
                        onChange={(e) => setNewSchedulePayload((prev) => ({ ...prev, targetUserId: e.target.value || null }))}
                        className={inputClassName}
                      >
                        <option value="">Selecione usuário (opcional)</option>
                        {Array.isArray(usersList) && usersList.map((u) => (
                          <option key={u.id} value={u.id}>{u.name} ({u.email})</option>
                        ))}
                      </select>

                      <input
                        type="number"
                        min={1}
                        max={28}
                        value={newSchedulePayload.scheduleDay ?? 12}
                        onChange={(e) => setNewSchedulePayload((prev) => ({ ...prev, scheduleDay: Number(e.target.value) }))}
                        className={inputClassName}
                      />

                      <label className="flex items-center gap-2 col-span-1 md:col-span-1">
                        <input type="checkbox" checked={newSchedulePayload.sendEmail ?? true} onChange={(e) => setNewSchedulePayload((prev) => ({ ...prev, sendEmail: e.target.checked }))} />
                        <span className="text-sm">Enviar por E-mail</span>
                      </label>

                      <label className="flex items-center gap-2 col-span-1 md:col-span-1">
                        <input type="checkbox" checked={newSchedulePayload.sendDiscord ?? false} onChange={(e) => setNewSchedulePayload((prev) => ({ ...prev, sendDiscord: e.target.checked }))} />
                        <span className="text-sm">Enviar por Discord</span>
                      </label>

                      <div className="col-span-1 md:col-span-1">
                        <button onClick={handleCreateSchedule} disabled={schedulesLoading} className="w-full bg-brand-primary hover:bg-brand-primary-dark text-white text-sm font-semibold px-4 py-2.5 rounded-xl transition-colors">Criar Agendamento</button>
                      </div>
                    </div>
                  </div>

                    <div className="mt-6 flex justify-end">
                      <button
                        type="button"
                        disabled={saving || !hasChanges}
                        onClick={handleSave}
                        className="inline-flex items-center gap-2 bg-brand-primary hover:bg-brand-primary-dark disabled:opacity-60 disabled:cursor-not-allowed text-white text-sm font-semibold px-4 py-2.5 rounded-xl transition-colors"
                      >
                        <Save size={16} />
                        {saving ? 'Salvando...' : 'Salvar Configurações'}
                      </button>
                    </div>

                  <div className="mt-8 grid grid-cols-1 lg:grid-cols-2 gap-4">
                    <div className="rounded-xl border border-slate-200 bg-white p-4">
                      <h3 className="text-sm font-semibold text-slate-800">Categorias de Equipamentos (CMDB)</h3>
                      <p className="text-xs text-slate-500 mt-1">Gerencie as categorias usadas nos ativos físicos.</p>

                      <div className="mt-3 flex flex-wrap gap-2">
                        {!Array.isArray(assetCategories) || assetCategories.length === 0 ? (
                          <span className="text-xs text-slate-500">Nenhuma categoria cadastrada.</span>
                        ) : (
                          assetCategories.map((category) => (
                            <span
                              key={category.id}
                              className="inline-flex items-center rounded-full border border-slate-100 bg-white px-2.5 py-1 text-xs text-slate-700"
                            >
                              {category.name}
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
                          placeholder="Nova categoria de equipamento"
                          maxLength={100}
                        />
                        <button
                          type="button"
                          onClick={handleCreateAssetCategory}
                          disabled={savingAssetCategory}
                          className="shrink-0 bg-brand-primary hover:bg-primary-hover disabled:opacity-60 text-white text-sm font-semibold px-4 py-2.5 rounded-xl transition-colors"
                        >
                          {savingAssetCategory ? 'Adicionando...' : 'Adicionar'}
                        </button>
                      </div>
                    </div>

                    <div className="rounded-xl border border-slate-200 bg-white p-4">
                      <h3 className="text-sm font-semibold text-slate-800">Categorias de Inventário (Consumíveis)</h3>
                      <p className="text-xs text-slate-500 mt-1">Gerencie categorias para materiais e itens de estoque.</p>

                      <div className="mt-3 flex flex-wrap gap-2">
                        {!Array.isArray(itemCategories) || !itemCategories.some((category) => category.isConsumable) ? (
                          <span className="text-xs text-slate-500">Nenhuma categoria cadastrada.</span>
                        ) : (
                          itemCategories
                            .filter((category) => category.isConsumable)
                            .map((category) => (
                              <span
                                key={category.id}
                                className="inline-flex items-center rounded-full border border-slate-100 bg-white px-2.5 py-1 text-xs text-slate-700"
                              >
                                {category.name}
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
                          placeholder="Nova categoria de inventário"
                          maxLength={100}
                        />
                        <button
                          type="button"
                          onClick={handleCreateItemCategory}
                          disabled={savingItemCategory}
                          className="shrink-0 bg-brand-primary hover:bg-primary-hover disabled:opacity-60 text-white text-sm font-semibold px-4 py-2.5 rounded-xl transition-colors"
                        >
                          {savingItemCategory ? 'Adicionando...' : 'Adicionar'}
                        </button>
                      </div>
                    </div>
                  </div>
                </>
              )}
            </>
          )}

          {/* ABA: PREFERÊNCIAS */}
          {activeTab === 'profile' && (
            <div className="space-y-6 max-w-2xl">
              {/* Email Notifications */}
              <div className="rounded-xl border border-slate-100 bg-white px-4 py-4 flex items-center justify-between shadow-sm">
                <div>
                  <p className="text-sm font-semibold text-slate-800">Notificações por E-mail</p>
                  <p className="text-xs text-slate-500 mt-1">Receba atualizações de chamados e tarefas por e-mail</p>
                </div>
                <label className="relative inline-flex items-center cursor-pointer">
                  <input
                    type="checkbox"
                    checked={emailNotifications}
                    onChange={(e) => setEmailNotifications(e.target.checked)}
                    className="sr-only peer"
                  />
                  <div className="w-11 h-6 bg-slate-300 peer-focus:outline-none peer-focus:ring-2 peer-focus:ring-brand-primary rounded-full peer peer-checked:after:translate-x-full peer-checked:after:border-white after:content-[''] after:absolute after:top-[2px] after:left-[2px] after:bg-white after:border-slate-300 after:border after:rounded-full after:h-5 after:w-5 after:transition-all peer-checked:bg-brand-primary" />
                </label>
              </div>

              {/* Theme */}
              <div className="rounded-xl border border-slate-100 bg-white px-4 py-4 flex items-center justify-between shadow-sm">
                <div>
                  <p className="text-sm font-semibold text-slate-800">Tema Escuro</p>
                  <p className="text-xs text-slate-500 mt-1">Ative o modo escuro para melhor visualização noturna</p>
                </div>
                <label className="relative inline-flex items-center cursor-pointer">
                  <input
                    type="checkbox"
                    checked={darkTheme}
                    onChange={(e) => setDarkTheme(e.target.checked)}
                    className="sr-only peer"
                  />
                  <div className="w-11 h-6 bg-slate-300 peer-focus:outline-none peer-focus:ring-2 peer-focus:ring-brand-primary rounded-full peer peer-checked:after:translate-x-full peer-checked:after:border-white after:content-[''] after:absolute after:top-[2px] after:left-[2px] after:bg-white after:border-slate-300 after:border after:rounded-full after:h-5 after:w-5 after:transition-all peer-checked:bg-brand-primary" />
                </label>
              </div>

              {/* Language */}
              <div className="rounded-xl border border-slate-100 bg-white px-4 py-4 grid grid-cols-1 lg:grid-cols-[1fr_180px] gap-3 lg:items-center shadow-sm">
                <div>
                  <p className="text-sm font-semibold text-slate-800">Idioma</p>
                  <p className="text-xs text-slate-500 mt-1">Escolha seu idioma preferido para a interface</p>
                </div>
                <select
                  value={language}
                  onChange={(e) => setLanguage(e.target.value as 'pt-BR' | 'en')}
                  className={inputClassName}
                >
                  <option value="pt-BR">Português (BR)</option>
                  <option value="en">English</option>
                </select>
              </div>

              {/* Botão Salvar */}
              <div className="flex justify-end pt-4">
                <button
                  type="button"
                  onClick={handleSavePreferences}
                  className="inline-flex items-center gap-2 bg-brand-primary hover:bg-primary-hover text-white text-sm font-semibold px-4 py-2.5 rounded-xl transition-colors"
                >
                  <Save size={16} />
                  Salvar Preferências
                </button>
              </div>
            </div>
          )}

          {/* TAB: INTEGRAÇÃO (ADMIN) */}
          {activeTab === 'integration' && isAdmin && (
            <div className="space-y-6 max-w-3xl">
              <div className="rounded-xl border border-slate-200 bg-white p-4">
                <h3 className="text-sm font-semibold text-slate-800">Ambiente (.env) e Integrações</h3>
                <p className="text-xs text-slate-500 mt-1">Valores e indicadores das integrações do sistema (somente leitura).</p>

                {adminConfig ? (
                  <div className="mt-4 grid grid-cols-1 lg:grid-cols-2 gap-3">
                    <div>
                      <p className="text-xs text-slate-500">SMTP From Email</p>
                      <input type="text" value={adminConfig.smtpFromEmail ?? ''} readOnly className={inputClassName} />
                    </div>

                    <div>
                      <p className="text-xs text-slate-500">SMTP From Name</p>
                      <input type="text" value={adminConfig.smtpFromName ?? ''} readOnly className={inputClassName} />
                    </div>

                    <div className="col-span-1 lg:col-span-2">
                      <p className="text-xs text-slate-500">Discord</p>
                      <div className="text-sm text-slate-700 mt-1">Bot: {adminConfig.discordBotEnabled ? 'Ativado' : 'Desativado'} — Webhook: {adminConfig.discordWebhookPresent ? 'Presente' : 'Não configurado'}</div>
                    </div>
                  </div>
                ) : (
                  <p className="text-xs text-slate-500 mt-2">Nenhuma configuração de integração disponível.</p>
                )}
              </div>
            </div>
          )}
        </div>
      </div>
    </main>
  );
}

