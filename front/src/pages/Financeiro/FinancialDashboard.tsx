import { useCallback, useEffect, useMemo, useState } from 'react';
import {
  AlertTriangle,
  ArrowUpRight,
  BadgeDollarSign,
  DollarSign,
  Landmark,
  Link2,
  RefreshCw,
  Wallet,
} from 'lucide-react';
import { useSearchParams } from 'react-router-dom';
import { toast } from 'react-toastify';
import api, {
  getDoctorMappings,
  executeFinanceAutomationNow,
  getFinanceAlerts,
  getFinanceConnectionStatus,
  getFinanceReceipts,
  getFinancialSummary,
  getDashboardAnalytics,
  type DoctorMapping,
  type FinanceAlert,
  type FinanceConnectionStatus,
  type FinanceReceipt,
  type FinancialSummaryDTO,
  type DashboardAnalyticsDTO,
} from '../../services/api';
import DoctorMappingPanel from './DoctorMappingPanel.tsx';
import InternalConsumptionPanel from './InternalConsumptionPanel';

const CONTA_AZUL_AUTHORIZE_URL = 'https://itsm-inovare.ctrls.dev.br/api/financeiro/contaazul/authorize';

function formatCurrency(value: number | null | undefined, currency = 'BRL'): string {
  if (value === null || value === undefined) {
    return 'Aguardando API';
  }

  return new Intl.NumberFormat('pt-BR', {
    style: 'currency',
    currency,
  }).format(value / 100);
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

function formatDateForInput(value: Date): string {
  const year = value.getFullYear();
  const month = String(value.getMonth() + 1).padStart(2, '0');
  const day = String(value.getDate()).padStart(2, '0');
  return `${year}-${month}-${day}`;
}

export default function FinancialDashboard() {
  function getDefaultCycleDates() {
    const now = new Date();
    const day = now.getDate();
    let start: Date;
    if (day >= 12) {
      start = new Date(now.getFullYear(), now.getMonth(), 12);
    } else {
      const prev = new Date(now.getFullYear(), now.getMonth() - 1, 12);
      start = prev;
    }
    const end = now;
    return { start: formatDateForInput(start), end: formatDateForInput(end) };
  }

  const [searchParams, setSearchParams] = useSearchParams();
  const [loading, setLoading] = useState(true);
  const [connectionStatus, setConnectionStatus] = useState<FinanceConnectionStatus | null>(null);
  const [summary, setSummary] = useState<FinancialSummaryDTO | null>(null);
  const [analytics, setAnalytics] = useState<DashboardAnalyticsDTO | null>(null);
  const [receipts, setReceipts] = useState<FinanceReceipt[]>([]);
  const [alerts, setAlerts] = useState<FinanceAlert[]>([]);
  const [doctorMappings, setDoctorMappings] = useState<DoctorMapping[]>([]);
  const [loadingDoctorMappings, setLoadingDoctorMappings] = useState(false);
  const [triggeringTestReceipt, setTriggeringTestReceipt] = useState(false);
  const [syncingReceipts, setSyncingReceipts] = useState(false);
  const defaultCycle = getDefaultCycleDates();
  const [startDate, setStartDate] = useState(defaultCycle.start);
  const [endDate, setEndDate] = useState(defaultCycle.end);
  const [activeTab, setActiveTab] = useState<'local' | 'integration'>('local');

  const hasContaAzulLinked = connectionStatus?.authorized === true;
  const unresolvedAlerts = useMemo(() => alerts.filter((alert) => !alert.resolved), [alerts]);

  useEffect(() => {
    if (searchParams.get('success') === 'true') {
      toast.success('Conexão com Conta Azul estabelecida com sucesso!');
      const nextParams = new URLSearchParams(searchParams);
      nextParams.delete('success');
      setSearchParams(nextParams, { replace: true });
    }
  }, [searchParams, setSearchParams]);

  // Carrega mapeamentos usados pelo backend para envio automático dos recibos.
  const reloadDoctorMappings = useCallback(async () => {
    try {
      setLoadingDoctorMappings(true);
      const dados = await getDoctorMappings();
      setDoctorMappings(dados);
    } catch {
      toast.error('Não foi possível carregar os mapeamentos de médicos.');
    } finally {
      setLoadingDoctorMappings(false);
    }
  }, []);

  const reloadDashboardData = useCallback(async () => {
    const status = await getFinanceConnectionStatus();
    setConnectionStatus(status);

    if (!status.authorized) {
      setSummary(null);
      setReceipts([]);
      setAlerts([]);
      setDoctorMappings([]);
      return;
    }

    const [summaryData, receiptsData, alertsData, analyticsData] = await Promise.all([
      getFinancialSummary(),
      getFinanceReceipts(),
      getFinanceAlerts(),
      getDashboardAnalytics(),
    ]);

    // set results
    setSummary(summaryData);
    setReceipts(receiptsData);
    setAlerts(alertsData);
    setAnalytics(analyticsData);
    await reloadDoctorMappings();
  }, [reloadDoctorMappings]);

  useEffect(() => {
    async function loadDashboard() {
      try {
        setLoading(true);
        await reloadDashboardData();
      } catch {
        toast.error('Não foi possível carregar o módulo financeiro.');
      } finally {
        setLoading(false);
      }
    }

    void loadDashboard();
  }, [reloadDashboardData]);

  async function handleTriggerTestReceipt() {
    try {
      setTriggeringTestReceipt(true);
      await api.get('/api/financeiro/trigger-test-receipt');
      toast.success('Recibo de teste enviado com sucesso.');
    } catch {
      toast.error('Falha ao enviar recibo de teste.');
    } finally {
      setTriggeringTestReceipt(false);
    }
  }

  async function handleSyncReceiptsNow() {
    if (startDate > endDate) {
      toast.error('Data Início não pode ser maior que Data Fim.');
      return;
    }

    try {
      setSyncingReceipts(true);
      await executeFinanceAutomationNow({ dataInicio: startDate, dataFim: endDate });
      await reloadDashboardData();
      toast.success('Sincronização executada com sucesso.');
    } catch {
      toast.error('Falha ao executar sincronização manual de recibos.');
    } finally {
      setSyncingReceipts(false);
    }
  }

  if (loading) {
    return (
      <main className="w-full max-w-full px-4 py-8 sm:px-6 lg:px-8">
        <div className="grid gap-6 lg:grid-cols-4">
          {Array.from({ length: 4 }).map((_, index) => (
            <div key={index} className="h-36 animate-pulse rounded-3xl border border-slate-200 bg-white shadow-sm" />
          ))}
        </div>
      </main>
    );
  }

  // O painel local é prioridade — não bloqueamos por ausência de integração externa.
  const currency = summary?.currency ?? 'BRL';

  const metricCards = [
    {
      title: 'Saldo',
      value: formatCurrency(summary?.balanceCents, currency),
      helper: 'Posição consolidada do financeiro na Conta Azul.',
      icon: Wallet,
      tone: 'bg-emerald-50 text-emerald-700',
    },
    {
      title: 'Total Pendente',
      value: formatCurrency(summary?.totalPendingCents, currency),
      helper: 'Valores ainda pendentes de quitação.',
      icon: DollarSign,
      tone: 'bg-sky-50 text-sky-700',
    },
    {
      title: 'Total Pago',
      value: formatCurrency(summary?.totalPaidCents, currency),
      helper: 'Valores já recebidos e conciliados.',
      icon: Landmark,
      tone: 'bg-violet-50 text-violet-700',
    },
    {
      title: 'Alertas Pendentes',
      value: String(unresolvedAlerts.length),
      helper: 'Ocorrências que ainda exigem acompanhamento.',
      icon: AlertTriangle,
      tone: 'bg-amber-50 text-amber-700',
    },
  ];

  return (
    <main className="w-full max-w-full px-4 py-8 sm:px-6 lg:px-8">
      <section className="mb-8 flex flex-col gap-4 lg:flex-row lg:items-end lg:justify-between">
        <div>
          <span className="rounded-full border border-emerald-200 bg-emerald-50 px-3 py-1 text-xs font-semibold uppercase tracking-[0.2em] text-emerald-700">
            Consumo Interno
          </span>
          <h1 className="mt-4 text-3xl font-bold tracking-tight text-slate-900">Dashboard Financeiro</h1>
          <p className="mt-2 max-w-3xl text-sm leading-6 text-slate-600">Acompanhe o faturamento e consumo interno por setor e médico.</p>
        </div>

        <div className="flex items-center gap-3">
          <div className="flex flex-col gap-1 text-xs font-semibold uppercase tracking-[0.12em] text-slate-500">
            <label className="text-[11px] text-slate-500">Data Início</label>
            <input
              type="date"
              value={startDate}
              onChange={(event) => setStartDate(event.target.value)}
              className="rounded-xl border border-slate-300 px-3 py-2 text-sm font-medium text-slate-700 focus:border-brand-primary focus:outline-none"
            />
          </div>

          <div className="flex flex-col gap-1 text-xs font-semibold uppercase tracking-[0.12em] text-slate-500">
            <label className="text-[11px] text-slate-500">Data Fim</label>
            <input
              type="date"
              value={endDate}
              onChange={(event) => setEndDate(event.target.value)}
              className="rounded-xl border border-slate-300 px-3 py-2 text-sm font-medium text-slate-700 focus:border-brand-primary focus:outline-none"
            />
          </div>
        </div>
      </section>

      <div className="mt-4 flex items-center gap-4">
        <button
          onClick={() => setActiveTab('local')}
          aria-selected={activeTab === 'local'}
          className={`px-4 py-2 rounded-2xl ${activeTab === 'local' ? 'bg-emerald-100 text-emerald-800' : 'bg-white text-slate-700 border border-slate-200'}`}
        >
          Consumo Local
        </button>
        <button
          onClick={() => setActiveTab('integration')}
          aria-selected={activeTab === 'integration'}
          className={`px-4 py-2 rounded-2xl ${activeTab === 'integration' ? 'bg-sky-100 text-sky-800' : 'bg-white text-slate-700 border border-slate-200'}`}
        >
          Configurações de Integração
        </button>
      </div>

      {activeTab === 'local' ? (
        <InternalConsumptionPanel startDate={startDate} endDate={endDate} />
      ) : (
        <section className="mt-6 space-y-6">
          <div className="flex flex-col gap-4 lg:flex-row lg:items-center lg:justify-between">
            <div className="rounded-2xl border border-slate-200 bg-white px-4 py-3 text-sm text-slate-600 shadow-sm">
              Última atualização do token: <strong className="text-slate-900">{formatDate(connectionStatus?.refreshedAt)}</strong>
            </div>

            <div className="flex items-center gap-3">
              <button
                type="button"
                onClick={() => {
                  void handleSyncReceiptsNow();
                }}
                disabled={syncingReceipts}
                className="inline-flex items-center gap-2 rounded-2xl border border-slate-300 bg-white px-4 py-3 text-sm font-medium text-slate-700 shadow-sm transition-colors hover:bg-slate-50 disabled:cursor-not-allowed disabled:opacity-70"
              >
                {syncingReceipts ? <RefreshCw size={16} className="animate-spin" /> : <RefreshCw size={16} />}
                {syncingReceipts ? 'Sincronizando...' : 'Sincronizar Recibos'}
              </button>

              <button
                type="button"
                onClick={() => {
                  void handleTriggerTestReceipt();
                }}
                disabled={triggeringTestReceipt}
                className="rounded-2xl border border-slate-300 bg-white px-4 py-3 text-sm font-medium text-slate-700 shadow-sm transition-colors hover:bg-slate-50 disabled:cursor-not-allowed disabled:opacity-70"
              >
                {triggeringTestReceipt ? 'Enviando...' : 'Enviar Recibo de Teste (Dev)'}
              </button>

              {!hasContaAzulLinked && (
                <button
                  type="button"
                  onClick={() => {
                    window.location.href = CONTA_AZUL_AUTHORIZE_URL;
                  }}
                  className="inline-flex items-center justify-center gap-3 rounded-2xl bg-brand-primary px-5 py-3 text-sm font-semibold text-white shadow-sm transition-colors hover:bg-brand-primary-dark"
                >
                  <BadgeDollarSign size={16} />
                  Vincular Conta Azul
                </button>
              )}
            </div>
          </div>

          <section className="grid gap-5 md:grid-cols-2 xl:grid-cols-4 mt-2">
            {metricCards.map((card) => {
              const Icon = card.icon;
              return (
                <article key={card.title} className="rounded-3xl border border-slate-200 bg-white p-5 shadow-sm transition-transform hover:-translate-y-0.5">
                  <div className="flex items-start justify-between gap-4">
                    <div>
                      <p className="text-xs font-semibold uppercase tracking-[0.2em] text-slate-400">{card.title}</p>
                      <strong className="mt-4 block text-2xl font-bold text-slate-900">{card.value}</strong>
                    </div>
                    <span className={`inline-flex h-12 w-12 items-center justify-center rounded-2xl ${card.tone}`}>
                      <Icon size={22} />
                    </span>
                  </div>
                  <p className="mt-4 text-sm leading-6 text-slate-500">{card.helper}</p>
                </article>
              );
            })}
          </section>

          <section className="mt-8 grid gap-6 xl:grid-cols-[1.6fr_1fr]">
            <article className="rounded-3xl border border-slate-200 bg-white p-6 shadow-sm">
              <div className="flex items-center justify-between gap-4">
                <div>
                  <h2 className="text-lg font-semibold text-slate-900">Resumo operacional</h2>
                  <p className="mt-1 text-sm text-slate-500">Visão preparada para os próximos indicadores financeiros retornados pela API.</p>
                </div>
                <ArrowUpRight className="text-slate-300" size={20} />
              </div>

              <div className="mt-6 grid gap-4 md:grid-cols-4">
                <div className="rounded-2xl bg-slate-50 p-4">
                  <p className="text-xs font-semibold uppercase tracking-[0.18em] text-slate-400">Integração</p>
                  <strong className={`mt-3 block text-lg font-semibold ${summary?.externalServiceAvailable === false ? 'text-amber-700' : 'text-emerald-700'}`}>
                    {summary?.externalServiceAvailable === false ? 'Indisponível' : 'Ativa'}
                  </strong>
                  {summary?.externalServiceAvailable === false && (
                    <p className="mt-2 text-sm text-amber-700">Conta Azul retornou restrição (403). Verifique assinatura.</p>
                  )}
                </div>
                <div className="rounded-2xl bg-slate-50 p-4">
                  <p className="text-xs font-semibold uppercase tracking-[0.18em] text-slate-400">Última renovação</p>
                  <strong className="mt-3 block text-lg font-semibold text-slate-900">{formatDate(connectionStatus?.refreshedAt)}</strong>
                </div>
                <div className="rounded-2xl bg-slate-50 p-4">
                  <p className="text-xs font-semibold uppercase tracking-[0.18em] text-slate-400">Moeda</p>
                  <strong className="mt-3 block text-lg font-semibold text-slate-900">{currency}</strong>
                </div>
                <div className="rounded-2xl bg-slate-50 p-4">
                  <p className="text-xs font-semibold uppercase tracking-[0.18em] text-slate-400">Itens</p>
                  <strong className="mt-3 block text-lg font-semibold text-slate-900">{analytics?.inventorySummary?.totalItems ?? '—'}</strong>
                  <p className="mt-1 text-sm text-slate-500">Baixa: {analytics?.inventorySummary?.lowStockItems ?? '—'} • Zerados: {analytics?.inventorySummary?.outOfStockItems ?? '—'}</p>
                </div>
              </div>
            </article>

            <aside className="rounded-3xl border border-slate-200 bg-white p-6 shadow-sm">
              <h2 className="text-lg font-semibold text-slate-900">Status da integração</h2>
              <div className="mt-5 space-y-4">
                <div className="rounded-2xl border border-slate-200 p-4">
                  <p className="text-xs font-semibold uppercase tracking-[0.18em] text-slate-400">Token OAuth</p>
                  <strong className="mt-2 block text-lg font-semibold text-emerald-700">Ativo</strong>
                  <p className="mt-2 text-sm text-slate-500">Expiração prevista: {formatDate(connectionStatus?.expiresAt)}</p>
                </div>
                <div className="rounded-2xl border border-slate-200 p-4">
                  <p className="text-xs font-semibold uppercase tracking-[0.18em] text-slate-400">Recibos sincronizados</p>
                  <strong className="mt-2 block text-lg font-semibold text-slate-900">{summary?.syncedReceiptsCount ?? receipts.length}</strong>
                </div>
                <div className="rounded-2xl border border-slate-200 p-4">
                  <p className="text-xs font-semibold uppercase tracking-[0.18em] text-slate-400">Alertas em aberto</p>
                  <strong className="mt-2 block text-lg font-semibold text-slate-900">{unresolvedAlerts.length}</strong>
                </div>
              </div>
            </aside>
          </section>

          {hasContaAzulLinked && (
            <section className="mt-8">
              <DoctorMappingPanel mapeamentos={doctorMappings} carregando={loadingDoctorMappings} onAtualizar={reloadDoctorMappings} />
            </section>
          )}
        </section>
      )}
    </main>
  );
}
