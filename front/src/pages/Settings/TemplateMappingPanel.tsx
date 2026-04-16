import { useEffect, useMemo, useState } from 'react';
import { AlertCircle, ChevronDown, Save } from 'lucide-react';
import { toast } from 'react-toastify';

import {
  getBlipTemplates,
  getAppointmentTemplateMappings,
  getFeegowFields,
  saveAppointmentTemplateMappings,
  type BlipTemplate,
} from '../../services/appointmentService';
import { getApiErrorMessage } from '../../lib/apiError';

const placeholderLabels = ['{{1}}', '{{2}}', '{{3}}', '{{4}}'];

const inputClassName =
  'w-full rounded-xl border border-slate-200 bg-white px-3.5 py-2.5 text-sm text-slate-800 shadow-sm placeholder-slate-400 focus:outline-none focus:ring-2 focus:ring-brand-primary/30 focus:border-brand-primary transition-all';

export default function TemplateMappingPanel() {
  const [templates, setTemplates] = useState<BlipTemplate[]>([]);
  const [feegowFields, setFeegowFields] = useState<string[]>([]);
  const [selectedTemplateId, setSelectedTemplateId] = useState('');
  const [drafts, setDrafts] = useState<Record<string, string[]>>({});
  const [loading, setLoading] = useState(true);
  const [loadingMappings, setLoadingMappings] = useState(false);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let mounted = true;

    async function loadData() {
      setLoading(true);
      setError(null);

      try {
        const [templateData, fieldData] = await Promise.all([getBlipTemplates(), getFeegowFields()]);

        if (!mounted) {
          return;
        }

        const safeTemplates = Array.isArray(templateData) ? templateData : [];
        const safeFields = Array.isArray(fieldData) ? fieldData : [];

        setTemplates(safeTemplates);
        setFeegowFields(safeFields);

        if (safeTemplates.length > 0) {
          setSelectedTemplateId((current) => current || safeTemplates[0].id);
        }
      } catch (loadError) {
        const message = getApiErrorMessage(loadError, 'Erro ao carregar templates e campos do Feegow.');
        setError(message);
        toast.error(message);
      } finally {
        if (mounted) {
          setLoading(false);
        }
      }
    }

    loadData();

    return () => {
      mounted = false;
    };
  }, []);

  useEffect(() => {
    if (!selectedTemplateId) {
      return;
    }

    let active = true;

    async function loadSavedMappings() {
      setLoadingMappings(true);

      try {
        const savedMappings = await getAppointmentTemplateMappings(selectedTemplateId);
        if (!active) {
          return;
        }

        const nextDraft = ['', '', '', ''];
        savedMappings.forEach((mapping) => {
          const position = Number(mapping.placeholderIndex) - 1;
          if (position >= 0 && position < nextDraft.length) {
            nextDraft[position] = mapping.feegowFieldName ?? '';
          }
        });

        setDrafts((current) => ({
          ...current,
          [selectedTemplateId]: nextDraft,
        }));
      } catch (loadError) {
        toast.error(getApiErrorMessage(loadError, 'Erro ao carregar os mapeamentos do template selecionado.'));
      } finally {
        if (active) {
          setLoadingMappings(false);
        }
      }
    }

    loadSavedMappings();

    return () => {
      active = false;
    };
  }, [selectedTemplateId]);

  const currentDraft = useMemo(() => {
    return drafts[selectedTemplateId] ?? ['', '', '', ''];
  }, [drafts, selectedTemplateId]);

  function handleTemplateChange(templateId: string) {
    setSelectedTemplateId(templateId);
  }

  function updateMapping(position: number, fieldName: string) {
    setDrafts((current) => {
      const nextDraft = [...(current[selectedTemplateId] ?? ['', '', '', ''])];
      nextDraft[position] = fieldName;
      return {
        ...current,
        [selectedTemplateId]: nextDraft,
      };
    });
  }

  const selectedTemplate = templates.find((template) => template.id === selectedTemplateId) ?? null;
  const canSave = Boolean(selectedTemplateId) && currentDraft.some((field) => field.trim().length > 0) && !saving && !loadingMappings;

  async function handleSave() {
    if (!selectedTemplateId) {
      toast.error('Selecione um template antes de salvar o mapeamento.');
      return;
    }

    if (!currentDraft.some((field) => field.trim().length > 0)) {
      toast.error('Preencha pelo menos um campo do mapeamento.');
      return;
    }

    setSaving(true);
    try {
      const response = await saveAppointmentTemplateMappings({
        templateName: selectedTemplateId,
        mappings: currentDraft
          .map((fieldName, index) => ({
            placeholderIndex: index + 1,
            feegowFieldName: fieldName.trim(),
          }))
          .filter((mapping) => mapping.feegowFieldName.length > 0),
      });

      if (response.status === 'success') {
        toast.success(`Mapeamento salvo para ${selectedTemplate?.name ?? selectedTemplateId}.`);
        setDrafts((current) => ({
          ...current,
          [selectedTemplateId]: [...currentDraft],
        }));
        return;
      }

      toast.error(response.message || 'Falha ao salvar o mapeamento.');
    } catch (saveError) {
      toast.error(getApiErrorMessage(saveError, 'Falha ao salvar o mapeamento.'));
    } finally {
      setSaving(false);
    }
  }

  if (loading) {
    return (
      <div className="bg-white rounded-2xl border border-slate-200 overflow-hidden">
        <div className="px-6 py-5 space-y-3 animate-pulse">
          <div className="h-4 w-40 rounded bg-slate-200" />
          <div className="h-10 rounded-xl bg-slate-100" />
          <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
            <div className="h-20 rounded-xl bg-slate-100" />
            <div className="h-20 rounded-xl bg-slate-100" />
          </div>
        </div>
      </div>
    );
  }

  if (error) {
    return (
      <div className="bg-white rounded-2xl border border-red-200 overflow-hidden">
        <div className="px-6 py-5 flex gap-3 items-start">
          <AlertCircle size={18} className="text-red-500 mt-0.5 shrink-0" />
          <div>
            <h2 className="text-sm font-semibold text-slate-900">Mapeamento dinâmico de templates</h2>
            <p className="text-sm text-red-700 mt-1">{error}</p>
          </div>
        </div>
      </div>
    );
  }

  if (!templates.length) {
    return (
      <div className="bg-white rounded-2xl border border-slate-200 overflow-hidden">
        <div className="px-6 py-5">
          <h2 className="text-sm font-semibold text-slate-900">Mapeamento dinâmico de templates</h2>
          <p className="text-sm text-slate-500 mt-1">Nenhum template disponível no momento.</p>
        </div>
      </div>
    );
  }

  return (
    <div className="bg-white rounded-2xl border border-slate-200 overflow-hidden">
      <div className="px-6 py-4 border-b border-slate-100 flex items-center justify-between gap-3">
        <div>
          <h2 className="text-sm font-semibold text-slate-900">Mapeamento dinâmico de templates</h2>
          <p className="text-xs text-slate-500 mt-0.5">Associe os placeholders do Blip aos campos do Feegow por template.</p>
        </div>
        <button
          type="button"
          onClick={() => {
            setLoading(true);
            Promise.all([getBlipTemplates(), getFeegowFields()])
              .then(([templateData, fieldData]) => {
                const safeTemplates = Array.isArray(templateData) ? templateData : [];
                const safeFields = Array.isArray(fieldData) ? fieldData : [];
                setTemplates(safeTemplates);
                setFeegowFields(safeFields);
                if (!selectedTemplateId && safeTemplates.length > 0) {
                  setSelectedTemplateId(safeTemplates[0].id);
                }
              })
              .catch((refreshError) => {
                toast.error(getApiErrorMessage(refreshError, 'Erro ao atualizar templates e campos.'));
              })
              .finally(() => setLoading(false));
          }}
          className="text-xs font-medium text-brand-primary hover:text-brand-primary-dark disabled:opacity-40"
        >
          Recarregar
        </button>
      </div>

      <div className="px-6 py-5 space-y-5">
        <div>
          <label className="block text-xs font-semibold uppercase tracking-wide text-slate-500 mb-2">Template</label>
          <div className="relative">
            <select
              value={selectedTemplateId}
              onChange={(event) => handleTemplateChange(event.target.value)}
              className={inputClassName}
            >
              <option value="">Selecione um template</option>
              {templates.map((template) => (
                <option key={template.id} value={template.id}>
                  {template.name}
                </option>
              ))}
            </select>
            <ChevronDown size={16} className="pointer-events-none absolute right-3 top-3.5 text-slate-600" />
          </div>
        </div>

        <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
          {placeholderLabels.map((placeholderLabel, index) => {
            const selectedField = currentDraft[index] ?? '';

            return (
              <div key={placeholderLabel} className="rounded-2xl border border-slate-200 bg-slate-50 p-4">
                <label className="block text-xs font-semibold uppercase tracking-wide text-slate-500 mb-2">
                  Placeholder {placeholderLabel}
                </label>
                <div className="relative">
                  <select
                    value={selectedField}
                    onChange={(event) => updateMapping(index, event.target.value)}
                    className={inputClassName}
                  >
                    <option value="">Selecione um campo do Feegow</option>
                    {feegowFields.map((fieldName) => (
                      <option key={fieldName} value={fieldName}>
                        {fieldName}
                      </option>
                    ))}
                  </select>
                  <ChevronDown size={16} className="pointer-events-none absolute right-3 top-3.5 text-slate-600" />
                </div>
              </div>
            );
          })}
        </div>

        <div className="flex items-center justify-between gap-3 pt-2">
          <p className="text-xs text-slate-500">
            {feegowFields.length} campos disponíveis no Feegow para preenchimento automático.
          </p>
          <button
            type="button"
            onClick={handleSave}
            disabled={!canSave}
            className="inline-flex items-center gap-2 rounded-xl bg-brand-primary px-4 py-2.5 text-sm font-semibold text-white transition-colors hover:bg-brand-primary-dark disabled:cursor-not-allowed disabled:opacity-40"
          >
            <Save size={14} />
            {saving ? 'Salvando...' : loadingMappings ? 'Carregando mapeamento...' : 'Salvar mapeamento'}
          </button>
        </div>
      </div>
    </div>
  );
}