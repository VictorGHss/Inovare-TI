// Tabela de chamados com dados reais da API
import { useNavigate } from 'react-router-dom';
import { Bot } from 'lucide-react';
import { motion } from 'framer-motion';
import type { Ticket } from '../../types/models';
import StatusBadge from '@/components/ui/StatusBadge';
import SlaBadge from '@/components/ui/SlaBadge';
import { useAuth } from '@/contexts/AuthContext';

interface TicketsTableProps {
  tickets: Ticket[];
}

const priorityLabelMap: Record<Ticket['priority'], string> = {
  LOW: 'Baixa',
  NORMAL: 'Normal',
  HIGH: 'Alta',
  URGENT: 'Urgente',
};

const priorityClassMap: Record<Ticket['priority'], string> = {
  LOW: 'bg-slate-100 text-slate-600',
  NORMAL: 'bg-brand-secondary/30 text-brand-primary',
    HIGH: 'bg-amber-100 text-amber-700 border border-amber-200',
  URGENT: 'bg-red-100 text-red-600',
};

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
  const { user } = useAuth();

  const containerVariants = {
    hidden: { opacity: 0 },
    show: {
      opacity: 1,
      transition: { staggerChildren: 0.045 },
    },
  };

  const rowVariants = {
    hidden: { opacity: 0, y: 10 },
    show: {
      opacity: 1,
      y: 0,
      transition: { duration: 0.22 },
    },
  };

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
            <th className="px-4 py-3 text-left">Solicitante</th>
            <th className="px-4 py-3 text-left">Categoria</th>
            <th className="px-4 py-3 text-left">Prioridade</th>
            <th className="px-4 py-3 text-left">Status</th>
            <th className="px-4 py-3 text-left">SLA</th>
            <th className="px-4 py-3 text-left">Criado em</th>
          </tr>
        </thead>
        <motion.tbody
          className="divide-y divide-slate-100 bg-white"
          variants={containerVariants}
          initial="hidden"
          animate="show"
        >
          {tickets.map((ticket) => (
            <motion.tr
              key={ticket.id}
              variants={rowVariants}
              onClick={() => navigate(`/tickets/${ticket.id}`)}
              className="hover:bg-slate-50 transition-colors cursor-pointer"
            >
              <td className="px-4 py-3 font-medium text-slate-800">
                <div className="flex flex-col gap-1">
                  <div className="flex items-center gap-2">
                    <span>{String(ticket.title ?? '-')}</span>
                    {ticket.isFromDiscord && (
                      <span className="inline-flex items-center gap-1 rounded-full bg-brand-secondary px-2 py-0.5 text-xs font-medium text-brand-primary-dark">
                        <Bot size={12} />
                        Discord
                      </span>
                    )}
                  </div>
                  <div className="flex flex-wrap items-center gap-1 mt-0.5">
                    {user?.id === ticket.requesterId && (
                      <span className="inline-flex items-center rounded-full bg-blue-50 px-1.5 py-0.5 text-[10px] font-medium text-blue-600 border border-blue-100">
                        👤 Autor
                      </span>
                    )}
                    {user?.id === ticket.assignedToId && (
                      <span className="inline-flex items-center rounded-full bg-emerald-50 px-1.5 py-0.5 text-[10px] font-medium text-emerald-600 border border-emerald-100">
                        🛠️ Responsável
                      </span>
                    )}
                    {ticket.assignedUserIds?.includes(user?.id ?? '') && user?.id !== ticket.assignedToId && (
                      <span className="inline-flex items-center rounded-full bg-violet-50 px-1.5 py-0.5 text-[10px] font-medium text-violet-600 border border-violet-100">
                        🔔 Coparticipante
                      </span>
                    )}
                  </div>
                </div>
              </td>
              <td className="px-4 py-3 text-slate-600">
                {String(ticket.requesterName ?? '-')}
              </td>
              <td className="px-4 py-3 text-slate-500">
                {String(ticket.categoryName ?? '-')}
              </td>
              <td className="px-4 py-3">
                  <span className={`inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-medium ${priorityClassMap[ticket.priority]}`}>
                  {priorityLabelMap[ticket.priority]}
                </span>
              </td>
              <td className="px-4 py-3">
                <StatusBadge status={ticket.status} />
              </td>
              <td className="px-4 py-3">
                <SlaBadge deadline={ticket.slaDeadline} status={ticket.status} closedAt={ticket.closedAt} />
              </td>
              <td className="px-4 py-3 text-slate-400">
                {formatDate(ticket.createdAt)}
              </td>
            </motion.tr>
          ))}
        </motion.tbody>
      </table>
    </div>
  );
}

