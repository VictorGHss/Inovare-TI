import { useState } from 'react';
import { X, Loader2, Wrench } from 'lucide-react';
import { toast } from 'react-toastify';
import { createAssetMaintenance, type AssetMaintenance, type CreateAssetMaintenanceData } from '../../services/api';

interface NewMaintenanceModalProps {
  isOpen: boolean;
  assetId: string;
  onClose: () => void;
  onMaintenanceCreated: (maintenance: AssetMaintenance) => void;
}

const inputClassName =
  'w-full rounded-xl border border-slate-200 bg-white px-3.5 py-2.5 text-sm text-slate-800 shadow-sm placeholder-slate-400 focus:outline-none focus:ring-2 focus:ring-brand-primary/30 focus:border-brand-primary transition';

const labelClassName = 'block text-[10px] font-bold uppercase tracking-widest text-slate-400 mb-2';

export default function NewMaintenanceModal({
  isOpen,
  assetId,
  onClose,
  onMaintenanceCreated,
}: NewMaintenanceModalProps) {
  const [loading, setLoading] = useState(false);
  const [formData, setFormData] = useState({
    maintenanceDate: new Date().toISOString().split('T')[0],
    type: 'PREVENTIVE' as 'PREVENTIVE' | 'CORRECTIVE' | 'UPGRADE',
    description: '',
    cost: '',
  });

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();

    if (!formData.maintenanceDate || !formData.type) {
      toast.error('Data e tipo da manutenção são obrigatórios.');
      return;
    }

    setLoading(true);
    try {
      const dto: CreateAssetMaintenanceData = {
        maintenanceDate: formData.maintenanceDate,
        type: formData.type,
        description: formData.description || undefined,
        cost: formData.cost ? parseFloat(formData.cost) : null,
      };

      const newMaintenance = await createAssetMaintenance(assetId, dto);
      toast.success('Manutenção registrada com sucesso!');
      onMaintenanceCreated(newMaintenance);

      setFormData({
        maintenanceDate: new Date().toISOString().split('T')[0],
        type: 'PREVENTIVE',
        description: '',
        cost: '',
      });
      onClose();
    } catch {
      toast.error('Erro ao registrar manutenção.');
    } finally {
      setLoading(false);
    }
  }

  if (!isOpen) return null;

  return (
    <div className="fixed inset-0 bg-black/40 flex items-center justify-center p-4 z-50 backdrop-blur-sm">
      <div className="bg-white rounded-2xl shadow-2xl max-w-md w-full max-h-[90vh] overflow-y-auto overflow-hidden">

        {/* Brand accent strip */}
        <div className="h-1.5 w-full bg-gradient-to-r from-brand-primary to-brand-primary-dark" />

        {/* Header */}
        <div className="flex items-center justify-between border-b border-slate-100 px-6 py-5">
          <div className="flex items-center gap-3">
            <span className="inline-flex h-9 w-9 items-center justify-center rounded-xl bg-brand-secondary/60">
              <Wrench size={17} className="text-brand-primary-dark" />
            </span>
            <div>
              <h2 className="text-base font-bold text-slate-800">Registrar Manutenção</h2>
              <p className="mt-0.5 text-xs text-slate-400">Preencha os dados do serviço realizado</p>
            </div>
          </div>
          <button
            onClick={onClose}
            className="rounded-lg p-1.5 text-slate-400 transition-colors hover:bg-slate-100 hover:text-slate-600"
            aria-label="Fechar"
          >
            <X size={17} />
          </button>
        </div>

        {/* Form */}
        <form onSubmit={handleSubmit} className="p-6 space-y-4">

          {/* Date */}
          <div>
            <label className={labelClassName}>
              Data da Manutenção <span className="text-red-400 normal-case tracking-normal font-bold">*</span>
            </label>
            <input
              type="date"
              value={formData.maintenanceDate}
              onChange={(e) => setFormData({ ...formData, maintenanceDate: e.target.value })}
              className={inputClassName}
            />
          </div>

          {/* Type */}
          <div>
            <label className={labelClassName}>
              Tipo de Manutenção <span className="text-red-400 normal-case tracking-normal font-bold">*</span>
            </label>
            <select
              value={formData.type}
              onChange={(e) =>
                setFormData({ ...formData, type: e.target.value as 'PREVENTIVE' | 'CORRECTIVE' | 'UPGRADE' })
              }
              className={inputClassName}
            >
              <option value="PREVENTIVE">Preventiva</option>
              <option value="CORRECTIVE">Corretiva</option>
              <option value="UPGRADE">Upgrade</option>
            </select>
          </div>

          {/* Cost */}
          <div>
            <label className={labelClassName}>
              Custo (R$){' '}
              <span className="normal-case tracking-normal font-normal text-slate-400">— opcional</span>
            </label>
            <input
              type="number"
              step="0.01"
              min="0"
              placeholder="0.00"
              value={formData.cost}
              onChange={(e) => setFormData({ ...formData, cost: e.target.value })}
              className={inputClassName}
            />
          </div>

          {/* Description */}
          <div>
            <label className={labelClassName}>
              Descrição Detalhada{' '}
              <span className="normal-case tracking-normal font-normal text-slate-400">— opcional</span>
            </label>
            <textarea
              placeholder="Descreva o que foi realizado na manutenção..."
              value={formData.description}
              onChange={(e) => setFormData({ ...formData, description: e.target.value })}
              rows={4}
              className={`${inputClassName} resize-none`}
            />
          </div>

          {/* Actions */}
          <div className="flex gap-2.5 pt-2 border-t border-slate-100">
            <button
              type="button"
              onClick={onClose}
              className="flex-1 rounded-xl border border-slate-200 px-4 py-2.5 text-sm font-semibold text-slate-600 transition-colors hover:bg-slate-50"
            >
              Cancelar
            </button>
            <button
              type="submit"
              disabled={loading}
              className="flex-1 inline-flex items-center justify-center gap-2 rounded-xl bg-brand-primary px-4 py-2.5 text-sm font-bold text-white shadow-sm transition-colors hover:bg-brand-primary-dark disabled:cursor-not-allowed disabled:opacity-60"
            >
              {loading && <Loader2 size={15} className="animate-spin" />}
              {loading ? 'Registrando...' : 'Registrar'}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
