import type { Ticket } from '../services/api';
import { CheckCircle2 } from 'lucide-react';

interface ReceivedItemsCardProps {
  tickets: Ticket[];
  totalReceivedCount: number;
}

export default function ReceivedItemsCard({ tickets, totalReceivedCount }: ReceivedItemsCardProps) {
  // Filter tickets that are RESOLVED and have requested items
  const receivedItems = tickets
    .filter((t) => t.status === 'RESOLVED' && t.requestedItemName)
    .slice(0, 5); // Show last 5 items

  return (
    <div className="bg-white rounded-lg shadow p-6">
      <h3 className="text-lg font-semibold text-slate-800 mb-4">Itens Recebidos da TI</h3>
      <p className="text-sm text-slate-500 mb-4">Total recebido: <span className="font-semibold text-slate-700">{totalReceivedCount}</span></p>
      
      {receivedItems.length === 0 ? (
        <p className="text-slate-500 text-center py-6">Nenhum item recebido ainda</p>
      ) : (
        <div className="space-y-3">
          {receivedItems.map((ticket) => (
            <div key={ticket.id} className="flex items-start gap-3 p-3 bg-green-50 rounded-lg border border-green-200">
              <CheckCircle2 className="w-5 h-5 text-green-600 flex-shrink-0 mt-0.5" />
              <div className="flex-1 min-w-0">
                <p className="font-medium text-slate-800 truncate">{ticket.requestedItemName}</p>
                <div className="flex items-center gap-2 text-xs text-slate-600 mt-1">
                  <span>Qtd: {ticket.requestedQuantity}</span>
                  <span>•</span>
                  <span>{new Date(ticket.closedAt || '').toLocaleDateString('pt-BR')}</span>
                </div>
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
