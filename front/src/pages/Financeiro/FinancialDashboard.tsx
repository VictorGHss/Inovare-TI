import { useEffect, useMemo, useState } from 'react';
import {
  AlertTriangle,
  ArrowUpRight,
  BadgeDollarSign,
  DollarSign,
  Landmark,
  Link2,
  Wallet,
} from 'lucide-react';
import { useSearchParams } from 'react-router-dom';
import { toast } from 'react-toastify';
import {
  getFinanceAlerts,
  getFinanceConnectionStatus,
  getFinanceReceipts,
  getFinancialSummary,
  type FinanceAlert,
  type FinanceConnectionStatus,
  type FinanceReceipt,
  type FinancialSummaryDTO,
} from '../../services/api';

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

export default function FinancialDashboard() {
  const [searchParams, setSearchParams] = useSearchParams();
  const [loading, setLoading] = useState(true);
  const [connectionStatus, setConnectionStatus] = useState<FinanceConnectionStatus | null>(null);
  const [summary, setSummary] = useState<FinancialSummaryDTO | null>(null);
  const [receipts, setReceipts] = useState<FinanceReceipt[]>([]);
  const [alerts, setAlerts] = useState<FinanceAlert[]>([]);

  const hasContaAzulLinked = connectionStatus?.authorized === true;
  const unresolvedAlerts = useMemo(
    () => alerts.filter((alert) => !alert.resolved),
    [alerts],
  );

  useEffect(() => {
    if (searchParams.get('success') === 'true') {
      toast.success('Conexão com Conta Azul estabelecida com sucesso!');
      const nextParams = new URLSearchParams(searchParams);
      nextParams.delete('success');
      setSearchParams(nextParams, { replace: true });
    }
  }, [searchParams, setSearchParams]);

  useEffect(() => {
    async function loadDashboard() {
      try {
        setLoading(true);
        const status = await getFinanceConnectionStatus();
        setConnectionStatus(status);

        if (!status.authorized) {
          setSummary(null);
          setReceipts([]);
          setAlerts([]);
          return;
        }

        const [summaryData, receiptsData, alertsData] = await Promise.all([
          getFinancialSummary(),
          getFinanceReceipts(),
          getFinanceAlerts(),
        ]);

        setSummary(summaryData);
        setReceipts(receiptsData);
        setAlerts(alertsData);
      } catch {
        toast.error('Não foi possível carregar o módulo financeiro.');
      } finally {
        setLoading(false);
      }
    }

    void loadDashboard();
  }, []);

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

  if (!hasContaAzulLinked) {
    return (
      <main className="flex min-h-[calc(100vh-12rem)] w-full items-center justify-center px-4 py-8 sm:px-6 lg:px-8">
        <section className="relative w-full max-w-3xl overflow-hidden rounded-[2rem] border border-slate-200 bg-white p-8 shadow-sm sm:p-12">
          <div className="absolute -left-10 -top-10 h-40 w-40 rounded-full bg-brand-secondary/60 blur-2xl" />
          <div className="absolute -bottom-12 -right-12 h-44 w-44 rounded-full bg-emerald-100 blur-2xl" />

          <div className="relative flex flex-col items-center text-center">
            <div className="mb-6 flex h-28 w-28 items-center justify-center rounded-[2rem] bg-[linear-gradient(135deg,_#eff6ff,_#dcfce7)] shadow-inner">
              <div className="relative flex h-20 w-20 items-center justify-center rounded-[1.5rem] bg-white shadow-sm">
                <Landmark size={30} className="text-brand-primary" />
                <div className="absolute -bottom-2 -right-2 flex h-9 w-9 items-center justify-center rounded-full bg-emerald-500 text-white shadow-lg">
                  <Link2 size={16} />
                </div>
              </div>
            </div>

            <span className="rounded-full border border-slate-200 bg-slate-50 px-3 py-1 text-xs font-semibold uppercase tracking-[0.2em] text-slate-500">
              Integração Financeira
            </span>

            <h1 className="mt-5 text-3xl font-bold tracking-tight text-slate-900">
              Sua conta Inovare ainda não está integrada ao Conta Azul.
            </h1>

            <p className="mt-4 max-w-2xl text-base leading-7 text-slate-600">
              Conecte sua conta para visualizar indicadores financeiros, acompanhar o fluxo de recebimentos e centralizar sua operação no mesmo painel do sistema.
            </p>

            <button
              type="button"
              onClick={() => {
                window.location.href = CONTA_AZUL_AUTHORIZE_URL;
              }}
              className="mt-8 inline-flex items-center justify-center gap-3 rounded-2xl bg-brand-primary px-7 py-4 text-base font-semibold text-white shadow-sm transition-colors hover:bg-brand-primary-dark"
            >
              <BadgeDollarSign size={20} />
              Vincular Conta Azul agora
            </button>
          </div>
        </section>
      </main>
    );
  }

  const currency = summary?.currency ?? 'BRL';

  const metricCards = [
    {
      title: 'Saldo Disponível',
      value: formatCurrency(summary?.balanceCents, currency),
      helper: 'Posição consolidada da integração financeira.',
      icon: Wallet,
      tone: 'bg-emerald-50 text-emerald-700',
    },
    {
      title: 'Contas a Receber',
      value: formatCurrency(summary?.accountsReceivableCents, currency),
      helper: 'Valores pendentes aguardando liquidação.',
      icon: DollarSign,
      tone: 'bg-sky-50 text-sky-700',
    },
    {
      title: 'Recibos Processados',
      value: String(receipts.length),
      helper: 'Recibos sincronizados a partir da Conta Azul.',
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
            Conta Azul vinculada
          </span>
          <h1 className="mt-4 text-3xl font-bold tracking-tight text-slate-900">Dashboard Financeiro</h1>
          <p className="mt-2 max-w-3xl text-sm leading-6 text-slate-600">
            Acompanhe o panorama financeiro da integração e os principais indicadores do módulo conforme a API da Conta Azul disponibiliza os dados.
          </p>
        </div>

        <div className="rounded-2xl border border-slate-200 bg-white px-4 py-3 text-sm text-slate-600 shadow-sm">
          Última atualização do token: <strong className="text-slate-900">{formatDate(connectionStatus?.refreshedAt)}</strong>
        </div>
      </section>

      <section className="grid gap-5 md:grid-cols-2 xl:grid-cols-4">
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
              <p className="mt-1 text-sm text-slate-500">
                Visão preparada para os próximos indicadores financeiros retornados pela API.
              </p>
            </div>
            <ArrowUpRight className="text-slate-300" size={20} />
          </div>

          <div className="mt-6 grid gap-4 md:grid-cols-3">
            <div className="rounded-2xl bg-slate-50 p-4">
              <p className="text-xs font-semibold uppercase tracking-[0.18em] text-slate-400">Próximo recebimento</p>
              <strong className="mt-3 block text-lg font-semibold text-slate-900">{formatDate(summary?.nextSettlementAt)}</strong>
            </div>
            <div className="rounded-2xl bg-slate-50 p-4">
              <p className="text-xs font-semibold uppercase tracking-[0.18em] text-slate-400">Recebíveis vencidos</p>
              <strong className="mt-3 block text-lg font-semibold text-slate-900">
                {formatCurrency(summary?.overdueReceivablesCents, currency)}
              </strong>
            </div>
            <div className="rounded-2xl bg-slate-50 p-4">
              <p className="text-xs font-semibold uppercase tracking-[0.18em] text-slate-400">Integração</p>
              <strong className="mt-3 block text-lg font-semibold text-emerald-700">Ativa</strong>
            </div>
          </div>

          {summary === null ? (
            <div className="mt-6 rounded-2xl border border-dashed border-slate-300 bg-slate-50 px-4 py-5 text-sm leading-6 text-slate-600">
              Os cards monetários já estão preparados para consumir o endpoint de resumo financeiro. Assim que a API expor os indicadores da Conta Azul, esta tela passa a mostrar os valores reais automaticamente.
            </div>
          ) : null}
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
              <strong className="mt-2 block text-lg font-semibold text-slate-900">{receipts.length}</strong>
            </div>
            <div className="rounded-2xl border border-slate-200 p-4">
              <p className="text-xs font-semibold uppercase tracking-[0.18em] text-slate-400">Alertas em aberto</p>
              <strong className="mt-2 block text-lg font-semibold text-slate-900">{unresolvedAlerts.length}</strong>
            </div>
          </div>
        </aside>
      </section>
    </main>
  );
}