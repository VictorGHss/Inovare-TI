import { Activity, BadgeDollarSign, Eye, EyeOff, FileDown, LayoutDashboard, RefreshCw, Settings2 } from 'lucide-react';
import { useEffect, useState } from 'react';
import DoctorMappingPanel from './DoctorMappingPanel.tsx';
import InternalConsumptionPanel from './InternalConsumptionPanel';
import FinancialMetricsGrid from './FinancialMetricsGrid';
import FinancialAlertsList from './FinancialAlertsList';
import ContaAzulStatusCard from './ContaAzulStatusCard';
import { useFinancialDashboard } from '../../hooks/useFinancialDashboard';
import { useFinancialActions } from '../../hooks/useFinancialActions';

const CONTA_AZUL_AUTHORIZE_URL = 'https://itsm-inovare.ctrls.dev.br/api/financeiro/contaazul/authorize';
const ISO_DATETIME_PREFIX_REGEX = /^(\d{4})-(\d{2})-(\d{2})[T\s](\d{2}):(\d{2})/;
const PRIVACY_STORAGE_KEY = '@InovareTI:financeiro:is-private';

function formatDate(value?: string | null): string {
  if (!value) {
    return '—';
  }

  const normalized = value.trim();
  const directMatch = normalized.match(ISO_DATETIME_PREFIX_REGEX);
  if (directMatch) {
    const [, year, month, day, hour, minute] = directMatch;
    // Exibe o horário exatamente como veio do backend, sem reconverter fuso no navegador.
    return `${day}/${month}/${year} ${hour}:${minute}`;
  }

  // Fallback: mantém o valor original do servidor quando não casar no ISO padrão.
  return normalized;
}

export default function FinancialDashboard() {
  const {
    loading,
    connectionStatus,
    summary,
    analytics,
    receipts,
    alerts,
    doctorMappings,
    loadingDoctorMappings,
    startDate,
    endDate,
    setStartDate,
    setEndDate,
    activeTab,
    setActiveTab,
    hasContaAzulLinked,
    unresolvedAlerts,
    reloadDashboardData,
    reloadDoctorMappings,
  } = useFinancialDashboard();

  const {
    triggeringTestReceipt,
    syncingReceipts,
    exporting,
    handleTriggerTestReceipt,
    handleSyncReceiptsNow,
    handleExportConsumption,
  } = useFinancialActions({
    startDate,
    endDate,
    reloadDashboardData,
  });

  const currency = summary?.currency ?? 'BRL';
  const integrationActive = summary?.integrationActive ?? hasContaAzulLinked;
  const lastUpdatedAt = summary?.lastUpdatedAt ?? connectionStatus?.refreshedAt;
  const [isPrivate, setIsPrivate] = useState<boolean>(() => {
    // Persiste preferência do usuário para não exigir novo clique a cada reload.
    const saved = localStorage.getItem(PRIVACY_STORAGE_KEY);
    return saved == null ? false : saved === 'true';
  });

  useEffect(() => {
    localStorage.setItem(PRIVACY_STORAGE_KEY, String(isPrivate));
  }, [isPrivate]);

  function handleConnectContaAzul() {
    window.location.href = CONTA_AZUL_AUTHORIZE_URL;
  }

  if (loading) {
    return (
      <main className="w-full max-w-full px-4 py-8 sm:px-6 lg:px-8 bg-orange-50/30 min-h-screen">
        <div className="grid gap-6 lg:grid-cols-4">
          {Array.from({ length: 4 }).map((_, index) => (
            <div
              key={index}
              className="h-36 animate-pulse rounded-3xl border border-orange-100 bg-white shadow-sm"
            />
          ))}
        </div>
      </main>
    );
  }

  return (
    <main className="w-full max-w-full px-4 py-8 sm:px-6 lg:px-8 bg-orange-50/20 min-h-screen">
      {/* ── Page Header ── */}
      <section className="mb-8 flex flex-col gap-5 lg:flex-row lg:items-end lg:justify-between">
        <div>
          <span className="inline-flex items-center gap-1.5 rounded-full border border-brand-primary/30 bg-brand-secondary/60 px-3 py-1 text-xs font-bold uppercase tracking-[0.2em] text-brand-primary-dark">
            Inovare · Financeiro
          </span>
          <h1 className="mt-3 text-3xl font-extrabold tracking-tight text-slate-900">Dashboard Financeiro</h1>
          <p className="mt-1.5 max-w-2xl text-sm leading-6 text-slate-500">
            Acompanhe o faturamento e consumo interno por setor e médico.
          </p>
        </div>

        {/* Date range pickers */}
        <div className="flex items-end gap-3">
          <button
            type="button"
            onClick={() => setIsPrivate((current) => !current)}
            className="mb-0.5 inline-flex h-11 w-11 items-center justify-center rounded-xl border border-slate-200 bg-white text-slate-600 shadow-sm transition-colors hover:bg-slate-50 hover:text-slate-800"
            aria-label={isPrivate ? 'Exibir valores financeiros' : 'Ocultar valores financeiros'}
            title={isPrivate ? 'Exibir valores financeiros' : 'Ocultar valores financeiros'}
          >
            {isPrivate ? <EyeOff size={18} /> : <Eye size={18} />}
          </button>
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
            <label className="text-[11px] font-semibold uppercase tracking-widest text-slate-400">Data Fim</label>
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
              onClick={() => {
                void handleExportConsumption();
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
              <span className={`h-2 w-2 rounded-full ${integrationActive ? 'bg-emerald-400' : 'bg-red-500'}`} />
              Última atualização:{' '}
              <strong className="text-slate-900">{formatDate(lastUpdatedAt)}</strong>
            </div>

            <div className="flex flex-wrap items-center gap-2.5">
              <button
                type="button"
                onClick={() => {
                  void handleSyncReceiptsNow();
                }}
                disabled={syncingReceipts}
                className="inline-flex items-center gap-2 rounded-xl border border-slate-200 bg-white px-4 py-2.5 text-sm font-semibold text-slate-700 shadow-sm transition-colors hover:bg-slate-50 disabled:cursor-not-allowed disabled:opacity-70"
              >
                <RefreshCw size={15} className={syncingReceipts ? 'animate-spin' : ''} />
                {syncingReceipts ? 'Sincronizando...' : 'Sincronizar Recibos'}
              </button>

              <button
                type="button"
                onClick={() => {
                  void handleTriggerTestReceipt();
                }}
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

              {!integrationActive && (
                <button
                  type="button"
                  onClick={handleConnectContaAzul}
                  className="inline-flex items-center gap-2 rounded-xl bg-brand-primary px-5 py-2.5 text-sm font-semibold text-white shadow-sm transition-colors hover:bg-brand-primary-dark"
                >
                  <BadgeDollarSign size={15} />
                  Conectar ContaAzul
                </button>
              )}
            </div>
          </div>

          <FinancialMetricsGrid summary={summary} unresolvedAlertsCount={unresolvedAlerts.length} isPrivate={isPrivate} />

          <section className="grid gap-5 xl:grid-cols-[1.6fr_1fr]">
            <article className="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm">
              <h2 className="text-base font-bold text-slate-800">Resumo operacional</h2>
              <p className="mt-0.5 text-sm text-slate-500">Indicadores financeiros retornados pela API.</p>

              <div className="mt-5 grid gap-3 md:grid-cols-4">
                {[
                  {
                    label: 'Integração',
                    value: integrationActive ? 'Ativa' : 'Integração Pendente',
                    valueClass:
                      integrationActive ? 'text-emerald-600' : 'text-red-600',
                    sub:
                      !integrationActive
                        ? 'Conecte sua ContaAzul para habilitar sincronização e resumo financeiro.'
                        : null,
                  },
                  {
                    label: 'Última renovação',
                    value: formatDate(lastUpdatedAt),
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

            <ContaAzulStatusCard
              connectionStatus={connectionStatus}
              summary={summary}
              receipts={receipts}
              unresolvedAlertsCount={unresolvedAlerts.length}
              integrationActive={integrationActive}
              onConnectContaAzul={handleConnectContaAzul}
            />
          </section>

          <FinancialAlertsList alerts={alerts} />

          <section>
            <DoctorMappingPanel
              mapeamentos={doctorMappings}
              carregando={loadingDoctorMappings}
              onAtualizar={reloadDoctorMappings}
              integrationActive={integrationActive}
              onConnectContaAzul={handleConnectContaAzul}
            />
          </section>
        </section>
      )}
    </main>
  );
}
