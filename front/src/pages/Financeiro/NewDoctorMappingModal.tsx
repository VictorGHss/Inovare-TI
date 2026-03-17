import { useState } from 'react';
import { Loader2, Stethoscope, UserPlus2, X } from 'lucide-react';
import { toast } from 'react-toastify';
import { createDoctorMapping } from '../../services/api';

const inputClassName =
  'w-full rounded-xl border border-slate-200 bg-white px-3.5 py-2.5 text-sm text-slate-800 shadow-sm placeholder-slate-400 focus:outline-none focus:ring-2 focus:ring-brand-primary focus:border-brand-primary transition';

interface NewDoctorMappingModalProps {
  isOpen: boolean;
  onClose: () => void;
  onCreated: () => Promise<void>;
}

interface FormState {
  doctorName: string;
  contaAzulCustomerUuid: string;
  doctorEmail: string;
}

/**
 * Modal de criação de mapeamento entre cliente da Conta Azul e e-mail real do médico.
 */
export default function NewDoctorMappingModal({
  isOpen,
  onClose,
  onCreated,
}: NewDoctorMappingModalProps) {
  const [formState, setFormState] = useState<FormState>({
    doctorName: '',
    contaAzulCustomerUuid: '',
    doctorEmail: '',
  });
  const [submitting, setSubmitting] = useState(false);

  function resetForm() {
    setFormState({
      doctorName: '',
      contaAzulCustomerUuid: '',
      doctorEmail: '',
    });
  }

  function handleClose() {
    resetForm();
    onClose();
  }

  function isValidEmail(email: string): boolean {
    return /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email);
  }

  async function handleSubmit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();

    const doctorName = formState.doctorName.trim();
    const contaAzulCustomerUuid = formState.contaAzulCustomerUuid.trim();
    const doctorEmail = formState.doctorEmail.trim();

    if (!doctorName || !contaAzulCustomerUuid || !doctorEmail) {
      toast.warn('Preencha todos os campos obrigatórios do mapeamento.');
      return;
    }

    if (!isValidEmail(doctorEmail)) {
      toast.warn('Informe um e-mail válido para o médico.');
      return;
    }

    try {
      setSubmitting(true);
      await createDoctorMapping({
        doctorName,
        contaAzulCustomerUuid,
        doctorEmail,
      });

      await onCreated();
      toast.success('Novo mapeamento de médico criado com sucesso.');
      handleClose();
    } catch {
      toast.error('Não foi possível criar o mapeamento de médico.');
    } finally {
      setSubmitting(false);
    }
  }

  if (!isOpen) {
    return null;
  }

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4 backdrop-blur-sm">
      <div className="w-full max-w-xl rounded-3xl bg-white shadow-xl">
        <header className="flex items-center justify-between border-b border-slate-200 px-6 py-4">
          <div>
            <h2 className="text-lg font-bold text-slate-800">Novo Mapeamento</h2>
            <p className="mt-1 text-xs text-slate-500">Integração de médico para envio automático de recibos</p>
          </div>

          <button
            type="button"
            onClick={handleClose}
            className="rounded-xl p-2 text-slate-400 transition-colors hover:bg-slate-100 hover:text-slate-600"
            title="Fechar modal"
          >
            <X size={18} />
          </button>
        </header>

        {/* Formulário de integração com o endpoint POST /api/financeiro/doctor-mappings */}
        <form onSubmit={(event) => void handleSubmit(event)} className="space-y-4 p-6">
          <label className="block text-sm font-medium text-slate-500">
            Nome do Médico
            <div className="relative mt-1.5">
              <Stethoscope size={16} className="pointer-events-none absolute left-3 top-3 text-slate-400" />
              <input
                type="text"
                value={formState.doctorName}
                onChange={(event) => setFormState((prev) => ({ ...prev, doctorName: event.target.value }))}
                className={`${inputClassName} pl-9`}
                placeholder="Ex.: Dr. João Silva"
                maxLength={160}
                required
              />
            </div>
          </label>

          <label className="block text-sm font-medium text-slate-500">
            UUID do Cliente Conta Azul
            <input
              type="text"
              value={formState.contaAzulCustomerUuid}
              onChange={(event) => setFormState((prev) => ({ ...prev, contaAzulCustomerUuid: event.target.value }))}
              className={`${inputClassName} mt-1.5`}
              placeholder="Ex.: 8fd57bc1-5c1f-4ef5-9a2e-..."
              maxLength={64}
              required
            />
          </label>

          <label className="block text-sm font-medium text-slate-500">
            E-mail de Destino
            <input
              type="email"
              value={formState.doctorEmail}
              onChange={(event) => setFormState((prev) => ({ ...prev, doctorEmail: event.target.value }))}
              className={`${inputClassName} mt-1.5`}
              placeholder="medico@clinica.com"
              maxLength={255}
              required
            />
          </label>

          <footer className="flex items-center justify-end gap-3 pt-1">
            <button
              type="button"
              onClick={handleClose}
              className="rounded-xl px-4 py-2.5 text-sm text-slate-500 transition-colors hover:text-slate-700"
            >
              Cancelar
            </button>
            <button
              type="submit"
              disabled={submitting}
              className="inline-flex items-center gap-2 rounded-xl bg-brand-primary px-5 py-2.5 text-sm font-semibold text-white transition-colors hover:bg-brand-primary-dark disabled:cursor-not-allowed disabled:opacity-70"
            >
              {submitting ? <Loader2 size={16} className="animate-spin" /> : <UserPlus2 size={16} />}
              {submitting ? 'Salvando...' : 'Salvar Mapeamento'}
            </button>
          </footer>
        </form>
      </div>
    </div>
  );
}
