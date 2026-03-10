import { useEffect, useMemo, useState } from 'react';
import { Save, Globe, User as UserIcon } from 'lucide-react';
import { toast } from 'react-toastify';

import { useAuth } from '../../contexts/AuthContext';
import {
  createAssetCategory,
  createItemCategory,
  getAssetCategories,
  getItemCategories,
  getSystemSettings,
  updateSystemSettings,
  type AssetCategory,
  type ItemCategory,
  type SystemSetting,
  type UpdateSystemSettingsPayload,
} from '../../services/api';

type TabType = 'global' | 'preferences';

const inputClassName =
  'w-full rounded-xl border border-slate-200 bg-white px-3.5 py-2.5 text-sm text-slate-800 shadow-sm placeholder-slate-400 focus:outline-none focus:ring-2 focus:ring-primary focus:border-primary transition';

const friendlyLabels: Record<string, string> = {
  SLA_URGENT_HOURS: 'SLA - Chamados Urgentes (Horas)',
  ATTACHMENT_SIZE_LIMIT_MB: 'Limite de Tamanho de Anexo (MB)',
};

function getFriendlyLabel(settingKey: string): string {
  return friendlyLabels[settingKey] ?? settingKey.replaceAll('_', ' ');
}

export default function Settings() {
  const { user } = useAuth();
  const [activeTab, setActiveTab] = useState<TabType>('global');
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

  // User preferences state
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
        setSettings(data);
        setValues(
          data.reduce<Record<string, string>>((acc, setting) => {
            acc[setting.id] = setting.value;
            return acc;
          }, {}),
        );
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

        setAssetCategories(assetData);
        setItemCategories(itemData);
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
    // Mock save - preferences not persisted yet
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

  if (!isAdmin && activeTab === 'global') {
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
      <section className="mb-6">
        <h1 className="text-2xl font-bold text-slate-800">Configurações</h1>
        <p className="text-sm text-slate-400 mt-1">Customize suas preferências e configurações do sistema.</p>
      </section>

      {/* Tabs */}
      <div className="bg-white rounded-xl border border-slate-200 shadow-sm overflow-hidden">
        <div className="flex border-b border-slate-200">
          {/* Tab: Global (ADMIN only) */}
          {isAdmin && (
            <button
              onClick={() => setActiveTab('global')}
              className={`flex items-center gap-2 px-6 py-4 font-medium transition-colors border-b-2 ${
                activeTab === 'global'
                  ? 'text-brand-primary border-b-brand-primary'
                  : 'text-slate-600 border-b-transparent hover:text-slate-800'
              }`}
            >
              <Globe size={18} />
              Configurações Globais
            </button>
          )}

          {/* Tab: Preferences */}
          <button
            onClick={() => setActiveTab('preferences')}
            className={`flex items-center gap-2 px-6 py-4 font-medium transition-colors border-b-2 ${
              activeTab === 'preferences'
                ? 'text-brand-primary border-b-brand-primary'
                : 'text-slate-600 border-b-transparent hover:text-slate-800'
            }`}
          >
            <UserIcon size={18} />
            Minhas Preferências
          </button>
        </div>

        {/* Tab Content */}
        <div className="p-6 sm:p-8">
          {/* TAB: GLOBAL */}
          {activeTab === 'global' && isAdmin && (
            <>
              {loading ? (
                <div className="animate-pulse space-y-3">
                  <div className="h-4 bg-slate-200 rounded w-3/4" />
                  <div className="h-4 bg-slate-200 rounded w-1/2" />
                </div>
              ) : settings.length === 0 ? (
                <p className="text-sm text-slate-500">Nenhuma configuração global encontrada.</p>
              ) : (
                <>
                  <div className="space-y-4">
                    {settings.map((setting) => (
                      <div
                        key={setting.id}
                        className="rounded-xl border border-slate-200 bg-slate-50/70 px-4 py-3 grid grid-cols-1 lg:grid-cols-[1fr_180px] gap-3 lg:items-center"
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

                  <div className="mt-6 flex justify-end">
                    <button
                      type="button"
                      disabled={saving || !hasChanges}
                      onClick={handleSave}
                      className="inline-flex items-center gap-2 bg-brand-primary hover:bg-primary-hover disabled:opacity-60 disabled:cursor-not-allowed text-white text-sm font-semibold px-4 py-2.5 rounded-xl transition-colors"
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
                        {assetCategories.length === 0 ? (
                          <span className="text-xs text-slate-500">Nenhuma categoria cadastrada.</span>
                        ) : (
                          assetCategories.map((category) => (
                            <span
                              key={category.id}
                              className="inline-flex items-center rounded-full border border-slate-200 bg-slate-50 px-2.5 py-1 text-xs text-slate-700"
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
                        {itemCategories.filter((category) => category.isConsumable).length === 0 ? (
                          <span className="text-xs text-slate-500">Nenhuma categoria cadastrada.</span>
                        ) : (
                          itemCategories
                            .filter((category) => category.isConsumable)
                            .map((category) => (
                            <span
                              key={category.id}
                              className="inline-flex items-center rounded-full border border-slate-200 bg-slate-50 px-2.5 py-1 text-xs text-slate-700"
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

          {/* TAB: PREFERENCES */}
          {activeTab === 'preferences' && (
            <div className="space-y-6 max-w-2xl">
              {/* Email Notifications */}
              <div className="rounded-xl border border-slate-200 bg-slate-50/70 px-4 py-4 flex items-center justify-between">
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
              <div className="rounded-xl border border-slate-200 bg-slate-50/70 px-4 py-4 flex items-center justify-between">
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
              <div className="rounded-xl border border-slate-200 bg-slate-50/70 px-4 py-4 grid grid-cols-1 lg:grid-cols-[1fr_180px] gap-3 lg:items-center">
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

              {/* Save Button */}
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
        </div>
      </div>
    </main>
  );
}

