// Tabela de chamados com dados reais da API
import { useNavigate } from 'react-router-dom';
import type { Ticket } from '../../services/api';
import StatusBadge from '../../components/StatusBadge';

interface TicketsTableProps {
  tickets: Ticket[];
}

// Converte data ISO para exibição segura em português — retorna '-' em caso de valor nulo
function formatDate(iso: string | null | undefined): string {
  if (!iso) return '-';
  try {
    return new Date(iso).toLocaleDateString('pt-BR', {
      day: '2-digit',
      month: '2-digit',
      year: 'numeric',
    });
  } catch {
    return '-';
  }
}

export default function TicketsTable({ tickets }: TicketsTableProps) {
  const navigate = useNavigate();

  if (!Array.isArray(tickets) || tickets.length === 0) {
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
            <th className="px-4 py-3 text-left">Título</th>
            <th className="px-4 py-3 text-left">Categoria</th>
            <th className="px-4 py-3 text-left">Prioridade</th>
            <th className="px-4 py-3 text-left">Status</th>
            <th className="px-4 py-3 text-left">Criado em</th>
          </tr>
        </thead>
        <tbody className="divide-y divide-slate-100 bg-white">
          {tickets.map((ticket) => (
            <tr
              key={ticket.id}
              // Navega para a tela de detalhes ao clicar na linha
              onClick={() => navigate(`/tickets/${ticket.id}`)}
              className="hover:bg-slate-50 transition-colors cursor-pointer"
            >
              <td className="px-4 py-3 font-medium text-slate-800">
                {String(ticket.title ?? '-')}
              </td>
              <td className="px-4 py-3 text-slate-500">
                {String(ticket.categoryName ?? '-')}
              </td>
              <td className="px-4 py-3 text-slate-500 capitalize">
                {String(ticket.priority ?? '-').toLowerCase()}
              </td>
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
