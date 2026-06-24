import { useEffect, useState } from 'react';
import { RefreshCw, Server, Wifi, AlertOctagon, Activity } from 'lucide-react';
import { getMacroTelemetry, type TelemetryData } from '@/services/telemetryService';

/**
 * Auxiliar para formatar volumetria de bytes para formatação legível (KB, MB, GB).
 */
function formatBytes(bytes: number): string {
  if (bytes === 0) return '0 Bytes';
  const k = 1024;
  const sizes = ['Bytes', 'KB', 'MB', 'GB', 'TB'];
  const i = Math.floor(Math.log(bytes) / Math.log(k));
  return `${parseFloat((bytes / Math.pow(k, i)).toFixed(2))} ${sizes[i]}`;
}

/**
 * Painel de Telemetria de Redes e Integração de APIs da infraestrutura (Design Premium).
 * Carrega contadores do Actuator/Prometheus (/actuator/prometheus) e renderiza de forma macro.
 */
export default function NetworkTelemetryPanel() {
  const [data, setData] = useState<TelemetryData | null>(null);
  const [loading, setLoading] = useState(true);

  const fetchTelemetry = async () => {
    setLoading(true);
    const telemetry = await getMacroTelemetry();
    setData(telemetry);
    setLoading(false);
  };

  useEffect(() => {
    let active = true;
    const initTelemetry = async () => {
      const telemetry = await getMacroTelemetry();
      if (active) {
        setData(telemetry);
        setLoading(false);
      }
    };
    void initTelemetry();
    return () => {
      active = false;
    };
  }, []);

  return (
    <div className="bg-white rounded-xl border border-slate-200 shadow-sm p-6 mt-6">
      <div className="flex items-center justify-between mb-6">
        <div className="flex items-center gap-2">
          <Activity size={18} className="text-brand-primary animate-pulse" />
          <h3 className="text-sm font-bold text-slate-800">Telemetria de Rede & Integração Blip</h3>
        </div>
        <button
          type="button"
          onClick={() => {
            void fetchTelemetry();
          }}
          disabled={loading}
          className="inline-flex items-center gap-1.5 rounded-lg border border-slate-200 bg-white px-3 py-1.5 text-xs font-semibold text-slate-600 hover:bg-slate-50 transition shadow-sm disabled:opacity-50"
        >
          <RefreshCw size={12} className={loading ? 'animate-spin' : ''} />
          Atualizar
        </button>
      </div>

      {loading && !data ? (
        <div className="animate-pulse space-y-3 py-4">
          <div className="h-4 bg-slate-200 rounded w-3/4 mx-auto" />
          <div className="h-4 bg-slate-200 rounded w-1/2 mx-auto" />
        </div>
      ) : (
        <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
          {/* Card Roteador MikroTik */}
          <div className="bg-slate-50 rounded-xl p-5 border border-slate-100 flex flex-col justify-between">
            <div>
              <div className="flex items-center justify-between mb-3">
                <span className="text-xs font-bold uppercase tracking-wider text-slate-500">MikroTik Principal</span>
                <Server size={18} className="text-slate-400" />
              </div>
              <div className="space-y-1">
                <p className="text-[10px] text-slate-400">Total Recebido (RX)</p>
                <p className="text-base font-bold text-slate-800">{formatBytes(data?.mikrotikRx ?? 0)}</p>
              </div>
              <div className="space-y-1 mt-3">
                <p className="text-[10px] text-slate-400">Total Transmitido (TX)</p>
                <p className="text-base font-bold text-slate-800">{formatBytes(data?.mikrotikTx ?? 0)}</p>
              </div>
            </div>
            <div className="border-t border-slate-200 mt-4 pt-3 flex items-center justify-between text-[10px] text-slate-500 font-semibold">
              <span>Status da Conexão</span>
              <span className="inline-flex items-center gap-1 text-emerald-600">
                <span className="h-1.5 w-1.5 rounded-full bg-emerald-500"></span> Ativo
              </span>
            </div>
          </div>

          {/* Card Ubiquiti Access Points */}
          <div className="bg-slate-50 rounded-xl p-5 border border-slate-100 flex flex-col justify-between">
            <div>
              <div className="flex items-center justify-between mb-3">
                <span className="text-xs font-bold uppercase tracking-wider text-slate-500">AP's Ubiquiti UniFi</span>
                <Wifi size={18} className="text-slate-400" />
              </div>
              <div className="space-y-1">
                <p className="text-[10px] text-slate-400">Total Recebido (RX)</p>
                <p className="text-base font-bold text-slate-800">{formatBytes(data?.ubiquitiRx ?? 0)}</p>
              </div>
              <div className="space-y-1 mt-3">
                <p className="text-[10px] text-slate-400">Total Transmitido (TX)</p>
                <p className="text-base font-bold text-slate-800">{formatBytes(data?.ubiquitiTx ?? 0)}</p>
              </div>
            </div>
            <div className="border-t border-slate-200 mt-4 pt-3 flex items-center justify-between text-[10px] text-slate-500 font-semibold">
              <span>Status da Controladora</span>
              <span className="inline-flex items-center gap-1 text-emerald-600">
                <span className="h-1.5 w-1.5 rounded-full bg-emerald-500"></span> Ativa
              </span>
            </div>
          </div>

          {/* Card Erros do Blip */}
          <div className="bg-slate-50 rounded-xl p-5 border border-slate-100 flex flex-col justify-between">
            <div>
              <div className="flex items-center justify-between mb-3">
                <span className="text-xs font-bold uppercase tracking-wider text-slate-500">Integração Blip API</span>
                <AlertOctagon size={18} className="text-slate-400" />
              </div>
              <div className="space-y-1">
                <p className="text-[10px] text-slate-400">Falhas Totais de Entrega</p>
                <p className={`text-2xl font-black ${data?.blipFailuresTotal && data.blipFailuresTotal > 0 ? 'text-rose-600' : 'text-slate-800'}`}>
                  {data?.blipFailuresTotal ?? 0}
                </p>
              </div>
              <p className="text-[10px] text-slate-400 mt-3">
                Monitoramento de falhas de envio de mensagens LIME ao cliente final.
              </p>
            </div>
            <div className="border-t border-slate-200 mt-4 pt-3 flex items-center justify-between text-[10px] text-slate-500 font-semibold">
              <span>Sincronização</span>
              <span className="text-slate-500">Ponta a Ponta</span>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
