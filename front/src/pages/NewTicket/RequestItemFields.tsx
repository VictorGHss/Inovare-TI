// Fields for REQUEST tickets with typeahead/autocomplete
import { useState } from 'react';
import { Search, X } from 'lucide-react';
import type { Item } from '../../services/api';

interface Props {
  items: Item[];
  // UUID do item selecionado (string) — alinhado com TicketRequestDTO.requestedItemId
  requestedItemId?: string;
  requestedQuantity: number;
  inputCls: string;
  // Retorna o UUID e o nome do item para permitir geração do título automático
  onItemChange: (id: string | undefined, name: string | undefined) => void;
  onQuantityChange: (q: number) => void;
}

export default function RequestItemFields({
  items, requestedItemId, requestedQuantity, inputCls, onItemChange, onQuantityChange,
}: Props) {
  const [searchTerm, setSearchTerm] = useState(() => {
    return items.find((i) => i.id === requestedItemId)?.name ?? '';
  });
  const [isDropdownOpen, setIsDropdownOpen] = useState(false);

  const filteredItems = items.filter((item) =>
    item.name.toLowerCase().includes(searchTerm.toLowerCase())
  );

  const handleSelectItem = (item: Item) => {
    setSearchTerm(item.name);
    onItemChange(item.id, item.name);
    setIsDropdownOpen(false);
  };

  const handleSelectOther = () => {
    setSearchTerm('Outro (Especificar na descrição)');
    onItemChange('OUTRO', undefined);
    setIsDropdownOpen(false);
  };

  const handleClear = () => {
    setSearchTerm('');
    onItemChange(undefined, undefined);
    setIsDropdownOpen(true);
  };

  return (
    <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
      <div className="flex flex-col gap-1.5 relative">
        <label className="text-sm font-medium text-slate-700">Item Solicitado</label>
        <div className="relative">
          <div className="relative flex items-center">
            <Search size={16} className="absolute left-3 top-1/2 -translate-y-1/2 text-slate-400" />
            <input
              type="text"
              placeholder="Digite para buscar um item..."
              value={searchTerm}
              onChange={(e) => {
                setSearchTerm(e.target.value);
                setIsDropdownOpen(true);
              }}
              onFocus={() => setIsDropdownOpen(true)}
              className={`${inputCls} pl-9 pr-10`}
            />
            {searchTerm && (
              <button
                type="button"
                onClick={handleClear}
                className="absolute right-3 top-1/2 -translate-y-1/2 text-slate-400 hover:text-slate-600 transition"
                aria-label="Limpar campo"
              >
                <X size={16} />
              </button>
            )}
          </div>
          {isDropdownOpen && filteredItems.length > 0 && (
            <ul className="absolute z-50 w-full bg-white shadow-lg max-h-60 overflow-y-auto border border-slate-200 rounded-lg mt-1 top-full">
              {filteredItems.map((item) => (
                <li key={item.id}>
                  <button
                    type="button"
                    onClick={() => handleSelectItem(item)}
                    className="w-full text-left px-4 py-2.5 hover:bg-primary hover:text-white transition-colors text-sm"
                  >
                    {item.name}
                  </button>
                </li>
              ))}
              <li className="border-t border-slate-100" />
              <li>
                <button
                  type="button"
                  onClick={handleSelectOther}
                  className="w-full text-left px-4 py-2.5 hover:bg-amber-50 hover:text-amber-700 transition-colors text-sm font-medium text-amber-600"
                >
                  📝 Outro (Especificar na descrição)
                </button>
              </li>
            </ul>
          )}
          {isDropdownOpen && searchTerm.length > 0 && filteredItems.length === 0 && (
            <ul className="absolute z-50 w-full bg-white shadow-lg border border-slate-200 rounded-lg mt-1 top-full">
              <li className="px-4 py-2.5 text-sm text-slate-500">
                Nenhum item encontrado
              </li>
              <li className="border-t border-slate-100" />
              <li>
                <button
                  type="button"
                  onClick={handleSelectOther}
                  className="w-full text-left px-4 py-2.5 hover:bg-amber-50 hover:text-amber-700 transition-colors text-sm font-medium text-amber-600"
                >
                  📝 Outro (Especificar na descrição)
                </button>
              </li>
            </ul>
          )}
        </div>
      </div>
      <div className="flex flex-col gap-1.5">
        <label className="text-sm font-medium text-slate-700">Quantidade</label>
        <input
          type="number"
          min={1}
          className={inputCls}
          value={requestedQuantity}
          onChange={(e) => onQuantityChange(Math.max(1, Number(e.target.value)))}
        />
      </div>
    </div>
  );
}

