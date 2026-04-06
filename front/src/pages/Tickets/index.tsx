import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { PlusCircle, Search, X } from 'lucide-react';
import { toast } from 'react-toastify';
import { getTickets } from '../../services/ticketService';
import type { Ticket } from '../../types/domain';
import SkeletonTable from '../../components/SkeletonTable';
import PageHero from '../../components/PageHero';
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
  const [sortOrder, setSortOrder] = useState<'newest' | 'oldest'>('newest');

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

  // Sort tickets by created date
  const sortedTickets = [...filteredTickets].sort((a, b) => {
    const dateA = new Date(a.createdAt).getTime();
    const dateB = new Date(b.createdAt).getTime();
    return sortOrder === 'newest' ? dateB - dateA : dateA - dateB;
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
    <main className="w-full max-w-full px-4 sm:px-6 lg:px-8 py-8">
      <PageHero
        eyebrow="Atendimento"
        title={isAdmin ? 'Todos os Chamados' : 'Meus Chamados'}
        description="Gerencie solicitações, refine a busca com filtros avançados e acompanhe o andamento dos tickets."
        actions={(
          <button
            onClick={() => navigate('/tickets/new')}
            className="flex items-center gap-2 bg-brand-primary hover:bg-brand-primary-dark text-white text-sm font-semibold px-4 py-2.5 rounded-xl transition-colors"
          >
            <PlusCircle size={17} />
            Novo Chamado
          </button>
        )}
      />

      {/* Tabs de filtro por status */}
      <div className="mb-6 flex flex-wrap gap-2">
        {tabs.map((tab) => (
          <button
            key={tab.key}
            onClick={() => setActiveTab(tab.key)}
            className={`px-4 py-2 rounded-xl text-sm font-medium transition-colors ${
              activeTab === tab.key
                ? 'bg-brand-primary text-white'
                : 'text-slate-500 hover:text-slate-700 hover:bg-slate-50'
            }`}
          >
            {tab.label}
          </button>
        ))}
      </div>

      {/* Advanced Filter Bar */}
      <div className="mb-6 bg-white rounded-2xl border border-slate-200 p-6 shadow-sm">
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4">
          {/* Search by Title */}
          <div className="relative">
            <label className="block text-xs font-bold uppercase tracking-widest text-slate-400 mb-2">
              Buscar por Título
            </label>
            <div className="relative flex items-center">
              <Search size={16} className="absolute left-3 text-slate-400" />
              <input
                type="text"
                value={searchTitle}
                onChange={(e) => setSearchTitle(e.target.value)}
                placeholder="Digite o título do chamado..."
                className="w-full rounded-xl border border-slate-200 bg-white py-2.5 pl-9 pr-8 text-sm text-slate-800 shadow-sm placeholder-slate-400 focus:outline-none focus:ring-2 focus:ring-brand-primary/30 focus:border-brand-primary"
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
            <label className="block text-xs font-bold uppercase tracking-widest text-slate-400 mb-2">
              Prioridade
            </label>
            <select
              value={selectedPriority}
              onChange={(e) => setSelectedPriority(e.target.value)}
              className="w-full cursor-pointer appearance-none rounded-xl border border-slate-200 bg-white px-3 py-2.5 text-sm text-slate-800 shadow-sm focus:outline-none focus:ring-2 focus:ring-brand-primary/30 focus:border-brand-primary"
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
            <label className="block text-xs font-bold uppercase tracking-widest text-slate-400 mb-2">
              Categoria
            </label>
            <select
              value={selectedCategory}
              onChange={(e) => setSelectedCategory(e.target.value)}
              className="w-full cursor-pointer appearance-none rounded-xl border border-slate-200 bg-white px-3 py-2.5 text-sm text-slate-800 shadow-sm focus:outline-none focus:ring-2 focus:ring-brand-primary/30 focus:border-brand-primary"
            >
              <option value="all">Todas as Categorias</option>
              {categories.map((category) => (
                <option key={category.id} value={category.id}>
                  {category.name}
                </option>
              ))}
            </select>
          </div>

          {/* Sort Order */}
          <div>
            <label className="block text-xs font-bold uppercase tracking-widest text-slate-400 mb-2">
              Ordenar por Data
            </label>
            <select
              value={sortOrder}
              onChange={(e) => setSortOrder(e.target.value as 'newest' | 'oldest')}
              className="w-full cursor-pointer appearance-none rounded-xl border border-slate-200 bg-white px-3 py-2.5 text-sm text-slate-800 shadow-sm focus:outline-none focus:ring-2 focus:ring-brand-primary/30 focus:border-brand-primary"
            >
              <option value="newest">Mais Recentes</option>
              <option value="oldest">Mais Antigos</option>
            </select>
          </div>
        </div>

        {/* Active Filters Indicator */}
        {(searchTitle || selectedPriority !== 'all' || selectedCategory !== 'all') && (
          <div className="mt-3 pt-3 border-t border-slate-200 flex items-center gap-2">
            <span className="text-xs text-slate-600 font-medium">Filtros ativos:</span>
            {searchTitle && (
              <span className="inline-flex items-center gap-2 bg-brand-secondary text-brand-primary px-2.5 py-1 rounded-md text-xs font-medium">
                Título: "{searchTitle}"
                <button onClick={() => setSearchTitle('')} className="hover:text-brand-primary">
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
              <span className="inline-flex items-center gap-2 bg-brand-secondary text-brand-primary px-2.5 py-1 rounded-md text-xs font-medium">
                {categories.find(c => c.id === selectedCategory)?.name}
                <button onClick={() => setSelectedCategory('all')} className="hover:text-brand-primary-dark">
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
      <div className="bg-white rounded-2xl border border-slate-200 shadow-sm p-6">
        {loading ? <SkeletonTable /> : <TicketsTable tickets={sortedTickets} />}
      </div>
    </main>
  );
}
