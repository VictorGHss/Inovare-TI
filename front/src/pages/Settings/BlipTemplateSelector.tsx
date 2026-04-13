import { useEffect, useState } from 'react';
import { ChevronDown, AlertCircle } from 'lucide-react';
import { toast } from 'react-toastify';

import { getBlipTemplates, updateAppointmentConfig, type BlipTemplate } from '../../services/appointmentService';

interface BlipTemplateSelectorProps {
  category: 'CONFIRMATION' | 'NUDGE_1' | 'NUDGE_FINAL';
  categoryLabel: string;
  currentTemplateId?: string;
  onTemplateChanged?: () => void;
}

export default function BlipTemplateSelector({
  category,
  categoryLabel,
  currentTemplateId,
  onTemplateChanged,
}: BlipTemplateSelectorProps) {
  const [templates, setTemplates] = useState<BlipTemplate[]>([]);
  const [selectedTemplateId, setSelectedTemplateId] = useState<string>(currentTemplateId || '');
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  // Carrega templates aprovados do Blip
  useEffect(() => {
    async function loadTemplates() {
      setLoading(true);
      setError(null);
      try {
        const data = await getBlipTemplates();
        setTemplates(data);
      } catch {
        setError('Erro ao carregar templates do Blip');
        toast.error('Não foi possível carregar os templates do Blip');
      } finally {
        setLoading(false);
      }
    }

    loadTemplates();
  }, []);

  async function handleTemplateChange(newTemplateId: string) {
    setSelectedTemplateId(newTemplateId);
    setSaving(true);

    try {
      const result = await updateAppointmentConfig(category, newTemplateId);
      
      if (result.status === 'success') {
        toast.success(`Template atualizado com sucesso para ${categoryLabel}`);
        if (onTemplateChanged) {
          onTemplateChanged();
        }
      } else {
        toast.error(result.message || 'Erro ao atualizar template');
        setSelectedTemplateId(currentTemplateId || '');
      }
    } catch {
      toast.error('Falha ao salvar configuração de template');
      setSelectedTemplateId(currentTemplateId || '');
    } finally {
      setSaving(false);
    }
  }

  if (loading) {
    return (
      <div className="py-3 px-4 bg-slate-50 rounded-lg animate-pulse">
        <div className="h-4 bg-slate-200 rounded w-1/3"></div>
      </div>
    );
  }

  if (error) {
    return (
      <div className="py-3 px-4 bg-red-50 border border-red-200 rounded-lg flex gap-2">
        <AlertCircle size={16} className="text-red-600 flex-shrink-0 mt-0.5" />
        <span className="text-sm text-red-700">{error}</span>
      </div>
    );
  }

  return (
    <div className="space-y-2">
      <label className="block text-sm font-medium text-slate-700">{categoryLabel}</label>
      <div className="relative">
        <select
          value={selectedTemplateId}
          onChange={(e) => handleTemplateChange(e.target.value)}
          disabled={saving}
          className="w-full appearance-none rounded-lg border border-slate-300 bg-white px-4 py-2.5 pr-10 text-sm transition-colors focus:border-brand-primary focus:outline-none focus:ring-1 focus:ring-brand-primary disabled:cursor-not-allowed disabled:opacity-60"
        >
          <option value="">→ Selecione um template</option>
          {templates.map((template) => (
            <option key={template.id} value={template.id}>
              {template.name}
            </option>
          ))}
        </select>
        <ChevronDown 
          size={16} 
          className="pointer-events-none absolute right-3 top-3.5 text-slate-600"
        />
      </div>
      {saving && (
        <div className="text-xs text-slate-500">Salvando...</div>
      )}
    </div>
  );
}
