import { AlertTriangle } from 'lucide-react';
import type { FinanceAlert } from '../../types/models';

interface FinancialAlertsListProps {
  alerts: FinanceAlert[];
}

function formatDate(value?: string | null): string {
  if (!value) {
    return '—';
  }

  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return '—';
  }

  return new Intl.DateTimeFormat('pt-BR', {
    dateStyle: 'short',
    timeStyle: 'short',
  }).format(date);
}

export default function FinancialAlertsList({ alerts }: FinancialAlertsListProps) {
  const sortedAlerts = [...alerts].sort((a, b) => Number(a.resolved) - Number(b.resolved));
  const unresolvedCount = sortedAlerts.filter((alert) => !alert.resolved).length;

  return (
    <section className="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm">
      <div className="flex items-start justify-between gap-4">
        <div>
          <h2 className="text-base font-bold text-slate-800">Alertas Financeiros</h2>
          <p className="mt-0.5 text-sm text-slate-500">
            Eventos recentes de integração e processamento.
          </p>
        </div>
        <span className="inline-flex items-center gap-1 rounded-full border border-amber-200 bg-amber-50 px-3 py-1 text-xs font-semibold text-amber-700">
          <AlertTriangle size={14} />
          {unresolvedCount} pendente(s)
        </span>
      </div>

      {sortedAlerts.length === 0 ? (
        <div className="mt-4 rounded-xl border border-slate-100 bg-slate-50 px-4 py-3 text-sm text-slate-500">
          Nenhum alerta registrado.
        </div>
      ) : (
        <ul className="mt-4 space-y-3">
          {sortedAlerts.slice(0, 6).map((alert) => (
            <li
              key={alert.id}
              className="rounded-xl border border-slate-100 bg-slate-50 px-4 py-3"
            >
              <div className="flex flex-wrap items-start justify-between gap-3">
                <div>
                  <p className="text-sm font-semibold text-slate-800">{alert.title}</p>
                  <p className="mt-1 text-xs text-slate-500">{alert.details}</p>
                </div>
                <div className="text-right">
                  <span className={`inline-flex rounded-full px-2.5 py-1 text-xs font-semibold ${alert.resolved ? 'bg-emerald-100 text-emerald-700' : 'bg-amber-100 text-amber-700'}`}>
                    {alert.resolved ? 'Resolvido' : 'Em aberto'}
                  </span>
                  <p className="mt-1 text-[11px] text-slate-400">{formatDate(alert.createdAt)}</p>
                </div>
              </div>
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}

