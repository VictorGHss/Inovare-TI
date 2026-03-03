// Página principal de gerenciamento de chamados
import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { PlusCircle } from 'lucide-react';
import { toast } from 'react-toastify';
import { getTickets, getDashboardAnalytics, type Ticket, type DashboardAnalyticsDTO } from '../../services/api';
import SkeletonTable from '../../components/SkeletonTable';
import TicketsTable from './TicketsTable';
import SummaryAside from './SummaryAside';

export default function Dashboard() {
  const navigate = useNavigate();
  const [tickets, setTickets] = useState<Ticket[]>([]);
  const [analytics, setAnalytics] = useState<DashboardAnalyticsDTO | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    // Busca chamados e analytics em paralelo
    const fetchData = async () => {
      try {
        const [ticketsData, analyticsData] = await Promise.all([
          getTickets(),
          getDashboardAnalytics(),
        ]);
        setTickets(ticketsData);
        setAnalytics(analyticsData);
      } catch {
        toast.error('Erro ao carregar dados. Tente novamente.');
        setTickets([]); // garante array válido para evitar crash no render
      } finally {
        setLoading(false);
      }
    };
    fetchData();
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
        {analytics && (
          <SummaryAside
            openTickets={analytics.totalOpenTickets}
            inProgressTickets={analytics.totalInProgressTickets}
            resolvedTickets={analytics.totalResolvedTickets}
            lowStockItems={analytics.lowStockItemsCount}
          />
        )}
      </div>
    </main>
  );
}

