import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { PlusCircle, Download, Laptop } from 'lucide-react';
import { toast } from 'react-toastify';
import { BarChart, Bar, XAxis, YAxis, Tooltip, ResponsiveContainer } from 'recharts';
import {
  getDashboardAnalytics,
  getTickets,
  getAssetsByUser,
  type Ticket,
  type DashboardAnalyticsDTO,
  type Asset,
} from '../../services/api';
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
  const [assets, setAssets] = useState<Asset[]>([]);
  const [loading, setLoading] = useState(true);
  const [reportHubOpen, setReportHubOpen] = useState(false);

  const isAdmin = user?.role === 'ADMIN' || user?.role === 'TECHNICIAN';

  useEffect(() => {
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
        setTickets([]);
      } finally {
        setLoading(false);
      }
    };

    fetchData();
  }, []);

  useEffect(() => {
    if (isAdmin || !user?.id) {
      setAssets([]);
      return;
    }

    const loadUserAssets = async () => {
      try {
        const userAssets = await getAssetsByUser(user.id);
        setAssets(userAssets);
      } catch {
        setAssets([]);
      }
    };

    loadUserAssets();
  }, [isAdmin, user?.id]);

  return (
    <main className="w-full max-w-full px-4 sm:px-6 lg:px-8 py-8">
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
              className="flex items-center gap-2 bg-brand-primary hover:bg-brand-primary-dark text-white text-sm font-semibold px-4 py-2.5 rounded-xl transition-colors"
            >
              <Download size={17} />
              Central de Relatórios
            </button>
          )}
        </div>
      </div>

      <div className="mb-8">
        {loading ? (
          <SkeletonTable />
        ) : (
          <SummaryAside
            openTickets={analytics?.totalOpenTickets ?? 0}
            inProgressTickets={analytics?.totalInProgressTickets ?? 0}
            resolvedTickets={analytics?.totalResolvedTickets ?? 0}
            lowStockItems={isAdmin ? (analytics?.lowStockItemsCount ?? 0) : 0}
            totalTickets={analytics?.totalTickets ?? 0}
            closedTickets={analytics?.totalClosedTickets ?? 0}
            isAdmin={isAdmin}
          />
        )}
      </div>

      {isAdmin ? (
        <>
          <div className="grid grid-cols-1 lg:grid-cols-2 gap-6 mb-8">
            {analytics && (
              <>
                <ChartsPie data={analytics.ticketsByStatus} title="Chamados por Status" />
                <ChartsPie data={analytics.ticketsByCategory} title="Chamados por Categoria" />
              </>
            )}
          </div>

          <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
            {!loading && (
              <>
                <ChartsBar tickets={tickets} title="Volume Mensal de Chamados" />
                {analytics && <InventorySummaryCard data={analytics.inventorySummary} />}
              </>
            )}
          </div>

          <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
            {analytics && analytics.ticketsBySector.length > 0 && (
              <div className="bg-white rounded-xl border border-slate-200 shadow-sm p-6">
                <h3 className="text-sm font-semibold text-slate-700 mb-4">Top Setores com Mais Chamados</h3>
                <ResponsiveContainer width="100%" height={300}>
                  <BarChart data={analytics.ticketsBySector} layout="vertical">
                    <XAxis type="number" style={{ fontSize: '12px' }} />
                    <YAxis type="category" dataKey="name" width={120} style={{ fontSize: '12px' }} />
                    <Tooltip />
                    <Bar dataKey="value" fill="#3b82f6" />
                  </BarChart>
                </ResponsiveContainer>
              </div>
            )}

            {analytics && analytics.ticketsByRequester.length > 0 && (
              <div className="bg-white rounded-xl border border-slate-200 shadow-sm p-6">
                <h3 className="text-sm font-semibold text-slate-700 mb-4">Top Usuários que Mais Abrem Chamados</h3>
                <ResponsiveContainer width="100%" height={300}>
                  <BarChart data={analytics.ticketsByRequester} layout="vertical">
                    <XAxis type="number" style={{ fontSize: '12px' }} />
                    <YAxis type="category" dataKey="name" width={120} style={{ fontSize: '12px' }} />
                    <Tooltip />
                    <Bar dataKey="value" fill="#8b5cf6" />
                  </BarChart>
                </ResponsiveContainer>
              </div>
            )}
          </div>
        </>
      ) : (
        <div className="grid grid-cols-1 gap-6">
          {!loading && (
            <>
              <UserTicketHistory tickets={tickets} />
              <ReceivedItemsCard
                tickets={tickets}
                totalReceivedCount={analytics?.inventorySummary.receivedItemsCount ?? 0}
              />

              <div className="bg-white rounded-xl border border-slate-200 shadow-sm p-6">
                <div className="flex items-center gap-2 mb-4">
                  <Laptop size={18} className="text-slate-600" />
                  <h3 className="text-sm font-semibold text-slate-700">Meus Equipamentos / Ativos</h3>
                </div>

                {assets.length === 0 ? (
                  <p className="text-sm text-slate-500">Nenhum ativo vinculado.</p>
                ) : (
                  <div className="overflow-x-auto">
                    <table className="min-w-full text-sm">
                      <thead className="bg-slate-50 text-slate-500 uppercase text-xs tracking-wider">
                        <tr>
                          <th className="px-4 py-3 text-left">Nome</th>
                          <th className="px-4 py-3 text-left">Patrimônio</th>
                          <th className="px-4 py-3 text-left">Categoria</th>
                        </tr>
                      </thead>
                      <tbody className="divide-y divide-slate-100">
                        {assets.map((asset) => (
                          <tr key={asset.id} className="hover:bg-slate-50 transition-colors">
                            <td className="px-4 py-3 font-medium text-slate-800">{asset.name}</td>
                            <td className="px-4 py-3 text-slate-600">{asset.patrimonyCode}</td>
                            <td className="px-4 py-3 text-slate-600">-</td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                )}
              </div>
            </>
          )}
        </div>
      )}

      <ReportHubModal isOpen={reportHubOpen} onClose={() => setReportHubOpen(false)} />
    </main>
  );
}

