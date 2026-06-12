import { useState, useEffect } from 'react';
import type { Item } from '../../types/models';
import SearchableDropdown from '../../components/SearchableDropdown';
import { getItems } from '../../services/inventoryService';

interface Props {
  items: Item[];
  // UUID do item selecionado (string) — alinhado com TicketRequestDTO.requestedItemId
  requestedItemId?: string;
  requestedQuantity: number;
  // Retorna o UUID e o nome do item para permitir geração do título automático
  onItemChange: (id: string | undefined, name: string | undefined) => void;
  onQuantityChange: (q: number) => void;
}

const labelCls = 'text-xs font-bold uppercase tracking-widest text-slate-400';

/**
 * Componente encarregue dos campos de solicitação de itens de inventário no ecrã de novo chamado.
 * Utiliza o componente SearchableDropdown para permitir a pesquisa remota assíncrona de itens da API.
 */
export default function RequestItemFields({
  items, requestedItemId, requestedQuantity, onItemChange, onQuantityChange,
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

  const handleDropdownChange = (val: string) => {
    if (val === 'OUTRO') {
      onItemChange('OUTRO', undefined);
    } else {
      const selectedItem = localItems.find((item) => item.id === val);
      onItemChange(val, selectedItem?.name);
    }
  };

  return (
    <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
      {/* Dropdown de Item Solicitado com Pesquisa Remota Assíncrona */}
      <div className="flex flex-col gap-1.5">
        <label className={labelCls}>Item Solicitado</label>
        <SearchableDropdown
          options={dropdownOptions}
          value={requestedItemId || ''}
          onChange={handleDropdownChange}
          onSearchChange={handleFetchItemsRemote}
          placeholder="Selecione um item..."
        />
      </div>

      {/* Quantidade Solicitada */}
      <div className="flex flex-col gap-1.5">
        <label className={labelCls}>Quantidade</label>
        <input
          type="number"
          min={1}
          className="w-full rounded-xl border border-slate-200 bg-white px-4 py-2.5 text-sm text-slate-800 shadow-sm focus:outline-none focus:ring-2 focus:ring-brand-primary focus:border-brand-primary transition"
          value={requestedQuantity}
          onChange={(e) => onQuantityChange(Math.max(1, Number(e.target.value) || 1))}
        />
      </div>
    </div>
  );
}
