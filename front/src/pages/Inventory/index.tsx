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
import { getItems } from '../../services/inventoryService';
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
  const [searchTerm, setSearchTerm] = useState('');
  const [isSortMenuOpen, setIsSortMenuOpen] = useState(false);
  const sortMenuRef = useRef<HTMLDivElement | null>(null);

  const lowStockOnly = searchParams.get('status') === LOW_STOCK_STATUS_PARAM;

  const loadItems = useCallback(async () => {
    setLoading(true);
    try {
      const data = await getItems({
        ...mapSortOptionToApi(sortOption),
        lowStockOnly,
      });
      setItems(data);
    } catch {
      toast.error('Erro ao carregar itens do inventário.');
      setItems([]);
    } finally {
      setLoading(false);
    }
  }, [sortOption, lowStockOnly]);

  useEffect(() => {
    void loadItems();
  }, [loadItems]);

  function handleBatchAdded() {
    setShowBatchModal(false);
    setPreselectedItemId(undefined);
    void loadItems(); // Recarrega a lista após adicionar lote
  }

  function openBatchModalForItem(itemId: string, e: React.MouseEvent) {
    e.stopPropagation(); // Prevent navigation to item details
    setPreselectedItemId(itemId);
    setShowBatchModal(true);
  }

  function openBatchModal() {
    setPreselectedItemId(undefined);
    setShowBatchModal(true);
  }

  // Filtro textual leve no frontend para agilizar a localização visual de itens.
  const normalizedSearchTerm = searchTerm.trim().toLowerCase();
  const filteredItems = normalizedSearchTerm
    ? items.filter((item) => item.name.toLowerCase().includes(normalizedSearchTerm)
      || item.itemCategoryName.toLowerCase().includes(normalizedSearchTerm))
    : items;

  // A ordenação está sempre ativa na listagem (incluindo o padrão Nome A-Z).
  const isSortActive = true;
  const currentSortLabel = SORT_OPTIONS.find((option) => option.value === sortOption)?.label ?? 'Nome (A-Z)';

  // Remove somente o filtro vindo da URL, mantendo os demais parâmetros de busca.
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
    // Fecha o menu de ordenação quando o usuário clicar fora da área do dropdown.
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
        description="Controle de estoque, entradas de lote e disponibilidade de itens para atendimento dos chamados."
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
        <div className="mb-4 flex flex-wrap items-center gap-3">
          <div className="relative w-full max-w-md">
              <Search size={16} className="pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 text-slate-400" />
              <input
                type="text"
                value={searchTerm}
                onChange={(e) => setSearchTerm(e.target.value)}
                placeholder="Buscar por nome ou categoria"
                className="w-full rounded-xl border border-slate-200 bg-white py-2.5 pl-9 pr-3 text-sm text-slate-800 shadow-sm placeholder-slate-400 transition focus:border-brand-primary focus:outline-none focus:ring-2 focus:ring-brand-primary/20"
              />
          </div>

          {/* Dropdown único de ordenação com ícone e rótulo no mesmo alvo de clique. */}
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

        {loading ? (
          <div className="p-12 text-center">
            <div className="animate-pulse space-y-3">
              <div className="h-4 bg-slate-200 rounded w-3/4 mx-auto" />
              <div className="h-4 bg-slate-200 rounded w-1/2 mx-auto" />
            </div>
          </div>
        ) : filteredItems.length === 0 ? (
          <p className="text-center text-slate-400 py-12 text-sm">
            Nenhum item cadastrado.
          </p>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full table-auto text-sm">
              <thead>
                <tr className="border-b border-slate-200 bg-slate-50">
                  <th className="px-4 py-3 text-left text-xs font-semibold uppercase tracking-wider text-slate-500">Nome</th>
                  <th className="px-4 py-3 text-left text-xs font-semibold uppercase tracking-wider text-slate-500">Categoria</th>
                  <th className="px-4 py-3 text-left text-xs font-semibold uppercase tracking-wider text-slate-500">Estoque Atual</th>
                  <th className="px-4 py-3 text-left text-xs font-semibold uppercase tracking-wider text-slate-500">Status</th>
                  <th className="px-4 py-3 text-center text-xs font-semibold uppercase tracking-wider text-slate-500">Ações</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {filteredItems.map((item) => (
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
                  </tr>
                ))}
              </tbody>
            </table>
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

