// Página de listagem de inventário com tabela e ações
import { useCallback, useEffect, useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import {
  ArrowDownAZ,
  ArrowDownWideNarrow,
  ArrowUpAZ,
  ArrowUpNarrowWide,
  Clock3,
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

  const hasActiveFilters = lowStockOnly || sortOption !== 'name-asc' || normalizedSearchTerm.length > 0;

  function clearFilters() {
    setSortOption('name-asc');
    setSearchTerm('');
    if (lowStockOnly) {
      setSearchParams({}, { replace: true });
    }
  }

  const sortDirectionIcon =
    sortOption === 'name-asc' ? <ArrowUpAZ size={16} className="text-cyan-700" />
      : sortOption === 'name-desc' ? <ArrowDownAZ size={16} className="text-cyan-700" />
        : sortOption === 'stock-desc' ? <ArrowDownWideNarrow size={16} className="text-cyan-700" />
          : sortOption === 'stock-asc' ? <ArrowUpNarrowWide size={16} className="text-cyan-700" />
            : <Clock3 size={16} className="text-cyan-700" />;

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

      {/* Tabela de itens */}
      <div className="bg-white rounded-2xl border border-slate-200 shadow-sm p-6">
        <div className="mb-4 flex flex-col gap-3 lg:flex-row lg:items-center lg:justify-between">
          <div className="flex items-center gap-2 flex-wrap">
            {lowStockOnly && (
              <span className="inline-flex items-center rounded-full bg-cyan-50 px-3 py-1 text-xs font-semibold text-cyan-700">
                Filtro ativo: Estoque baixo (&lt;= {LOW_STOCK_THRESHOLD})
              </span>
            )}
            {hasActiveFilters && (
              <button
                type="button"
                onClick={clearFilters}
                className="inline-flex items-center rounded-lg border border-brand-primary/20 bg-brand-secondary/30 px-3 py-1.5 text-xs font-semibold text-brand-primary transition-colors hover:bg-brand-secondary/50"
              >
                Limpar Filtros
              </button>
            )}
          </div>

          <div className="flex w-full flex-col gap-2 sm:flex-row lg:w-auto">
            <div className="relative min-w-[260px]">
              <Search size={16} className="pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 text-slate-400" />
              <input
                type="text"
                value={searchTerm}
                onChange={(e) => setSearchTerm(e.target.value)}
                placeholder="Buscar por nome ou categoria"
                className="w-full rounded-xl border border-slate-200 bg-white py-2.5 pl-9 pr-3 text-sm text-slate-800 shadow-sm placeholder-slate-400 transition focus:border-primary focus:outline-none focus:ring-2 focus:ring-primary"
              />
            </div>

            <div className="flex items-center gap-2">
              <span className="inline-flex items-center gap-1 rounded-lg border border-cyan-200 bg-cyan-50 px-2.5 py-2 text-xs font-semibold text-cyan-700">
                {sortDirectionIcon}
                Ordenação
              </span>
              <select
                value={sortOption}
                onChange={(e) => setSortOption(e.target.value as InventorySortOption)}
                className="rounded-xl border border-slate-200 bg-white px-3 py-2.5 text-sm text-slate-700 shadow-sm outline-none transition focus:border-primary focus:ring-2 focus:ring-primary"
              >
                <option value="name-asc">Nome (A-Z)</option>
                <option value="name-desc">Nome (Z-A)</option>
                <option value="stock-desc">Maior Estoque</option>
                <option value="stock-asc">Menor Estoque</option>
                <option value="oldest-batch-asc">Mais Antigos no Estoque</option>
              </select>
            </div>
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
                  <th className="px-4 py-3 text-left text-[10px] font-bold uppercase tracking-widest text-slate-400">Nome</th>
                  <th className="px-4 py-3 text-left text-[10px] font-bold uppercase tracking-widest text-slate-400">Categoria</th>
                  <th className="px-4 py-3 text-left text-[10px] font-bold uppercase tracking-widest text-slate-400">Estoque Atual</th>
                  <th className="px-4 py-3 text-left text-[10px] font-bold uppercase tracking-widest text-slate-400">Status</th>
                  <th className="px-4 py-3 text-center text-[10px] font-bold uppercase tracking-widest text-slate-400">Ações</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {filteredItems.map((item) => (
                  <tr
                    key={item.id}
                    onClick={() => navigate(`/inventory/${item.id}`)}
                    className="cursor-pointer transition-colors hover:bg-cyan-50/40"
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
                        <span className="inline-flex items-center rounded-full bg-cyan-100 px-2.5 py-0.5 text-xs font-medium text-cyan-700">
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

