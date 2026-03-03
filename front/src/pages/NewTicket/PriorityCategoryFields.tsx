// Seletores de Prioridade e Categoria do chamado
import type { CreateTicketDto, TicketCategory } from '../../services/api';

interface Props {
  priority: CreateTicketDto['priority'];
  // categoryId é UUID (string) alinhado com o backend
  categoryId: string;
  categories: TicketCategory[];
  inputCls: string;
  onPriorityChange: (v: CreateTicketDto['priority']) => void;
  onCategoryChange: (id: string) => void;
}

const priorities = [
  { value: 'LOW', label: 'Baixa' },
  { value: 'NORMAL', label: 'Normal' },
  { value: 'HIGH', label: 'Alta' },
  { value: 'URGENT', label: 'Urgente' },
];

export default function PriorityCategoryFields({
  priority, categoryId, categories, inputCls, onPriorityChange, onCategoryChange,
}: Props) {
  return (
    <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
      <div className="flex flex-col gap-1.5">
        <label className="text-sm font-medium text-slate-700">Prioridade</label>
        <select className={inputCls} value={priority}
          onChange={(e) => onPriorityChange(e.target.value as CreateTicketDto['priority'])}>
          {priorities.map((p) => <option key={p.value} value={p.value}>{p.label}</option>)}
        </select>
      </div>
      <div className="flex flex-col gap-1.5">
        <label className="text-sm font-medium text-slate-700">
          Categoria <span className="text-red-500">*</span>
        </label>
        {/* Passa o UUID como string diretamente — sem conversão para número */}
        <select className={inputCls} value={categoryId}
          onChange={(e) => onCategoryChange(e.target.value)} required>
          {categories.map((c) => <option key={c.id} value={c.id}>{c.name}</option>)}
        </select>
      </div>
    </div>
  );
}
