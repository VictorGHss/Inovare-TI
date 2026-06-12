// Seletores de Prioridade e Categoria do chamado no ecrã
import type { CreateTicketDto, TicketCategory } from '../../types/models';
import SearchableDropdown from '../../components/SearchableDropdown';

interface Props {
  priority: CreateTicketDto['priority'];
  // categoryId é o UUID (string) alinhado com o backend
  categoryId: string;
  categories: TicketCategory[];
  onPriorityChange: (v: CreateTicketDto['priority']) => void;
  onCategoryChange: (id: string) => void;
}

const priorities = [
  { value: 'LOW', label: 'Baixa' },
  { value: 'NORMAL', label: 'Normal' },
  { value: 'HIGH', label: 'Alta' },
  { value: 'URGENT', label: 'Urgente' },
];

const labelCls = 'text-xs font-bold uppercase tracking-widest text-slate-400';

/**
 * Componente que exibe os campos de prioridade e categoria no ecrã de novo chamado.
 * Utiliza o dropdown premium pesquisável para a seleção de categorias.
 */
export default function PriorityCategoryFields({
  priority, categoryId, categories, onPriorityChange, onCategoryChange,
}: Props) {
  return (
    <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
      {/* Dropdown de Prioridade (Filtrável e pesquisável com design premium) */}
      <div className="flex flex-col gap-1.5">
        <label className={labelCls}>Prioridade</label>
        <SearchableDropdown
          options={priorities}
          value={priority}
          onChange={(val) => onPriorityChange(val as CreateTicketDto['priority'])}
          placeholder="Selecione uma prioridade..."
        />
      </div>

      {/* Dropdown de Categoria (Filtrável, ordenado alfabeticamente e pesquisável) */}
      <div className="flex flex-col gap-1.5">
        <label className={labelCls}>
          Categoria <span className="text-red-500 normal-case tracking-normal">*</span>
        </label>
        <SearchableDropdown
          options={categories}
          value={categoryId}
          onChange={onCategoryChange}
          placeholder="Selecione uma categoria..."
        />
      </div>
    </div>
  );
}
