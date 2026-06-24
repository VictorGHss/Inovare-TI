import { useState, useEffect } from 'react';
import { CircleHelp, Loader2, SearchCheck, UserPlus2, UserRound, X } from 'lucide-react';
import { toast } from 'react-toastify';
import { checkContaAzulCustomerByEmail, getSectors } from '../../services/userService';
import type { CreateUserDto, Sector } from '../../types/models';
import SearchableDropdown from '@/components/common/SearchableDropdown';

const customerNotFoundMessage =
  'Não encontramos nenhum médico com este e-mail no Conta Azul. Verifique se o e-mail está correto no ERP ou insira o ID manualmente.';

const inputClassName =
  'w-full rounded-xl border border-slate-200 bg-white px-3.5 py-2.5 text-sm text-slate-800 shadow-sm placeholder-slate-400 focus:outline-none focus:ring-2 focus:ring-brand-primary/30 focus:border-brand-primary transition';

const labelClassName = 'block text-xs font-bold uppercase tracking-widest text-slate-400';

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
  const [dropdownSectors, setDropdownSectors] = useState<Sector[]>(sectors);

  useEffect(() => {
    setDropdownSectors(sectors);
  }, [sectors]);

  async function handleSearchSectorsRemote(searchTerm: string) {
    try {
      const data = await getSectors({ search: searchTerm, activeOnly: true }) as unknown as Sector[];
      setDropdownSectors(data);
    } catch (error) {
      console.error('Falha ao buscar setores remotamente:', error);
    }
  }

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
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-4 backdrop-blur-sm">
      <div className="w-full max-w-xl overflow-hidden rounded-2xl bg-white shadow-2xl">

        <div className="h-1.5 w-full bg-gradient-to-r from-brand-primary to-brand-primary-dark" />

        <header className="flex items-center justify-between border-b border-slate-100 px-6 py-5">
          <div className="flex items-center gap-3">
            <span className="inline-flex h-9 w-9 items-center justify-center rounded-xl bg-brand-secondary/60">
              <UserRound size={18} className="text-brand-primary-dark" />
            </span>
            <div>
              <h2 className="text-base font-bold text-slate-800">Novo Usuário</h2>
              <p className="mt-0.5 text-xs text-slate-400">
                Cadastro de colaborador com perfil, setor e vínculo financeiro opcional.
              </p>
            </div>
          </div>

          <button
            onClick={onClose}
            className="rounded-lg p-1.5 text-slate-400 transition-colors hover:bg-slate-100 hover:text-slate-600"
            type="button"
          >
            <X size={17} />
          </button>
        </header>

        <form onSubmit={(event) => void onSubmit(event)} className="space-y-4 p-6">
          <div>
            <label className={labelClassName}>Nome Completo</label>
            <input
              type="text"
              value={formData.name}
              onChange={(event) => onChange({ ...formData, name: event.target.value })}
              className={`${inputClassName} mt-2`}
              disabled={submitting}
            />
          </div>

          <div>
            <label className={labelClassName}>E-mail</label>
            <input
              type="email"
              value={formData.email}
              onChange={(event) => onChange({ ...formData, email: event.target.value })}
              className={`${inputClassName} mt-2`}
              disabled={submitting || checkingContaAzul}
            />
          </div>

          <div>
            <label className={labelClassName}>Senha</label>
            <input
              type="password"
              value={formData.password}
              onChange={(event) => onChange({ ...formData, password: event.target.value })}
              className={`${inputClassName} mt-2`}
              disabled={submitting}
              placeholder="Mínimo 8 caracteres"
            />
          </div>

          <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
            <div>
              <label className={labelClassName}>Setor</label>
              <div className="mt-2">
                <SearchableDropdown
                  options={dropdownSectors.map((sector) => ({
                    id: sector.id,
                    name: sector.name,
                  }))}
                  value={formData.sectorId || ''}
                  onChange={(val) => onChange({ ...formData, sectorId: val })}
                  onSearchChange={handleSearchSectorsRemote}
                  placeholder="Selecione um setor..."
                  disabled={submitting}
                />
              </div>
            </div>

            <div>
              <label className={labelClassName}>Nível de Acesso</label>
              <select
                value={formData.role}
                onChange={(event) =>
                  onChange({ ...formData, role: event.target.value as CreateUserDto['role'] })
                }
                className={`${inputClassName} mt-2`}
                disabled={submitting}
              >
                <option value="USER">Usuário</option>
                <option value="TECHNICIAN">Técnico</option>
                <option value="ADMIN">Administrador</option>
              </select>
            </div>
          </div>

          {/* Localização */}
          <div>
            <label className={labelClassName}>Localização (unidade/andar)</label>
            <input
              type="text"
              value={formData.location ?? ''}
              onChange={(event) => onChange({ ...formData, location: event.target.value })}
              className={`${inputClassName} mt-2`}
              disabled={submitting}
              placeholder="Ex: Sede - 2º andar, Unidade Centro..."
              maxLength={150}
            />
          </div>

          <div>
            <label className={labelClassName}>ID Conta Azul (Opcional)</label>
            <div className="mt-2 flex items-center gap-2">
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
                className="inline-flex shrink-0 items-center gap-1.5 rounded-xl border border-slate-200 bg-white px-3 py-2.5 text-xs font-semibold text-slate-600 shadow-sm transition-colors hover:bg-slate-50 disabled:cursor-not-allowed disabled:opacity-60"
              >
                {checkingContaAzul ? <Loader2 size={13} className="animate-spin" /> : <SearchCheck size={13} />}
                {checkingContaAzul ? 'Buscando...' : 'Verificar'}
              </button>
            </div>
          </div>

          <div className="flex items-center justify-between rounded-xl border border-slate-200 bg-slate-50/70 px-3.5 py-2.5">
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
                  ? 'focus:ring-brand-primary/40'
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

          <footer className="mt-2 flex items-center justify-end gap-2.5 border-t border-slate-100 pt-4">
            <button
              type="button"
              onClick={onClose}
              className="rounded-xl px-4 py-2.5 text-sm font-semibold text-slate-500 transition-colors hover:bg-slate-100 hover:text-slate-700"
            >
              Cancelar
            </button>
            <button
              type="submit"
              disabled={submitting}
              className="inline-flex items-center gap-2 rounded-xl bg-brand-primary px-5 py-2.5 text-sm font-bold text-white shadow-sm transition-colors hover:bg-brand-primary-dark disabled:cursor-not-allowed disabled:opacity-70"
            >
              {submitting ? <Loader2 size={15} className="animate-spin" /> : <UserPlus2 size={15} />}
              {submitting ? 'Salvando...' : 'Cadastrar Usuário'}
            </button>
          </footer>
        </form>
      </div>
    </div>
  );
}

