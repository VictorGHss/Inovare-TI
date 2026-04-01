import { useEffect, useMemo, useState } from 'react';
import { Building2, Stethoscope, PackageOpen } from 'lucide-react';
import { getFinancialTransactions, type FinancialTransactionLineDTO } from '../../services/api';

function formatCurrency(value: number | null | undefined, currency = 'BRL'): string {
  if (value === null || value === undefined) return '—';
  return new Intl.NumberFormat('pt-BR', { style: 'currency', currency }).format(value / 100);
}

function formatDateTime(value?: string | null) {
  if (!value) return '—';
  const d = new Date(value);
  if (Number.isNaN(d.getTime())) return '—';
  return new Intl.DateTimeFormat('pt-BR', { dateStyle: 'short', timeStyle: 'short' }).format(d);
}

export default function InternalConsumptionPanel({ startDate, endDate }: { startDate?: string; endDate?: string }) {
  const [lines, setLines] = useState<FinancialTransactionLineDTO[]>([]);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    let mounted = true;
    async function load() {
      try {
        setLoading(true);
        const data = await getFinancialTransactions({ startDate, endDate });
        if (mounted) setLines(data);
      } finally {
        if (mounted) setLoading(false);
      }
    }

    void load();
    return () => { mounted = false; };
  }, [startDate, endDate]);

  const totalsBySector = useMemo(() => {
    const map = new Map<string, number>();
    for (const l of (lines ?? [])) {
      if (l.targetType !== 'SECTOR') continue;
      map.set(l.destination, (map.get(l.destination) ?? 0) + l.amountCents);
    }
    return Array.from(map.entries()).map(([k, v]) => ({ name: k, amountCents: v }));
  }, [lines]);

  const totalsByDoctor = useMemo(() => {
    const map = new Map<string, number>();
    for (const l of (lines ?? [])) {
      if (l.targetType !== 'DOCTOR') continue;
      map.set(l.destination, (map.get(l.destination) ?? 0) + l.amountCents);
    }
    return Array.from(map.entries()).map(([k, v]) => ({ name: k, amountCents: v }));
  }, [lines]);

  return (
    <section className="mt-6">
      <div className="rounded-2xl border border-slate-200 bg-white shadow-sm overflow-hidden">

        {/* Panel header with brand accent */}
        <div className="border-b border-slate-100 bg-gradient-to-r from-brand-secondary/30 to-transparent px-6 py-5">
          <div className="flex items-center gap-3">
            <span className="inline-flex h-9 w-9 items-center justify-center rounded-xl bg-brand-primary/10">
              <PackageOpen size={18} className="text-brand-primary-dark" />
            </span>
            <div>
              <h2 className="text-base font-bold text-slate-800">Extrato de Consumo Interno</h2>
              <p className="mt-0.5 text-xs text-slate-500">
                Resumo das saídas de estoque por setor e médico no período selecionado.
              </p>
            </div>
          </div>
        </div>

        <div className="p-6">
          {/* Summary cards */}
          <div className="grid gap-4 md:grid-cols-2">
            {/* By sector */}
            <div className="rounded-xl border border-brand-primary/25 bg-gradient-to-br from-brand-secondary/50 to-brand-secondary/20 p-4">
              <div className="flex items-center gap-2 mb-3">
                <Building2 size={14} className="text-brand-primary-dark" />
                <p className="text-xs font-bold uppercase tracking-[0.18em] text-brand-primary-dark">
                  Total por Setores
                </p>
              </div>
              <ul className="space-y-2">
                {totalsBySector.length === 0 && (
                  <li className="text-sm text-slate-400 italic">Nenhum lançamento encontrado.</li>
                )}
                {totalsBySector.map((s) => (
                  <li key={s.name} className="flex items-center justify-between gap-2">
                    <span className="text-sm text-slate-700 truncate">{s.name}</span>
                    <strong className="text-sm font-bold text-slate-900 shrink-0">
                      {formatCurrency(s.amountCents)}
                    </strong>
                  </li>
                ))}
              </ul>
            </div>

            {/* By doctor */}
            <div className="rounded-xl border border-brand-primary/25 bg-gradient-to-br from-brand-secondary/50 to-brand-secondary/20 p-4">
              <div className="flex items-center gap-2 mb-3">
                <Stethoscope size={14} className="text-brand-primary-dark" />
                <p className="text-xs font-bold uppercase tracking-[0.18em] text-brand-primary-dark">
                  Total por Médicos
                </p>
              </div>
              <ul className="space-y-2">
                {totalsByDoctor.length === 0 && (
                  <li className="text-sm text-slate-400 italic">Nenhum lançamento encontrado.</li>
                )}
                {totalsByDoctor.map((d) => (
                  <li key={d.name} className="flex items-center justify-between gap-2">
                    <span className="text-sm text-slate-700 truncate">{d.name}</span>
                    <strong className="text-sm font-bold text-slate-900 shrink-0">
                      {formatCurrency(d.amountCents)}
                    </strong>
                  </li>
                ))}
              </ul>
            </div>
          </div>

          {/* Transactions table */}
          <div className="mt-5 overflow-x-auto rounded-xl border border-slate-200">
            <table className="w-full table-auto text-sm">
              <thead>
                <tr className="border-b border-slate-200 bg-slate-50">
                  {['Data', 'Destino', 'Item', 'Qtd', 'Valor Total'].map((col) => (
                    <th
                      key={col}
                      className="px-4 py-3 text-left text-[10px] font-bold uppercase tracking-widest text-slate-400"
                    >
                      {col}
                    </th>
                  ))}
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {loading && (
                  <tr>
                    <td colSpan={5} className="py-8 text-center text-sm text-slate-400">
                      <div className="flex items-center justify-center gap-2">
                        <span className="h-4 w-4 animate-spin rounded-full border-2 border-brand-primary border-t-transparent" />
                        Carregando...
                      </div>
                    </td>
                  </tr>
                )}
                {!loading && lines.length === 0 && (
                  <tr>
                    <td colSpan={5} className="py-8 text-center text-sm text-slate-400 italic">
                      Nenhuma saída registrada neste período.
                    </td>
                  </tr>
                )}
                {!loading && lines.map((l) => (
                  <tr
                    key={l.transactionId + l.item + l.date}
                    className="transition-colors hover:bg-orange-50/40"
                  >
                    <td className="px-4 py-3 text-slate-500 text-xs">{formatDateTime(l.date)}</td>
                    <td className="px-4 py-3 font-medium text-slate-800">{l.destination}</td>
                    <td className="px-4 py-3 text-slate-600">{l.item}</td>
                    <td className="px-4 py-3 text-slate-600">{l.quantity}</td>
                    <td className="px-4 py-3 font-semibold text-slate-900">{formatCurrency(l.amountCents)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      </div>
    </section>
  );
}
