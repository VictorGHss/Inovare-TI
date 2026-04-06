// Página de listagem de inventário com tabela e ações
import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { PlusCircle, Package, PackagePlus } from 'lucide-react';
import { toast } from 'react-toastify';
import { getItems } from '../../services/financeService';
import type { Item } from '../../types/domain';
import AddBatchModal from './AddBatchModal';
import PageHero from '../../components/PageHero';

export default function Inventory() {
  const navigate = useNavigate();
  const [items, setItems] = useState<Item[]>([]);
  const [loading, setLoading] = useState(true);
  const [showBatchModal, setShowBatchModal] = useState(false);
  const [preselectedItemId, setPreselectedItemId] = useState<string | undefined>(undefined);

  useEffect(() => {
    loadItems();
  }, []);

  async function loadItems() {
    setLoading(true);
    try {
      const data = await getItems();
      setItems(data);
    } catch {
      toast.error('Erro ao carregar itens do inventário.');
      setItems([]);
    } finally {
      setLoading(false);
    }
  }

  function handleBatchAdded() {
    setShowBatchModal(false);
    setPreselectedItemId(undefined);
    loadItems(); // Recarrega a lista após adicionar lote
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
        {loading ? (
          <div className="p-12 text-center">
            <div className="animate-pulse space-y-3">
              <div className="h-4 bg-slate-200 rounded w-3/4 mx-auto" />
              <div className="h-4 bg-slate-200 rounded w-1/2 mx-auto" />
            </div>
          </div>
        ) : items.length === 0 ? (
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
                {items.map((item) => (
                  <tr
                    key={item.id}
                    onClick={() => navigate(`/inventory/${item.id}`)}
                    className="cursor-pointer transition-colors hover:bg-orange-50/40"
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
                      ) : (
                        <span className="inline-flex items-center rounded-full bg-brand-secondary/40 px-2.5 py-0.5 text-xs font-medium text-brand-primary-dark">
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
