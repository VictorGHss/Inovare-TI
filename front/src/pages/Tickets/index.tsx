import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { PlusCircle, Search, X } from 'lucide-react';
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
  const [searchTitle, setSearchTitle] = useState('');
  const [selectedPriority, setSelectedPriority] = useState<string>('all');
  const [selectedCategory, setSelectedCategory] = useState<string>('all');

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
    // Filter by tab status
    if (activeTab !== 'ALL' && ticket.status !== activeTab) return false;
    
    // Filter by title search
    if (searchTitle && !ticket.title.toLowerCase().includes(searchTitle.toLowerCase())) {
      return false;
    }
    
    // Filter by priority
    if (selectedPriority !== 'all' && ticket.priority !== selectedPriority) {
      return false;
    }
    
    // Filter by category
    if (selectedCategory !== 'all' && ticket.categoryId !== selectedCategory) {
      return false;
    }
    
    return true;
  });

  // Get unique categories from tickets
  const categories = Array.from(
    new Map(tickets.map(t => [t.categoryId, { id: t.categoryId, name: t.categoryName }])).values()
  ).sort((a, b) => a.name.localeCompare(b.name));

  const priorities = [
    { value: 'LOW', label: 'Baixa' },
    { value: 'NORMAL', label: 'Normal' },
    { value: 'HIGH', label: 'Alta' },
    { value: 'URGENT', label: 'Urgente' },
  ];

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

      {/* Advanced Filter Bar */}
      <div className="mb-6 bg-white rounded-lg border border-slate-200 p-4 shadow-sm">
        <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
          {/* Search by Title */}
          <div className="relative">
            <label className="block text-xs font-semibold text-slate-600 mb-2">
              Buscar por Título
            </label>
            <div className="relative flex items-center">
              <Search size={16} className="absolute left-3 text-slate-400" />
              <input
                type="text"
                value={searchTitle}
                onChange={(e) => setSearchTitle(e.target.value)}
                placeholder="Digite o título do chamado..."
                className="w-full pl-9 pr-8 py-2.5 border border-slate-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent text-sm"
              />
              {searchTitle && (
                <button
                  onClick={() => setSearchTitle('')}
                  className="absolute right-3 text-slate-400 hover:text-slate-600"
                >
                  <X size={16} />
                </button>
              )}
            </div>
          </div>

          {/* Priority Filter */}
          <div>
            <label className="block text-xs font-semibold text-slate-600 mb-2">
              Prioridade
            </label>
            <select
              value={selectedPriority}
              onChange={(e) => setSelectedPriority(e.target.value)}
              className="w-full px-3 py-2.5 border border-slate-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent text-sm appearance-none bg-white cursor-pointer"
            >
              <option value="all">Todas as Prioridades</option>
              {priorities.map((priority) => (
                <option key={priority.value} value={priority.value}>
                  {priority.label}
                </option>
              ))}
            </select>
          </div>

          {/* Category Filter */}
          <div>
            <label className="block text-xs font-semibold text-slate-600 mb-2">
              Categoria
            </label>
            <select
              value={selectedCategory}
              onChange={(e) => setSelectedCategory(e.target.value)}
              className="w-full px-3 py-2.5 border border-slate-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent text-sm appearance-none bg-white cursor-pointer"
            >
              <option value="all">Todas as Categorias</option>
              {categories.map((category) => (
                <option key={category.id} value={category.id}>
                  {category.name}
                </option>
              ))}
            </select>
          </div>
        </div>

        {/* Active Filters Indicator */}
        {(searchTitle || selectedPriority !== 'all' || selectedCategory !== 'all') && (
          <div className="mt-3 pt-3 border-t border-slate-200 flex items-center gap-2">
            <span className="text-xs text-slate-600 font-medium">Filtros ativos:</span>
            {searchTitle && (
              <span className="inline-flex items-center gap-2 bg-blue-50 text-blue-700 px-2.5 py-1 rounded-md text-xs font-medium">
                Título: "{searchTitle}"
                <button onClick={() => setSearchTitle('')} className="hover:text-blue-900">
                  <X size={14} />
                </button>
              </span>
            )}
            {selectedPriority !== 'all' && (
              <span className="inline-flex items-center gap-2 bg-orange-50 text-orange-700 px-2.5 py-1 rounded-md text-xs font-medium">
                {priorities.find(p => p.value === selectedPriority)?.label}
                <button onClick={() => setSelectedPriority('all')} className="hover:text-orange-900">
                  <X size={14} />
                </button>
              </span>
            )}
            {selectedCategory !== 'all' && (
              <span className="inline-flex items-center gap-2 bg-green-50 text-green-700 px-2.5 py-1 rounded-md text-xs font-medium">
                {categories.find(c => c.id === selectedCategory)?.name}
                <button onClick={() => setSelectedCategory('all')} className="hover:text-green-900">
                  <X size={14} />
                </button>
              </span>
            )}
            <button
              onClick={() => {
                setSearchTitle('');
                setSelectedPriority('all');
                setSelectedCategory('all');
              }}
              className="ml-auto text-xs text-slate-500 hover:text-slate-700 font-medium"
            >
              Limpar Filtros
            </button>
          </div>
        )}
      </div>

      {/* Table */}
      <div className="bg-white rounded-lg shadow">
        {loading ? <SkeletonTable /> : <TicketsTable tickets={filteredTickets} />}
      </div>
    </main>
  );
}
