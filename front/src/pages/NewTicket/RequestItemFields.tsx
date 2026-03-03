// Campos de item e quantidade para chamados do tipo REQUEST
import type { Item, CreateTicketDto } from '../../services/api';

interface Props {
  items: Item[];
  itemId?: number;
  quantity: number;
  inputCls: string;
  onItemChange: (id?: number) => void;
  onQuantityChange: (q: number) => void;
}

export default function RequestItemFields({
  items, itemId, quantity, inputCls, onItemChange, onQuantityChange,
}: Props) {
  return (
    <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
      <div className="flex flex-col gap-1.5">
        <label className="text-sm font-medium text-slate-700">Item Solicitado</label>
        <select className={inputCls} value={itemId ?? ''}
          onChange={(e) => onItemChange(e.target.value ? Number(e.target.value) : undefined)}>
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
          value={quantity}
          onChange={(e) => onQuantityChange(Math.max(1, Number(e.target.value)))}
        />
      </div>
    </div>
  );
}

// Re-export type so consumers can import from one place
export type { CreateTicketDto };
