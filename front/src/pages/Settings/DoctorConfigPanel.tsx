import { useEffect, useState } from 'react';
import { Loader2, Plus, Trash2, Save, X } from 'lucide-react';
import { toast } from 'react-toastify';
import {
  getAll,
  save,
  deleteDoctorConfig,
  type DoctorConfiguration,
} from '../../services/doctorConfigService';

const emptyForm = (): DoctorConfiguration => ({
  feegowProfissionalId: 0,
  doctorName: '',
  gerAcessoMatricula: '',
  gerAcessoCpf: '',
  blipQueueId: '',
  blipQueueName: '',
});

const inputCls =
  'w-full rounded-xl border border-slate-200 bg-white px-3 py-2 text-sm text-slate-800 shadow-sm placeholder-slate-400 focus:outline-none focus:ring-2 focus:ring-[#feb56c]/60 focus:border-[#feb56c] transition-all';

export default function DoctorConfigPanel() {
  const [configs, setConfigs] = useState<DoctorConfiguration[]>([]);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [deletingId, setDeletingId] = useState<number | null>(null);
  const [showForm, setShowForm] = useState(false);
  const [form, setForm] = useState<DoctorConfiguration>(emptyForm());

  async function loadConfigs() {
    try {
      setLoading(true);
      const data = await getAll();
      setConfigs(data);
    } catch {
      toast.error('Erro ao carregar configurações de médicos.');
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    loadConfigs();
  }, []);

  function handleChange(field: keyof DoctorConfiguration, value: string | number) {
    setForm((prev) => ({ ...prev, [field]: value }));
  }

  async function handleSave() {
    if (!form.doctorName.trim() || form.feegowProfissionalId <= 0) {
      toast.warning('Preencha o nome do médico e o ID Feegow.');
      return;
    }
    setSaving(true);
    try {
      const saved = await save(form);
      setConfigs((prev) => {
        const exists = prev.find((c) => c.feegowProfissionalId === saved.feegowProfissionalId);
        if (exists) {
          return prev.map((c) => (c.feegowProfissionalId === saved.feegowProfissionalId ? saved : c));
        }
        return [...prev, saved];
      });
      toast.success(`Configuração de "${saved.doctorName}" salva com sucesso.`);
      setForm(emptyForm());
      setShowForm(false);
    } catch {
      toast.error('Erro ao salvar configuração.');
    } finally {
      setSaving(false);
    }
  }

  async function handleDelete(id: number, name: string) {
    if (!window.confirm(`Remover configuração de "${name}"?`)) return;
    setDeletingId(id);
    try {
      await deleteDoctorConfig(id);
      setConfigs((prev) => prev.filter((c) => c.feegowProfissionalId !== id));
      toast.success(`Configuração de "${name}" removida.`);
    } catch {
      toast.error('Erro ao remover configuração.');
    } finally {
      setDeletingId(null);
    }
  }

  function handleEdit(config: DoctorConfiguration) {
    setForm({ ...config });
    setShowForm(true);
    window.scrollTo({ top: 0, behavior: 'smooth' });
  }

  return (
    <div className="space-y-4">
      {/* Header row */}
      <div className="flex items-center justify-between">
        <div>
          <h3 className="text-sm font-bold text-slate-800">Configurações de Médicos</h3>
          <p className="text-xs text-slate-500 mt-0.5">
            Gerencie credenciais de catraca (GerAcesso) e filas do Blip por profissional.
          </p>
        </div>
        <button
          type="button"
          onClick={() => { setForm(emptyForm()); setShowForm(true); }}
          className="inline-flex items-center gap-1.5 rounded-xl bg-[#feb56c] px-4 py-2 text-xs font-bold text-slate-900 shadow-sm hover:bg-[#f6a455] transition-all"
        >
          <Plus size={14} />
          Novo Médico
        </button>
      </div>

      {/* Form */}
      {showForm && (
        <div className="rounded-2xl border border-[#feb56c]/40 bg-white p-5 shadow-sm space-y-4">
          <div className="flex items-center justify-between mb-1">
            <p className="text-sm font-semibold text-slate-800">
              {form.feegowProfissionalId > 0 ? `Editando: ${form.doctorName}` : 'Novo Médico'}
            </p>
            <button
              type="button"
              onClick={() => setShowForm(false)}
              className="text-slate-400 hover:text-slate-600 transition-colors"
            >
              <X size={16} />
            </button>
          </div>

          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-3">
            <div>
              <label className="block text-xs font-medium text-slate-600 mb-1">ID Feegow *</label>
              <input
                id="doctor-config-feegow-id"
                type="number"
                min={1}
                value={form.feegowProfissionalId || ''}
                onChange={(e) => handleChange('feegowProfissionalId', Number(e.target.value))}
                className={inputCls}
                placeholder="Ex: 8"
              />
            </div>
            <div>
              <label className="block text-xs font-medium text-slate-600 mb-1">Nome do Médico *</label>
              <input
                id="doctor-config-name"
                type="text"
                value={form.doctorName}
                onChange={(e) => handleChange('doctorName', e.target.value)}
                className={inputCls}
                placeholder="Dr. João Silva"
              />
            </div>
            <div>
              <label className="block text-xs font-medium text-slate-600 mb-1">Matrícula GerAcesso</label>
              <input
                id="doctor-config-matricula"
                type="text"
                value={form.gerAcessoMatricula}
                onChange={(e) => handleChange('gerAcessoMatricula', e.target.value)}
                className={inputCls}
                placeholder="Ex: 001234"
              />
            </div>
            <div>
              <label className="block text-xs font-medium text-slate-600 mb-1">CPF (só dígitos)</label>
              <input
                id="doctor-config-cpf"
                type="text"
                maxLength={11}
                value={form.gerAcessoCpf}
                onChange={(e) => handleChange('gerAcessoCpf', e.target.value.replace(/\D/g, ''))}
                className={inputCls}
                placeholder="12345678901"
              />
            </div>
            <div>
              <label className="block text-xs font-medium text-slate-600 mb-1">UUID Fila Blip</label>
              <input
                id="doctor-config-blip-queue-id"
                type="text"
                value={form.blipQueueId}
                onChange={(e) => handleChange('blipQueueId', e.target.value)}
                className={inputCls}
                placeholder="xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx"
              />
            </div>
            <div>
              <label className="block text-xs font-medium text-slate-600 mb-1">Nome da Fila Blip</label>
              <input
                id="doctor-config-blip-queue-name"
                type="text"
                value={form.blipQueueName}
                onChange={(e) => handleChange('blipQueueName', e.target.value)}
                className={inputCls}
                placeholder="Dr. Silva"
              />
            </div>
          </div>

          <div className="flex justify-end pt-1">
            <button
              id="doctor-config-save-btn"
              type="button"
              disabled={saving}
              onClick={handleSave}
              className="inline-flex items-center gap-2 rounded-xl bg-[#feb56c] px-5 py-2 text-sm font-bold text-slate-900 hover:bg-[#f6a455] disabled:opacity-50 disabled:cursor-not-allowed transition-all shadow-sm"
            >
              {saving ? <Loader2 size={14} className="animate-spin" /> : <Save size={14} />}
              {saving ? 'Salvando...' : 'Salvar'}
            </button>
          </div>
        </div>
      )}

      {/* Table */}
      <div className="bg-white rounded-2xl border border-slate-200 shadow-sm overflow-hidden">
        {loading ? (
          <div className="flex items-center justify-center py-16 gap-2 text-slate-400">
            <Loader2 size={20} className="animate-spin" />
            <span className="text-sm">Carregando...</span>
          </div>
        ) : configs.length === 0 ? (
          <div className="py-16 text-center">
            <p className="text-sm font-medium text-slate-600">Nenhum médico cadastrado ainda.</p>
            <p className="text-xs text-slate-400 mt-1">Clique em "Novo Médico" para começar.</p>
          </div>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead>
                <tr className="border-b border-slate-100 bg-slate-50">
                  <th className="text-left px-4 py-3 text-xs font-semibold text-slate-500 uppercase tracking-wide">ID Feegow</th>
                  <th className="text-left px-4 py-3 text-xs font-semibold text-slate-500 uppercase tracking-wide">Nome</th>
                  <th className="text-left px-4 py-3 text-xs font-semibold text-slate-500 uppercase tracking-wide">Matrícula</th>
                  <th className="text-left px-4 py-3 text-xs font-semibold text-slate-500 uppercase tracking-wide">Fila Blip</th>
                  <th className="text-left px-4 py-3 text-xs font-semibold text-slate-500 uppercase tracking-wide">CPF</th>
                  <th className="px-4 py-3" />
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {configs.map((c) => (
                  <tr
                    key={c.feegowProfissionalId}
                    className="hover:bg-slate-50 transition-colors cursor-pointer"
                    onClick={() => handleEdit(c)}
                  >
                    <td className="px-4 py-3 font-mono text-xs text-slate-600">{c.feegowProfissionalId}</td>
                    <td className="px-4 py-3 font-medium text-slate-800">{c.doctorName}</td>
                    <td className="px-4 py-3 text-slate-600">{c.gerAcessoMatricula || <span className="text-slate-300 italic">—</span>}</td>
                    <td className="px-4 py-3 text-slate-600">
                      <div className="flex flex-col gap-0.5">
                        <span className="text-xs text-slate-800 font-medium">{c.blipQueueName || <span className="text-slate-300 italic">—</span>}</span>
                        {c.blipQueueId && (
                          <span className="font-mono text-[10px] text-slate-400 truncate max-w-[180px]">{c.blipQueueId}</span>
                        )}
                      </div>
                    </td>
                    <td className="px-4 py-3 font-mono text-xs text-slate-400">
                      {c.gerAcessoCpf
                        ? `***${c.gerAcessoCpf.slice(-3)}`
                        : <span className="italic text-slate-300">—</span>}
                    </td>
                    <td className="px-4 py-3" onClick={(e) => e.stopPropagation()}>
                      <button
                        type="button"
                        disabled={deletingId === c.feegowProfissionalId}
                        onClick={() => handleDelete(c.feegowProfissionalId, c.doctorName)}
                        className="p-1.5 rounded-lg text-slate-400 hover:text-red-500 hover:bg-red-50 transition-all disabled:opacity-40"
                        title="Remover configuração"
                      >
                        {deletingId === c.feegowProfissionalId
                          ? <Loader2 size={14} className="animate-spin" />
                          : <Trash2 size={14} />}
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>
    </div>
  );
}
