import { useState } from 'react';
import { Lock, ShieldCheck } from 'lucide-react';
import { toast } from 'react-toastify';

import { useAuth } from '../contexts/AuthContext';
import { verify2FA } from '../services/api';

const inputClassName =
  'w-full rounded-xl border border-slate-300 bg-white px-3.5 py-2.5 text-sm text-slate-800 shadow-sm placeholder-slate-400 focus:outline-none focus:ring-2 focus:ring-primary focus:border-primary transition';

export default function FinancialTwoFactorChallenge() {
  const { updateAuthToken } = useAuth();

  const [code, setCode] = useState('');
  const [loading, setLoading] = useState(false);

  async function handleVerify() {
    if (!/^\d{6}$/.test(code)) {
      toast.error('Informe um código 2FA com 6 dígitos.');
      return;
    }

    setLoading(true);
    try {
      const response = await verify2FA(code);
      if (!response.token) {
        throw new Error('Token inválido');
      }

      updateAuthToken(response.token, response.user);
      setCode('');
      toast.success('Desafio 2FA validado. Acesso financeiro liberado.');
    } catch {
      toast.error('Código 2FA inválido.');
    } finally {
      setLoading(false);
    }
  }

  return (
    <div className="fixed inset-0 z-50 bg-black/60 backdrop-blur-md flex items-center justify-center px-4">
      <div className="w-full max-w-md bg-white rounded-xl border border-slate-200 shadow-sm p-6">
        <div className="flex items-center gap-2 mb-3">
          <ShieldCheck className="text-brand-primary" size={20} />
          <h2 className="text-lg font-semibold text-slate-800">Validação 2FA do Financeiro</h2>
        </div>

        <p className="text-sm text-slate-600 mb-4">
          Para acessar páginas do módulo financeiro, valide seu TOTP de 6 dígitos.
        </p>

        <label className="block text-xs font-medium text-slate-600 mb-1.5">Código do Autenticador</label>
        <input
          value={code}
          onChange={(event) => setCode(event.target.value.replace(/\D/g, '').slice(0, 6))}
          className={inputClassName}
          placeholder="000000"
          inputMode="numeric"
        />

        <button
          onClick={() => void handleVerify()}
          disabled={loading}
          className="mt-4 w-full inline-flex items-center justify-center gap-2 bg-brand-primary hover:bg-brand-primary-dark text-white text-sm font-semibold py-2.5 rounded-xl transition-colors disabled:opacity-60"
        >
          <Lock size={16} />
          {loading ? 'Validando...' : 'Validar 2FA'}
        </button>
      </div>
    </div>
  );
}