import { useEffect, useMemo, useState } from 'react';
import { Save } from 'lucide-react';
import { toast } from 'react-toastify';

import { useAuth } from '../../contexts/AuthContext';
import {
  getSystemSettings,
  updateSystemSettings,
  type SystemSetting,
  type UpdateSystemSettingsPayload,
} from '../../services/api';

const inputClassName =
  'w-full rounded-xl border border-slate-200 bg-white px-3.5 py-2.5 text-sm text-slate-800 shadow-sm placeholder-slate-400 focus:outline-none focus:ring-2 focus:ring-primary focus:border-primary transition';

const friendlyLabels: Record<string, string> = {
  SLA_URGENT_HOURS: 'SLA - Chamados Urgentes (Horas)',
};

function getFriendlyLabel(settingKey: string): string {
  return friendlyLabels[settingKey] ?? settingKey.replaceAll('_', ' ');
}

export default function Settings() {
  const { user } = useAuth();
  const [settings, setSettings] = useState<SystemSetting[]>([]);
  const [values, setValues] = useState<Record<string, string>>({});
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);

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

  if (!isAdmin) {
    return (
      <main className="w-full max-w-full px-4 sm:px-6 lg:px-8 py-8">
        <div className="bg-white rounded-xl border border-slate-200 shadow-sm p-8 text-center">
          <p className="text-sm text-slate-500">Você não possui permissão para acessar esta área.</p>
        </div>
      </main>
    );
  }

  return (
    <main className="w-full max-w-full px-4 sm:px-6 lg:px-8 py-8">
      <section className="mb-6">
        <h1 className="text-2xl font-bold text-slate-800">Configurações Globais</h1>
        <p className="text-sm text-slate-400 mt-1">Ajuste parâmetros globais do sistema para toda a operação.</p>
      </section>

      <section className="bg-white rounded-xl border border-slate-200 shadow-sm p-5 sm:p-6">
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
          </>
        )}
      </section>
    </main>
  );
}
