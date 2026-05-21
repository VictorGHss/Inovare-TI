import StatusBadge from '../../components/StatusBadge';
import SlaBadge from '../../components/SlaBadge';
import type { Ticket } from '../../types/models';

interface TicketHeaderProps {
  ticket: Ticket;
}

const priorityLabels: Record<string, string> = {
  LOW: 'Baixa',
  NORMAL: 'Normal',
  HIGH: 'Alta',
  URGENT: 'Urgente',
};

const priorityBadgeClass: Record<string, string> = {
  LOW: 'bg-slate-100 text-slate-600',
  NORMAL: 'bg-[#feb56c]/30 text-amber-800',
  HIGH: 'bg-orange-100 text-orange-700',
  URGENT: 'bg-red-100 text-red-600',
};

export default function TicketHeader({ ticket }: TicketHeaderProps) {
  return (
    <section className="rounded-2xl border border-[#feb56c]/35 bg-white p-6 shadow-sm">
      <div className="mb-4 flex flex-wrap items-start justify-between gap-3">
        <h2 className="flex-1 text-xl font-bold leading-snug text-slate-800">{ticket.title}</h2>
        <StatusBadge status={ticket.status} />
      </div>

      {ticket.tags && ticket.tags.length > 0 && (
        <div className="mb-4 flex flex-wrap gap-2">
          {ticket.tags.map((tag) => (
            <span
              key={tag}
              className="inline-flex items-center rounded-full bg-brand-secondary/35 text-brand-primary px-2.5 py-0.5 text-xs font-semibold"
            >
              {tag}
            </span>
          ))}
        </div>
      )}

      <div className="flex flex-wrap items-center gap-x-4 gap-y-1 text-sm text-slate-500">
        <span>
          Solicitante: <span className="font-medium text-slate-700">{ticket.requesterName}</span>
        </span>
        {ticket.assignedToName && (
          <span>
            Tecnico: <span className="font-medium text-slate-700">{ticket.assignedToName}</span>
          </span>
        )}
        <span
          className={`inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-semibold ${
            priorityBadgeClass[ticket.priority] ?? 'bg-slate-100 text-slate-600'
          }`}
        >
          Prioridade: {priorityLabels[ticket.priority] ?? ticket.priority}
        </span>
        <SlaBadge deadline={ticket.slaDeadline} status={ticket.status} closedAt={ticket.closedAt} />
      </div>
    </section>
  );
}
