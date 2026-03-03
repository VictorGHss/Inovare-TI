// Card lateral com dicas dinâmicas baseadas no tipo de chamado
import { Lightbulb } from 'lucide-react';
import type { TicketType } from './TicketTypeToggle';

const TIPS: Record<TicketType, { heading: string; items: string[] }> = {
  INCIDENT: {
    heading: 'Dicas para Relatar Problema',
    items: [
      'Seja específico no título — evite "não funciona".',
      'Descreva os passos exatos para reproduzir o problema.',
      'Tire prints do erro e anexe ao chamado.',
      'Detalhe o comportamento inesperado vs. o esperado.',
    ],
  },
  REQUEST: {
    heading: 'Dicas para Solicitar Material',
    items: [
      'Verifique se o item não está disponível no seu setor.',
      'Especifique corretamente a quantidade necessária.',
      'Justifique na descrição se for um volume atípico.',
      'Informe a finalidade do item solicitado.',
    ],
  },
};

interface Props {
  ticketType: TicketType;
}

export default function TutorialAside({ ticketType }: Props) {
  const { heading, items } = TIPS[ticketType];

  return (
    <div className="sticky top-24">
      <div className="bg-white border border-slate-200 rounded-xl shadow-sm p-6">
        <div className="flex items-center gap-2 mb-4">
          <div className="p-1.5 bg-primary/10 rounded-lg">
            <Lightbulb size={18} className="text-primary" />
          </div>
          <h2 className="font-semibold text-slate-800 text-sm transition-all">
            {heading}
          </h2>
        </div>
        <ul className="flex flex-col gap-3">
          {items.map((tip, i) => (
            <li key={i} className="flex items-start gap-2">
              <span className="mt-0.5 flex-shrink-0 w-4 h-4 rounded-full bg-primary/10 text-primary text-[10px] font-bold flex items-center justify-center">
                {i + 1}
              </span>
              <span className="text-sm text-gray-600 leading-snug">{tip}</span>
            </li>
          ))}
        </ul>
      </div>
    </div>
  );
}
