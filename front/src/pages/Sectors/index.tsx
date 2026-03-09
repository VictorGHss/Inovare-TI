// Página de listagem e cadastro de setores
import { useEffect, useState } from 'react';
import { PlusCircle, Building2 } from 'lucide-react';
import { toast } from 'react-toastify';
import { getSectors, createSector, type Sector } from '../../services/api';

export default function Sectors() {
  const [sectors, setSectors] = useState<Sector[]>([]);
  const [loading, setLoading] = useState(true);
  const [showForm, setShowForm] = useState(false);
  const [newSectorName, setNewSectorName] = useState('');
  const [submitting, setSubmitting] = useState(false);

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

  return (
    <main className="w-full max-w-full px-4 sm:px-6 lg:px-8 py-8">
      {/* Cabeçalho */}
      <div className="flex items-center justify-between mb-6">
        <h1 className="text-2xl font-bold text-slate-800">Setores</h1>
        <button
          onClick={() => setShowForm((prev) => !prev)}
          className="flex items-center gap-2 bg-primary hover:bg-primary-hover text-white text-sm font-semibold px-4 py-2.5 rounded-xl transition-colors"
        >
          <PlusCircle size={17} />
          {showForm ? 'Cancelar' : 'Novo Setor'}
        </button>
      </div>

      {/* Formulário de cadastro */}
      {showForm && (
        <form
          onSubmit={handleSubmit}
          className="bg-white rounded-xl border border-slate-200 shadow-sm p-6 mb-6"
        >
          <h2 className="text-sm font-semibold text-slate-700 mb-4">Cadastrar Novo Setor</h2>
          <div className="flex gap-3">
            <input
              type="text"
              value={newSectorName}
              onChange={(e) => setNewSectorName(e.target.value)}
              placeholder="Nome do setor (ex: Recepção, TI, Faturamento)"
              className="flex-1 px-4 py-2.5 border border-slate-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-primary"
              disabled={submitting}
            />
            <button
              type="submit"
              disabled={submitting}
              className="bg-primary hover:bg-primary-hover text-white text-sm font-semibold px-6 py-2.5 rounded-lg transition-colors disabled:opacity-50"
            >
              {submitting ? 'Salvando...' : 'Salvar'}
            </button>
          </div>
        </form>
      )}

      {/* Lista de setores */}
      <div className="bg-white rounded-xl border border-slate-200 shadow-sm">
        {loading ? (
          <div className="p-12 text-center">
            <div className="animate-pulse space-y-3">
              <div className="h-4 bg-slate-200 rounded w-3/4 mx-auto" />
              <div className="h-4 bg-slate-200 rounded w-1/2 mx-auto" />
            </div>
          </div>
        ) : sectors.length === 0 ? (
          <p className="text-center text-slate-400 py-12 text-sm">Nenhum setor cadastrado.</p>
        ) : (
          <div className="divide-y divide-slate-100">
            {sectors.map((sector) => (
              <div key={sector.id} className="px-6 py-4 flex items-center gap-3">
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
