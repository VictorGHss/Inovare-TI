import { useEffect, useState } from 'react';
import { Loader2, Stethoscope, UserPlus2, X, RefreshCw, SearchCheck, UserRound } from 'lucide-react';
import { toast } from 'react-toastify';
import { checkContaAzulCustomerByEmail, getUsers } from '../../services/userService';
import { createDoctorMapping, getContaAzulCustomerEmailById, updateDoctorMapping } from '../../services/financeService';
import type { DoctorMapping, User } from '../../types/models';

const inputClassName =
  'w-full rounded-xl border border-slate-200 bg-white px-3.5 py-2.5 text-sm text-slate-800 shadow-sm placeholder-slate-400 focus:outline-none focus:ring-2 focus:ring-brand-primary/30 focus:border-brand-primary transition';

const labelClassName = 'block text-xs font-bold uppercase tracking-widest text-slate-400';

const customerNotFoundMessage =
  'Não encontramos nenhum médico com este e-mail no Conta Azul. Verifique se o e-mail está correto no ERP ou insira o ID manualmente.';

interface NewDoctorMappingModalProps {
  isOpen: boolean;
  onClose: () => void;
  onSaved: () => Promise<void>;
  mappingToEdit?: DoctorMapping | null;
}

interface FormState {
  userId: string;
  doctorName: string;
  contaAzulCustomerUuid: string;
  doctorEmail: string;
}

export default function NewDoctorMappingModal({
  isOpen,
  onClose,
  onSaved,
  mappingToEdit,
}: NewDoctorMappingModalProps) {
  const [formState, setFormState] = useState<FormState>({
    userId: '',
    doctorName: '',
    contaAzulCustomerUuid: '',
    doctorEmail: '',
  });
  const [users, setUsers] = useState<User[]>([]);
  const [loadingUsers, setLoadingUsers] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [pullingContaAzulEmail, setPullingContaAzulEmail] = useState(false);
  const [checkingContaAzulCustomer, setCheckingContaAzulCustomer] = useState(false);

  useEffect(() => {
    if (!isOpen) return;
    void loadUsers();
  }, [isOpen]);

  useEffect(() => {
    if (!isOpen) {
      return;
    }

    if (mappingToEdit) {
      setFormState({
        userId: mappingToEdit.userId ?? '',
        doctorName: mappingToEdit.doctorName ?? '',
        contaAzulCustomerUuid: mappingToEdit.contaAzulCustomerUuid ?? '',
        doctorEmail: mappingToEdit.doctorEmail ?? '',
      });
      return;
    }

    resetForm();
  }, [isOpen, mappingToEdit]);

  const editMode = Boolean(mappingToEdit);

  async function loadUsers() {
    try {
      setLoadingUsers(true);
      const data = await getUsers();
      setUsers(data);
    } catch {
      toast.error('Não foi possível carregar os usuários para vínculo.');
      setUsers([]);
    } finally {
      setLoadingUsers(false);
    }
  }

  function resetForm() {
    setFormState({ userId: '', doctorName: '', contaAzulCustomerUuid: '', doctorEmail: '' });
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

    const userId = formState.userId.trim();
    const doctorName = formState.doctorName.trim();
    const contaAzulCustomerUuid = formState.contaAzulCustomerUuid.trim();
    const doctorEmail = formState.doctorEmail.trim();

    if (!contaAzulCustomerUuid) {
      toast.warn('Informe o UUID do cliente da Conta Azul.');
      return;
    }

    if (!userId && !doctorEmail) {
      toast.warn('Selecione um usuário ou informe um e-mail de destino no fallback.');
      return;
    }

    if (doctorEmail && !isValidEmail(doctorEmail)) {
      toast.warn('Informe um e-mail válido para o médico.');
      return;
    }

    try {
      setSubmitting(true);
      const payload = {
        userId: userId || undefined,
        doctorName: doctorName || undefined,
        contaAzulCustomerUuid,
        doctorEmail: doctorEmail || undefined,
      };

      if (mappingToEdit) {
        await updateDoctorMapping(mappingToEdit.id, payload);
        toast.success('Mapeamento de médico atualizado com sucesso.');
      } else {
        await createDoctorMapping(payload);
        toast.success('Novo mapeamento de médico criado com sucesso.');
      }

      await onSaved();
      handleClose();
    } catch {
      toast.error(editMode ? 'Não foi possível atualizar o mapeamento de médico.' : 'Não foi possível criar o mapeamento de médico.');
    } finally {
      setSubmitting(false);
    }
  }

  async function handlePullContaAzulEmail() {
    const customerId = formState.contaAzulCustomerUuid.trim();
    if (!customerId) {
      toast.warn('Informe o UUID do cliente Conta Azul para puxar o e-mail.');
      return;
    }

    try {
      setPullingContaAzulEmail(true);
      const response = await getContaAzulCustomerEmailById(customerId);

      if (response.email) {
        setFormState((prev) => ({ ...prev, doctorEmail: response.email ?? prev.doctorEmail }));
        toast.success('E-mail do cliente puxado com sucesso da Conta Azul.');
        return;
      }

      toast.info('A Conta Azul não retornou e-mail para este cliente.');
    } catch {
      toast.error('Não foi possível puxar o e-mail na Conta Azul.');
    } finally {
      setPullingContaAzulEmail(false);
    }
  }

  async function handleCheckContaAzulCustomerByEmail() {
    const email = formState.doctorEmail.trim();
    if (!email) {
      toast.warn('Informe o e-mail do médico para verificar no Conta Azul.');
      return;
    }

    try {
      setCheckingContaAzulCustomer(true);
      const response = await checkContaAzulCustomerByEmail(email);

      if (response.customerId) {
        setFormState((prev) => ({ ...prev, contaAzulCustomerUuid: response.customerId ?? prev.contaAzulCustomerUuid }));
        toast.success('Médico localizado na Conta Azul e UUID preenchido no formulário.');
        return;
      }

      toast.warn(customerNotFoundMessage);
    } catch {
      toast.error('Não foi possível verificar o médico na Conta Azul.');
    } finally {
      setCheckingContaAzulCustomer(false);
    }
  }

  if (!isOpen) return null;

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-4 backdrop-blur-sm">
      <div className="w-full max-w-xl rounded-2xl bg-white shadow-2xl overflow-hidden">

        {/* Modal header with brand accent strip */}
        <div className="h-1.5 w-full bg-gradient-to-r from-brand-primary to-brand-primary-dark" />

        <header className="flex items-center justify-between border-b border-slate-100 px-6 py-5">
          <div className="flex items-center gap-3">
            <span className="inline-flex h-9 w-9 items-center justify-center rounded-xl bg-brand-secondary/60">
              <UserRound size={18} className="text-brand-primary-dark" />
            </span>
            <div>
              <h2 className="text-base font-bold text-slate-800">{editMode ? 'Editar Mapeamento' : 'Novo Mapeamento'}</h2>
              <p className="mt-0.5 text-xs text-slate-400">
                {editMode ? 'Atualize vínculo e e-mail do médico no financeiro.' : 'Integração de médico para envio automático de recibos'}
              </p>
            </div>
          </div>

          <button
            type="button"
            onClick={handleClose}
            className="rounded-lg p-1.5 text-slate-400 transition-colors hover:bg-slate-100 hover:text-slate-600"
            title="Fechar modal"
          >
            <X size={17} />
          </button>
        </header>

        <form onSubmit={(event) => void handleSubmit(event)} className="space-y-4 p-6">

          {/* Linked user */}
          <div>
            <label className={labelClassName}>Usuário Vinculado (opcional)</label>
            <select
              value={formState.userId}
              onChange={(event) => {
                const nextUserId = event.target.value;
                const user = users.find((candidate) => candidate.id === nextUserId);
                setFormState((prev) => ({
                  ...prev,
                  userId: nextUserId,
                  doctorName: user?.name ?? prev.doctorName,
                  doctorEmail: user?.email ?? prev.doctorEmail,
                }));
              }}
              className={`${inputClassName} mt-2`}
              disabled={submitting || loadingUsers}
            >
              <option value="">Sem vínculo (usar fallback manual)</option>
              {users.map((user) => (
                <option key={user.id} value={user.id}>
                  {user.name} ({user.email})
                </option>
              ))}
            </select>
          </div>

          {/* Doctor name */}
          <div>
            <label className={labelClassName}>Nome do Médico</label>
            <div className="relative mt-2">
              <Stethoscope size={15} className="pointer-events-none absolute left-3.5 top-3 text-slate-400" />
              <input
                type="text"
                value={formState.doctorName}
                onChange={(event) => setFormState((prev) => ({ ...prev, doctorName: event.target.value }))}
                className={`${inputClassName} pl-9`}
                placeholder="Ex.: Dr. João Silva"
                maxLength={160}
                disabled={submitting}
              />
            </div>
          </div>

          {/* Conta Azul UUID */}
          <div>
            <label className={labelClassName}>UUID do Cliente Conta Azul</label>
            <input
              type="text"
              value={formState.contaAzulCustomerUuid}
              onChange={(event) => setFormState((prev) => ({ ...prev, contaAzulCustomerUuid: event.target.value }))}
              className={`${inputClassName} mt-2 font-mono text-xs`}
              placeholder="Ex.: 8fd57bc1-5c1f-4ef5-9a2e-..."
              maxLength={64}
              required
            />
          </div>

          {/* Doctor email */}
          <div>
            <label className={labelClassName}>E-mail de Destino</label>
            <div className="mt-2 space-y-2">
              <div className="flex items-center gap-2">
                <input
                  type="email"
                  value={formState.doctorEmail}
                  onChange={(event) => setFormState((prev) => ({ ...prev, doctorEmail: event.target.value }))}
                  className={inputClassName}
                  placeholder="medico@clinica.com"
                  maxLength={255}
                  disabled={submitting}
                />
                <button
                  type="button"
                  onClick={() => { void handleCheckContaAzulCustomerByEmail(); }}
                  disabled={submitting || checkingContaAzulCustomer}
                  className="inline-flex shrink-0 items-center gap-1.5 rounded-xl border border-slate-200 bg-white px-3 py-2.5 text-xs font-semibold text-slate-600 shadow-sm transition-colors hover:bg-slate-50 disabled:cursor-not-allowed disabled:opacity-60"
                >
                  {checkingContaAzulCustomer
                    ? <Loader2 size={13} className="animate-spin" />
                    : <SearchCheck size={13} />}
                  {checkingContaAzulCustomer ? 'Buscando...' : 'Verificar'}
                </button>
              </div>
              <button
                type="button"
                onClick={() => { void handlePullContaAzulEmail(); }}
                disabled={submitting || pullingContaAzulEmail}
                className="inline-flex items-center gap-1.5 rounded-xl border border-brand-primary/30 bg-brand-secondary/30 px-3 py-2 text-xs font-semibold text-brand-primary-dark transition-colors hover:bg-brand-secondary/50 disabled:cursor-not-allowed disabled:opacity-60"
              >
                {pullingContaAzulEmail
                  ? <Loader2 size={13} className="animate-spin" />
                  : <RefreshCw size={13} />}
                Puxar e-mail do Conta Azul
              </button>
            </div>
          </div>

          {/* Footer actions */}
          <footer className="flex items-center justify-end gap-2.5 border-t border-slate-100 pt-4 mt-2">
            <button
              type="button"
              onClick={handleClose}
              className="rounded-xl px-4 py-2.5 text-sm font-semibold text-slate-500 transition-colors hover:bg-slate-100 hover:text-slate-700"
            >
              Cancelar
            </button>
            <button
              type="submit"
              disabled={submitting}
              className="inline-flex items-center gap-2 rounded-xl bg-brand-primary px-5 py-2.5 text-sm font-bold text-white shadow-sm transition-colors hover:bg-brand-primary-dark disabled:cursor-not-allowed disabled:opacity-70"
            >
              {submitting
                ? <Loader2 size={15} className="animate-spin" />
                : <UserPlus2 size={15} />}
              {submitting ? 'Salvando...' : editMode ? 'Salvar Alterações' : 'Salvar Mapeamento'}
            </button>
          </footer>
        </form>
      </div>
    </div>
  );
}

