import { useMemo, useState } from 'react';
import { BadgeDollarSign, Loader2, Mail, Pencil, PlusCircle, RefreshCcw, Trash2, UserRound } from 'lucide-react';
import { toast } from 'react-toastify';
import { deleteDoctorMapping, syncDoctorsBaseFromContaAzul } from '../../services/financeService';
import type { DoctorMapping } from '../../types/models';
import NewDoctorMappingModal from './NewDoctorMappingModal';

interface DoctorMappingPanelProps {
  mapeamentos: DoctorMapping[];
  carregando: boolean;
  onAtualizar: () => Promise<void>;
  integrationActive: boolean;
  onConnectContaAzul: () => void;
}

function ordenarPorNomeMedico(mapeamentos: DoctorMapping[]): DoctorMapping[] {
  return [...mapeamentos].sort((a, b) =>
    (a.doctorName ?? 'Sem nome').localeCompare(b.doctorName ?? 'Sem nome', 'pt-BR'),
  );
}

export default function DoctorMappingPanel({
  mapeamentos,
  carregando,
  onAtualizar,
  integrationActive,
  onConnectContaAzul,
}: DoctorMappingPanelProps) {
  const [showNovoMapeamentoModal, setShowNovoMapeamentoModal] = useState(false);
  const [mapeamentoEmEdicao, setMapeamentoEmEdicao] = useState<DoctorMapping | null>(null);
  const [syncingBase, setSyncingBase] = useState(false);
  const [deletingMappingId, setDeletingMappingId] = useState<string | null>(null);

  const mapeamentosOrdenados = useMemo(() => ordenarPorNomeMedico(mapeamentos ?? []), [mapeamentos]);

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

  function handleOpenCreateModal() {
    setMapeamentoEmEdicao(null);
    setShowNovoMapeamentoModal(true);
  }

  function handleOpenEditModal(mapeamento: DoctorMapping) {
    setMapeamentoEmEdicao(mapeamento);
    setShowNovoMapeamentoModal(true);
  }

  function handleCloseModal() {
    setShowNovoMapeamentoModal(false);
    setMapeamentoEmEdicao(null);
  }

  async function handleDeleteMapping(mapeamento: DoctorMapping) {
    const confirmou = window.confirm(`Deseja remover o mapeamento de ${mapeamento.doctorName ?? 'médico sem nome'}?`);
    if (!confirmou) {
      return;
    }

    try {
      setDeletingMappingId(mapeamento.id);
      await deleteDoctorMapping(mapeamento.id);
      await onAtualizar();
      toast.success('Mapeamento removido com sucesso.');
    } catch {
      toast.error('Não foi possível remover o mapeamento de médico.');
    } finally {
      setDeletingMappingId(null);
    }
  }

  return (
    <section className="rounded-2xl border border-slate-200 bg-white shadow-sm overflow-hidden">

      {/* Panel header */}
      <header className="border-b border-slate-100 bg-gradient-to-r from-brand-secondary/30 to-transparent px-6 py-5">
        <div className="flex flex-col gap-4 lg:flex-row lg:items-center lg:justify-between">
          <div className="flex items-center gap-3">
            <span className="inline-flex h-9 w-9 items-center justify-center rounded-xl bg-brand-primary/10">
              <UserRound size={18} className="text-brand-primary-dark" />
            </span>
            <div>
              <h2 className="text-base font-bold text-slate-800">Mapeamento de Médicos/Usuários</h2>
              <p className="mt-0.5 text-xs text-slate-500">
                Relaciona cliente da Conta Azul com o e-mail de destino para envio de recibos.
              </p>
            </div>
          </div>

          <div className="flex flex-wrap items-center gap-2">
            <button
              type="button"
              onClick={() => { void handleSyncBase(); }}
              disabled={syncingBase || !integrationActive}
              className="inline-flex items-center gap-2 rounded-xl border border-slate-200 bg-white px-4 py-2.5 text-sm font-semibold text-slate-700 shadow-sm transition-colors hover:bg-slate-50 disabled:cursor-not-allowed disabled:opacity-70"
            >
              {syncingBase
                ? <Loader2 size={15} className="animate-spin" />
                : <RefreshCcw size={15} />}
              {syncingBase ? 'Sincronizando...' : 'Sincronizar Base de Médicos'}
            </button>

            {!integrationActive && (
              <button
                type="button"
                onClick={onConnectContaAzul}
                className="inline-flex items-center gap-2 rounded-xl bg-brand-primary px-4 py-2.5 text-sm font-semibold text-white shadow-sm transition-colors hover:bg-brand-primary-dark"
              >
                <BadgeDollarSign size={15} />
                Conectar ContaAzul
              </button>
            )}

            <button
              type="button"
              onClick={handleOpenCreateModal}
              className="inline-flex items-center gap-2 rounded-xl bg-brand-primary px-4 py-2.5 text-sm font-semibold text-white shadow-sm transition-colors hover:bg-brand-primary-dark"
            >
              <PlusCircle size={15} />
              Novo Mapeamento
            </button>
          </div>
        </div>
      </header>

      {/* Table */}
      <div className="overflow-x-auto">
        <table className="min-w-full text-sm">
          <thead>
            <tr className="border-b border-slate-200 bg-slate-50">
              {['ID Conta Azul', 'Nome', 'Email', 'Status de Vínculo', 'Ações'].map((col) => (
                <th
                  key={col}
                  className="px-4 py-3 text-left text-xs font-semibold uppercase tracking-wider text-slate-500"
                >
                  {col}
                </th>
              ))}
            </tr>
          </thead>

          <tbody className="divide-y divide-slate-100">
            {carregando ? (
              <tr>
                <td colSpan={5} className="px-4 py-8 text-center text-sm text-slate-400">
                  <div className="flex items-center justify-center gap-2">
                    <span className="h-4 w-4 animate-spin rounded-full border-2 border-brand-primary border-t-transparent" />
                    Carregando mapeamentos...
                  </div>
                </td>
              </tr>
            ) : mapeamentosOrdenados.length === 0 ? (
              <tr>
                <td colSpan={5} className="px-4 py-8 text-center text-sm text-slate-400 italic">
                  Nenhum mapeamento cadastrado até o momento.
                </td>
              </tr>
            ) : (
              mapeamentosOrdenados.map((mapeamento) => (
                <tr
                  key={mapeamento.id}
                  className="transition-colors hover:bg-slate-50"
                >
                  <td className="max-w-[260px] px-4 py-3">
                    <span
                      className="block truncate rounded-lg bg-slate-100 px-2.5 py-1 font-mono text-xs text-slate-500"
                      title={mapeamento.contaAzulCustomerUuid}
                    >
                      {mapeamento.contaAzulCustomerUuid}
                    </span>
                  </td>

                  <td className="px-4 py-3">
                    <div className="flex items-center gap-2.5">
                      <span className="inline-flex h-7 w-7 shrink-0 items-center justify-center rounded-lg bg-brand-secondary/60">
                        <UserRound size={13} className="text-brand-primary-dark" />
                      </span>
                      <span className="font-semibold text-slate-800">
                        {mapeamento.doctorName ?? 'Sem nome informado'}
                      </span>
                    </div>
                  </td>

                  <td className="px-4 py-3">
                    <div className="flex items-center gap-2 text-slate-600">
                      <Mail size={13} className="shrink-0 text-slate-400" />
                      <span className="text-sm">{mapeamento.doctorEmail ?? 'Sem e-mail de fallback'}</span>
                    </div>
                  </td>

                  <td className="px-4 py-3">
                    <span
                      className={`inline-flex rounded-full px-2.5 py-0.5 text-xs font-medium ${mapeamento.userId ? 'bg-emerald-100 text-emerald-700' : 'bg-amber-100 text-amber-700'}`}
                    >
                      {mapeamento.userId ? 'Vinculado ao usuário' : 'Fallback por e-mail'}
                    </span>
                  </td>

                  <td className="px-4 py-3">
                    <div className="flex items-center gap-2">
                      <button
                        type="button"
                        onClick={() => handleOpenEditModal(mapeamento)}
                        className="inline-flex items-center gap-1.5 rounded-xl border border-slate-200 bg-white px-2.5 py-1.5 text-xs font-semibold text-slate-600 transition-colors hover:bg-slate-50"
                        title="Editar mapeamento"
                      >
                        <Pencil size={13} />
                        Editar
                      </button>
                      <button
                        type="button"
                        onClick={() => { void handleDeleteMapping(mapeamento); }}
                        disabled={deletingMappingId === mapeamento.id}
                        className="inline-flex items-center gap-1.5 rounded-xl border border-red-200 bg-white px-2.5 py-1.5 text-xs font-semibold text-red-600 transition-colors hover:bg-red-50 disabled:cursor-not-allowed disabled:opacity-60"
                        title="Remover mapeamento"
                      >
                        {deletingMappingId === mapeamento.id ? <Loader2 size={13} className="animate-spin" /> : <Trash2 size={13} />}
                        Remover
                      </button>
                    </div>
                  </td>
                </tr>
              ))
            )}
          </tbody>
        </table>
      </div>

      <NewDoctorMappingModal
        isOpen={showNovoMapeamentoModal}
        onClose={handleCloseModal}
        onSaved={onAtualizar}
        mappingToEdit={mapeamentoEmEdicao}
      />
    </section>
  );
}

