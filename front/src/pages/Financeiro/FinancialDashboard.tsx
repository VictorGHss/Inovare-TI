import { useCallback, useEffect, useMemo, useState } from 'react';
import {
  AlertTriangle,
  ArrowUpRight,
  BadgeDollarSign,
  DollarSign,
  Landmark,
  RefreshCw,
  Wallet,
  Activity,
  FileDown,
  LayoutDashboard,
  Settings2,
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
  exportInventoryExitsReport,
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
  const [exporting, setExporting] = useState(false);

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

  const reloadDoctorMappings = useCallback(async () => {
    try {
      setLoadingDoctorMappings(true);
      const dados = await getDoctorMappings();
      setDoctorMappings(Array.isArray(dados) ? dados : []);
    } catch {
      toast.error('Não foi possível carregar os mapeamentos de médicos.');
      setDoctorMappings([]);
    } finally {
      setLoadingDoctorMappings(false);
    }
  }, []);

  const reloadDashboardData = useCallback(async () => {
    try {
      const status = await getFinanceConnectionStatus();
      setConnectionStatus(status);

      if (!status?.authorized) {
        setSummary(null);
        setReceipts([]);
        setAlerts([]);
        setAnalytics(null);
        setDoctorMappings([]);
        return;
      }

      const [summaryData, receiptsData, alertsData, analyticsData] = await Promise.all([
        getFinancialSummary(),
        getFinanceReceipts(),
        getFinanceAlerts(),
        getDashboardAnalytics(),
      ]);

      setSummary(summaryData ?? null);
      setReceipts(Array.isArray(receiptsData) ? receiptsData : []);
      setAlerts(Array.isArray(alertsData) ? alertsData : []);
      setAnalytics(analyticsData ?? null);
      await reloadDoctorMappings();
    } catch (err) {
      console.error('Erro ao carregar dados do dashboard financeiro', err);
      toast.error('Não foi possível carregar dados financeiros.');
      setSummary(null);
      setReceipts([]);
      setAlerts([]);
      setAnalytics(null);
      setDoctorMappings([]);
    }
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
      <main className="w-full max-w-full px-4 py-8 sm:px-6 lg:px-8 bg-orange-50/30 min-h-screen">
        <div className="grid gap-6 lg:grid-cols-4">
          {Array.from({ length: 4 }).map((_, index) => (
            <div key={index} className="h-36 animate-pulse rounded-3xl border border-orange-100 bg-white shadow-sm" />
          ))}
        </div>
      </main>
    );
  }

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
      value: String(unresolvedAlerts.length),
      helper: 'Ocorrências que ainda exigem acompanhamento.',
      icon: AlertTriangle,
      iconBg: 'bg-amber-50',
      iconColor: 'text-amber-600',
      accent: 'border-l-amber-400',
    },
  ];

  return (
    <main className="w-full max-w-full px-4 py-8 sm:px-6 lg:px-8 bg-orange-50/20 min-h-screen">

      {/* ── Page Header ── */}
      <section className="mb-8 flex flex-col gap-5 lg:flex-row lg:items-end lg:justify-between">
        <div>
          <span className="inline-flex items-center gap-1.5 rounded-full border border-brand-primary/30 bg-brand-secondary/60 px-3 py-1 text-xs font-bold uppercase tracking-[0.2em] text-brand-primary-dark">
            Inovare · Financeiro
          </span>
          <h1 className="mt-3 text-3xl font-extrabold tracking-tight text-slate-900">
            Dashboard Financeiro
          </h1>
          <p className="mt-1.5 max-w-2xl text-sm leading-6 text-slate-500">
            Acompanhe o faturamento e consumo interno por setor e médico.
          </p>
        </div>

        {/* Date range pickers */}
        <div className="flex items-end gap-3">
          <div className="flex flex-col gap-1.5">
            <label className="text-[11px] font-semibold uppercase tracking-widest text-slate-400">
              Data Início
            </label>
            <input
              type="date"
              value={startDate}
              onChange={(event) => setStartDate(event.target.value)}
              className="rounded-xl border border-slate-200 bg-white px-3 py-2.5 text-sm font-medium text-slate-700 shadow-sm focus:border-brand-primary focus:outline-none focus:ring-2 focus:ring-brand-primary/20 transition"
            />
          </div>
          <div className="flex flex-col gap-1.5">
            <label className="text-[11px] font-semibold uppercase tracking-widest text-slate-400">
              Data Fim
            </label>
            <input
              type="date"
              value={endDate}
              onChange={(event) => setEndDate(event.target.value)}
              className="rounded-xl border border-slate-200 bg-white px-3 py-2.5 text-sm font-medium text-slate-700 shadow-sm focus:border-brand-primary focus:outline-none focus:ring-2 focus:ring-brand-primary/20 transition"
            />
          </div>
        </div>
      </section>

      {/* ── Tab Navigation ── */}
      <div className="mb-6 flex items-center gap-2 rounded-2xl border border-slate-200 bg-white p-1.5 shadow-sm w-fit">
        <button
          onClick={() => setActiveTab('local')}
          aria-selected={activeTab === 'local'}
          className={`inline-flex items-center gap-2 rounded-xl px-5 py-2.5 text-sm font-semibold transition-all duration-200 ${
            activeTab === 'local'
              ? 'bg-brand-primary text-white shadow-sm'
              : 'text-slate-500 hover:text-slate-800 hover:bg-slate-50'
          }`}
        >
          <LayoutDashboard size={15} />
          Consumo Local
        </button>
        <button
          onClick={() => setActiveTab('integration')}
          aria-selected={activeTab === 'integration'}
          className={`inline-flex items-center gap-2 rounded-xl px-5 py-2.5 text-sm font-semibold transition-all duration-200 ${
            activeTab === 'integration'
              ? 'bg-brand-primary text-white shadow-sm'
              : 'text-slate-500 hover:text-slate-800 hover:bg-slate-50'
          }`}
        >
          <Settings2 size={15} />
          Integração
        </button>
      </div>

      {/* ── TAB: Consumo Local ── */}
      {activeTab === 'local' ? (
        <>
          <div className="flex items-center justify-end">
            <button
              onClick={async () => {
                if (startDate > endDate) {
                  toast.error('Data Início não pode ser maior que Data Fim.');
                  return;
                }

                try {
                  setExporting(true);
                  const blob = await exportInventoryExitsReport({ startDate, endDate });
                  const url = window.URL.createObjectURL(new Blob([blob]));
                  const link = document.createElement('a');
                  link.href = url;
                  const filename = `relatorio_consumo_${startDate}_to_${endDate}.xlsx`;
                  link.setAttribute('download', filename);
                  document.body.appendChild(link);
                  link.click();
                  link.parentNode?.removeChild(link);
                  window.URL.revokeObjectURL(url);
                } catch {
                  toast.error('Falha ao exportar relatório de consumo.');
                } finally {
                  setExporting(false);
                }
              }}
              disabled={exporting}
              className="inline-flex items-center gap-2 rounded-xl border border-brand-primary/40 bg-white px-4 py-2.5 text-sm font-semibold text-brand-primary-dark shadow-sm transition-colors hover:bg-brand-secondary/40 disabled:cursor-not-allowed disabled:opacity-70"
            >
              <FileDown size={15} />
              {exporting ? 'Exportando...' : 'Exportar Relatório de Consumo'}
            </button>
          </div>
          <InternalConsumptionPanel startDate={startDate} endDate={endDate} />
        </>
      ) : (
        /* ── TAB: Integração ── */
        <section className="space-y-6">

          {/* Action bar */}
          <div className="flex flex-col gap-4 lg:flex-row lg:items-center lg:justify-between">
            <div className="inline-flex items-center gap-2 rounded-2xl border border-slate-200 bg-white px-4 py-3 text-sm text-slate-600 shadow-sm">
              <span className="h-2 w-2 rounded-full bg-emerald-400" />
              Última atualização:{' '}
              <strong className="text-slate-900">{formatDate(connectionStatus?.refreshedAt)}</strong>
            </div>

            <div className="flex flex-wrap items-center gap-2.5">
              <button
                type="button"
                onClick={() => { void handleSyncReceiptsNow(); }}
                disabled={syncingReceipts}
                className="inline-flex items-center gap-2 rounded-xl border border-slate-200 bg-white px-4 py-2.5 text-sm font-semibold text-slate-700 shadow-sm transition-colors hover:bg-slate-50 disabled:cursor-not-allowed disabled:opacity-70"
              >
                <RefreshCw size={15} className={syncingReceipts ? 'animate-spin' : ''} />
                {syncingReceipts ? 'Sincronizando...' : 'Sincronizar Recibos'}
              </button>

              <button
                type="button"
                onClick={() => { void handleTriggerTestReceipt(); }}
                disabled={triggeringTestReceipt}
                className="inline-flex items-center gap-2 rounded-xl border border-slate-200 bg-white px-4 py-2.5 text-sm font-semibold text-slate-700 shadow-sm transition-colors hover:bg-slate-50 disabled:cursor-not-allowed disabled:opacity-70"
              >
                {triggeringTestReceipt ? 'Enviando...' : 'Enviar Recibo de Teste'}
              </button>

              <button
                type="button"
                onClick={() => {
                  window.open('http://172.25.0.171:3001/dashboards', '_blank', 'noopener');
                }}
                className="inline-flex items-center gap-2 rounded-xl bg-brand-primary px-4 py-2.5 text-sm font-semibold text-white shadow-sm transition-colors hover:bg-brand-primary-dark"
              >
                <Activity size={15} />
                <span className="hidden sm:inline">Dashboard de Saúde</span>
              </button>

              {!hasContaAzulLinked && (
                <button
                  type="button"
                  onClick={() => { window.location.href = CONTA_AZUL_AUTHORIZE_URL; }}
                  className="inline-flex items-center gap-2 rounded-xl bg-brand-primary px-5 py-2.5 text-sm font-semibold text-white shadow-sm transition-colors hover:bg-brand-primary-dark"
                >
                  <BadgeDollarSign size={15} />
                  Vincular Conta Azul
                </button>
              )}
            </div>
          </div>

          {/* Metric cards */}
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

          {/* Resumo + Status */}
          <section className="grid gap-5 xl:grid-cols-[1.6fr_1fr]">
            <article className="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm">
              <div className="flex items-center justify-between gap-4">
                <div>
                  <h2 className="text-base font-bold text-slate-800">Resumo operacional</h2>
                  <p className="mt-0.5 text-sm text-slate-500">
                    Indicadores financeiros retornados pela API.
                  </p>
                </div>
                <ArrowUpRight className="text-brand-primary" size={20} />
              </div>

              <div className="mt-5 grid gap-3 md:grid-cols-4">
                {[
                  {
                    label: 'Integração',
                    value: summary?.externalServiceAvailable === false ? 'Indisponível' : 'Ativa',
                    valueClass: summary?.externalServiceAvailable === false ? 'text-amber-600' : 'text-emerald-600',
                    sub: summary?.externalServiceAvailable === false ? 'Conta Azul retornou restrição (403).' : null,
                  },
                  {
                    label: 'Última renovação',
                    value: formatDate(connectionStatus?.refreshedAt),
                    valueClass: 'text-slate-800',
                    sub: null,
                  },
                  {
                    label: 'Moeda',
                    value: currency,
                    valueClass: 'text-slate-800',
                    sub: null,
                  },
                  {
                    label: 'Itens no estoque',
                    value: analytics?.inventorySummary?.totalItems ?? '—',
                    valueClass: 'text-slate-800',
                    sub: `Baixa: ${analytics?.inventorySummary?.lowStockItems ?? '—'} · Zerados: ${analytics?.inventorySummary?.outOfStockItems ?? '—'}`,
                  },
                ].map((item) => (
                  <div key={item.label} className="rounded-xl bg-orange-50/60 border border-orange-100 p-4">
                    <p className="text-[10px] font-bold uppercase tracking-widest text-slate-400">{item.label}</p>
                    <strong className={`mt-2 block text-base font-bold ${item.valueClass}`}>{item.value}</strong>
                    {item.sub && <p className="mt-1 text-xs text-amber-600">{item.sub}</p>}
                  </div>
                ))}
              </div>
            </article>

            <aside className="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm">
              <h2 className="text-base font-bold text-slate-800">Status da integração</h2>
              <div className="mt-4 space-y-3">
                {[
                  { label: 'Token OAuth', value: 'Ativo', valueClass: 'text-emerald-600', sub: `Expiração: ${formatDate(connectionStatus?.expiresAt)}` },
                  { label: 'Recibos sincronizados', value: String(summary?.syncedReceiptsCount ?? receipts.length), valueClass: 'text-slate-800', sub: null },
                  { label: 'Alertas em aberto', value: String(unresolvedAlerts.length), valueClass: unresolvedAlerts.length > 0 ? 'text-amber-600' : 'text-slate-800', sub: null },
                ].map((item) => (
                  <div key={item.label} className="flex items-center justify-between rounded-xl border border-slate-100 bg-slate-50 px-4 py-3">
                    <div>
                      <p className="text-[10px] font-bold uppercase tracking-widest text-slate-400">{item.label}</p>
                      {item.sub && <p className="mt-0.5 text-xs text-slate-400">{item.sub}</p>}
                    </div>
                    <strong className={`text-lg font-extrabold ${item.valueClass}`}>{item.value}</strong>
                  </div>
                ))}
              </div>
            </aside>
          </section>

          {hasContaAzulLinked && (
            <section>
              <DoctorMappingPanel mapeamentos={doctorMappings} carregando={loadingDoctorMappings} onAtualizar={reloadDoctorMappings} />
            </section>
          )}
        </section>
      )}
    </main>
  );
}
