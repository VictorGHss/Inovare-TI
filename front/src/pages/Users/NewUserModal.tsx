import { CircleHelp, Loader2, SearchCheck, X } from 'lucide-react';
import { toast } from 'react-toastify';
import { checkContaAzulCustomerByEmail, type CreateUserDto, type Sector } from '../../services/api';

const customerNotFoundMessage =
  'Não encontramos nenhum médico com este e-mail no Conta Azul. Verifique se o e-mail está correto no ERP ou insira o ID manualmente.';

const inputClassName =
  'w-full rounded-xl border border-slate-200 bg-white px-3 py-2 text-sm text-slate-800 focus:outline-none focus:ring-2 focus:ring-brand-primary focus:border-brand-primary';

interface NewUserModalProps {
  isOpen: boolean;
  submitting: boolean;
  formData: CreateUserDto;
  sectors: Sector[];
  checkingContaAzul: boolean;
  onClose: () => void;
  onSubmit: (event: React.FormEvent<HTMLFormElement>) => Promise<void>;
  onChange: (nextFormData: CreateUserDto) => void;
  onCheckContaAzul: (checking: boolean) => void;
}

/**
 * Modal de criação de usuários com suporte opcional ao vínculo de cliente da Conta Azul.
 */
export default function NewUserModal({
  isOpen,
  submitting,
  formData,
  sectors,
  checkingContaAzul,
  onClose,
  onSubmit,
  onChange,
  onCheckContaAzul,
}: NewUserModalProps) {
  if (!isOpen) {
    return null;
  }

  async function handleCheckContaAzul() {
    const email = formData.email.trim();
    if (!email) {
      toast.warn('Informe o e-mail do usuário para verificar o vínculo na Conta Azul.');
      return;
    }

    try {
      onCheckContaAzul(true);
      const response = await checkContaAzulCustomerByEmail(email);

      if (response.customerId) {
        onChange({ ...formData, contaAzulId: response.customerId });
        toast.success('Vínculo encontrado na Conta Azul e preenchido no formulário.');
        return;
      }

      toast.warn(customerNotFoundMessage);
    } catch {
      toast.error('Não foi possível verificar o vínculo na Conta Azul.');
    } finally {
      onCheckContaAzul(false);
    }
  }

  return (
    <div className="fixed inset-0 bg-black/50 backdrop-blur-sm flex items-center justify-center z-50">
      <div className="bg-white rounded-xl shadow-xl w-full max-w-md mx-4 p-6">
        <div className="flex items-center justify-between mb-4">
          <h2 className="text-lg font-bold text-slate-800">Novo Usuário</h2>
          <button
            onClick={onClose}
            className="p-1 rounded-lg hover:bg-slate-200 transition-colors"
            type="button"
          >
            <X size={18} className="text-slate-500" />
          </button>
        </div>

        <form onSubmit={(event) => void onSubmit(event)} className="space-y-4">
          <div>
            <label className="block text-xs font-medium text-slate-600 mb-1.5">Nome Completo</label>
            <input
              type="text"
              value={formData.name}
              onChange={(event) => onChange({ ...formData, name: event.target.value })}
              className={inputClassName}
              disabled={submitting}
            />
          </div>

          <div>
            <label className="block text-xs font-medium text-slate-600 mb-1.5">E-mail</label>
            <input
              type="email"
              value={formData.email}
              onChange={(event) => onChange({ ...formData, email: event.target.value })}
              className={inputClassName}
              disabled={submitting || checkingContaAzul}
            />
          </div>

          <div>
            <label className="block text-xs font-medium text-slate-600 mb-1.5">Senha</label>
            <input
              type="password"
              value={formData.password}
              onChange={(event) => onChange({ ...formData, password: event.target.value })}
              className={inputClassName}
              disabled={submitting}
              placeholder="Mínimo 8 caracteres"
            />
          </div>

          <div>
            <label className="block text-xs font-medium text-slate-600 mb-1.5">Setor</label>
            <select
              value={formData.sectorId}
              onChange={(event) => onChange({ ...formData, sectorId: event.target.value })}
              className={inputClassName}
              disabled={submitting}
            >
              <option value="">Selecione...</option>
              {sectors.map((sector) => (
                <option key={sector.id} value={sector.id}>
                  {sector.name}
                </option>
              ))}
            </select>
          </div>

          <div>
            <label className="block text-xs font-medium text-slate-600 mb-1.5">Nível de Acesso</label>
            <select
              value={formData.role}
              onChange={(event) =>
                onChange({ ...formData, role: event.target.value as CreateUserDto['role'] })
              }
              className={inputClassName}
              disabled={submitting}
            >
              <option value="USER">Usuário</option>
              <option value="TECHNICIAN">Técnico</option>
              <option value="ADMIN">Administrador</option>
            </select>
          </div>

          <div>
            <label className="block text-xs font-medium text-slate-600 mb-1.5">ID Conta Azul (opcional)</label>
            <div className="flex items-center gap-2">
              <input
                type="text"
                value={formData.contaAzulId ?? ''}
                onChange={(event) => onChange({ ...formData, contaAzulId: event.target.value })}
                className={inputClassName}
                disabled={submitting}
                placeholder="UUID/ID do cliente na Conta Azul"
                maxLength={120}
              />
              <button
                type="button"
                onClick={() => {
                  void handleCheckContaAzul();
                }}
                disabled={submitting || checkingContaAzul}
                className="inline-flex shrink-0 items-center gap-1.5 rounded-xl border border-slate-200 px-3 py-2 text-xs font-semibold text-slate-700 transition-colors hover:bg-slate-50 disabled:cursor-not-allowed disabled:opacity-60"
              >
                {checkingContaAzul ? <Loader2 size={14} className="animate-spin" /> : <SearchCheck size={14} />}
                {checkingContaAzul ? 'Buscando...' : 'Verificar'}
              </button>
            </div>
          </div>

          <div className="flex items-center justify-between rounded-lg border border-slate-200 px-3 py-2.5">
            <div className="flex items-center gap-2">
              <span className="text-sm text-slate-700">Receber notificações de chamados (Discord)</span>
              <span
                title="Essa opção controla o envio de alertas de chamados e SLA no Discord."
                aria-label="Informação sobre notificações no Discord"
                className="inline-flex text-slate-400"
              >
                <CircleHelp size={15} />
              </span>
            </div>

            <button
              type="button"
              role="switch"
              aria-checked={formData.receives_it_notifications}
              onClick={() =>
                onChange({
                  ...formData,
                  receives_it_notifications: !formData.receives_it_notifications,
                })
              }
              className={`relative inline-flex h-6 w-11 items-center rounded-full transition-colors focus:outline-none focus:ring-2 focus:ring-offset-2 ${
                formData.receives_it_notifications
                  ? 'focus:ring-[#ffa751]'
                  : 'bg-slate-300 focus:ring-slate-400'
              }`}
              style={{
                backgroundColor: formData.receives_it_notifications ? '#ffa751' : undefined,
              }}
              disabled={submitting}
            >
              <span
                className={`inline-block h-5 w-5 transform rounded-full bg-white transition-transform ${
                  formData.receives_it_notifications ? 'translate-x-5' : 'translate-x-1'
                }`}
              />
            </button>
          </div>

          <button
            type="submit"
            disabled={submitting}
            className="w-full bg-primary hover:bg-primary-hover text-white text-sm font-semibold py-2.5 rounded-lg transition-colors disabled:opacity-50"
          >
            {submitting ? 'Salvando...' : 'Cadastrar Usuário'}
          </button>
        </form>
      </div>
    </div>
  );
}
