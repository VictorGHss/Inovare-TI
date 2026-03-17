import { useEffect, useMemo, useRef, useState } from 'react';
import { Loader2, Send } from 'lucide-react';
import { toast } from 'react-toastify';

type Status = 'idle' | 'running' | 'done' | 'error';

interface ReprocessResult {
  totalFetched: number;
  totalProcessed: number;
  skippedAlreadyProcessed: number;
  failures: number;
  processedParcelIds: string[];
}

interface ProgressLine {
  id: number;
  text: string;
  type: 'info' | 'success' | 'error';
}

function formatDateInput(date: Date): string {
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');
  return `${year}-${month}-${day}`;
}

export default function ReceiptReprocessPanel() {
  const hoje = useMemo(() => new Date(), []);
  const dataFinalPadrao = useMemo(() => formatDateInput(hoje), [hoje]);
  const dataInicialPadrao = useMemo(() => {
    const data = new Date(hoje);
    data.setDate(data.getDate() - 45);
    return formatDateInput(data);
  }, [hoje]);

  const [dataInicial, setDataInicial] = useState(dataInicialPadrao);
  const [dataFinal, setDataFinal] = useState(dataFinalPadrao);
  const [status, setStatus] = useState<Status>('idle');
  const [resultado, setResultado] = useState<ReprocessResult | null>(null);
  const [linhasProgresso, setLinhasProgresso] = useState<ProgressLine[]>([]);

  const feedRef = useRef<HTMLDivElement | null>(null);
  const lineIdRef = useRef(0);

  const intervaloDias = useMemo(() => {
    if (!dataInicial || !dataFinal) {
      return null;
    }

    const dataInicialDate = new Date(`${dataInicial}T00:00:00`);
    const dataFinalDate = new Date(`${dataFinal}T00:00:00`);

    if (Number.isNaN(dataInicialDate.getTime()) || Number.isNaN(dataFinalDate.getTime())) {
      return null;
    }

    const diffMs = dataFinalDate.getTime() - dataInicialDate.getTime();
    return Math.floor(diffMs / (1000 * 60 * 60 * 24));
  }, [dataInicial, dataFinal]);

  const intervaloMuitoGrande = intervaloDias !== null && intervaloDias > 45;
  const dataInicialAntesDataFinal = intervaloDias !== null && intervaloDias > 0;
  const envioDesabilitado = status === 'running' || !dataInicialAntesDataFinal || intervaloMuitoGrande;

  useEffect(() => {
    if (feedRef.current) {
      feedRef.current.scrollTop = feedRef.current.scrollHeight;
    }
  }, [linhasProgresso]);

  function addLinha(texto: string, tipo: ProgressLine['type']) {
    lineIdRef.current += 1;
    setLinhasProgresso((prev) => [...prev, { id: lineIdRef.current, text: texto, type: tipo }]);
  }

  async function iniciarReprocessamento() {
    try {
      setStatus('running');
      setLinhasProgresso([]);
      setResultado(null);

      // Integração SSE com o backend Inovare para acompanhar o job de reprocessamento em tempo real.
      const token = localStorage.getItem('@InovareTI:token');
      const url = `/api/admin/financeiro/reprocess/stream?from=${dataInicial}&to=${dataFinal}`;

      const response = await fetch(url, {
        headers: { Authorization: `Bearer ${token}` },
      });

      if (!response.ok || !response.body) {
        throw new Error('Falha ao iniciar stream de reprocessamento.');
      }

      const reader = response.body.getReader();
      const decoder = new TextDecoder();
      let buffer = '';

      while (true) {
        const { done, value } = await reader.read();
        if (done) {
          break;
        }

        buffer += decoder.decode(value, { stream: true });
        const parts = buffer.split('\n\n');
        buffer = parts.pop() ?? '';

        for (const part of parts) {
          const eventMatch = part.match(/^event:\s*(.+)$/m);
          const dataMatch = part.match(/^data:\s*(.+)$/m);
          const event = eventMatch?.[1]?.trim();
          const data = dataMatch?.[1]?.trim();

          if (!event || !data) {
            continue;
          }

          if (event === 'result') {
            setResultado(JSON.parse(data) as ReprocessResult);
          } else if (event === 'done') {
            setStatus('done');
          } else if (event === 'error') {
            addLinha(data, 'error');
            setStatus('error');
            toast.error('Erro no reprocessamento: ' + data);
          } else if (event === 'start') {
            addLinha(data, 'info');
          } else if (event === 'progress') {
            addLinha(data, data.startsWith('ERRO') ? 'error' : 'success');
          }
        }
      }
    } catch {
      setStatus('error');
      toast.error('Erro de rede ao iniciar reprocessamento.');
    }
  }

  return (
    <div className="w-full">
      <section className="mb-8 flex flex-col gap-4 lg:flex-row lg:items-end lg:justify-between">
        <div>
          <span className="rounded-full border border-violet-200 bg-violet-50 px-3 py-1 text-xs font-semibold uppercase tracking-[0.2em] text-violet-700">
            Reprocessamento de Recibos
          </span>
          <h1 className="mt-4 text-3xl font-bold tracking-tight text-slate-900">
            Envio Manual de Recibos
          </h1>
          <p className="mt-2 max-w-3xl text-sm leading-6 text-slate-600">
            Selecione o período para reenviar recibos de parcelas pagas.
            Máximo de 45 dias por operação. Parcelas já enviadas são ignoradas automaticamente.
          </p>
        </div>
      </section>

      <section>
        <article className="rounded-3xl border border-slate-200 bg-white p-6 shadow-sm">
          <div className="grid gap-4 md:grid-cols-2">
            <label className="flex flex-col gap-2 text-sm font-medium text-slate-700">
              De
              <input
                type="date"
                value={dataInicial}
                onChange={(event) => setDataInicial(event.target.value)}
                className="rounded-2xl border border-slate-300 bg-white px-3 py-2 text-slate-900 outline-none transition-colors focus:border-brand-primary"
              />
            </label>

            <label className="flex flex-col gap-2 text-sm font-medium text-slate-700">
              Até
              <input
                type="date"
                value={dataFinal}
                onChange={(event) => setDataFinal(event.target.value)}
                className="rounded-2xl border border-slate-300 bg-white px-3 py-2 text-slate-900 outline-none transition-colors focus:border-brand-primary"
              />
            </label>
          </div>

          {intervaloMuitoGrande && (
            <p className="mt-3 text-sm text-amber-600">
              O intervalo máximo permitido é de 45 dias.
            </p>
          )}

          <div className="mt-5">
            <button
              type="button"
              onClick={() => {
                void iniciarReprocessamento();
              }}
              disabled={envioDesabilitado}
              className="inline-flex items-center justify-center gap-2 rounded-2xl bg-brand-primary px-5 py-3 text-sm font-semibold text-white shadow-sm transition-colors hover:bg-brand-primary-dark disabled:cursor-not-allowed disabled:opacity-70"
            >
              {status === 'running' ? (
                <>
                  <Loader2 size={18} className="animate-spin" />
                  Processando...
                </>
              ) : (
                <>
                  <Send size={18} />
                  {status === 'done' ? 'Reenviar para outro período' : 'Enviar Recibos'}
                </>
              )}
            </button>
          </div>
        </article>
      </section>

      {status !== 'idle' && (
        <section className="mt-6">
          <article className="rounded-3xl border border-slate-200 bg-white p-6 shadow-sm">
            <h2 className="mb-4 text-lg font-semibold text-slate-900">Progresso</h2>
            <div
              ref={feedRef}
              className="h-64 space-y-1 overflow-y-auto rounded-2xl bg-slate-50 p-4 font-mono text-sm"
            >
              {linhasProgresso.map((line) => (
                <p
                  key={line.id}
                  className={
                    line.type === 'error'
                      ? 'text-red-600'
                      : line.type === 'success'
                        ? 'text-emerald-600'
                        : 'text-slate-500'
                  }
                >
                  {line.type === 'error' ? '⚠️ ' : line.type === 'success' ? '✅ ' : '🔄 '}
                  {line.text}
                </p>
              ))}
            </div>
          </article>
        </section>
      )}

      {status === 'done' && resultado && (
        <section className="mt-6 grid gap-5 md:grid-cols-2 xl:grid-cols-4">
          <article className="rounded-3xl border border-slate-200 bg-white p-5 shadow-sm">
            <p className="text-xs font-semibold uppercase tracking-[0.2em] text-slate-400">📦 Encontradas</p>
            <strong className="mt-4 block text-2xl font-bold text-slate-900">{resultado.totalFetched}</strong>
          </article>

          <article className="rounded-3xl border border-slate-200 bg-white p-5 shadow-sm">
            <p className="text-xs font-semibold uppercase tracking-[0.2em] text-slate-400">✅ Enviadas</p>
            <strong className="mt-4 block text-2xl font-bold text-emerald-700">{resultado.totalProcessed}</strong>
          </article>

          <article className="rounded-3xl border border-slate-200 bg-white p-5 shadow-sm">
            <p className="text-xs font-semibold uppercase tracking-[0.2em] text-slate-400">⏭️ Ignoradas</p>
            <strong className="mt-4 block text-2xl font-bold text-slate-900">{resultado.skippedAlreadyProcessed}</strong>
          </article>

          <article className="rounded-3xl border border-slate-200 bg-white p-5 shadow-sm">
            <p className="text-xs font-semibold uppercase tracking-[0.2em] text-slate-400">❌ Falhas</p>
            <strong className={`mt-4 block text-2xl font-bold ${resultado.failures > 0 ? 'text-red-600' : 'text-emerald-700'}`}>
              {resultado.failures}
            </strong>
          </article>
        </section>
      )}
    </div>
  );
}
