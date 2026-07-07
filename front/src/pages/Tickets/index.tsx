import { useEffect, useState, useCallback } from 'react';
import { useNavigate } from 'react-router-dom';
import { PlusCircle, Search, X, ArrowDownWideNarrow } from 'lucide-react';
import { toast } from 'react-toastify';
import { getTickets, getTicketTags } from '../../services/ticketService';
import type { Ticket, TicketTag } from '../../types/models';
import SkeletonTable from '@/components/ui/SkeletonTable';
import PageHero from '@/components/ui/PageHero';
import TicketsTable from '../Dashboard/TicketsTable';
import { useAuth } from '../../contexts/AuthContext';
import SearchableDropdown from '@/components/common/SearchableDropdown';

export default function Tickets() {
  const navigate = useNavigate();
  const { user } = useAuth();
  const [tickets, setTickets] = useState<Ticket[]>([]);
  const [availableTags, setAvailableTags] = useState<TicketTag[]>([]);
  const [selectedTagIds, setSelectedTagIds] = useState<string[]>([]);
  const [isTagsDropdownOpen, setIsTagsDropdownOpen] = useState(false);
  const [loading, setLoading] = useState(true);
  const [activeTab, setActiveTab] = useState<'ALL' | 'OPEN' | 'IN_PROGRESS' | 'RESOLVED'>('ALL');
  
  // Estados para a pesquisa global com debounce
  const [searchQuery, setSearchQuery] = useState('');
  const [debouncedSearch, setDebouncedSearch] = useState('');
  
  const [selectedPriority, setSelectedPriority] = useState<string>('all');
  const [selectedCategory, setSelectedCategory] = useState<string>('all');
  const [sortOrder, setSortOrder] = useState<'newest' | 'oldest'>('newest');
  
  const [currentPage, setCurrentPage] = useState(0);
  const [totalPages, setTotalPages] = useState(1);

  const isAdmin = user?.role === 'ADMIN' || user?.role === 'TECHNICIAN';

  // Efeito de debounce para a pesquisa global
  useEffect(() => {
    const handler = setTimeout(() => {
      setDebouncedSearch(searchQuery);
    }, 300);

    return () => {
      clearTimeout(handler);
    };
  }, [searchQuery]);

  const fetchTickets = useCallback(async () => {
    setLoading(true);
    try {
      // Repassa os filtros de tags, paginação, busca por texto, status, prioridade e categoria para o backend
      const ticketsPage = await getTickets(
        selectedTagIds,
        currentPage,
        debouncedSearch,
        activeTab,
        selectedPriority,
        selectedCategory
      );
      setTickets(ticketsPage.content);
      setTotalPages(ticketsPage.totalPages);
    } catch {
      toast.error('Erro ao carregar chamados. Tente novamente.');
      setTickets([]);
    } finally {
      setLoading(false);
    }
  }, [selectedTagIds, currentPage, debouncedSearch, activeTab, selectedPriority, selectedCategory]);

  useEffect(() => {
    void fetchTickets();
  }, [fetchTickets]);

  useEffect(() => {
    const fetchTags = async () => {
      try {
        const tagsData = await getTicketTags(true);
        setAvailableTags(tagsData);
      } catch {
        setAvailableTags([]);
      }
    };
    void fetchTags();
  }, []);

  // Reseta o ecrã/página de listagem para 0 ao iniciar uma nova busca ou alterar os filtros
  useEffect(() => {
    setCurrentPage(0);
  }, [selectedTagIds, activeTab, searchQuery, selectedPriority, selectedCategory]);

  // Com a filtragem ocorrendo no servidor, o array filteredTickets apenas repassa a lista original recebida
  const filteredTickets = tickets;

  // Ordenar a tabela de chamados localmente pela data de criação
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

      {/* Barra de filtros padronizada com foco em consistência visual entre módulos */}
      <div className="mb-6 bg-white rounded-2xl border border-slate-200 p-6 shadow-sm">
        <div className="flex flex-wrap items-end gap-3">
          {/* Search by Title */}
          <div className="relative w-full max-w-md">
            <label className="mb-2 block text-xs font-semibold uppercase tracking-wider text-slate-500">
              Buscar por Título
            </label>
            <div className="relative flex items-center">
              <Search size={16} className="absolute left-3 text-slate-400" />
              <input
                type="text"
                value={searchQuery}
                onChange={(e) => setSearchQuery(e.target.value)}
                placeholder="Digite o título do chamado..."
                className="w-full rounded-xl border border-slate-200 bg-white py-2.5 pl-9 pr-8 text-sm text-slate-800 shadow-sm placeholder-slate-400 focus:border-brand-primary focus:outline-none focus:ring-2 focus:ring-brand-primary/20 transition-all"
              />
              {searchQuery && (
                <button
                  onClick={() => setSearchQuery('')}
                  className="absolute right-3 text-slate-400 hover:text-slate-600 transition-colors"
                >
                  <X size={16} />
                </button>
              )}
            </div>
          </div>

          {/* Priority Filter */}
          <div className="w-full sm:w-56">
            <label className="mb-2 block text-xs font-semibold uppercase tracking-wider text-slate-500">
              Prioridade
            </label>
            <select
              value={selectedPriority}
              onChange={(e) => setSelectedPriority(e.target.value)}
              className="w-full cursor-pointer rounded-xl border border-slate-200 bg-white px-3.5 py-2.5 text-sm font-medium text-slate-700 shadow-sm focus:border-brand-primary focus:outline-none focus:ring-2 focus:ring-brand-primary/20 transition-all"
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
          <div className="w-full sm:w-56">
            <label className="mb-2 block text-xs font-semibold uppercase tracking-wider text-slate-500">
              Categoria
            </label>
            <SearchableDropdown
              options={[
                { id: 'all', name: 'Todas as Categorias' },
                ...categories.map((category) => ({
                  id: category.id || '',
                  name: category.name || 'Sem Categoria',
                })),
              ]}
              value={selectedCategory}
              onChange={(val) => setSelectedCategory(val)}
              placeholder="Todas as Categorias"
            />
          </div>

          {/* Sort Order */}
          <div className="w-full sm:w-56">
            <label className="mb-2 block text-xs font-semibold uppercase tracking-wider text-slate-500 flex items-center gap-1">
              <ArrowDownWideNarrow size={14} /> Ordenar por Data
            </label>
            <select
              value={sortOrder}
              onChange={(e) => setSortOrder(e.target.value as 'newest' | 'oldest')}
              className="w-full cursor-pointer rounded-xl border border-slate-200 bg-white px-3.5 py-2.5 text-sm font-medium text-slate-700 shadow-sm focus:border-brand-primary focus:outline-none focus:ring-2 focus:ring-brand-primary/20 transition-all"
            >
              <option value="newest">Mais Recentes</option>
              <option value="oldest">Mais Antigos</option>
            </select>
          </div>

          {/* Tag Filter (Multi-Select) */}
          <div className="w-full sm:w-56 relative">
            <label className="mb-2 block text-xs font-semibold uppercase tracking-wider text-slate-500">
              Filtrar por Tags
            </label>
            <div className="relative">
              <button
                type="button"
                onClick={() => setIsTagsDropdownOpen(!isTagsDropdownOpen)}
                className="w-full text-left rounded-xl border border-slate-200 bg-white px-3.5 py-2.5 text-sm font-medium text-slate-700 shadow-sm focus:border-brand-primary focus:outline-none focus:ring-2 focus:ring-brand-primary/20 transition-all flex items-center justify-between"
              >
                <span className="truncate">
                  {selectedTagIds.length === 0
                    ? 'Todas as Tags'
                    : `${selectedTagIds.length} Tag(s) selecionada(s)`}
                </span>
                <span className="ml-2 text-slate-400">▼</span>
              </button>

              {isTagsDropdownOpen && (
                <>
                  <div
                    className="fixed inset-0 z-10"
                    onClick={() => setIsTagsDropdownOpen(false)}
                  />
                  <div className="absolute right-0 left-0 mt-2 z-20 rounded-2xl border border-slate-200 bg-white p-2 shadow-lg max-h-60 overflow-y-auto flex flex-col gap-1">
                    {availableTags.length === 0 ? (
                      <p className="text-xs text-slate-400 p-2 text-center">Nenhuma tag ativa cadastrada.</p>
                    ) : (
                      availableTags.map((tag) => {
                        const isSelected = selectedTagIds.includes(tag.id);
                        return (
                          <button
                            key={tag.id}
                            type="button"
                            onClick={() => {
                              setSelectedTagIds((prev) =>
                                isSelected
                                  ? prev.filter((id) => id !== tag.id)
                                  : [...prev, tag.id]
                              );
                            }}
                            className="flex items-center gap-2 px-2.5 py-2 rounded-xl text-xs font-semibold text-slate-700 hover:bg-slate-50 transition-colors text-left w-full"
                          >
                            <input
                              type="checkbox"
                              checked={isSelected}
                              readOnly
                              className="rounded border-slate-350 text-brand-primary focus:ring-brand-primary cursor-pointer shrink-0"
                            />
                            <span
                              className="inline-block w-3 h-3 rounded-full shrink-0 shadow-sm border border-black/10"
                              style={{ backgroundColor: tag.color }}
                            />
                            <span className="truncate">{tag.name}</span>
                          </button>
                        );
                      })
                    )}
                  </div>
                </>
              )}
            </div>
          </div>
        </div>

        {/* Active Filters Indicator */}
        {(searchQuery || selectedPriority !== 'all' || selectedCategory !== 'all' || selectedTagIds.length > 0) && (
          <div className="mt-3 flex flex-wrap items-center gap-2 border-t border-slate-200 pt-3">
            <span className="text-xs text-slate-600 font-medium">Filtros ativos:</span>
            {searchQuery && (
              <span className="inline-flex items-center gap-2 rounded-full bg-brand-primary/10 px-2.5 py-0.5 text-xs font-medium text-brand-primary">
                Título: "{searchQuery}"
                <button onClick={() => setSearchQuery('')} className="hover:text-brand-primary">
                  <X size={14} />
                </button>
              </span>
            )}
            {selectedPriority !== 'all' && (
              <span className="inline-flex items-center gap-2 rounded-full bg-amber-100 px-2.5 py-0.5 text-xs font-medium text-amber-700">
                {priorities.find(p => p.value === selectedPriority)?.label}
                <button onClick={() => setSelectedPriority('all')} className="hover:text-amber-900">
                  <X size={14} />
                </button>
              </span>
            )}
            {selectedCategory !== 'all' && (
              <span className="inline-flex items-center gap-2 rounded-full bg-brand-primary/10 px-2.5 py-0.5 text-xs font-medium text-brand-primary">
                {categories.find(c => c.id === selectedCategory)?.name}
                <button onClick={() => setSelectedCategory('all')} className="hover:text-brand-primary-dark">
                  <X size={14} />
                </button>
              </span>
            )}
            {selectedTagIds.map(tagId => {
              const tagObj = availableTags.find(t => t.id === tagId);
              if (!tagObj) return null;
              return (
                <span
                  key={tagId}
                  className="inline-flex items-center gap-1.5 rounded-full px-2.5 py-0.5 text-xs font-medium border"
                  style={{
                    backgroundColor: `${tagObj.color}15`,
                    color: tagObj.color,
                    borderColor: `${tagObj.color}35`
                  }}
                >
                  {tagObj.name}
                  <button
                    onClick={() => setSelectedTagIds(prev => prev.filter(id => id !== tagId))}
                    className="hover:opacity-75"
                  >
                    <X size={13} />
                  </button>
                </span>
              );
            })}
            <button
              onClick={() => {
                setSearchQuery('');
                setSelectedPriority('all');
                setSelectedCategory('all');
                setSelectedTagIds([]);
              }}
              className="ml-auto text-xs text-slate-500 hover:text-slate-700 font-medium"
            >
              Limpar Filtros
            </button>
          </div>
        )}
      </div>

      {/* Table */}
      <div className="bg-white rounded-2xl border border-slate-200 shadow-sm p-6 flex flex-col gap-4">
        {loading ? (
          <SkeletonTable />
        ) : (
          <>
            <TicketsTable tickets={sortedTickets} />
            <div className="flex items-center justify-between border-t border-slate-100 pt-4 mt-2">
              <button
                type="button"
                onClick={() => setCurrentPage((prev) => Math.max(0, prev - 1))}
                disabled={currentPage === 0 || loading}
                className="px-4 py-2 text-xs font-semibold text-slate-700 bg-slate-50 hover:bg-slate-100 border border-slate-200 disabled:opacity-50 disabled:cursor-not-allowed rounded-xl transition-colors"
              >
                Anterior
              </button>
              <span className="text-xs text-slate-500 font-semibold">
                Página {currentPage + 1} de {totalPages || 1}
              </span>
              <button
                type="button"
                onClick={() => setCurrentPage((prev) => Math.min(totalPages - 1, prev + 1))}
                disabled={currentPage >= totalPages - 1 || loading}
                className="px-4 py-2 text-xs font-semibold text-slate-700 bg-slate-50 hover:bg-slate-100 border border-slate-200 disabled:opacity-50 disabled:cursor-not-allowed rounded-xl transition-colors"
              >
                Seguinte
              </button>
            </div>
          </>
        )}
      </div>
    </main>
  );
}

