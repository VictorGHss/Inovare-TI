import type { Ticket } from '../services/api';
import StatusBadge from './StatusBadge';
import { Calendar, FileText } from 'lucide-react';

interface UserTicketHistoryProps {
  tickets: Ticket[];
}

export default function UserTicketHistory({ tickets }: UserTicketHistoryProps) {
  // Sort by created date, newest first
  const sortedTickets = [...tickets].sort((a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime());

  return (
    <div className="bg-white rounded-lg shadow p-6">
      <h3 className="text-lg font-semibold text-slate-800 mb-4">Histórico de Chamados</h3>
      
      {sortedTickets.length === 0 ? (
        <p className="text-slate-500 text-center py-6">Nenhum chamado registrado</p>
      ) : (
        <div className="space-y-3 max-h-96 overflow-y-auto">
          {sortedTickets.map((ticket) => (
            <div
              key={ticket.id}
              className="flex items-start gap-3 p-3 border border-slate-200 rounded-lg hover:bg-slate-50 transition-colors"
            >
              <FileText className="w-5 h-5 text-slate-400 flex-shrink-0 mt-0.5" />
              <div className="flex-1 min-w-0">
                <div className="flex items-start justify-between gap-2">
                  <div className="flex-1">
                    <p className="font-medium text-slate-800 truncate">{ticket.title}</p>
                    <p className="text-xs text-slate-600 truncate mt-1">{ticket.description}</p>
                  </div>
                  <StatusBadge status={ticket.status} />
                </div>
                <div className="flex items-center gap-3 text-xs text-slate-600 mt-2">
                  <span className="flex items-center gap-1">
                    <Calendar className="w-3 h-3" />
                    {new Date(ticket.createdAt).toLocaleDateString('pt-BR')}
                  </span>
                  <span className="px-2 py-0.5 bg-slate-100 rounded text-slate-700">{ticket.categoryName}</span>
                </div>
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
