// Página de listagem e cadastro de setores
import { useEffect, useState, useMemo } from 'react';
import { PlusCircle, Building2, Search, X, ArrowDownWideNarrow } from 'lucide-react';
import { toast } from 'react-toastify';
import { getSectors, createSector } from '../../services/userService';
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

  useEffect(() => {
    loadSectors();
  }, []);

  async function loadSectors() {
    setLoading(true);
    try {
      const data = await getSectors();
      setSectors(data);
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

  const filteredAndSortedSectors = useMemo(() => {
    let result = [...sectors];

    if (searchQuery.trim()) {
      const q = searchQuery.toLowerCase();
      result = result.filter((s) => s.name?.toLowerCase().includes(q));
    }

    result.sort((a, b) => {
      if (sortOption === 'name-asc') {
        return (a.name ?? '').localeCompare(b.name ?? '');
      } else {
        return (b.name ?? '').localeCompare(a.name ?? '');
      }
    });

    return result;
  }, [sectors, searchQuery, sortOption]);

  return (
    <main className="w-full max-w-full px-4 sm:px-6 lg:px-8 py-8">
      <PageHero
        eyebrow="Organização"
        title="Setores"
        description="Estruture departamentos e mantenha a segmentação organizacional usada em chamados e permissões."
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
            onChange={(e) => setSearchQuery(e.target.value)}
            className="w-full rounded-xl border border-slate-200 bg-white pl-10 pr-10 py-2.5 text-sm text-slate-800 placeholder-slate-400 focus:border-brand-primary focus:outline-none focus:ring-2 focus:ring-brand-primary/20 transition"
          />
          <div className="absolute left-3 top-1/2 -translate-y-1/2 text-slate-400">
            <Search size={16} />
          </div>
          {searchQuery && (
            <button
              type="button"
              onClick={() => setSearchQuery('')}
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
            onChange={(e) => setSortOption(e.target.value as any)}
            className="rounded-xl border border-slate-200 bg-white px-3 py-2 text-sm font-semibold text-slate-750 shadow-sm focus:border-brand-primary focus:outline-none transition"
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
          <div className="divide-y divide-slate-100">
            {filteredAndSortedSectors.map((sector) => (
              <div key={sector.id} className="flex items-center gap-3 px-6 py-4 transition-colors hover:bg-orange-50/40 rounded-xl">
                <Building2 size={20} className="text-slate-400" />
                <span className="text-sm font-medium text-slate-800">{sector.name}</span>
              </div>
            ))}
          </div>
        )}
      </div>
    </main>
  );
}

