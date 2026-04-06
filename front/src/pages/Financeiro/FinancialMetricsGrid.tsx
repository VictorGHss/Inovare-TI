import { AlertTriangle, DollarSign, Landmark, Wallet } from 'lucide-react';
import type { FinancialSummaryDTO } from '../../types/domain';

interface FinancialMetricsGridProps {
  summary: FinancialSummaryDTO | null;
  unresolvedAlertsCount: number;
}

function formatCurrency(value: number | null | undefined, currency = 'BRL'): string {
  if (value === null || value === undefined) {
    return 'Aguardando API';
  }

  return new Intl.NumberFormat('pt-BR', {
    style: 'currency',
    currency,
  }).format(value / 100);
}

export default function FinancialMetricsGrid({
  summary,
  unresolvedAlertsCount,
}: FinancialMetricsGridProps) {
  const currency = summary?.currency ?? 'BRL';

  const metricCards = [
    {
      title: 'Saldo',
      value: formatCurrency(summary?.balanceCents, currency),
      helper: 'Posição consolidada do financeiro na Conta Azul.',
      icon: Wallet,
      iconBg: 'bg-brand-secondary',
      iconColor: 'text-brand-primary-dark',
      accent: 'border-l-brand-primary',
    },
    {
      title: 'Total Pendente',
      value: formatCurrency(summary?.totalPendingCents, currency),
      helper: 'Valores ainda pendentes de quitação.',
      icon: DollarSign,
      iconBg: 'bg-sky-50',
      iconColor: 'text-sky-600',
      accent: 'border-l-sky-400',
    },
    {
      title: 'Total Pago',
      value: formatCurrency(summary?.totalPaidCents, currency),
      helper: 'Valores já recebidos e conciliados.',
      icon: Landmark,
      iconBg: 'bg-emerald-50',
      iconColor: 'text-emerald-600',
      accent: 'border-l-emerald-400',
    },
    {
      title: 'Alertas Pendentes',
      value: String(unresolvedAlertsCount),
      helper: 'Ocorrências que ainda exigem acompanhamento.',
      icon: AlertTriangle,
      iconBg: 'bg-amber-50',
      iconColor: 'text-amber-600',
      accent: 'border-l-amber-400',
    },
  ];

  return (
    <section className="grid gap-4 md:grid-cols-2 xl:grid-cols-4">
      {metricCards.map((card) => {
        const Icon = card.icon;
        return (
          <article
            key={card.title}
            className={`rounded-2xl border border-slate-200 bg-white p-5 shadow-sm transition-all hover:-translate-y-0.5 hover:shadow-md border-l-4 ${card.accent}`}
          >
            <div className="flex items-start justify-between gap-3">
              <p className="text-xs font-bold uppercase tracking-[0.18em] text-slate-400">
                {card.title}
              </p>
              <span className={`inline-flex h-9 w-9 shrink-0 items-center justify-center rounded-xl ${card.iconBg} ${card.iconColor}`}>
                <Icon size={18} />
              </span>
            </div>
            <strong className="mt-4 block text-2xl font-extrabold text-slate-900">
              {card.value}
            </strong>
            <p className="mt-2 text-xs leading-5 text-slate-400">{card.helper}</p>
          </article>
        );
      })}
    </section>
  );
}
