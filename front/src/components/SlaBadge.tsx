import { formatDistanceToNow } from 'date-fns';
import { ptBR } from 'date-fns/locale';

interface SlaBadgeProps {
  deadline: string | null;
  status: string;
  closedAt: string | null;
}

export default function SlaBadge({ deadline, status, closedAt }: SlaBadgeProps) {
  if (!deadline) {
    return (
      <span className="inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium bg-slate-100 text-slate-600">
        Sem SLA
      </span>
    );
  }

  const deadlineDate = new Date(deadline);
  const isClosedOrResolved = status === 'RESOLVED' || status === 'CLOSED';

  // If ticket is resolved/closed, compare deadline with closedAt
  if (isClosedOrResolved && closedAt) {
    const closedDate = new Date(closedAt);
    const isOnTime = closedDate <= deadlineDate;

    return (
      <span
        className={`inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium ${
          isOnTime
            ? 'bg-green-100 text-green-800'
            : 'bg-amber-100 text-amber-800'
        }`}
        title={`Prazo: ${deadlineDate.toLocaleDateString('pt-BR')} ${deadlineDate.toLocaleTimeString('pt-BR')}`}
      >
        {isOnTime ? 'No Prazo' : 'Fechado com Atraso'}
      </span>
    );
  }

  // For open/in-progress tickets, calculate relative time
  const now = new Date();
  const isOverdue = now > deadlineDate;

  if (isOverdue) {
    return (
      <span
        className="inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium bg-red-100 text-red-800"
        title={`Prazo: ${deadlineDate.toLocaleDateString('pt-BR')} ${deadlineDate.toLocaleTimeString('pt-BR')}`}
      >
        Vencido
      </span>
    );
  }

  // Time remaining
  const timeRemaining = formatDistanceToNow(deadlineDate, {
    locale: ptBR,
    addSuffix: false,
  });

  return (
    <span
      className="inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium bg-blue-100 text-blue-800"
      title={`Prazo: ${deadlineDate.toLocaleDateString('pt-BR')} ${deadlineDate.toLocaleTimeString('pt-BR')}`}
    >
      {`Faltam ${timeRemaining}`}
    </span>
  );
}
