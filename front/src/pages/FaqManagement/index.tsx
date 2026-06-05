import { useEffect, useState, useMemo } from 'react';
import { PlusCircle, Trash2, Search, HelpCircle, X, Terminal, Pencil } from 'lucide-react';
import { toast } from 'react-toastify';
import axios from 'axios';
import { getFaqs, createFaq, deleteFaq, updateFaq } from '../../services/faqService';
import type { FaqTi } from '../../types/models';
import { useAuth } from '../../contexts/AuthContext';

export default function FaqManagement() {
  const { user } = useAuth();
  const isAuthorized = user?.role === 'ADMIN' || user?.role === 'TECHNICIAN';

  const [faqs, setFaqs] = useState<FaqTi[]>([]);
  const [loading, setLoading] = useState(true);
  const [showForm, setShowForm] = useState(false);
  const [submitting, setSubmitting] = useState(false);

  // Form Fields
  const [palavraChave, setPalavraChave] = useState('');
  const [pergunta, setPergunta] = useState('');
  const [resposta, setResposta] = useState('');

  // Editing control state
  const [isEditing, setIsEditing] = useState(false);
  const [currentFaqId, setCurrentFaqId] = useState<string | null>(null);

  // Search & Filters
  const [searchQuery, setSearchQuery] = useState('');

  // Deletion Confirmation Modal/Popup state
  const [faqToDelete, setFaqToDelete] = useState<FaqTi | null>(null);

  useEffect(() => {
    if (isAuthorized) {
      loadFaqs();
    }
  }, [isAuthorized]);

  async function loadFaqs() {
    setLoading(true);
    try {
      const data = await getFaqs();
      setFaqs(data);
    } catch {
      toast.error('Erro ao carregar os FAQs da TI.');
      setFaqs([]);
    } finally {
      setLoading(false);
    }
  }

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();

    if (!isAuthorized) {
      toast.error('Você não tem permissão para realizar esta ação.');
      return;
    }

    if (!palavraChave.trim()) {
      toast.error('A palavra-chave é obrigatória.');
      return;
    }
    if (!pergunta.trim()) {
      toast.error('A pergunta é obrigatória.');
      return;
    }
    if (!resposta.trim()) {
      toast.error('A resposta é obrigatória.');
      return;
    }

    setSubmitting(true);
    try {
      if (isEditing && currentFaqId) {
        await updateFaq(currentFaqId, {
          palavraChave: palavraChave.trim(),
          pergunta: pergunta.trim(),
          resposta: resposta.trim()
        });
        toast.success('FAQ atualizado com sucesso!');
        handleCancel();
        loadFaqs();
      } else {
        await createFaq({
          palavraChave: palavraChave.trim(),
          pergunta: pergunta.trim(),
          resposta: resposta.trim()
        });
        toast.success('FAQ cadastrado com sucesso!');
        setPalavraChave('');
        setPergunta('');
        setResposta('');
        setShowForm(false);
        loadFaqs();
      }
    } catch (error: any) {
      if (axios.isAxiosError(error) && error.response?.status === 409) {
        toast.error('Erro: Já existe um FAQ cadastrado com esta palavra-chave!');
      } else {
        toast.error('Erro ao salvar FAQ. Tente novamente.');
      }
    } finally {
      setSubmitting(false);
    }
  }

  function handleEditClick(faq: FaqTi) {
    if (!isAuthorized) {
      toast.error('Você não tem permissão para editar FAQs.');
      return;
    }
    setIsEditing(true);
    setCurrentFaqId(faq.id || null);
    setPalavraChave(faq.palavraChave);
    setPergunta(faq.pergunta);
    setResposta(faq.resposta);
    setShowForm(true);
    
    // Scroll to form smoothly
    setTimeout(() => {
      document.getElementById('faq-form')?.scrollIntoView({ behavior: 'smooth' });
    }, 100);
  }

  function handleCancel() {
    setPalavraChave('');
    setPergunta('');
    setResposta('');
    setIsEditing(false);
    setCurrentFaqId(null);
    setShowForm(false);
  }

  async function handleDeleteConfirm() {
    if (!isAuthorized) {
      toast.error('Você não tem permissão para excluir FAQs.');
      return;
    }
    if (!faqToDelete || !faqToDelete.id) return;

    try {
      await deleteFaq(faqToDelete.id);
      toast.success('FAQ excluído com sucesso!');
      setFaqToDelete(null);
      loadFaqs();
    } catch {
      toast.error('Erro ao excluir o FAQ.');
    }
  }

  const filteredFaqs = useMemo(() => {
    if (!searchQuery.trim()) return faqs;
    const query = searchQuery.toLowerCase();
    return faqs.filter(
      (f) =>
        f.palavraChave.toLowerCase().includes(query) ||
        f.pergunta.toLowerCase().includes(query) ||
        f.resposta.toLowerCase().includes(query)
    );
  }, [faqs, searchQuery]);

  if (!isAuthorized) {
    return (
      <div className="bg-white rounded-2xl border border-slate-200 shadow-sm p-12 text-center">
        <div className="w-12 h-12 bg-slate-100 rounded-full flex items-center justify-center mx-auto mb-4">
          <HelpCircle size={22} className="text-slate-400" />
        </div>
        <p className="text-sm font-medium text-slate-700">Acesso Restrito</p>
        <p className="text-xs text-slate-400 mt-1">Você não possui permissão para acessar o gerenciamento de FAQs.</p>
      </div>
    );
  }

  const inputClassName =
    'w-full rounded-2xl border border-slate-200 bg-white px-3.5 py-2.5 text-sm text-slate-800 shadow-sm placeholder-slate-400 focus:outline-none focus:ring-2 focus:ring-[#feb56c]/60 focus:border-[#feb56c] transition-all';

  return (
    <div className="space-y-6">
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
        <div>
          <h2 className="text-base font-bold text-slate-900">FAQ da TI</h2>
          <p className="text-xs text-slate-500">Configure as palavras-chave (gatilhos do Discord), perguntas e respostas automáticas que o bot usa no comando /ajuda.</p>
        </div>
        <div>
          <button
            onClick={() => {
              if (showForm) {
                handleCancel();
              } else {
                setShowForm(true);
              }
            }}
            className="flex items-center gap-2 rounded-2xl bg-[#feb56c] px-5 py-2.5 text-sm font-bold text-slate-900 shadow-sm transition-all hover:bg-[#f6a455] hover:scale-[1.02] active:scale-[0.98]"
          >
            <PlusCircle size={17} />
            {showForm ? (isEditing ? 'Cancelar Edição' : 'Fechar Formulário') : 'Novo FAQ'}
          </button>
        </div>
      </div>

      {/* Formulário de cadastro / edição */}
      {showForm && (
        <form
          id="faq-form"
          onSubmit={handleSubmit}
          className="mb-6 rounded-2xl border border-[#feb56c]/35 bg-white p-6 shadow-sm animate-fadeIn"
        >
          <div className="flex items-center justify-between border-b border-slate-100 pb-4 mb-4">
            <h2 className="text-sm font-bold text-slate-755 flex items-center gap-2">
              <HelpCircle size={18} className="text-[#feb56c]" />
              {isEditing ? 'Editar Pergunta Frequente (FAQ)' : 'Cadastrar Nova Pergunta Frequente (FAQ)'}
            </h2>
            <button
              type="button"
              onClick={handleCancel}
              className="text-slate-400 hover:text-slate-600 transition-colors"
            >
              <X size={18} />
            </button>
          </div>

          <div className="grid grid-cols-1 gap-4">
            <div>
              <label htmlFor="palavraChave" className="block text-xs font-semibold text-slate-500 mb-1.5">
                Palavra-chave (Gatilho do Bot no Discord)
              </label>
              <div className="relative">
                <span className="absolute inset-y-0 left-0 pl-3.5 flex items-center pointer-events-none text-slate-400">
                  <Terminal size={15} />
                </span>
                <input
                  id="palavraChave"
                  type="text"
                  value={palavraChave}
                  onChange={(e) => setPalavraChave(e.target.value)}
                  placeholder="Ex: feegow, internet, impressora (letras minúsculas, sem espaços)"
                  maxLength={80}
                  className={`${inputClassName} pl-10`}
                  disabled={submitting}
                  required
                />
              </div>
            </div>

            <div>
              <label htmlFor="pergunta" className="block text-xs font-semibold text-slate-500 mb-1.5">
                Pergunta Completa
              </label>
              <input
                id="pergunta"
                type="text"
                value={pergunta}
                onChange={(e) => setPergunta(e.target.value)}
                placeholder="Ex: Como faço para resetar o cache do Feegow?"
                className={inputClassName}
                disabled={submitting}
                required
              />
            </div>

            <div>
              <label htmlFor="resposta" className="block text-xs font-semibold text-slate-500 mb-1.5">
                Resposta Rápida (Exibida pelo Bot)
              </label>
              <textarea
                id="resposta"
                value={resposta}
                onChange={(e) => setResposta(e.target.value)}
                placeholder="Escreva a instrução detalhada e clara que o usuário receberá..."
                rows={4}
                className={`${inputClassName} resize-y`}
                disabled={submitting}
                required
              />
            </div>
          </div>

          <div className="mt-5 flex justify-end gap-2 border-t border-slate-100 pt-4">
            {isEditing && (
              <button
                type="button"
                onClick={handleCancel}
                className="rounded-2xl border border-slate-200 bg-white px-5 py-2.5 text-sm font-bold text-slate-600 transition-colors hover:bg-slate-50"
                disabled={submitting}
              >
                Cancelar
              </button>
            )}
            <button
              type="submit"
              disabled={submitting}
              className="rounded-2xl bg-[#feb56c] px-6 py-2.5 text-sm font-bold text-slate-900 transition-colors hover:bg-[#f6a455] disabled:opacity-50 disabled:cursor-not-allowed shadow-sm"
            >
              {submitting ? 'Salvando...' : (isEditing ? 'Salvar Alterações' : 'Cadastrar FAQ')}
            </button>
          </div>
        </form>
      )}

      {/* Barra de Filtros */}
      <div className="mb-6 flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
        <div className="relative flex-1 max-w-md">
          <span className="absolute inset-y-0 left-0 pl-3.5 flex items-center pointer-events-none text-slate-400">
            <Search size={16} />
          </span>
          <input
            type="text"
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            placeholder="Pesquisar por palavra-chave, pergunta ou resposta..."
            className={`${inputClassName} pl-10`}
          />
        </div>
      </div>

      {/* Grid/Tabela de listagem */}
      {loading ? (
        <div className="flex h-48 items-center justify-center rounded-2xl border border-slate-200 bg-white shadow-sm">
          <span className="text-sm text-slate-500 animate-pulse">Carregando perguntas frequentes...</span>
        </div>
      ) : filteredFaqs.length === 0 ? (
        <div className="flex h-48 flex-col items-center justify-center gap-2 rounded-2xl border border-slate-200 bg-white shadow-sm">
          <HelpCircle size={32} className="text-slate-300 animate-bounce" />
          <span className="text-sm font-medium text-slate-500">Nenhum FAQ encontrado.</span>
          <span className="text-xs text-slate-400">Crie uma nova dúvida frequente para alimentar o bot.</span>
        </div>
      ) : (
        <div className="overflow-x-auto rounded-2xl border border-[#feb56c]/35 bg-white shadow-sm">
          <table className="w-full border-collapse text-left text-sm text-slate-500">
            <thead className="bg-slate-50 text-xs font-bold uppercase text-slate-600 border-b border-slate-100">
              <tr>
                <th scope="col" className="px-6 py-4">Palavra-chave</th>
                <th scope="col" className="px-6 py-4">Gatilho no Discord</th>
                <th scope="col" className="px-6 py-4">Pergunta</th>
                <th scope="col" className="px-6 py-4">Resposta</th>
                <th scope="col" className="px-6 py-4 text-right">Ações</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100 font-medium text-slate-700">
              {filteredFaqs.map((faq) => (
                <tr key={faq.id} className="hover:bg-slate-50 transition-colors">
                  <td className="px-6 py-4 whitespace-nowrap">
                    <span className="inline-flex items-center gap-1.5 rounded-full bg-slate-100 px-2.5 py-0.5 text-xs font-semibold text-slate-800">
                      <Terminal size={11} />
                      {faq.palavraChave}
                    </span>
                  </td>
                  <td className="px-6 py-4 whitespace-nowrap">
                    <div className="inline-flex items-center gap-1.5 rounded-lg bg-indigo-50 border border-indigo-100 px-2.5 py-1 text-xs font-mono text-indigo-600 shadow-sm">
                      <span className="text-indigo-400">/ajuda</span>
                      <span className="font-bold text-indigo-700 bg-indigo-100/50 px-1.5 py-0.5 rounded">{faq.palavraChave}</span>
                    </div>
                  </td>
                  <td className="px-6 py-4 max-w-xs truncate">{faq.pergunta}</td>
                  <td className="px-6 py-4 max-w-sm truncate text-slate-500 font-normal">{faq.resposta}</td>
                  <td className="px-6 py-4 text-right whitespace-nowrap">
                    <div className="flex items-center justify-end gap-2">
                      <button
                        onClick={() => handleEditClick(faq)}
                        className="inline-flex h-8 w-8 items-center justify-center rounded-lg text-slate-400 transition-colors hover:bg-slate-100 hover:text-[#feb56c]"
                        title="Editar FAQ"
                      >
                        <Pencil size={16} />
                      </button>
                      <button
                        onClick={() => setFaqToDelete(faq)}
                        className="inline-flex h-8 w-8 items-center justify-center rounded-lg text-slate-400 transition-colors hover:bg-red-50 hover:text-red-600"
                        title="Excluir FAQ"
                      >
                        <Trash2 size={16} />
                      </button>
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {/* Confirmação de exclusão */}
      {faqToDelete && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/40 backdrop-blur-sm p-4 animate-fadeIn">
          <div className="w-full max-w-md rounded-2xl border border-slate-100 bg-white p-6 shadow-xl">
            <h3 className="text-lg font-bold text-slate-800 mb-2">Excluir FAQ?</h3>
            <p className="text-sm text-slate-500 mb-6">
              Tem certeza que deseja excluir o FAQ com o gatilho <span className="font-semibold text-slate-700">"{faqToDelete.palavraChave}"</span>? Esta ação não pode ser desfeita.
            </p>
            <div className="flex justify-end gap-3">
              <button
                onClick={() => setFaqToDelete(null)}
                className="rounded-2xl border border-slate-200 bg-white px-4 py-2 text-sm font-bold text-slate-650 transition-colors hover:bg-slate-50"
              >
                Cancelar
              </button>
              <button
                onClick={handleDeleteConfirm}
                className="rounded-2xl bg-red-600 px-5 py-2 text-sm font-bold text-white transition-colors hover:bg-red-700 shadow-sm"
              >
                Confirmar Exclusão
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
