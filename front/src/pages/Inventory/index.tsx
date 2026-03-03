// Página de listagem de inventário com tabela e ações
import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { PlusCircle, Package } from 'lucide-react';
import { toast } from 'react-toastify';
import { getItems, type Item } from '../../services/api';
import AddBatchModal from './AddBatchModal';

export default function Inventory() {
  const navigate = useNavigate();
  const [items, setItems] = useState<Item[]>([]);
  const [loading, setLoading] = useState(true);
  const [showBatchModal, setShowBatchModal] = useState(false);

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
    loadItems(); // Recarrega a lista após adicionar lote
  }

  return (
    <main className="max-w-7xl mx-auto px-4 sm:px-6 py-8">
      {/* Cabeçalho da seção */}
      <div className="flex items-center justify-between mb-6">
        <h1 className="text-2xl font-bold text-slate-800">Inventário</h1>
        <div className="flex gap-3">
          <button
            onClick={() => setShowBatchModal(true)}
            className="flex items-center gap-2 bg-blue-600 hover:bg-blue-700 text-white text-sm font-semibold px-4 py-2.5 rounded-xl transition-colors"
          >
            <Package size={17} />
            Entrada de Lote
          </button>
          <button
            onClick={() => navigate('/inventory/new')}
            className="flex items-center gap-2 bg-primary hover:bg-primary-hover text-white text-sm font-semibold px-4 py-2.5 rounded-xl transition-colors"
          >
            <PlusCircle size={17} />
            Novo Item
          </button>
        </div>
      </div>

      {/* Tabela de itens */}
      <div className="bg-white rounded-xl border border-slate-200 shadow-sm">
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
            <table className="min-w-full text-sm">
              <thead className="bg-slate-50 text-slate-500 uppercase text-xs tracking-wider">
                <tr>
                  <th className="px-4 py-3 text-left">Nome</th>
                  <th className="px-4 py-3 text-left">Categoria</th>
                  <th className="px-4 py-3 text-left">Estoque Atual</th>
                  <th className="px-4 py-3 text-left">Status</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {items.map((item) => (
                  <tr key={item.id} className="hover:bg-slate-50 transition-colors">
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
                        <span className="inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium bg-red-100 text-red-700">
                          Sem Estoque
                        </span>
                      ) : (
                        <span className="inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium bg-green-100 text-green-700">
                          Em Estoque
                        </span>
                      )}
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
      />
    </main>
  );
}
