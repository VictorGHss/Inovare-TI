import { useEffect, useState } from 'react';
import { Loader2, Plus, Trash2, Edit2, Check, AlertCircle, HelpCircle } from 'lucide-react';
import { toast } from 'react-toastify';

import {
  getTicketTags,
  createTicketTag,
  updateTicketTag,
  toggleTicketTagActive,
  deleteTicketTag,
} from '../../services/ticketService';
import type { TicketTag } from '../../types/models';

export default function TagsSection() {
  const [tags, setTags] = useState<TicketTag[]>([]);
  const [loading, setLoading] = useState(true);
  const [submitting, setSubmitting] = useState(false);

  // Form states
  const [editingId, setEditingId] = useState<string | null>(null);
  const [name, setName] = useState('');
  const [color, setColor] = useState('#2563eb');
  const [defaultResolution, setDefaultResolution] = useState('');
  const [active, setActive] = useState(true);

  const loadTags = async () => {
    try {
      setLoading(true);
      const data = await getTicketTags();
      setTags(Array.isArray(data) ? data : []);
    } catch {
      toast.error('Falha ao carregar as tags do sistema.');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    void loadTags();
  }, []);

  const handleEditClick = (tag: TicketTag) => {
    setEditingId(tag.id);
    setName(tag.name);
    setColor(tag.color.startsWith('#') ? tag.color : `#${tag.color}`);
    setDefaultResolution(tag.defaultResolution || '');
    setActive(tag.active);
  };

  const handleCancel = () => {
    setEditingId(null);
    setName('');
    setColor('#2563eb');
    setDefaultResolution('');
    setActive(true);
  };

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    if (!name.trim()) {
      toast.error('Por favor, informe o nome da tag.');
      return;
    }
    if (!color) {
      toast.error('Por favor, escolha uma cor para a tag.');
      return;
    }

    // Certifique-se de que a tag comece com '#' caso seja informada sem
    let formattedName = name.trim();
    if (!formattedName.startsWith('#')) {
      formattedName = `#${formattedName}`;
    }

    setSubmitting(true);
    try {
      if (editingId) {
        // Modo Edição
        const updated = await updateTicketTag(editingId, {
          name: formattedName,
          color,
          active,
          defaultResolution: defaultResolution.trim() || null,
        });
        setTags((prev) => prev.map((t) => (t.id === editingId ? updated : t)));
        toast.success('Tag atualizada com sucesso.');
      } else {
        // Modo Criação
        const created = await createTicketTag({
          name: formattedName,
          color,
          active: true,
          defaultResolution: defaultResolution.trim() || null,
        });
        setTags((prev) => [...prev, created]);
        toast.success('Tag criada com sucesso.');
      }
      handleCancel();
    } catch (err) {
      const error = err as { response?: { data?: { detail?: string; error?: string } } };
      const msg = error.response?.data?.detail || error.response?.data?.error || 'Falha ao salvar a tag.';
      toast.error(msg);
    } finally {
      setSubmitting(false);
    }
  }

  async function handleToggleActive(id: string) {
    try {
      const updated = await toggleTicketTagActive(id);
      setTags((prev) => prev.map((t) => (t.id === id ? updated : t)));
      toast.success(`Tag ${updated.active ? 'ativada' : 'inativada'} com sucesso.`);
    } catch {
      toast.error('Erro ao alternar o status da tag.');
    }
  }

  async function handleDelete(id: string) {
    if (!confirm('Tem certeza de que deseja excluir permanentemente esta tag? Esta ação removerá a tag de todos os chamados existentes.')) {
      return;
    }

    try {
      await deleteTicketTag(id);
      setTags((prev) => prev.filter((t) => t.id !== id));
      toast.success('Tag excluída com sucesso.');
      if (editingId === id) {
        handleCancel();
      }
    } catch (err) {
      toast.error('Erro ao excluir tag.');
    }
  }

  return (
    <div className="bg-white rounded-2xl border border-slate-200 overflow-hidden shadow-sm flex flex-col">
      <header className="px-6 py-5 border-b border-slate-100 bg-slate-50/50">
        <h2 className="text-base font-bold text-slate-900 flex items-center gap-2">
          Gerenciamento de Tags & Macros Contextuais
        </h2>
        <p className="text-xs text-slate-500 mt-0.5">
          Cadastre tags customizadas com cores personalizadas e textos de resoluções rápidas (macros) para acelerar o fechamento de chamados pela equipe técnica.
        </p>
      </header>

      <div className="grid grid-cols-1 lg:grid-cols-12 divide-y lg:divide-y-0 lg:divide-x divide-slate-100">
        {/* Formulário lateral */}
        <div className="lg:col-span-4 p-6">
          <h3 className="text-sm font-bold text-slate-800 mb-4">
            {editingId ? 'Editar Tag' : 'Cadastrar Nova Tag'}
          </h3>
          <form onSubmit={handleSubmit} className="space-y-4">
            <div>
              <label className="block text-xs font-semibold text-slate-600 mb-1.5">
                Nome da Tag
              </label>
              <input
                type="text"
                value={name}
                onChange={(e) => setName(e.target.value)}
                placeholder="Ex: #🚨ParadaCrítica, #RedeLocal"
                className="w-full rounded-xl border border-slate-200 bg-white px-3.5 py-2.5 text-sm text-slate-800 shadow-sm placeholder-slate-400 focus:outline-none focus:ring-2 focus:ring-brand-primary/30 focus:border-brand-primary transition-all"
                disabled={submitting}
              />
            </div>

            <div>
              <label className="block text-xs font-semibold text-slate-600 mb-1.5">
                Cor Visual
              </label>
              <div className="flex items-center gap-3">
                <input
                  type="color"
                  value={color}
                  onChange={(e) => setColor(e.target.value)}
                  className="w-12 h-10 cursor-pointer rounded-xl border border-slate-200 bg-white p-1 focus:outline-none shrink-0"
                  disabled={submitting}
                />
                <input
                  type="text"
                  value={color}
                  onChange={(e) => setColor(e.target.value)}
                  placeholder="#000000"
                  className="w-full rounded-xl border border-slate-200 bg-white px-3.5 py-2.5 text-sm text-slate-800 shadow-sm placeholder-slate-400 focus:outline-none focus:ring-2 focus:ring-brand-primary/30 focus:border-brand-primary transition-all"
                  disabled={submitting}
                />
              </div>
            </div>

            <div>
              <label className="block text-xs font-semibold text-slate-600 mb-1.5 flex items-center gap-1.5">
                Resolução Padrão (Macro)
                <span className="group relative">
                  <HelpCircle size={13} className="text-slate-400 cursor-pointer hover:text-slate-600" />
                  <span className="absolute bottom-full left-1/2 -translate-x-1/2 mb-1.5 hidden group-hover:block w-48 rounded-lg bg-slate-900 p-2 text-[10px] text-white leading-relaxed shadow-lg z-20">
                    O texto inserido aqui ficará disponível como ação de um clique para o técnico fechar o chamado instantaneamente.
                  </span>
                </span>
              </label>
              <textarea
                value={defaultResolution}
                onChange={(e) => setDefaultResolution(e.target.value)}
                placeholder="Descreva a solução padrão para chamados com esta tag..."
                className="w-full resize-none rounded-xl border border-slate-200 bg-white px-3.5 py-2.5 text-sm text-slate-800 shadow-sm placeholder-slate-400 focus:outline-none focus:ring-2 focus:ring-brand-primary/30 focus:border-brand-primary transition-all"
                rows={5}
                disabled={submitting}
              />
            </div>

            {editingId && (
              <div className="flex items-center gap-2">
                <input
                  type="checkbox"
                  id="active"
                  checked={active}
                  onChange={(e) => setActive(e.target.checked)}
                  className="h-4 w-4 cursor-pointer rounded border-slate-350 text-brand-primary focus:ring-brand-primary"
                  disabled={submitting}
                />
                <label htmlFor="active" className="cursor-pointer text-sm font-semibold text-slate-700">
                  Tag Ativa (visível para novos chamados)
                </label>
              </div>
            )}

            <div className="flex items-center justify-end gap-2 pt-2">
              {editingId && (
                <button
                  type="button"
                  onClick={handleCancel}
                  className="px-4 py-2 rounded-xl text-xs font-bold border border-slate-200 bg-white text-slate-700 hover:bg-slate-50 transition-colors"
                  disabled={submitting}
                >
                  Cancelar
                </button>
              )}
              <button
                type="submit"
                disabled={submitting}
                className="inline-flex items-center gap-1.5 rounded-xl bg-brand-primary px-4 py-2 text-xs font-bold text-white transition-colors hover:bg-brand-primary-dark disabled:opacity-50"
              >
                {submitting ? (
                  <Loader2 size={13} className="animate-spin" />
                ) : editingId ? (
                  <Check size={13} />
                ) : (
                  <Plus size={13} />
                )}
                {editingId ? 'Salvar Edição' : 'Adicionar Tag'}
              </button>
            </div>
          </form>
        </div>

        {/* Tabela de listagem */}
        <div className="lg:col-span-8 p-6">
          <h3 className="text-sm font-bold text-slate-800 mb-4">
            Tags Existentes ({tags.length})
          </h3>

          {loading ? (
            <div className="flex items-center justify-center py-12">
              <Loader2 size={24} className="animate-spin text-brand-primary" />
            </div>
          ) : tags.length === 0 ? (
            <div className="rounded-xl border border-dashed border-slate-200 p-8 text-center bg-slate-50/50">
              <AlertCircle size={24} className="text-slate-400 mx-auto mb-2" />
              <p className="text-xs text-slate-500 font-semibold">Nenhuma tag cadastrada ainda.</p>
              <p className="text-[10px] text-slate-400 mt-0.5">Use o formulário ao lado para cadastrar a primeira tag do sistema.</p>
            </div>
          ) : (
            <div className="overflow-x-auto rounded-xl border border-slate-100 shadow-sm">
              <table className="min-w-full divide-y divide-slate-100 text-left text-xs bg-white">
                <thead className="bg-slate-50 text-slate-500 font-bold uppercase tracking-wider">
                  <tr>
                    <th className="px-4 py-3">Tag</th>
                    <th className="px-4 py-3">Macro (Resolução Padrão)</th>
                    <th className="px-4 py-3">Status</th>
                    <th className="px-4 py-3 text-right">Ações</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-100 font-medium text-slate-700">
                  {tags.map((tag) => (
                    <tr key={tag.id} className="hover:bg-slate-50/50 transition-colors">
                      <td className="px-4 py-3">
                        <span
                          className="inline-flex items-center rounded-full px-2.5 py-0.5 text-[11px] font-bold border shadow-sm"
                          style={{
                            backgroundColor: `${tag.color}15`,
                            color: tag.color,
                            borderColor: `${tag.color}35`,
                          }}
                        >
                          {tag.name}
                        </span>
                      </td>
                      <td className="px-4 py-3 max-w-[200px] truncate">
                        {tag.defaultResolution ? (
                          <span className="text-slate-600" title={tag.defaultResolution}>
                            {tag.defaultResolution}
                          </span>
                        ) : (
                          <span className="text-slate-400 italic font-normal">Nenhuma macro</span>
                        )}
                      </td>
                      <td className="px-4 py-3">
                        <button
                          type="button"
                          onClick={() => void handleToggleActive(tag.id)}
                          className={`inline-flex items-center rounded-full px-2.5 py-0.5 font-bold transition-all shadow-sm ${
                            tag.active
                              ? 'bg-emerald-50 text-emerald-700 hover:bg-emerald-100 border border-emerald-250'
                              : 'bg-slate-100 text-slate-500 hover:bg-slate-200 border border-slate-200'
                          }`}
                        >
                          {tag.active ? 'Ativo' : 'Inativo'}
                        </button>
                      </td>
                      <td className="px-4 py-3 text-right">
                        <div className="inline-flex items-center gap-1.5">
                          <button
                            type="button"
                            onClick={() => handleEditClick(tag)}
                            className="p-1.5 rounded-lg border border-slate-200 text-slate-500 hover:text-slate-800 bg-white hover:bg-slate-50 shadow-sm transition-all"
                            title="Editar"
                          >
                            <Edit2 size={13} />
                          </button>
                          <button
                            type="button"
                            onClick={() => void handleDelete(tag.id)}
                            className="p-1.5 rounded-lg border border-red-100 text-red-500 hover:text-red-700 bg-white hover:bg-red-50 shadow-sm transition-all"
                            title="Excluir"
                          >
                            <Trash2 size={13} />
                          </button>
                        </div>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
