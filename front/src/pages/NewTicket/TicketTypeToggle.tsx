// Toggle de tipo de chamado: Problema ou Solicitação
import { AlertCircle, Package } from 'lucide-react';

export type TicketType = 'INCIDENT' | 'REQUEST';

interface Props {
  value: TicketType;
  onChange: (v: TicketType) => void;
}

const tabs = [
  { value: 'INCIDENT' as TicketType, label: 'Relatar Problema', Icon: AlertCircle },
  { value: 'REQUEST' as TicketType, label: 'Solicitar Item de TI', Icon: Package },
];

export default function TicketTypeToggle({ value, onChange }: Props) {
  return (
    <div className="grid grid-cols-2 gap-2">
      {tabs.map(({ value: v, label, Icon }) => (
        <button
          key={v}
          type="button"
          onClick={() => onChange(v)}
          className={`flex items-center justify-center gap-2 py-3 rounded-xl font-medium text-sm transition-colors border ${
            value === v
              ? 'bg-primary text-white border-primary shadow-sm'
              : 'bg-white text-slate-600 border-slate-200 hover:border-primary hover:text-primary'
          }`}
        >
          <Icon size={16} />
          {label}
        </button>
      ))}
    </div>
  );
}
