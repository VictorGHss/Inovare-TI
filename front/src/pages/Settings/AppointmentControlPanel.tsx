import { useEffect, useState } from 'react';
import { AlertTriangle, PlayCircle } from 'lucide-react';
import { toast } from 'react-toastify';

import { getApiErrorMessage } from '../../lib/apiError';
import {
  getAppointmentMotorConfig,
  triggerAppointmentMotorManual,
  type AppointmentMotorConfig,
} from '../../services/appointmentService';

export default function AppointmentControlPanel() {
  const [loading, setLoading] = useState(true);
  const [running, setRunning] = useState(false);
  const [confirmOpen, setConfirmOpen] = useState(false);
  const [config, setConfig] = useState<AppointmentMotorConfig | null>(null);

  async function loadConfig() {
    setLoading(true);
    try {
      const data = await getAppointmentMotorConfig();
      setConfig(data);
    } catch {
      toast.error('Erro ao carregar status do motor de agendamentos.');
      setConfig(null);
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    loadConfig();
  }, []);

  async function handleConfirmExecution() {
    setRunning(true);
    try {
      const result = await triggerAppointmentMotorManual();
      toast.success(`Motor executado com sucesso. Mensagens enviadas: ${result.messages_sent}.`);
      setConfirmOpen(false);
      await loadConfig();
    } catch (error) {
      toast.error(getApiErrorMessage(error, 'Falha ao executar o motor de confirmação manualmente.'));
    } finally {
      setRunning(false);
    }
  }

  const mode = config?.mode ?? 'PROD';
  const modeBadgeClass =
    mode === 'TEST'
      ? 'bg-amber-100 text-amber-700 border-amber-200'
      : 'bg-emerald-100 text-emerald-700 border-emerald-200';

  return (
    <>
      <div className="bg-white rounded-2xl border border-slate-200 overflow-hidden">
        <div className="px-6 py-4 border-b border-slate-100 flex items-center justify-between gap-3">
          <div>
            <h2 className="text-sm font-semibold text-slate-900">Controle do Motor de Agendamentos</h2>
            <p className="text-xs text-slate-500 mt-0.5">Disparo manual e verificação do modo de execução.</p>
          </div>
          <button
            type="button"
            onClick={loadConfig}
            disabled={loading}
            className="text-xs font-medium text-brand-primary hover:text-brand-primary-dark disabled:opacity-40"
          >
            {loading ? 'Atualizando...' : 'Atualizar status'}
          </button>
        </div>

        <div className="px-6 py-5 space-y-6">
          {/* Seção de Status do Motor */}
          <div>
            <h3 className="text-xs font-semibold uppercase tracking-wide text-slate-500 mb-3">Status do Motor</h3>
            <div className="flex flex-col gap-3">
              <div className="flex items-center gap-2">
                <span className={`inline-flex items-center rounded-full border px-2.5 py-1 text-xs font-semibold ${modeBadgeClass}`}>
                  {mode === 'TEST' ? 'TEST MODE' : 'PRODUCTION'}
                </span>
              </div>
              {config?.testDoctorIds && config.testDoctorIds.length > 0 && (
                <div className="text-xs text-slate-600 bg-slate-50 border border-slate-100 rounded-xl p-3">
                  <span className="font-semibold text-amber-700 block mb-1">Médicos em Homologação (Teste):</span>
                  <span className="font-mono">{config.testDoctorIds.join(', ')}</span>
                </div>
              )}
              {config?.activeDoctorIds && config.activeDoctorIds.length > 0 && (
                <div className="text-xs text-slate-600 bg-slate-50 border border-slate-100 rounded-xl p-3">
                  <span className="font-semibold text-emerald-700 block mb-1">Médicos Ativos (Produção):</span>
                  <span className="font-mono">{config.activeDoctorIds.join(', ')}</span>
                </div>
              )}
            </div>
          </div>

          {/* Seção de Execução Manual */}
          <div>
            <h3 className="text-xs font-semibold uppercase tracking-wide text-slate-500 mb-3">Execução Manual</h3>
            <button
              type="button"
              onClick={() => setConfirmOpen(true)}
              disabled={loading || running}
              className="inline-flex items-center gap-2 rounded-xl bg-brand-primary px-4 py-2.5 text-sm font-semibold text-white transition-colors hover:bg-brand-primary-dark disabled:cursor-not-allowed disabled:opacity-40"
            >
              <PlayCircle size={16} />
              {running ? 'Executando...' : 'Executar Motor de Confirmação Agora'}
            </button>
          </div>

        </div>
      </div>

      {confirmOpen && (
        <div className="fixed inset-0 z-50 bg-black/50 backdrop-blur-sm flex items-center justify-center px-4">
          <div className="w-full max-w-lg rounded-2xl border border-slate-200 bg-white shadow-xl">
            <div className="px-6 py-4 border-b border-slate-100 flex items-center gap-2">
              <AlertTriangle size={18} className="text-amber-500" />
              <h3 className="text-sm font-semibold text-slate-900">Confirmar execução manual</h3>
            </div>

            <div className="px-6 py-5">
              <p className="text-sm text-slate-700">
                Deseja iniciar o disparo manual? O sistema respeitará o Modo de Teste atual.
              </p>
            </div>

            <div className="px-6 py-4 border-t border-slate-100 flex justify-end gap-2">
              <button
                type="button"
                onClick={() => setConfirmOpen(false)}
                disabled={running}
                className="rounded-xl border border-slate-200 px-4 py-2 text-sm font-medium text-slate-600 hover:bg-slate-50 disabled:opacity-40"
              >
                Cancelar
              </button>
              <button
                type="button"
                onClick={handleConfirmExecution}
                disabled={running}
                className="rounded-xl bg-brand-primary px-4 py-2 text-sm font-semibold text-white hover:bg-brand-primary-dark disabled:opacity-40"
              >
                {running ? 'Executando...' : 'Confirmar execução'}
              </button>
            </div>
          </div>
        </div>
      )}
    </>
  );
}
