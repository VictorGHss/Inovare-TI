import { useState, useEffect } from 'react';
import { Plus, Trash2 } from 'lucide-react';
import type { Item } from '../../types/models';
import SearchableDropdown from '../../components/SearchableDropdown';
import { getItems } from '../../services/inventoryService';

export interface RequestedItemState {
  itemId: string;
  quantity: number;
  itemName?: string;
}

interface Props {
  items: Item[];
  requestedItems: RequestedItemState[];
  onChange: (items: RequestedItemState[]) => void;
}

const labelCls = 'text-xs font-bold uppercase tracking-widest text-slate-400';

/**
 * Componente encarregue dos campos de solicitação de itens de inventário no ecrã de novo chamado.
 * Suporta a solicitação de múltiplos insumos de forma dinâmica através do botão "+ Adicionar Outro Item".
 */
export default function RequestItemFields({
  items,
  requestedItems,
  onChange,
}: Props) {
  // Estado local para armazenar a lista de itens carregada dinamicamente via pesquisa remota
  const [localItems, setLocalItems] = useState<Item[]>(items);

  // Sincroniza o estado local com os itens fornecidos pelo ecrã pai
  useEffect(() => {
    setLocalItems(items);
  }, [items]);

  /**
   * Função para carregar os itens do inventário remotamente com base no termo de pesquisa digitado pelo utilizador.
   * 
   * @param searchTerm O termo de pesquisa introduzido pelo utilizador no dropdown.
   */
  const handleFetchItemsRemote = async (searchTerm: string) => {
    try {
      const response = await getItems({
        page: 0,
        size: 15,
        search: searchTerm,
      });
      setLocalItems(response.content);
    } catch (error) {
      console.error('Erro ao efetuar pesquisa remota de itens de inventário:', error);
    }
  };

  // Mapeia os itens locais para o formato do dropdown de forma a suportar o design unificado,
  // injetando a opção especial de "Outro" no final da lista.
  const dropdownOptions = [
    ...localItems.map((item) => ({
      id: item.id,
      name: `${item.name} (Estoque: ${item.currentStock})`,
    })),
    { id: 'OUTRO', name: '📝 Outro (Especificar na descrição)' }
  ];

  const handleItemChange = (index: number, itemId: string) => {
    const updated = [...requestedItems];
    if (itemId === 'OUTRO') {
      updated[index] = { ...updated[index], itemId: 'OUTRO', itemName: undefined };
    } else {
      const selectedItem = localItems.find((item) => item.id === itemId);
      updated[index] = { ...updated[index], itemId, itemName: selectedItem?.name };
    }
    onChange(updated);
  };

  const handleQuantityChange = (index: number, quantity: number) => {
    const updated = [...requestedItems];
    updated[index] = { ...updated[index], quantity: Math.max(1, quantity) };
    onChange(updated);
  };

  const handleAddItem = () => {
    onChange([...requestedItems, { itemId: '', quantity: 1 }]);
  };

  const handleRemoveItem = (index: number) => {
    const updated = requestedItems.filter((_, i) => i !== index);
    onChange(updated);
  };

  return (
    <div className="flex flex-col gap-4">
      <div className="flex flex-col gap-3">
        {requestedItems.map((reqItem, index) => (
          <div key={index} className="flex flex-col sm:flex-row gap-4 items-end border-b border-slate-100 pb-4 last:border-0 last:pb-0">
            {/* Dropdown de Item Solicitado */}
            <div className="flex flex-col gap-1.5 flex-1 w-full">
              <label className={labelCls}>Item Solicitado #{index + 1}</label>
              <SearchableDropdown
                options={dropdownOptions}
                value={reqItem.itemId}
                onChange={(val) => handleItemChange(index, val)}
                onSearchChange={handleFetchItemsRemote}
                placeholder="Selecione um item..."
              />
            </div>

            {/* Quantidade Solicitada */}
            <div className="flex flex-col gap-1.5 w-full sm:w-32">
              <label className={labelCls}>Quantidade</label>
              <input
                type="number"
                min={1}
                className="w-full rounded-xl border border-slate-200 bg-white px-4 py-2.5 text-sm text-slate-800 shadow-sm focus:outline-none focus:ring-2 focus:ring-brand-primary focus:border-brand-primary transition"
                value={reqItem.quantity}
                onChange={(e) => handleQuantityChange(index, Number(e.target.value) || 1)}
              />
            </div>

            {/* Botão de Remover */}
            {requestedItems.length > 1 && (
              <button
                type="button"
                onClick={() => handleRemoveItem(index)}
                className="p-2.5 text-rose-500 hover:bg-rose-50 rounded-xl border border-transparent hover:border-rose-100 transition-colors flex items-center justify-center self-end sm:mb-0.5"
                title="Remover este item"
              >
                <Trash2 size={18} />
              </button>
            )}
          </div>
        ))}
      </div>

      {/* Botão Adicionar Outro Item */}
      <button
        type="button"
        onClick={handleAddItem}
        className="flex items-center justify-center gap-2 rounded-xl border border-dashed border-slate-350 px-4 py-2.5 text-sm font-semibold text-slate-600 hover:bg-slate-50 hover:text-slate-800 transition-colors self-start mt-2"
      >
        <Plus size={16} />
        Adicionar Outro Item
      </button>
    </div>
  );
}
