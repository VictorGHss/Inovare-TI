import { useMemo, useState, type FormEvent } from 'react';
import { toast } from 'react-toastify';
import { ShieldCheck, UserCircle2 } from 'lucide-react';
import { useAuth } from '../../contexts/AuthContext';
import { changePassword } from '../../services/api';

export default function Profile() {
  const { user } = useAuth();

  const [currentPassword, setCurrentPassword] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [submitting, setSubmitting] = useState(false);

  const roleLabel = useMemo(() => {
    if (!user) return '-';

    if (user.role === 'ADMIN') return 'Administrador';
    if (user.role === 'TECHNICIAN') return 'Técnico';
    return 'Usuário';
  }, [user]);

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();

    if (!currentPassword || !newPassword || !confirmPassword) {
      toast.error('Preencha todos os campos de senha.');
      return;
    }

    if (newPassword.length < 8) {
      toast.error('A nova senha deve ter pelo menos 8 caracteres.');
      return;
    }

    if (newPassword !== confirmPassword) {
      toast.error('A confirmação da nova senha não confere.');
      return;
    }

    if (currentPassword === newPassword) {
      toast.error('A nova senha deve ser diferente da senha atual.');
      return;
    }

    setSubmitting(true);
    try {
      await changePassword(currentPassword, newPassword);
      toast.success('Senha alterada com sucesso!');
      setCurrentPassword('');
      setNewPassword('');
      setConfirmPassword('');
    } catch {
      toast.error('Não foi possível alterar a senha. Verifique a senha atual.');
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <main className="w-full max-w-full px-4 sm:px-6 lg:px-8 py-8">
      <div className="mb-6">
        <h1 className="text-2xl font-bold text-slate-800">Meu Perfil</h1>
        <p className="text-sm text-slate-500 mt-1">Gerencie suas informações e segurança da conta.</p>
      </div>

      <div className="grid grid-cols-1 xl:grid-cols-2 gap-6">
        <section className="bg-white rounded-xl border border-slate-200 shadow-sm p-6">
          <div className="flex items-center gap-2 mb-5">
            <UserCircle2 className="w-5 h-5 text-brand-primary" />
            <h2 className="text-base font-semibold text-slate-800">Informações da Conta</h2>
          </div>

          <div className="space-y-4">
            <div>
              <p className="text-xs uppercase tracking-wide text-slate-400">Nome</p>
              <p className="text-sm font-medium text-slate-800">{user?.name ?? '-'}</p>
            </div>
            <div>
              <p className="text-xs uppercase tracking-wide text-slate-400">E-mail</p>
              <p className="text-sm font-medium text-slate-800">{user?.email ?? '-'}</p>
            </div>
            <div>
              <p className="text-xs uppercase tracking-wide text-slate-400">Setor</p>
              <p className="text-sm font-medium text-slate-800">{user?.sectorName ?? '-'}</p>
            </div>
            <div>
              <p className="text-xs uppercase tracking-wide text-slate-400">Nível de Acesso</p>
              <p className="text-sm font-medium text-slate-800">{roleLabel}</p>
            </div>
          </div>
        </section>

        <section className="bg-white rounded-xl border border-slate-200 shadow-sm p-6">
          <div className="flex items-center gap-2 mb-5">
            <ShieldCheck className="w-5 h-5 text-brand-primary" />
            <h2 className="text-base font-semibold text-slate-800">Trocar Senha</h2>
          </div>

          <form onSubmit={handleSubmit} className="space-y-4">
            <div>
              <label className="block text-sm font-medium text-slate-700 mb-1.5">Senha Atual</label>
              <input
                type="password"
                value={currentPassword}
                onChange={(event) => setCurrentPassword(event.target.value)}
                className="w-full rounded-xl border border-slate-300 px-3.5 py-2.5 text-sm text-slate-800 focus:outline-none focus:ring-2 focus:ring-brand-primary"
                disabled={submitting}
                autoComplete="current-password"
              />
            </div>

            <div>
              <label className="block text-sm font-medium text-slate-700 mb-1.5">Nova Senha</label>
              <input
                type="password"
                value={newPassword}
                onChange={(event) => setNewPassword(event.target.value)}
                className="w-full rounded-xl border border-slate-300 px-3.5 py-2.5 text-sm text-slate-800 focus:outline-none focus:ring-2 focus:ring-brand-primary"
                disabled={submitting}
                autoComplete="new-password"
              />
            </div>

            <div>
              <label className="block text-sm font-medium text-slate-700 mb-1.5">Confirmar Nova Senha</label>
              <input
                type="password"
                value={confirmPassword}
                onChange={(event) => setConfirmPassword(event.target.value)}
                className="w-full rounded-xl border border-slate-300 px-3.5 py-2.5 text-sm text-slate-800 focus:outline-none focus:ring-2 focus:ring-brand-primary"
                disabled={submitting}
                autoComplete="new-password"
              />
            </div>

            <button
              type="submit"
              disabled={submitting}
              className="w-full bg-brand-primary hover:bg-orange-500 text-white text-sm font-semibold py-2.5 rounded-xl transition-colors disabled:opacity-60 disabled:cursor-not-allowed"
            >
              {submitting ? 'Alterando...' : 'Salvar Nova Senha'}
            </button>
          </form>
        </section>
      </div>
    </main>
  );
}
