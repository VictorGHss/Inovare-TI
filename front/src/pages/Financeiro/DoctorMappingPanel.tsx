import { useMemo, useState } from 'react';
import type { FormEvent } from 'react';
import { Loader2, Plus, Trash2 } from 'lucide-react';
import { toast } from 'react-toastify';
import {
  createDoctorMapping,
  deleteDoctorMapping,
  type DoctorMapping,
} from '../../services/api';

interface DoctorMappingPanelProps {
  mapeamentos: DoctorMapping[];
  carregando: boolean;
  onAtualizar: () => Promise<void>;
}

function ordenarPorEmail(mapeamentos: DoctorMapping[]): DoctorMapping[] {
  return [...mapeamentos].sort((a, b) => a.doctorEmail.localeCompare(b.doctorEmail, 'pt-BR'));
}

export default function DoctorMappingPanel({
  mapeamentos,
  carregando,
  onAtualizar,
}: DoctorMappingPanelProps) {
  const [uuidClienteContaAzul, setUuidClienteContaAzul] = useState('');
  const [emailMedico, setEmailMedico] = useState('');
  const [salvando, setSalvando] = useState(false);
  const [idRemovendo, setIdRemovendo] = useState<string | null>(null);

  const mapeamentosOrdenados = useMemo(() => ordenarPorEmail(mapeamentos), [mapeamentos]);

  async function handleSalvar(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();

    const uuidLimpo = uuidClienteContaAzul.trim();
    const emailLimpo = emailMedico.trim();

    if (!uuidLimpo || !emailLimpo) {
      toast.warn('Preencha UUID e e-mail do médico.');
      return;
    }

    try {
      setSalvando(true);
      await createDoctorMapping({
        contaAzulCustomerUuid: uuidLimpo,
        doctorEmail: emailLimpo,
      });

      setUuidClienteContaAzul('');
      setEmailMedico('');
      await onAtualizar();
      toast.success('Mapeamento de médico salvo com sucesso.');
    } catch {
      toast.error('Não foi possível salvar o mapeamento.');
    } finally {
      setSalvando(false);
    }
  }

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

  return (
    <section className="mt-8 grid gap-6 xl:grid-cols-[1.1fr_1.9fr]">
      <article className="rounded-3xl border border-slate-200 bg-white p-6 shadow-sm">
        <h2 className="text-lg font-semibold text-slate-900">Mapear médico por UUID</h2>
        <p className="mt-2 text-sm leading-6 text-slate-500">
          Esta configuração conecta o cliente da Conta Azul ao e-mail real do médico para envio automático do recibo.
        </p>

        {/* Formulário de integração com o backend /financeiro/doctor-mappings */}
        <form onSubmit={(event) => void handleSalvar(event)} className="mt-5 space-y-4">
          <label className="block text-sm font-medium text-slate-700">
            UUID do cliente (Conta Azul)
            <input
              type="text"
              value={uuidClienteContaAzul}
              onChange={(event) => setUuidClienteContaAzul(event.target.value)}
              placeholder="ex.: 8fd57bc1-..."
              className="mt-2 w-full rounded-2xl border border-slate-300 bg-white px-3 py-2 text-sm text-slate-900 outline-none transition-colors focus:border-brand-primary"
            />
          </label>

          <label className="block text-sm font-medium text-slate-700">
            E-mail do médico
            <input
              type="email"
              value={emailMedico}
              onChange={(event) => setEmailMedico(event.target.value)}
              placeholder="medico@clinica.com"
              className="mt-2 w-full rounded-2xl border border-slate-300 bg-white px-3 py-2 text-sm text-slate-900 outline-none transition-colors focus:border-brand-primary"
            />
          </label>

          <button
            type="submit"
            disabled={salvando}
            className="inline-flex items-center justify-center gap-2 rounded-2xl bg-brand-primary px-4 py-2.5 text-sm font-semibold text-white shadow-sm transition-colors hover:bg-brand-primary-dark disabled:cursor-not-allowed disabled:opacity-70"
          >
            {salvando ? <Loader2 size={16} className="animate-spin" /> : <Plus size={16} />}
            {salvando ? 'Salvando...' : 'Adicionar mapeamento'}
          </button>
        </form>
      </article>

      <article className="rounded-3xl border border-slate-200 bg-white p-6 shadow-sm">
        <h2 className="text-lg font-semibold text-slate-900">Mapeamentos cadastrados</h2>
        <p className="mt-2 text-sm text-slate-500">
          A automação financeira consulta esta lista para descobrir o destinatário correto do recibo.
        </p>

        <div className="mt-5 overflow-hidden rounded-2xl border border-slate-200">
          <table className="min-w-full divide-y divide-slate-200 text-sm">
            <thead className="bg-slate-50">
              <tr>
                <th className="px-4 py-3 text-left font-semibold text-slate-600">UUID Conta Azul</th>
                <th className="px-4 py-3 text-left font-semibold text-slate-600">E-mail médico</th>
                <th className="px-4 py-3 text-right font-semibold text-slate-600">Ações</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100 bg-white">
              {carregando ? (
                <tr>
                  <td colSpan={3} className="px-4 py-6 text-center text-slate-500">
                    Carregando mapeamentos...
                  </td>
                </tr>
              ) : mapeamentosOrdenados.length === 0 ? (
                <tr>
                  <td colSpan={3} className="px-4 py-6 text-center text-slate-500">
                    Nenhum mapeamento encontrado.
                  </td>
                </tr>
              ) : (
                mapeamentosOrdenados.map((mapeamento) => (
                  <tr key={mapeamento.id}>
                    <td className="max-w-[250px] truncate px-4 py-3 text-slate-700">{mapeamento.contaAzulCustomerUuid}</td>
                    <td className="px-4 py-3 text-slate-700">{mapeamento.doctorEmail}</td>
                    <td className="px-4 py-3 text-right">
                      <button
                        type="button"
                        onClick={() => {
                          void handleRemover(mapeamento.id);
                        }}
                        disabled={idRemovendo === mapeamento.id}
                        className="inline-flex items-center gap-2 rounded-xl border border-red-200 px-3 py-1.5 text-xs font-semibold text-red-600 transition-colors hover:bg-red-50 disabled:cursor-not-allowed disabled:opacity-70"
                      >
                        {idRemovendo === mapeamento.id ? (
                          <Loader2 size={14} className="animate-spin" />
                        ) : (
                          <Trash2 size={14} />
                        )}
                        Remover
                      </button>
                    </td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>
      </article>
    </section>
  );
}
