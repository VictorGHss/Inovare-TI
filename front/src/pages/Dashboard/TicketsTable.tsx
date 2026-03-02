// Tabela de chamados com dados reais da API
import type { Ticket } from '../../services/api';
import StatusBadge from '../../components/StatusBadge';

interface TicketsTableProps {
  tickets: Ticket[];
}

// Formata data ISO para exibição em português
function formatDate(iso: string) {
  return new Date(iso).toLocaleDateString('pt-BR', {
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
  });
}

export default function TicketsTable({ tickets }: TicketsTableProps) {
  if (tickets.length === 0) {
    return (
      <p className="text-center text-slate-400 py-12 text-sm">
        Nenhum chamado encontrado.
      </p>
    );
  }

  return (
    <div className="overflow-x-auto rounded-xl border border-slate-200">
      <table className="min-w-full text-sm">
        <thead className="bg-slate-50 text-slate-500 uppercase text-xs tracking-wider">
          <tr>
            <th className="px-4 py-3 text-left">#</th>
            <th className="px-4 py-3 text-left">Título</th>
            <th className="px-4 py-3 text-left">Categoria</th>
            <th className="px-4 py-3 text-left">Status</th>
            <th className="px-4 py-3 text-left">Criado em</th>
          </tr>
        </thead>
        <tbody className="divide-y divide-slate-100 bg-white">
          {tickets.map((ticket) => (
            <tr key={ticket.id} className="hover:bg-slate-50 transition-colors">
              <td className="px-4 py-3 text-slate-400">{ticket.id}</td>
              <td className="px-4 py-3 font-medium text-slate-800">
                {ticket.title}
              </td>
              <td className="px-4 py-3 text-slate-500">{ticket.category}</td>
              <td className="px-4 py-3">
                <StatusBadge status={ticket.status} />
              </td>
              <td className="px-4 py-3 text-slate-400">
                {formatDate(ticket.createdAt)}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
