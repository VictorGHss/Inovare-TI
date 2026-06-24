import type {
  FinanceConnectionStatus,
  FinanceReceipt,
  FinancialSummaryDTO,
} from '@/types/models';

interface ContaAzulStatusCardProps {
  connectionStatus: FinanceConnectionStatus | null;
  summary: FinancialSummaryDTO | null;
  receipts: FinanceReceipt[];
  unresolvedAlertsCount: number;
  integrationActive: boolean;
  onConnectContaAzul: () => void;
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

export default function ContaAzulStatusCard({
  connectionStatus,
  summary,
  receipts,
  unresolvedAlertsCount,
  integrationActive,
  onConnectContaAzul,
}: ContaAzulStatusCardProps) {
  const latestReceipt = receipts[0];
  const latestDisplayIdentifier =
    latestReceipt?.commercialNumber
    ?? latestReceipt?.referenceCode
    ?? latestReceipt?.displayIdentifier
    ?? latestReceipt?.parcelaId;

  const items = [
    {
      label: 'Integração ContaAzul',
      value: integrationActive ? 'Ativa' : 'Integração Pendente',
      valueClass: integrationActive ? 'text-emerald-600' : 'text-red-600',
      sub: integrationActive
        ? `Expiração do token: ${formatDate(connectionStatus?.expiresAt)}`
        : 'Aguardando autorização OAuth2 da ContaAzul.',
    },
    {
      label: 'Recibos sincronizados',
      value: String(summary?.syncedReceiptsCount ?? receipts.length),
      valueClass: 'text-slate-800',
      sub: latestDisplayIdentifier ? `Último identificador: ${latestDisplayIdentifier}` : null,
    },
    {
      label: 'Alertas em aberto',
      value: String(unresolvedAlertsCount),
      valueClass: unresolvedAlertsCount > 0 ? 'text-amber-600' : 'text-slate-800',
      sub: null,
    },
  ];

  return (
    <aside className="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm">
      <h2 className="text-base font-bold text-slate-800">Status da integração</h2>
      <div className="mt-4 space-y-3">
        {items.map((item) => (
          <div key={item.label} className="flex items-center justify-between rounded-xl border border-slate-100 bg-slate-50 px-4 py-3">
            <div>
              <p className="text-[10px] font-bold uppercase tracking-widest text-slate-400">{item.label}</p>
              {item.sub && <p className="mt-0.5 text-xs text-slate-400">{item.sub}</p>}
            </div>
            <strong className={`text-lg font-extrabold ${item.valueClass}`}>{item.value}</strong>
          </div>
        ))}
      </div>

      {!integrationActive && (
        <button
          type="button"
          onClick={onConnectContaAzul}
          className="mt-4 inline-flex w-full items-center justify-center rounded-xl bg-brand-primary px-4 py-2.5 text-sm font-semibold text-white shadow-sm transition-colors hover:bg-brand-primary-dark"
        >
          Conectar ContaAzul
        </button>
      )}
    </aside>
  );
}

