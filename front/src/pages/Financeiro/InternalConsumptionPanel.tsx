import { useEffect, useMemo, useState } from 'react';
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
    for (const l of lines) {
      if (l.targetType !== 'SECTOR') continue;
      map.set(l.destination, (map.get(l.destination) ?? 0) + l.amountCents);
    }
    return Array.from(map.entries()).map(([k, v]) => ({ name: k, amountCents: v }));
  }, [lines]);

  const totalsByDoctor = useMemo(() => {
    const map = new Map<string, number>();
    for (const l of lines) {
      if (l.targetType !== 'DOCTOR') continue;
      map.set(l.destination, (map.get(l.destination) ?? 0) + l.amountCents);
    }
    return Array.from(map.entries()).map(([k, v]) => ({ name: k, amountCents: v }));
  }, [lines]);

  return (
    <section className="mt-8">
      <div className="rounded-3xl border border-slate-200 bg-white p-6 shadow-sm">
        <div className="flex items-center justify-between">
          <div>
            <h2 className="text-lg font-semibold text-slate-900">Extrato de Consumo Interno</h2>
            <p className="mt-1 text-sm text-slate-500">Resumo das saídas de estoque por setor e médico no período selecionado.</p>
          </div>
        </div>

        <div className="mt-6 grid gap-4 md:grid-cols-2">
          <div style={{ border: '1px solid #feb56c', backgroundColor: '#fed8b0' }} className="rounded-2xl p-4">
            <p className="text-xs font-semibold uppercase tracking-[0.18em] text-slate-700">Total por Setores</p>
            <ul className="mt-3 space-y-2">
              {totalsBySector.length === 0 && <li className="text-sm text-slate-500">Nenhum lançamento encontrado.</li>}
              {totalsBySector.map((s) => (
                <li key={s.name} className="flex items-center justify-between">
                  <span className="text-sm text-slate-700">{s.name}</span>
                  <strong className="text-sm text-slate-900">{formatCurrency(s.amountCents)}</strong>
                </li>
              ))}
            </ul>
          </div>

          <div style={{ border: '1px solid #feb56c', backgroundColor: '#fed8b0' }} className="rounded-2xl p-4">
            <p className="text-xs font-semibold uppercase tracking-[0.18em] text-slate-700">Total por Médicos</p>
            <ul className="mt-3 space-y-2">
              {totalsByDoctor.length === 0 && <li className="text-sm text-slate-500">Nenhum lançamento encontrado.</li>}
              {totalsByDoctor.map((d) => (
                <li key={d.name} className="flex items-center justify-between">
                  <span className="text-sm text-slate-700">{d.name}</span>
                  <strong className="text-sm text-slate-900">{formatCurrency(d.amountCents)}</strong>
                </li>
              ))}
            </ul>
          </div>
        </div>

        <div className="mt-6 overflow-x-auto">
          <table className="w-full table-auto text-sm">
            <thead>
              <tr className="text-left text-slate-600">
                <th className="py-2">Data</th>
                <th className="py-2">Destino</th>
                <th className="py-2">Item</th>
                <th className="py-2">Qtd</th>
                <th className="py-2">Valor Total</th>
              </tr>
            </thead>
            <tbody>
              {loading && (
                <tr><td colSpan={5} className="py-4 text-center text-slate-500">Carregando...</td></tr>
              )}
              {!loading && lines.length === 0 && (
                <tr><td colSpan={5} className="py-4 text-center text-slate-500">Nenhuma saída registrada neste período.</td></tr>
              )}
              {!loading && lines.map((l) => (
                <tr key={l.transactionId + l.item + l.date} className="border-t border-slate-100">
                  <td className="py-3">{formatDateTime(l.date)}</td>
                  <td className="py-3">{l.destination}</td>
                  <td className="py-3">{l.item}</td>
                  <td className="py-3">{l.quantity}</td>
                  <td className="py-3">{formatCurrency(l.amountCents)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>
    </section>
  );
}
