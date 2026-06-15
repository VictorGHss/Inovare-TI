// Página de listagem de inventário com tabela e ações
import { useCallback, useEffect, useRef, useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import {
  X,
  ArrowDownWideNarrow,
  ChevronDown,
  Search,
  PlusCircle,
  Package,
  PackagePlus,
} from 'lucide-react';
import { toast } from 'react-toastify';
import { getItems, getObsoleteItems } from '../../services/inventoryService';
import type { Item } from '../../types/models';
import AddBatchModal from './AddBatchModal';
import PageHero from '../../components/PageHero';

type InventorySortOption = 'name-asc' | 'name-desc' | 'stock-desc' | 'stock-asc' | 'oldest-batch-asc';

const SORT_OPTIONS: Array<{ value: InventorySortOption; label: string }> = [
  { value: 'name-asc', label: 'Nome (A-Z)' },
  { value: 'name-desc', label: 'Nome (Z-A)' },
  { value: 'stock-desc', label: 'Maior Estoque' },
  { value: 'stock-asc', label: 'Menor Estoque' },
  { value: 'oldest-batch-asc', label: 'Mais Antigos' },
];

const LOW_STOCK_STATUS_PARAM = 'low-stock';
const LOW_STOCK_THRESHOLD = 3;

function mapSortOptionToApi(sortOption: InventorySortOption): {
  sortField: 'name' | 'currentStock' | 'oldestBatchEntryDate';
  sortDirection: 'ASC' | 'DESC';
} {
  switch (sortOption) {
    case 'name-desc':
      return { sortField: 'name', sortDirection: 'DESC' };
    case 'stock-desc':
      return { sortField: 'currentStock', sortDirection: 'DESC' };
    case 'stock-asc':
      return { sortField: 'currentStock', sortDirection: 'ASC' };
    case 'oldest-batch-asc':
      return { sortField: 'oldestBatchEntryDate', sortDirection: 'ASC' };
    case 'name-asc':
    default:
      return { sortField: 'name', sortDirection: 'ASC' };
  }
}

export default function Inventory() {
  const navigate = useNavigate();
  const [searchParams, setSearchParams] = useSearchParams();
  const [items, setItems] = useState<Item[]>([]);
  const [loading, setLoading] = useState(true);
  const [showBatchModal, setShowBatchModal] = useState(false);
  const [preselectedItemId, setPreselectedItemId] = useState<string | undefined>(undefined);
  const [sortOption, setSortOption] = useState<InventorySortOption>('name-asc');
  
  // Controle de Tabs para alternar entre listagem geral e itens obsoletos
  const [activeTab, setActiveTab] = useState<'all' | 'obsolete'>('all');
  
  // Estados para a pesquisa global com debounce na tabela de inventário
  const [searchQuery, setSearchQuery] = useState('');
  const [debouncedSearch, setDebouncedSearch] = useState('');
  
  const [isSortMenuOpen, setIsSortMenuOpen] = useState(false);
  const sortMenuRef = useRef<HTMLDivElement | null>(null);
  
  const [currentPage, setCurrentPage] = useState(0);
  const [totalPages, setTotalPages] = useState(1);

  const lowStockOnly = searchParams.get('status') === LOW_STOCK_STATUS_PARAM;

  // Efeito de debounce de 300ms para a pesquisa global de itens
  useEffect(() => {
    const handler = setTimeout(() => {
      setDebouncedSearch(searchQuery);
    }, 300);

    return () => {
      clearTimeout(handler);
    };
  }, [searchQuery]);

  const loadItems = useCallback(async () => {
    setLoading(true);
    try {
      if (activeTab === 'all') {
        // Faz a chamada à API carregando todos os itens com paginação e ordenação
        const data = await getItems({
          ...mapSortOptionToApi(sortOption),
          lowStockOnly,
          page: currentPage,
          search: debouncedSearch,
        });
        setItems(data.content);
        setTotalPages(data.totalPages);
      } else {
        // Carrega somente os itens com obsolescência detectada (Fim de Vida Útil EOL)
        const data = await getObsoleteItems({
          page: currentPage,
        });
        setItems(data.content);
        setTotalPages(data.totalPages);
      }
    } catch {
      toast.error('Erro ao carregar itens do inventário.');
      setItems([]);
    } finally {
      setLoading(false);
    }
  }, [sortOption, lowStockOnly, currentPage, debouncedSearch, activeTab]);

  useEffect(() => {
    void loadItems();
  }, [loadItems]);

  // Reseta a página para a primeira ao iniciar uma nova busca, alterar filtros ou trocar de tab
  useEffect(() => {
    setCurrentPage(0);
  }, [sortOption, lowStockOnly, searchQuery, activeTab]);

  function handleBatchAdded() {
    setShowBatchModal(false);
    setPreselectedItemId(undefined);
    void loadItems();
  }

  function openBatchModalForItem(itemId: string, e: React.MouseEvent) {
    e.stopPropagation();
    setPreselectedItemId(itemId);
    setShowBatchModal(true);
  }

  function openBatchModal() {
    setPreselectedItemId(undefined);
    setShowBatchModal(true);
  }

  const filteredItems = items;
  const isSortActive = true;
  const currentSortLabel = SORT_OPTIONS.find((option) => option.value === sortOption)?.label ?? 'Nome (A-Z)';

  function clearLowStockFilter() {
    const nextParams = new URLSearchParams(searchParams);
    nextParams.delete('status');
    setSearchParams(nextParams, { replace: true });
  }

  function handleSelectSort(nextSortOption: InventorySortOption) {
    setSortOption(nextSortOption);
    setIsSortMenuOpen(false);
  }

  useEffect(() => {
    function handleOutsideClick(event: MouseEvent) {
      if (!sortMenuRef.current) {
        return;
      }
      if (event.target instanceof Node && !sortMenuRef.current.contains(event.target)) {
        setIsSortMenuOpen(false);
      }
    }

    document.addEventListener('mousedown', handleOutsideClick);
    return () => document.removeEventListener('mousedown', handleOutsideClick);
  }, []);

  return (
    <main className="w-full max-w-full px-4 sm:px-6 lg:px-8 py-8">
      <PageHero
        eyebrow="Suprimentos"
        title="Inventário"
        description="Controle de estoque, entradas de lote, obsolescência de ativos e disponibilidade de itens para atendimento dos chamados."
        actions={(
          <>
            <button
              onClick={openBatchModal}
              className="flex items-center gap-2 bg-brand-primary hover:bg-brand-primary-dark text-white text-sm font-semibold px-4 py-2.5 rounded-xl transition-colors"
            >
              <Package size={17} />
              Adicionar Estoque
            </button>
            <button
              onClick={() => navigate('/inventory/new')}
              className="flex items-center gap-2 bg-brand-secondary/30 hover:bg-brand-secondary/50 text-brand-primary-dark text-sm font-semibold px-4 py-2.5 rounded-xl transition-colors"
            >
              <PlusCircle size={17} />
              Novo Item
            </button>
          </>
        )}
      />

      {lowStockOnly && (
        <div className="mb-4">
          <span className="inline-flex items-center gap-2 rounded-full border border-brand-primary/20 bg-brand-primary/10 px-3 py-1 text-xs font-semibold text-brand-primary">
            Estoque Baixo
            <button
              type="button"
              onClick={clearLowStockFilter}
              className="inline-flex h-4 w-4 items-center justify-center rounded-full text-brand-primary transition-colors hover:bg-brand-primary/20"
              aria-label="Remover filtro de estoque baixo"
            >
              <X size={12} />
            </button>
          </span>
        </div>
      )}

      {/* Tabela de itens */}
      <div className="bg-white rounded-2xl border border-slate-200 shadow-sm p-6">
        
        {/* Abas de Navegação (Design System) */}
        <div className="flex border-b border-slate-100 mb-6 gap-6">
          <button
            type="button"
            onClick={() => setActiveTab('all')}
            className={`pb-3 text-sm font-bold border-b-2 transition-all ${
              activeTab === 'all'
                ? 'border-brand-primary text-brand-primary'
                : 'border-transparent text-slate-500 hover:text-slate-700'
            }`}
          >
            Todos os Itens
          </button>
          <button
            type="button"
            onClick={() => setActiveTab('obsolete')}
            className={`pb-3 text-sm font-bold border-b-2 transition-all flex items-center gap-2 ${
              activeTab === 'obsolete'
                ? 'border-brand-primary text-brand-primary'
                : 'border-transparent text-slate-500 hover:text-slate-700'
            }`}
          >
            Itens Obsoletos / Alerta EOL
            <span className="rounded-full bg-rose-50 px-2 py-0.5 text-[10px] font-bold text-rose-600 border border-rose-100">
              Alerta
            </span>
          </button>
        </div>

        {activeTab === 'all' && (
          <div className="mb-4 flex flex-wrap items-center gap-3">
            <div className="relative w-full max-w-md">
                <Search size={16} className="pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 text-slate-400" />
                <input
                  type="text"
                  value={searchQuery}
                  onChange={(e) => setSearchQuery(e.target.value)}
                  placeholder="Buscar por nome ou categoria..."
                  className="w-full rounded-xl border border-slate-200 bg-white py-2.5 pl-9 pr-3 text-sm text-slate-800 shadow-sm placeholder-slate-400 transition focus:border-brand-primary focus:outline-none focus:ring-2 focus:ring-brand-primary/20"
                />
            </div>

            <div ref={sortMenuRef} className="relative w-full max-w-xs">
              <button
                type="button"
                onClick={() => setIsSortMenuOpen((previousState) => !previousState)}
                className={`inline-flex w-full items-center justify-between rounded-xl border bg-white px-3 py-2.5 text-sm font-medium shadow-sm transition-colors ${isSortActive || isSortMenuOpen
                  ? 'border-brand-primary text-brand-primary'
                  : 'border-slate-200 text-slate-700 hover:bg-slate-50'}`}
              >
                <span className="inline-flex items-center gap-2">
                  <ArrowDownWideNarrow size={16} />
                  {`Ordenar por: ${currentSortLabel}`}
                </span>
                <ChevronDown size={16} className={isSortMenuOpen ? 'rotate-180 transition-transform' : 'transition-transform'} />
              </button>

              {isSortMenuOpen && (
                <div className="absolute right-0 z-20 mt-2 w-full rounded-xl border border-slate-200 bg-white p-1.5 shadow-lg">
                  {SORT_OPTIONS.map((option) => (
                    <button
                      key={option.value}
                      type="button"
                      onClick={() => handleSelectSort(option.value)}
                      className={`w-full rounded-lg px-3 py-2 text-left text-sm transition-colors ${sortOption === option.value
                        ? 'bg-brand-primary/10 text-brand-primary'
                        : 'text-slate-700 hover:bg-slate-50'}`}
                    >
                      {option.label}
                    </button>
                  ))}
                </div>
              )}
            </div>
          </div>
        )}

        {loading ? (
          <div className="p-12 text-center">
            <div className="animate-pulse space-y-3">
              <div className="h-4 bg-slate-200 rounded w-3/4 mx-auto" />
              <div className="h-4 bg-slate-200 rounded w-1/2 mx-auto" />
            </div>
          </div>
        ) : filteredItems.length === 0 ? (
          <p className="text-center text-slate-400 py-12 text-sm">
            {activeTab === 'all' ? 'Nenhum item cadastrado.' : 'Nenhum item obsoleto identificado no inventário.'}
          </p>
        ) : (
          <div className="flex flex-col gap-4">
            <div className="overflow-x-auto">
              <table className="w-full table-auto text-sm">
                <thead>
                  <tr className="border-b border-slate-200 bg-slate-50">
                    <th className="px-4 py-3 text-left text-xs font-semibold uppercase tracking-wider text-slate-500">Nome</th>
                    <th className="px-4 py-3 text-left text-xs font-semibold uppercase tracking-wider text-slate-500">Categoria</th>
                    {activeTab === 'all' ? (
                      <>
                        <th className="px-4 py-3 text-left text-xs font-semibold uppercase tracking-wider text-slate-500">Estoque Atual</th>
                        <th className="px-4 py-3 text-left text-xs font-semibold uppercase tracking-wider text-slate-500">Status</th>
                        <th className="px-4 py-3 text-center text-xs font-semibold uppercase tracking-wider text-slate-500">Ações</th>
                      </>
                    ) : (
                      <>
                        <th className="px-4 py-3 text-left text-xs font-semibold uppercase tracking-wider text-slate-500">Data de Fabricação</th>
                        <th className="px-4 py-3 text-left text-xs font-semibold uppercase tracking-wider text-slate-500">Vida Útil Estimada</th>
                        <th className="px-4 py-3 text-center text-xs font-semibold uppercase tracking-wider text-slate-500">Ciclo de Vida</th>
                      </>
                    )}
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-100">
                  {filteredItems.map((item) => {
                    const specs = item.specifications || {};
                    const dataFabricacao = specs.data_fabricacao ? String(specs.data_fabricacao) : 'Não cadastrada';
                    const tempoVidaUtil = specs.tempo_vida_util_anos ? `${specs.tempo_vida_util_anos} anos` : 'Não informado';

                    return (
                      <tr
                        key={item.id}
                        onClick={() => navigate(`/inventory/${item.id}`)}
                        className="cursor-pointer transition-colors hover:bg-slate-50"
                      >
                        <td className="px-4 py-3 font-medium text-slate-800">
                          {item.name}
                        </td>
                        <td className="px-4 py-3 text-slate-500">
                          {item.itemCategoryName}
                        </td>
                        {activeTab === 'all' ? (
                          <>
                            <td className="px-4 py-3 text-slate-600 font-semibold">
                              {item.currentStock}
                            </td>
                            <td className="px-4 py-3">
                              {item.currentStock === 0 ? (
                                <span className="inline-flex items-center rounded-full bg-red-100 px-2.5 py-0.5 text-xs font-medium text-red-700">
                                  Sem Estoque
                                </span>
                              ) : item.currentStock <= LOW_STOCK_THRESHOLD ? (
                                <span className="inline-flex items-center rounded-full bg-amber-100 px-2.5 py-0.5 text-xs font-medium text-amber-700">
                                  Estoque Baixo
                                </span>
                              ) : (
                                <span className="inline-flex items-center rounded-full bg-brand-secondary px-2.5 py-0.5 text-xs font-medium text-brand-primary-dark">
                                  Em Estoque
                                </span>
                              )}
                            </td>
                            <td className="px-4 py-3">
                              <div className="flex items-center justify-center">
                                <button
                                  onClick={(e) => openBatchModalForItem(item.id, e)}
                                  className="inline-flex items-center gap-1.5 rounded-xl bg-brand-primary px-3 py-1.5 text-xs font-semibold text-white transition-colors hover:bg-brand-primary-dark"
                                  title="Registrar nova entrada de lote"
                                >
                                  <PackagePlus size={15} />
                                  Nova Entrada
                                </button>
                              </div>
                            </td>
                          </>
                        ) : (
                          <>
                            <td className="px-4 py-3 text-slate-600 font-medium">
                              {dataFabricacao}
                            </td>
                            <td className="px-4 py-3 text-slate-600 font-semibold">
                              {tempoVidaUtil}
                            </td>
                            <td className="px-4 py-3 text-center">
                              <span className="inline-flex items-center rounded-full bg-rose-50 border border-rose-200 px-2.5 py-1 text-xs font-bold text-rose-700 shadow-sm">
                                ⚠️ Fim de Vida Útil (EOL)
                              </span>
                            </td>
                          </>
                        )}
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>

            {/* Barra de Paginação */}
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
          </div>
        )}
      </div>

      {/* Modal de entrada de lote */}
      <AddBatchModal
        isOpen={showBatchModal}
        onClose={() => setShowBatchModal(false)}
        onSuccess={handleBatchAdded}
        items={items}
        preselectedItemId={preselectedItemId}
      />
    </main>
  );
}

