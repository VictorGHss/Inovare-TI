// Campos de item e quantidade para chamados do tipo REQUEST
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
  return (
    <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
      <div className="flex flex-col gap-1.5">
        <label className="text-sm font-medium text-slate-700">Item Solicitado</label>
        <select
          className={inputCls}
          value={requestedItemId ?? ''}
          onChange={(e) => {
            // Passa UUID como string e o nome do item para geração do título automático
            const selected = items.find((i) => i.id === e.target.value);
            onItemChange(selected?.id, selected?.name);
          }}
        >
          <option value="">Selecione um item</option>
          {items.map((i) => <option key={i.id} value={i.id}>{i.name}</option>)}
        </select>
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

