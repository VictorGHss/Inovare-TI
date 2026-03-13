import { useEffect, useMemo, useState } from 'react';
import { toast } from 'react-toastify';
import api from '../../services/api';

type ReceiptStatus = 'SENT' | 'HISTORICO' | 'PENDING_RETRY' | 'FAILED' | 'SKIPPED_DUPLICATE';

interface FinanceReceipt {
  id: string;
  parcelaId: string;
  originalRecipientEmail: string;
  status: ReceiptStatus;
  processedAt: string;
  payload: Record<string, unknown> | null;
}

interface FinanceAlert {
  id: string;
  title: string;
  details: string;
  resolved: boolean;
  createdAt: string;
  context: Record<string, unknown> | null;
}

type FinanceTab = 'recibos' | 'alertas';

const STATUS_UI: Record<ReceiptStatus, { label: string; className: string }> = {
  SENT: {
    label: 'Enviado',
    className: 'bg-emerald-100 text-emerald-700 border border-emerald-200',
  },
  HISTORICO: {
    label: 'Histórico',
    className: 'bg-slate-100 text-slate-700 border border-slate-200',
  },
  PENDING_RETRY: {
    label: 'Pendente Reenvio',
    className: 'bg-amber-100 text-amber-700 border border-amber-200',
  },
  FAILED: {
    label: 'Falhou',
    className: 'bg-rose-100 text-rose-700 border border-rose-200',
  },
  SKIPPED_DUPLICATE: {
    label: 'Duplicado',
    className: 'bg-slate-100 text-slate-700 border border-slate-200',
  },
};

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

function readPayloadString(payload: Record<string, unknown> | null, keys: string[]): string | null {
  if (!payload) {
    return null;
  }

  for (const key of keys) {
    const value = payload[key];
    if (typeof value === 'string' && value.trim().length > 0) {
      return value;
    }
  }

  return null;
}

function fallbackDoctorNameFromEmail(email?: string): string {
  if (!email || !email.includes('@')) {
    return 'Não informado';
  }

  return email.split('@')[0].replace(/[._-]/g, ' ').trim() || 'Não informado';
}

export default function Financeiro() {
  const [activeTab, setActiveTab] = useState<FinanceTab>('recibos');
  const [receipts, setReceipts] = useState<FinanceReceipt[]>([]);
  const [alerts, setAlerts] = useState<FinanceAlert[]>([]);
  const [loadingReceipts, setLoadingReceipts] = useState(false);
  const [loadingAlerts, setLoadingAlerts] = useState(false);
  const [runningBackfill, setRunningBackfill] = useState(false);
  const [requeueingId, setRequeueingId] = useState<string | null>(null);

  const unresolvedAlerts = useMemo(
    () => alerts.filter((alert) => !alert.resolved),
    [alerts],
  );

  async function loadReceipts() {
    try {
      setLoadingReceipts(true);
      const { data } = await api.get<FinanceReceipt[]>('/api/financeiro/recibos');
      setReceipts(data);
    } catch {
      toast.error('Erro ao carregar histórico de recibos.');
    } finally {
      setLoadingReceipts(false);
    }
  }

  async function loadAlerts() {
    try {
      setLoadingAlerts(true);
      const { data } = await api.get<FinanceAlert[]>('/api/financeiro/alertas');
      setAlerts(data);
    } catch {
      toast.error('Erro ao carregar alertas de envio.');
    } finally {
      setLoadingAlerts(false);
    }
  }

  async function handleBackfill() {
    try {
      setRunningBackfill(true);
      await api.post('/api/financeiro/backfill');
      toast.success('Sincronização dos últimos 30 dias concluída.');
      await Promise.all([loadReceipts(), loadAlerts()]);
    } catch {
      toast.error('Não foi possível executar a sincronização dos últimos 30 dias.');
    } finally {
      setRunningBackfill(false);
    }
  }

  async function handleRequeue(alertId: string) {
    try {
      setRequeueingId(alertId);
      await api.post(`/api/financeiro/alertas/${alertId}/reenviar`);
      toast.success('Reenvio solicitado com sucesso.');
      await Promise.all([loadAlerts(), loadReceipts()]);
    } catch {
      toast.error('Falha ao reenviar recibo do alerta selecionado.');
    } finally {
      setRequeueingId(null);
    }
  }

  useEffect(() => {
    void Promise.all([loadReceipts(), loadAlerts()]);
  }, []);

  return (
    <main className="w-full max-w-full px-4 py-8 sm:px-6 lg:px-8">
      <section className="mb-6 flex flex-col gap-4 lg:flex-row lg:items-start lg:justify-between">
        <div>
          <h1 className="text-2xl font-bold text-slate-800">Financeiro • Visão de Conferência</h1>
          <p className="mt-1 text-sm text-slate-500">
            Gestão de histórico de recibos e alertas de envio protegida por 2FA.
          </p>
        </div>

        <button
          type="button"
          onClick={handleBackfill}
          disabled={runningBackfill}
          className="inline-flex items-center justify-center rounded-xl bg-brand-primary px-4 py-2.5 text-sm font-semibold text-white transition-colors hover:bg-brand-primary-dark disabled:cursor-not-allowed disabled:opacity-60"
        >
          {runningBackfill ? 'Sincronizando...' : 'Sincronizar Últimos 30 Dias'}
        </button>
      </section>

      <section className="overflow-hidden rounded-xl border border-slate-200 bg-white shadow-sm">
        <div className="border-b border-brand-secondary/80 bg-brand-secondary/35 px-5 py-3">
          <div className="inline-flex rounded-lg border border-brand-primary/20 bg-white p-1">
            <button
              type="button"
              onClick={() => setActiveTab('recibos')}
              className={`rounded-md px-3 py-2 text-sm font-semibold transition-colors ${
                activeTab === 'recibos'
                  ? 'bg-brand-primary text-white'
                  : 'text-brand-primary hover:bg-brand-secondary/70'
              }`}
            >
              Histórico de Recibos
            </button>
            <button
              type="button"
              onClick={() => setActiveTab('alertas')}
              className={`rounded-md px-3 py-2 text-sm font-semibold transition-colors ${
                activeTab === 'alertas'
                  ? 'bg-brand-primary text-white'
                  : 'text-brand-primary hover:bg-brand-secondary/70'
              }`}
            >
              Alertas de Envio
            </button>
          </div>
        </div>

        {activeTab === 'recibos' ? (
          <div className="overflow-x-auto">
            <table className="min-w-full text-sm">
              <thead className="bg-brand-primary text-white">
                <tr>
                  <th className="px-4 py-3 text-left font-semibold">ID da Parcela</th>
                  <th className="px-4 py-3 text-left font-semibold">Nome do Médico</th>
                  <th className="px-4 py-3 text-left font-semibold">Data do Pagamento</th>
                  <th className="px-4 py-3 text-left font-semibold">Data de Envio</th>
                  <th className="px-4 py-3 text-left font-semibold">Status</th>
                </tr>
              </thead>
              <tbody>
                {loadingReceipts ? (
                  <tr>
                    <td colSpan={5} className="px-4 py-8 text-center text-slate-500">
                      Carregando histórico de recibos...
                    </td>
                  </tr>
                ) : receipts.length === 0 ? (
                  <tr>
                    <td colSpan={5} className="px-4 py-8 text-center text-slate-500">
                      Nenhum recibo encontrado.
                    </td>
                  </tr>
                ) : (
                  receipts.map((receipt) => {
                    const payload = receipt.payload;
                    const doctorName =
                      readPayloadString(payload, ['medicoNome', 'doctorName', 'doctor'])
                      ?? fallbackDoctorNameFromEmail(receipt.originalRecipientEmail);

                    const paymentDate = readPayloadString(payload, [
                      'paymentDate',
                      'paidAt',
                      'dataPagamento',
                      'paid_at',
                    ]);

                    const statusUi = STATUS_UI[receipt.status] ?? {
                      label: receipt.status,
                      className: 'bg-slate-100 text-slate-700 border border-slate-200',
                    };

                    return (
                      <tr key={receipt.id} className="border-b border-slate-100 last:border-b-0 hover:bg-brand-secondary/20 transition-colors">
                        <td className="px-4 py-3 font-medium text-slate-700">{receipt.parcelaId}</td>
                        <td className="px-4 py-3 text-slate-700">{doctorName}</td>
                        <td className="px-4 py-3 text-slate-700">{formatDate(paymentDate)}</td>
                        <td className="px-4 py-3 text-slate-700">{formatDate(receipt.processedAt)}</td>
                        <td className="px-4 py-3">
                          <span className={`inline-flex rounded-full px-2.5 py-1 text-xs font-semibold ${statusUi.className}`}>
                            {statusUi.label}
                          </span>
                        </td>
                      </tr>
                    );
                  })
                )}
              </tbody>
            </table>
          </div>
        ) : (
          <div className="space-y-3 p-4">
            {loadingAlerts ? (
              <div className="rounded-xl border border-slate-200 px-4 py-6 text-center text-sm text-slate-500">
                Carregando alertas de envio...
              </div>
            ) : unresolvedAlerts.length === 0 ? (
              <div className="rounded-xl border border-emerald-200 bg-emerald-50 px-4 py-6 text-center text-sm font-medium text-emerald-700">
                Nenhum alerta pendente de ação.
              </div>
            ) : (
              unresolvedAlerts.map((alert) => {
                const contextParcela =
                  (typeof alert.context?.parcelaId === 'string' && alert.context.parcelaId) || null;

                return (
                  <article key={alert.id} className="rounded-xl border border-amber-200 bg-amber-50/60 p-4">
                    <div className="flex flex-col gap-3 lg:flex-row lg:items-start lg:justify-between">
                      <div>
                        <h3 className="text-sm font-semibold text-slate-800">
                          {alert.title || 'Falha no envio de recibo'}
                        </h3>
                        <p className="mt-1 text-sm text-slate-700">{alert.details}</p>
                        <p className="mt-2 text-xs text-slate-500">
                          Parcela: {contextParcela ?? 'Não informada'} • Criado em: {formatDate(alert.createdAt)}
                        </p>
                      </div>

                      <button
                        type="button"
                        onClick={() => handleRequeue(alert.id)}
                        disabled={requeueingId === alert.id}
                        className="inline-flex items-center justify-center rounded-lg bg-brand-primary px-3 py-2 text-xs font-semibold text-white transition-colors hover:bg-brand-primary-dark disabled:cursor-not-allowed disabled:opacity-60"
                      >
                        {requeueingId === alert.id ? 'Reenviando...' : 'Reenviar Recibo'}
                      </button>
                    </div>
                  </article>
                );
              })
            )}
          </div>
        )}
      </section>
    </main>
  );
}