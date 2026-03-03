interface SlaBadgeProps {
  deadline: string | null;
}

function formatDate(iso: string): string {
  return new Date(iso).toLocaleDateString('pt-BR', {
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  });
}

export default function SlaBadge({ deadline }: SlaBadgeProps) {
  if (!deadline) {
    return (
      <span className="inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium bg-slate-100 text-slate-600">
        Sem SLA
      </span>
    );
  }

  const deadlineDate = new Date(deadline);
  const isOverdue = new Date() > deadlineDate;

  return (
    <span
      className={`inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium ${
        isOverdue ? 'bg-red-100 text-red-800' : 'bg-emerald-100 text-emerald-700'
      }`}
      title={`Prazo: ${formatDate(deadline)}`}
    >
      {isOverdue ? 'SLA Vencido' : 'Dentro do SLA'}
    </span>
  );
}
