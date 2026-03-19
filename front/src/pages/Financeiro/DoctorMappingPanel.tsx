import { useMemo, useState } from 'react';
import { Loader2, Mail, PlusCircle, Trash2, UserRound } from 'lucide-react';
import { toast } from 'react-toastify';
import { deleteDoctorMapping, syncDoctorsBaseFromContaAzul, type DoctorMapping } from '../../services/api';
import NewDoctorMappingModal from './NewDoctorMappingModal';

interface DoctorMappingPanelProps {
  mapeamentos: DoctorMapping[];
  carregando: boolean;
  onAtualizar: () => Promise<void>;
}

function ordenarPorNomeMedico(mapeamentos: DoctorMapping[]): DoctorMapping[] {
  return [...mapeamentos].sort((a, b) =>
    (a.doctorName ?? 'Sem nome').localeCompare(b.doctorName ?? 'Sem nome', 'pt-BR'),
  );
}

/**
 * Tabela de mapeamentos médicos que integra o dashboard financeiro com o backend.
 */
export default function DoctorMappingPanel({
  mapeamentos,
  carregando,
  onAtualizar,
}: DoctorMappingPanelProps) {
  const [idRemovendo, setIdRemovendo] = useState<string | null>(null);
  const [showNovoMapeamentoModal, setShowNovoMapeamentoModal] = useState(false);
  const [syncingBase, setSyncingBase] = useState(false);

  const mapeamentosOrdenados = useMemo(() => ordenarPorNomeMedico(mapeamentos), [mapeamentos]);

  async function handleRemover(id: string) {
    try {
      setIdRemovendo(id);
      await deleteDoctorMapping(id);
      await onAtualizar();
      toast.success('Mapeamento removido com sucesso.');
    } catch {
      toast.error('Não foi possível remover o mapeamento.');
    } finally {
      setIdRemovendo(null);
    }
  }

  async function handleSyncBase() {
    try {
      setSyncingBase(true);
      const result = await syncDoctorsBaseFromContaAzul();
      await onAtualizar();
      toast.success(`Sincronização concluída. Novos: ${result.novos}. Atualizados: ${result.atualizados}.`);
    } catch {
      toast.error('Não foi possível sincronizar a base de médicos do Conta Azul.');
    } finally {
      setSyncingBase(false);
    }
  }

  return (
    <section className="rounded-3xl border border-slate-200 bg-white p-6 shadow-sm">
      <header className="mb-5 flex flex-col gap-3 lg:flex-row lg:items-center lg:justify-between">
        <div>
          <h2 className="text-xl font-bold text-slate-800">Mapeamento de Médicos</h2>
          <p className="mt-1 text-sm text-slate-500">
            Relaciona cliente da Conta Azul com o e-mail de destino para envio de recibos liquidados.
          </p>
        </div>

        <div className="flex flex-wrap items-center gap-2">
          <button
            type="button"
            onClick={() => {
              void handleSyncBase();
            }}
            disabled={syncingBase}
            className="inline-flex items-center justify-center gap-2 rounded-xl border border-slate-300 bg-white px-4 py-2.5 text-sm font-semibold text-slate-700 transition-colors hover:bg-slate-50 disabled:cursor-not-allowed disabled:opacity-70"
          >
            {syncingBase ? <Loader2 size={16} className="animate-spin" /> : <PlusCircle size={16} />}
            {syncingBase ? 'Sincronizando...' : 'Sincronizar Base do Conta Azul'}
          </button>

          <button
            type="button"
            onClick={() => setShowNovoMapeamentoModal(true)}
            className="inline-flex items-center justify-center gap-2 rounded-xl bg-brand-primary px-4 py-2.5 text-sm font-semibold text-white transition-colors hover:bg-brand-primary-dark"
          >
            <PlusCircle size={16} />
            Novo Mapeamento
          </button>
        </div>
      </header>

      <div className="overflow-x-auto rounded-xl border border-slate-200">
        <table className="min-w-full text-sm">
          <thead className="bg-slate-50 text-xs uppercase tracking-wider text-slate-500">
            <tr>
              <th className="px-4 py-3 text-left">Nome do Médico</th>
              <th className="px-4 py-3 text-left">UUID Conta Azul</th>
              <th className="px-4 py-3 text-left">E-mail de Destino</th>
              <th className="px-4 py-3 text-center">Ações</th>
            </tr>
          </thead>

          <tbody className="divide-y divide-slate-100">
            {carregando ? (
              <tr>
                <td colSpan={4} className="px-4 py-8 text-center text-slate-500">
                  Carregando mapeamentos...
                </td>
              </tr>
            ) : mapeamentosOrdenados.length === 0 ? (
              <tr>
                <td colSpan={4} className="px-4 py-8 text-center text-slate-500">
                  Nenhum mapeamento cadastrado até o momento.
                </td>
              </tr>
            ) : (
              mapeamentosOrdenados.map((mapeamento) => (
                <tr key={mapeamento.id} className="transition-colors hover:bg-slate-50">
                  <td className="px-4 py-3 text-slate-800">
                    <div className="flex items-center gap-2">
                      <UserRound size={15} className="text-slate-400" />
                      <span className="font-medium">{mapeamento.doctorName ?? 'Sem nome informado'}</span>
                    </div>
                  </td>

                  <td className="max-w-[280px] px-4 py-3 text-slate-800">
                    <span className="block truncate" title={mapeamento.contaAzulCustomerUuid}>
                      {mapeamento.contaAzulCustomerUuid}
                    </span>
                  </td>

                  <td className="px-4 py-3 text-slate-800">
                    <div className="flex items-center gap-2">
                      <Mail size={15} className="text-slate-400" />
                      <span>{mapeamento.doctorEmail ?? 'Sem e-mail de fallback'}</span>
                    </div>
                  </td>

                  <td className="px-4 py-3 text-center">
                    <button
                      type="button"
                      onClick={() => {
                        void handleRemover(mapeamento.id);
                      }}
                      disabled={idRemovendo === mapeamento.id}
                      className="inline-flex items-center gap-1.5 rounded-xl border border-red-200 px-3 py-1.5 text-xs font-semibold text-red-600 transition-colors hover:bg-red-50 disabled:cursor-not-allowed disabled:opacity-70"
                    >
                      {idRemovendo === mapeamento.id ? (
                        <Loader2 size={14} className="animate-spin" />
                      ) : (
                        <Trash2 size={14} />
                      )}
                      Excluir
                    </button>
                  </td>
                </tr>
              ))
            )}
          </tbody>
        </table>
      </div>

      <NewDoctorMappingModal
        isOpen={showNovoMapeamentoModal}
        onClose={() => setShowNovoMapeamentoModal(false)}
        onCreated={onAtualizar}
      />
    </section>
  );
}
