import { useState } from 'react';
import { X, Loader2 } from 'lucide-react';
import { toast } from 'react-toastify';
import { createAssetMaintenance, type AssetMaintenance, type CreateAssetMaintenanceData } from '../../services/api';

interface NewMaintenanceModalProps {
  isOpen: boolean;
  assetId: string;
  onClose: () => void;
  onMaintenanceCreated: (maintenance: AssetMaintenance) => void;
}

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
      
      // Resetar formulário
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
    <div className="fixed inset-0 bg-black/50 flex items-center justify-center p-4 z-50">
      <div className="bg-white rounded-xl shadow-lg max-w-md w-full max-h-[90vh] overflow-y-auto">
        {/* Header */}
        <div className="flex items-center justify-between p-6 border-b border-slate-200">
          <h2 className="text-lg font-bold text-slate-800">Registrar Manutenção</h2>
          <button
            onClick={onClose}
            className="p-1.5 rounded-lg hover:bg-slate-100 text-slate-500 hover:text-slate-700 transition-colors"
            aria-label="Fechar"
          >
            <X size={20} />
          </button>
        </div>

        {/* Formulário */}
        <form onSubmit={handleSubmit} className="p-6 space-y-4">
          {/* Data da Manutenção */}
          <div>
            <label className="block text-sm font-medium text-slate-700 mb-1.5">
              Data da Manutenção <span className="text-red-500">*</span>
            </label>
            <input
              type="date"
              value={formData.maintenanceDate}
              onChange={(e) =>
                setFormData({ ...formData, maintenanceDate: e.target.value })
              }
              className="w-full px-3 py-2.5 border border-slate-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-primary/50 text-sm"
            />
          </div>

          {/* Tipo de Manutenção */}
          <div>
            <label className="block text-sm font-medium text-slate-700 mb-1.5">
              Tipo de Manutenção <span className="text-red-500">*</span>
            </label>
            <select
              value={formData.type}
              onChange={(e) =>
                setFormData({
                  ...formData,
                  type: e.target.value as 'PREVENTIVE' | 'CORRECTIVE' | 'UPGRADE',
                })
              }
              className="w-full px-3 py-2.5 border border-slate-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-primary/50 text-sm"
            >
              <option value="PREVENTIVE">Preventiva</option>
              <option value="CORRECTIVE">Corretiva</option>
              <option value="UPGRADE">Upgrade</option>
            </select>
          </div>

          {/* Custo */}
          <div>
            <label className="block text-sm font-medium text-slate-700 mb-1.5">
              Custo (R$) <span className="text-slate-400 text-xs">(Opcional)</span>
            </label>
            <input
              type="number"
              step="0.01"
              min="0"
              placeholder="0.00"
              value={formData.cost}
              onChange={(e) =>
                setFormData({ ...formData, cost: e.target.value })
              }
              className="w-full px-3 py-2.5 border border-slate-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-primary/50 text-sm"
            />
          </div>

          {/* Descrição */}
          <div>
            <label className="block text-sm font-medium text-slate-700 mb-1.5">
              Descrição Detalhada <span className="text-slate-400 text-xs">(Opcional)</span>
            </label>
            <textarea
              placeholder="Descreva o que foi realizado na manutenção..."
              value={formData.description}
              onChange={(e) =>
                setFormData({ ...formData, description: e.target.value })
              }
              rows={4}
              className="w-full px-3 py-2.5 border border-slate-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-primary/50 text-sm resize-none"
            />
          </div>

          {/* Botões */}
          <div className="flex gap-3 pt-4">
            <button
              type="button"
              onClick={onClose}
              className="flex-1 px-4 py-2.5 border border-slate-300 rounded-lg text-slate-700 font-medium hover:bg-slate-50 transition-colors text-sm"
            >
              Cancelar
            </button>
            <button
              type="submit"
              disabled={loading}
              className="flex-1 px-4 py-2.5 bg-primary hover:bg-primary-dark text-white font-medium rounded-lg transition-colors text-sm flex items-center justify-center gap-2 disabled:opacity-50 disabled:cursor-not-allowed"
            >
              {loading && <Loader2 size={16} className="animate-spin" />}
              {loading ? 'Registrando...' : 'Registrar'}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
