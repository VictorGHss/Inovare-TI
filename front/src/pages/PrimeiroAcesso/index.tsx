import { useEffect, useState, type FormEvent } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import { Lock, ShieldCheck, Loader2 } from 'lucide-react';
import { toast } from 'react-toastify';
import { useAuth } from '../../contexts/AuthContext';

interface FirstAccessState {
  tempToken?: string;
  userId?: string;
}

const LOGO_URL = 'https://inovare.med.br/wp-content/uploads/2023/01/Logo.png';

export default function PrimeiroAcesso() {
  const navigate = useNavigate();
  const location = useLocation();
  const { completeInitialPasswordReset } = useAuth();

  const state = (location.state as FirstAccessState | null) ?? null;
  const pending = (() => {
    const raw = sessionStorage.getItem('@InovareTI:firstAccess');
    if (!raw) return null;
    try {
      return JSON.parse(raw) as FirstAccessState;
    } catch {
      return null;
    }
  })();

  const tempToken = state?.tempToken ?? pending?.tempToken;
  const userId = state?.userId ?? pending?.userId;

  const [newPassword, setNewPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    if (!tempToken || !userId) {
      navigate('/login', { replace: true });
    }
  }, [navigate, tempToken, userId]);

  if (!tempToken || !userId) return null;

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();

    if (!tempToken || !userId) {
      toast.error('Sessão de primeiro acesso inválida. Faça login novamente.');
      navigate('/login', { replace: true });
      return;
    }

    if (newPassword.length < 8) {
      toast.error('A nova senha deve ter pelo menos 8 caracteres.');
      return;
    }

    if (newPassword !== confirmPassword) {
      toast.error('A confirmação de senha não confere.');
      return;
    }

    setLoading(true);
    try {
      await completeInitialPasswordReset({
        tempToken,
        userId,
        newPassword,
      });
      sessionStorage.removeItem('@InovareTI:firstAccess');
      toast.success('Senha atualizada com sucesso!');
      navigate('/dashboard', { replace: true });
    } catch {
      sessionStorage.removeItem('@InovareTI:firstAccess');
      toast.error('Não foi possível redefinir a senha. Faça login novamente.');
      navigate('/login', { replace: true });
    } finally {
      setLoading(false);
    }
  }

  return (
    <div className="min-h-screen bg-gradient-to-br from-white to-brand-secondary/20 flex items-center justify-center px-4">
      <div className="w-full max-w-md">
        <div className="flex justify-center mb-8">
          <img src={LOGO_URL} alt="Inovare TI" className="h-16 object-contain" />
        </div>

        <form
          onSubmit={handleSubmit}
          className="bg-white rounded-2xl border border-slate-200 shadow-sm p-8 flex flex-col gap-5"
        >
          <div className="flex items-start gap-3 p-4 rounded-2xl bg-brand-secondary/30 border border-brand-primary/30">
            <ShieldCheck className="w-5 h-5 text-brand-primary-dark mt-0.5" />
            <div>
              <p className="text-sm font-semibold text-slate-800">Primeiro acesso seguro</p>
              <p className="mt-1 text-sm text-slate-600">
                Bem-vindo! Por segurança, você precisa criar sua própria senha antes de entrar.
              </p>
            </div>
          </div>

          <div className="grid gap-2 sm:grid-cols-3">
            {["Informe sua nova senha", "Confirme a senha", "Finalize para entrar"].map((step, index) => (
              <div key={step} className="rounded-2xl border border-brand-primary/20 bg-brand-secondary/20 px-3 py-2.5">
                <p className="text-[10px] font-bold uppercase tracking-widest text-brand-primary-dark">Passo {index + 1}</p>
                <p className="mt-1 text-xs text-slate-700">{step}</p>
              </div>
            ))}
          </div>

          <div className="flex flex-col gap-2">
            <label className="text-xs font-bold uppercase tracking-widest text-slate-400">Nova Senha</label>
            <div className="relative">
              <Lock size={16} className="absolute left-3 top-1/2 -translate-y-1/2 text-slate-400" />
              <input
                type="password"
                value={newPassword}
                onChange={(event) => setNewPassword(event.target.value)}
                className="w-full rounded-xl border border-slate-200 bg-white pl-10 pr-3 py-2.5 text-sm text-slate-800 shadow-sm focus:outline-none focus:ring-2 focus:ring-brand-primary/30 focus:border-brand-primary"
                placeholder="Digite sua nova senha"
                disabled={loading}
              />
            </div>
          </div>

          <div className="flex flex-col gap-2">
            <label className="text-xs font-bold uppercase tracking-widest text-slate-400">Confirmar Senha</label>
            <div className="relative">
              <Lock size={16} className="absolute left-3 top-1/2 -translate-y-1/2 text-slate-400" />
              <input
                type="password"
                value={confirmPassword}
                onChange={(event) => setConfirmPassword(event.target.value)}
                className="w-full rounded-xl border border-slate-200 bg-white pl-10 pr-3 py-2.5 text-sm text-slate-800 shadow-sm focus:outline-none focus:ring-2 focus:ring-brand-primary/30 focus:border-brand-primary"
                placeholder="Confirme sua nova senha"
                disabled={loading}
              />
            </div>
          </div>

          <button
            type="submit"
            disabled={loading}
            className="mt-1 flex items-center justify-center gap-2 bg-brand-primary hover:bg-brand-primary-dark disabled:opacity-60 disabled:cursor-not-allowed text-white font-semibold py-2.5 rounded-xl transition-colors"
          >
            {loading ? <Loader2 size={18} className="animate-spin" /> : <ShieldCheck size={18} />}
            {loading ? 'Salvando...' : 'Confirmar Nova Senha'}
          </button>
        </form>
      </div>
    </div>
  );
}
