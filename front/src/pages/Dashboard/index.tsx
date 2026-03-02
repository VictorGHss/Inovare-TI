// Página principal de gerenciamento de chamados
import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { PlusCircle, LogOut } from 'lucide-react';
import { toast } from 'react-toastify';
import { getTickets, type Ticket } from '../../services/api';
import { useAuth } from '../../contexts/AuthContext';
import SkeletonTable from '../../components/SkeletonTable';
import TicketsTable from './TicketsTable';
import SummaryAside from './SummaryAside';

export default function Dashboard() {
  const { signOut, user } = useAuth();
  const navigate = useNavigate();
  const [tickets, setTickets] = useState<Ticket[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    getTickets()
      .then(setTickets)
      .catch(() => toast.error('Erro ao carregar chamados. Tente novamente.'))
      .finally(() => setLoading(false));
  }, []);

  function handleLogout() {
    signOut();
    navigate('/login');
  }

  return (
    <div className="min-h-screen bg-slate-50">
      {/* Barra de navegação superior */}
      <header className="bg-white border-b border-slate-200 px-6 py-4 flex items-center justify-between">
        <img
          src="http://inovare.med.br/wp-content/uploads/2023/01/Logo.png"
          alt="Inovare TI"
          className="h-10 object-contain"
        />
        <div className="flex items-center gap-3">
          <span className="text-sm text-slate-500 hidden sm:block">
            {user?.name ?? 'Usuário'}
          </span>
          <button
            onClick={handleLogout}
            className="flex items-center gap-1.5 text-sm text-slate-500 hover:text-red-500 transition-colors"
          >
            <LogOut size={16} />
            Sair
          </button>
        </div>
      </header>

      {/* Conteúdo principal */}
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
    </div>
  );
}
