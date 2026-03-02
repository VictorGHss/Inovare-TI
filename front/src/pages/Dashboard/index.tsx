// Página principal de gerenciamento de chamados
import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { PlusCircle } from 'lucide-react';
import { toast } from 'react-toastify';
import { getTickets, type Ticket } from '../../services/api';
import SkeletonTable from '../../components/SkeletonTable';
import TicketsTable from './TicketsTable';
import SummaryAside from './SummaryAside';

export default function Dashboard() {
  const navigate = useNavigate();
  const [tickets, setTickets] = useState<Ticket[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    getTickets()
      .then(setTickets)
      .catch(() => toast.error('Erro ao carregar chamados. Tente novamente.'))
      .finally(() => setLoading(false));
  }, []);

  return (
    <main className="max-w-7xl mx-auto px-4 sm:px-6 py-8">
      {/* Cabeçalho da seção */}
      <div className="flex items-center justify-between mb-6">
        <h1 className="text-2xl font-bold text-slate-800">Meus Chamados</h1>
        <button
          onClick={() => navigate('/tickets/new')}
          className="flex items-center gap-2 bg-primary hover:bg-primary-hover text-white text-sm font-semibold px-4 py-2.5 rounded-xl transition-colors"
        >
          <PlusCircle size={17} />
          Novo Chamado
        </button>
      </div>

      {/* Layout de duas colunas: tabela + aside */}
      <div className="flex flex-col lg:flex-row gap-6 items-start">
        <div className="flex-1 min-w-0">
          {loading ? <SkeletonTable /> : <TicketsTable tickets={tickets} />}
        </div>
        <SummaryAside />
      </div>
    </main>
  );
}
