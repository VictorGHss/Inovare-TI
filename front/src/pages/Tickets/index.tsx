import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { PlusCircle } from 'lucide-react';
import { toast } from 'react-toastify';
import { getTickets, type Ticket } from '../../services/api';
import SkeletonTable from '../../components/SkeletonTable';
import TicketsTable from '../Dashboard/TicketsTable';
import { useAuth } from '../../contexts/AuthContext';

export default function Tickets() {
  const navigate = useNavigate();
  const { user } = useAuth();
  const [tickets, setTickets] = useState<Ticket[]>([]);
  const [loading, setLoading] = useState(true);
  const [activeTab, setActiveTab] = useState<'ALL' | 'OPEN' | 'IN_PROGRESS' | 'RESOLVED'>('ALL');

  const isAdmin = user?.role === 'ADMIN' || user?.role === 'TECHNICIAN';

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

  const filteredTickets = tickets.filter((ticket) => {
    if (activeTab === 'ALL') return true;
    return ticket.status === activeTab;
  });

  const tabs = [
    { key: 'ALL' as const, label: 'Todos' },
    { key: 'OPEN' as const, label: 'Abertos' },
    { key: 'IN_PROGRESS' as const, label: 'Em Andamento' },
    { key: 'RESOLVED' as const, label: 'Resolvidos' },
  ];

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

      {/* Tabs de filtro por status */}
      <div className="mb-6 flex flex-wrap gap-2">
        {tabs.map((tab) => (
          <button
            key={tab.key}
            onClick={() => setActiveTab(tab.key)}
            className={`px-4 py-2 rounded-lg text-sm font-medium transition-colors ${
              activeTab === tab.key
                ? 'bg-slate-100 text-blue-600'
                : 'text-slate-500 hover:text-slate-700 hover:bg-slate-50'
            }`}
          >
            {tab.label}
          </button>
        ))}
      </div>

      {/* Table */}
      <div className="bg-white rounded-lg shadow">
        {loading ? <SkeletonTable /> : <TicketsTable tickets={filteredTickets} />}
      </div>
    </main>
  );
}
