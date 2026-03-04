// Dashboard dinâmica para Admin e User
import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { PlusCircle, Download } from 'lucide-react';
import { toast } from 'react-toastify';
import { getDashboardAnalytics, getTickets, type Ticket, type DashboardAnalyticsDTO } from '../../services/api';
import SkeletonTable from '../../components/SkeletonTable';
import SummaryAside from './SummaryAside';
import ChartsPie from '../../components/ChartsPie';
import ChartsBar from '../../components/ChartsBar';
import InventorySummaryCard from '../../components/InventorySummaryCard';
import ReceivedItemsCard from '../../components/ReceivedItemsCard';
import UserTicketHistory from '../../components/UserTicketHistory';
import ReportHubModal from '../../components/ReportHubModal';
import { useAuth } from '../../contexts/AuthContext';

export default function Dashboard() {
  const navigate = useNavigate();
  const { user } = useAuth();
  const [tickets, setTickets] = useState<Ticket[]>([]);
  const [analytics, setAnalytics] = useState<DashboardAnalyticsDTO | null>(null);
  const [loading, setLoading] = useState(true);
  const [reportHubOpen, setReportHubOpen] = useState(false);

  const isAdmin = user?.role === 'ADMIN' || user?.role === 'TECHNICIAN';

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
      <div className="flex items-center justify-between mb-6 flex-wrap gap-4">
        <h1 className="text-2xl font-bold text-slate-800">
          {isAdmin ? 'Visão Geral de Chamados' : 'Dashboard'}
        </h1>
        <div className="flex items-center gap-2 flex-wrap">
          <button
            onClick={() => navigate('/tickets/new')}
            className="flex items-center gap-2 bg-primary hover:bg-primary-hover text-white text-sm font-semibold px-4 py-2.5 rounded-xl transition-colors"
          >
            <PlusCircle size={17} />
            Novo Chamado
          </button>
          {isAdmin && (
            <button
              onClick={() => setReportHubOpen(true)}
              className="flex items-center gap-2 bg-green-600 hover:bg-green-700 text-white text-sm font-semibold px-4 py-2.5 rounded-xl transition-colors"
            >
              <Download size={17} />
              Central de Relatórios
            </button>
          )}
        </div>
      </div>

      {/* Summary cards no topo */}
      <div className="mb-8">
        {loading ? (
          <SkeletonTable />
        ) : (
          <SummaryAside
            openTickets={analytics?.totalOpenTickets ?? 0}
            inProgressTickets={analytics?.totalInProgressTickets ?? 0}
            resolvedTickets={analytics?.totalResolvedTickets ?? 0}
            lowStockItems={analytics?.lowStockItemsCount ?? 0}
            totalTickets={analytics?.totalTickets ?? 0}
            closedTickets={analytics?.totalClosedTickets ?? 0}
          />
        )}
      </div>

      {/* Admin Dashboard */}
      {isAdmin ? (
        <>
          {/* Gráficos */}
          <div className="grid grid-cols-1 lg:grid-cols-2 gap-6 mb-8">
            {analytics && (
              <>
                <ChartsPie
                  data={analytics.ticketsByStatus}
                  title="Chamados por Status"
                />
                <ChartsPie
                  data={analytics.ticketsByCategory}
                  title="Chamados por Categoria"
                />
              </>
            )}
          </div>

          {/* Volume Mensal e Inventário */}
          <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
            {!loading && (
              <>
                <ChartsBar tickets={tickets} title="Volume Mensal de Chamados" />
                {analytics && (
                  <InventorySummaryCard data={analytics.inventorySummary} />
                )}
              </>
            )}
          </div>
        </>
      ) : (
        <>
          {/* User Dashboard */}
          <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
            {!loading && (
              <>
                <UserTicketHistory tickets={tickets} />
                <ReceivedItemsCard tickets={tickets} />
              </>
            )}
          </div>
        </>
      )}

      <ReportHubModal isOpen={reportHubOpen} onClose={() => setReportHubOpen(false)} />
    </main>
  );
}

