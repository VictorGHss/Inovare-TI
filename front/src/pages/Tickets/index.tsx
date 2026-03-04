import { useEffect, useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { PlusCircle } from 'lucide-react';
import { toast } from 'react-toastify';
import { getTickets, type Ticket } from '../../services/api';
import SkeletonTable from '../../components/SkeletonTable';
import TicketsTable from '../Dashboard/TicketsTable';
import { useAuth } from '../../contexts/AuthContext';

export default function Tickets() {
  const navigate = useNavigate();
  const { user } = useAuth();
  const [searchParams] = useSearchParams();
  const [tickets, setTickets] = useState<Ticket[]>([]);
  const [loading, setLoading] = useState(true);

  const isAdmin = user?.role === 'ADMIN' || user?.role === 'TECHNICIAN';
  const tab = searchParams.get('tab') || 'all';

  useEffect(() => {
    const fetchTickets = async () => {
      try {
        const data = await getTickets();
        setTickets(data);
      } catch {
        toast.error('Erro ao carregar chamados. Tente novamente.');
        setTickets([]);
      } finally {
        setLoading(false);
      }
    };
    fetchTickets();
  }, []);

  // Filtra chamados baseado no papel do usuário
  // Nota: A API já retorna apenas os chamados relevantes para o usuário
  const filteredTickets = tickets;

  return (
    <main className="max-w-7xl mx-auto px-4 sm:px-6 py-8">
      {/* Header */}
      <div className="flex items-center justify-between mb-6 flex-wrap gap-4">
        <h1 className="text-2xl font-bold text-slate-800">
          {isAdmin ? 'Todos os Chamados' : 'Meus Chamados'}
        </h1>
        <button
          onClick={() => navigate('/tickets/new')}
          className="flex items-center gap-2 bg-primary hover:bg-primary-hover text-white text-sm font-semibold px-4 py-2.5 rounded-xl transition-colors"
        >
          <PlusCircle size={17} />
          Novo Chamado
        </button>
      </div>

      {/* Tabs for filtering (optional) */}
      <div className="mb-6 flex gap-2 border-b border-slate-200">
        <button
          onClick={() => {}}
          className={`px-4 py-2 font-medium text-sm transition-colors ${
            tab === 'all'
              ? 'border-b-2 border-primary text-primary'
              : 'text-slate-600 hover:text-slate-800'
          }`}
        >
          Todos ({filteredTickets.length})
        </button>
      </div>

      {/* Table */}
      <div className="bg-white rounded-lg shadow">
        {loading ? <SkeletonTable /> : <TicketsTable tickets={filteredTickets} />}
      </div>
    </main>
  );
}
