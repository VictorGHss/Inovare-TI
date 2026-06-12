// Página de listagem, cadastro e gestão de setores
import { useEffect, useState, useMemo } from 'react';
import { PlusCircle, Building2, Search, X, ArrowDownWideNarrow, Edit2, Check, Eye, EyeOff } from 'lucide-react';
import { toast } from 'react-toastify';
import { getSectors, createSector, updateSector, toggleSectorActive } from '../../services/userService';
import type { Sector } from '../../types/models';
import PageHero from '../../components/PageHero';

export default function Sectors() {
  const [sectors, setSectors] = useState<Sector[]>([]);
  const [loading, setLoading] = useState(true);
  const [showForm, setShowForm] = useState(false);
  const [newSectorName, setNewSectorName] = useState('');
  const [submitting, setSubmitting] = useState(false);

  const [searchQuery, setSearchQuery] = useState('');
  const [sortOption, setSortOption] = useState<'name-asc' | 'name-desc'>('name-asc');

  // Controle de Edição e Status
  const [editingSectorId, setEditingSectorId] = useState<string | null>(null);
  const [editSectorName, setEditSectorName] = useState('');
  const [updatingId, setUpdatingId] = useState<string | null>(null);

  const [currentPage, setCurrentPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);

  useEffect(() => {
    loadSectors();
  }, [currentPage, searchQuery, sortOption]);

  async function loadSectors() {
    setLoading(true);
    try {
      // Carrega os setores com paginação, busca e ordenação
      const response = await getSectors({
        page: currentPage,
        size: 15,
        activeOnly: false,
        search: searchQuery,
        sort: sortOption,
      });
      setSectors(response.content);
      setTotalPages(response.totalPages);
    } catch {
      toast.error('Erro ao carregar setores.');
      setSectors([]);
    } finally {
      setLoading(false);
    }
  }

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    if (!newSectorName.trim()) {
      toast.error('Digite o nome do setor.');
      return;
    }

    setSubmitting(true);
    try {
      await createSector({ name: newSectorName.trim() });
      toast.success('Setor cadastrado com sucesso!');
      setNewSectorName('');
      setShowForm(false);
      loadSectors();
    } catch {
      toast.error('Erro ao cadastrar setor. Tente novamente.');
    } finally {
      setSubmitting(false);
    }
  }

  async function handleRename(id: string) {
    if (!editSectorName.trim()) {
      toast.error('Digite o nome do setor.');
      return;
    }

    setUpdatingId(id);
    try {
      await updateSector(id, { name: editSectorName.trim() });
      toast.success('Setor renomeado com sucesso!');
      setEditingSectorId(null);
      loadSectors();
    } catch {
      toast.error('Erro ao renomear setor. Tente outro nome.');
    } finally {
      setUpdatingId(null);
    }
  }

  async function handleToggleActive(id: string) {
    setUpdatingId(id);
    try {
      await toggleSectorActive(id);
      toast.success('Estado do setor atualizado com sucesso!');
      loadSectors();
    } catch {
      toast.error('Erro ao alternar ativação do setor.');
    } finally {
      setUpdatingId(null);
    }
  }

  const filteredAndSortedSectors = useMemo(() => {
    return sectors;
  }, [sectors]);

  return (
    <main className="w-full max-w-full px-4 sm:px-6 lg:px-8 py-8">
      <PageHero
        eyebrow="Organização"
        title="Setores"
        description="Estruture departamentos e mantenha a segmentação organizacional usada em chamados, usuários e ativos."
        actions={(
          <button
            onClick={() => setShowForm((prev) => !prev)}
            className="flex items-center gap-2 rounded-xl bg-brand-primary px-4 py-2.5 text-sm font-semibold text-white transition-colors hover:bg-brand-primary-dark"
          >
            <PlusCircle size={17} />
            {showForm ? 'Cancelar' : 'Novo Setor'}
          </button>
        )}
      />

      {/* Formulário de cadastro */}
      {showForm && (
        <form
          onSubmit={handleSubmit}
          className="mb-6 rounded-2xl border border-slate-200 bg-white p-6 shadow-sm animate-fadeIn"
        >
          <h2 className="text-sm font-semibold text-slate-700 mb-4">Cadastrar Novo Setor</h2>
          <div className="flex gap-3">
            <input
              type="text"
              value={newSectorName}
              onChange={(e) => setNewSectorName(e.target.value)}
              placeholder="Nome do setor (ex: Recepção, TI, Faturamento)"
              className="flex-1 rounded-xl border border-slate-200 bg-white px-4 py-2.5 text-sm text-slate-800 shadow-sm placeholder-slate-400 focus:outline-none focus:ring-2 focus:ring-brand-primary/30 focus:border-brand-primary"
              disabled={submitting}
            />
            <button
              type="submit"
              disabled={submitting}
              className="rounded-xl bg-brand-primary px-6 py-2.5 text-sm font-semibold text-white transition-colors hover:bg-brand-primary-dark disabled:opacity-50"
            >
              {submitting ? 'Salvando...' : 'Salvar'}
            </button>
          </div>
        </form>
      )}

      {/* ── Search & Filter Controls ── */}
      <div className="mb-6 flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between bg-white p-4 rounded-2xl border border-slate-200 shadow-sm">
        <div className="relative flex-1 max-w-md">
          <input
            type="text"
            placeholder="Pesquisar por nome do setor..."
            value={searchQuery}
            onChange={(e) => {
              setSearchQuery(e.target.value);
              setCurrentPage(0);
            }}
            className="w-full rounded-xl border border-slate-200 bg-white pl-10 pr-10 py-2.5 text-sm text-slate-800 placeholder-slate-400 focus:border-brand-primary focus:outline-none focus:ring-2 focus:ring-brand-primary/20 transition"
          />
          <div className="absolute left-3 top-1/2 -translate-y-1/2 text-slate-400">
            <Search size={16} />
          </div>
          {searchQuery && (
            <button
              type="button"
              onClick={() => {
                setSearchQuery('');
                setCurrentPage(0);
              }}
              className="absolute right-3 top-1/2 -translate-y-1/2 text-slate-400 hover:text-slate-600"
            >
              <X size={16} />
            </button>
          )}
        </div>

        <div className="flex items-center gap-2 self-start sm:self-auto">
          <span className="text-xs font-bold uppercase tracking-widest text-slate-400 flex items-center gap-1">
            <ArrowDownWideNarrow size={14} /> Ordenar:
          </span>
          <select
            value={sortOption}
            onChange={(e) => {
              setSortOption(e.target.value as 'name-asc' | 'name-desc');
              setCurrentPage(0);
            }}
            className="cursor-pointer rounded-xl border border-slate-200 bg-white px-3.5 py-2.5 text-sm font-medium text-slate-700 shadow-sm focus:border-brand-primary focus:outline-none focus:ring-2 focus:ring-brand-primary/20 transition-all"
          >
            <option value="name-asc">Nome (A-Z)</option>
            <option value="name-desc">Nome (Z-A)</option>
          </select>
        </div>
      </div>

      {/* Lista de setores */}
      <div className="rounded-2xl border border-slate-200 bg-white shadow-sm p-6">
        {loading ? (
          <div className="p-12 text-center">
            <div className="animate-pulse space-y-3">
              <div className="h-4 bg-slate-200 rounded w-3/4 mx-auto" />
              <div className="h-4 bg-slate-200 rounded w-1/2 mx-auto" />
            </div>
          </div>
        ) : filteredAndSortedSectors.length === 0 ? (
          <p className="text-center text-slate-400 py-12 text-sm">Nenhum setor localizado.</p>
        ) : (
          <>
            <div className="divide-y divide-slate-100">
              {filteredAndSortedSectors.map((sector) => {
                const isEditing = editingSectorId === sector.id;
                const isUpdating = updatingId === sector.id;
                const isActive = sector.active !== false; // default true

                return (
                  <div
                    key={sector.id}
                    className={`flex items-center justify-between gap-3 px-6 py-4 transition-colors rounded-xl ${
                      isActive ? 'hover:bg-slate-50/50' : 'bg-slate-50/40 opacity-70 hover:bg-slate-100/30'
                    }`}
                  >
                    <div className="flex items-center gap-3 flex-1">
                      <Building2
                        size={20}
                        className={isActive ? 'text-slate-400' : 'text-slate-300'}
                      />
                      
                      {isEditing ? (
                        <input
                          type="text"
                          value={editSectorName}
                          onChange={(e) => setEditSectorName(e.target.value)}
                          placeholder="Nome do setor..."
                          className="rounded-xl border border-slate-200 bg-white px-3 py-1.5 text-sm text-slate-800 shadow-sm focus:outline-none focus:ring-2 focus:ring-brand-primary/30 focus:border-brand-primary"
                          disabled={isUpdating}
                          autoFocus
                        />
                      ) : (
                        <div className="flex items-center gap-2">
                          <span className={`text-sm font-semibold ${isActive ? 'text-slate-800' : 'text-slate-400 line-through'}`}>
                            {sector.name}
                          </span>
                          {!isActive && (
                            <span className="rounded-full bg-slate-200/70 px-2 py-0.5 text-[10px] font-bold text-slate-500 uppercase tracking-wide">
                              Inativo
                            </span>
                          )}
                        </div>
                      )}
                    </div>

                    <div className="flex items-center gap-2">
                      {isEditing ? (
                        <>
                          <button
                            onClick={() => handleRename(sector.id)}
                            disabled={isUpdating}
                            className="flex items-center justify-center p-2 rounded-lg bg-green-50 text-green-600 hover:bg-green-100 transition-colors disabled:opacity-50"
                            title="Salvar"
                          >
                            <Check size={16} />
                          </button>
                          <button
                            onClick={() => setEditingSectorId(null)}
                            disabled={isUpdating}
                            className="flex items-center justify-center p-2 rounded-lg bg-slate-50 text-slate-500 hover:bg-slate-100 transition-colors disabled:opacity-50"
                            title="Cancelar"
                          >
                            <X size={16} />
                          </button>
                        </>
                      ) : (
                        <>
                          <button
                            onClick={() => {
                              setEditingSectorId(sector.id);
                              setEditSectorName(sector.name);
                            }}
                            disabled={isUpdating}
                            className="flex items-center justify-center p-2 rounded-lg text-slate-400 hover:text-slate-600 hover:bg-slate-50 transition-colors disabled:opacity-50"
                            title="Editar Nome"
                          >
                            <Edit2 size={16} />
                          </button>
                          
                          <button
                            onClick={() => handleToggleActive(sector.id)}
                            disabled={isUpdating}
                            className={`flex items-center justify-center p-2 rounded-lg transition-colors disabled:opacity-50 ${
                              isActive
                                ? 'text-orange-500 hover:text-orange-700 hover:bg-orange-50'
                                : 'text-emerald-500 hover:text-emerald-700 hover:bg-emerald-50'
                            }`}
                            title={isActive ? 'Inativar Setor' : 'Ativar Setor'}
                          >
                            {isActive ? <EyeOff size={16} /> : <Eye size={16} />}
                          </button>
                        </>
                      )}
                    </div>
                  </div>
                );
              })}
            </div>

            {/* Paginação */}
            {totalPages > 1 && (
              <div className="mt-6 flex items-center justify-between border-t border-slate-100 pt-6">
                <p className="text-xs text-slate-500 font-medium">
                  A mostrar página <span className="font-semibold text-slate-800">{currentPage + 1}</span> de{' '}
                  <span className="font-semibold text-slate-800">{totalPages}</span>
                </p>
                <div className="flex gap-2">
                  <button
                    onClick={() => setCurrentPage((prev) => Math.max(0, prev - 1))}
                    disabled={currentPage === 0}
                    className="rounded-xl border border-slate-200 bg-white px-4 py-2 text-xs font-semibold text-slate-700 shadow-sm transition hover:bg-slate-50 disabled:opacity-50 disabled:cursor-not-allowed"
                  >
                    Anterior
                  </button>
                  <button
                    onClick={() => setCurrentPage((prev) => Math.min(totalPages - 1, prev + 1))}
                    disabled={currentPage >= totalPages - 1}
                    className="rounded-xl border border-slate-200 bg-white px-4 py-2 text-xs font-semibold text-slate-700 shadow-sm transition hover:bg-slate-50 disabled:opacity-50 disabled:cursor-not-allowed"
                  >
                    Seguinte
                  </button>
                </div>
              </div>
            )}
          </>
        )}
      </div>
    </main>
  );
}
