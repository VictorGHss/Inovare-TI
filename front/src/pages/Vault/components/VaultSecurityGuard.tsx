import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Lock, ShieldCheck, ShieldX, X } from 'lucide-react';
import { toast } from 'react-toastify';

import { useAuth } from '../../../contexts/AuthContext';
import { confirm2FAReset, request2FAReset, verify2FA } from '../../../services/api';

const inputClassName =
  'w-full rounded-xl border border-slate-300 bg-white px-3.5 py-2.5 text-sm text-slate-800 shadow-sm placeholder-slate-400 focus:outline-none focus:ring-2 focus:ring-primary focus:border-primary transition';

interface Props {
  /** ID do item que estava aguardando desbloqueio para ser revelado. */
  revealingItemId: string | null;
  /** Chamado quando o cofre é desbloqueado com sucesso. Passa o pendingItemId, se houver. */
  onUnlocked: (pendingItemId: string | null) => void;
}

/**
 * Overlay de segurança do cofre.
 * Renderiza a tela de desbloqueio 2FA e o modal de recuperação via Discord.
 * Gerencia seu próprio estado local — o pai só recebe o callback `onUnlocked`.
 */
export default function VaultSecurityGuard({ revealingItemId, onUnlocked }: Props) {
  const { updateAuthToken, invalidateTwoFactorVerification } = useAuth();
  const navigate = useNavigate();

  const [unlockCode, setUnlockCode] = useState('');
  const [unlocking, setUnlocking] = useState(false);

  const [showRecoveryModal, setShowRecoveryModal] = useState(false);
  const [recoveryStep, setRecoveryStep] = useState<'request' | 'confirm'>('request');
  const [recoveryCode, setRecoveryCode] = useState('');
  const [recoveryPassword, setRecoveryPassword] = useState('');
  const [recoveryLoading, setRecoveryLoading] = useState(false);

  async function handleUnlockVault() {
    if (!/^\d{6}$/.test(unlockCode)) {
      toast.error('Informe um código 2FA com 6 dígitos.');
      return;
    }
    setUnlocking(true);
    try {
      const response = await verify2FA(unlockCode);
      if (!response.token) throw new Error('Token inválido');
      updateAuthToken(response.token, response.user);
      setUnlockCode('');
      toast.success('Cofre desbloqueado com sucesso.');
      onUnlocked(revealingItemId);
    } catch {
      toast.error('Código 2FA inválido.');
    } finally {
      setUnlocking(false);
    }
  }

  function openRecoveryModal() {
    setRecoveryStep('request');
    setRecoveryCode('');
    setRecoveryPassword('');
    setShowRecoveryModal(true);
  }

  async function handleRequestRecoveryCode() {
    setRecoveryLoading(true);
    try {
      await request2FAReset();
      toast.success('Código de recuperação enviado ao seu Discord. Verifique suas mensagens diretas.');
      setRecoveryStep('confirm');
    } catch {
      toast.error(
        'Não foi possível enviar o código. Verifique se sua conta Discord está vinculada ou contacte um administrador.',
      );
    } finally {
      setRecoveryLoading(false);
    }
  }

  async function handleConfirmRecovery() {
    if (!recoveryCode.trim()) {
      toast.error('Informe o código de recuperação.');
      return;
    }
    if (!recoveryPassword.trim()) {
      toast.error('Informe sua senha atual.');
      return;
    }
    setRecoveryLoading(true);
    try {
      const response = await confirm2FAReset(recoveryCode.trim(), recoveryPassword);
      if (!response.token) throw new Error('Token inválido');
      updateAuthToken(response.token, response.user);
      invalidateTwoFactorVerification();
      toast.success('2FA redefinido com sucesso! Configure um novo autenticador na página de Perfil.');
      setShowRecoveryModal(false);
    } catch {
      toast.error('Código ou senha inválidos. Tente novamente.');
    } finally {
      setRecoveryLoading(false);
    }
  }

  return (
    <>
      {/* Overlay de desbloqueio 2FA */}
      <div className="fixed inset-0 z-40 bg-black/60 backdrop-blur-md flex items-center justify-center px-4">
        <div className="w-full max-w-md bg-white rounded-xl border border-slate-200 shadow-sm p-6">
          <div className="flex justify-end">
            <button
              onClick={() => navigate('/dashboard')}
              className="text-slate-400 hover:text-slate-600"
              aria-label="Voltar para o dashboard"
              title="Voltar"
            >
              <X size={20} />
            </button>
          </div>

          <div className="flex items-center gap-2 mb-3">
            <ShieldCheck className="text-brand-primary" size={20} />
            <h2 className="text-lg font-semibold text-slate-800">Desbloqueio de Segurança</h2>
          </div>

          <p className="text-sm text-slate-600 mb-4">
            Para acessar o cofre, confirme sua autenticação em dois fatores com o código de 6 dígitos.
          </p>

          <label className="block text-xs font-medium text-slate-600 mb-1.5">Código do Autenticador</label>
          <input
            value={unlockCode}
            onChange={(event) => setUnlockCode(event.target.value.replace(/\D/g, '').slice(0, 6))}
            className={inputClassName}
            placeholder="000000"
            inputMode="numeric"
          />

          <button
            onClick={() => void handleUnlockVault()}
            disabled={unlocking}
            className="mt-4 w-full inline-flex items-center justify-center gap-2 bg-brand-primary hover:bg-brand-primary-dark text-white text-sm font-semibold py-2.5 rounded-xl transition-colors disabled:opacity-60"
          >
            <Lock size={16} />
            {unlocking ? 'Validando...' : 'Desbloquear Cofre'}
          </button>

          <div className="mt-4 text-center">
            <button
              onClick={openRecoveryModal}
              className="text-xs text-slate-500 hover:text-brand-primary underline transition-colors"
            >
              Perdi meu acesso ao autenticador
            </button>
          </div>
          <div className="mt-2 text-center">
            <button
              onClick={() => navigate('/dashboard')}
              className="text-xs text-slate-500 hover:text-slate-700 transition-colors"
            >
              Voltar ao dashboard
            </button>
          </div>
        </div>
      </div>

      {/* Modal de recuperação do 2FA via código Discord */}
      {showRecoveryModal && (
        <div className="fixed inset-0 z-50 bg-black/60 backdrop-blur-sm flex items-center justify-center px-4">
          <div className="w-full max-w-md bg-white rounded-xl border border-slate-200 shadow-sm p-6">
            <div className="flex items-center justify-between mb-4">
              <div className="flex items-center gap-2">
                <ShieldX className="text-amber-500" size={20} />
                <h2 className="text-lg font-semibold text-slate-800">Recuperação de Acesso 2FA</h2>
              </div>
              <button
                onClick={() => setShowRecoveryModal(false)}
                className="text-slate-400 hover:text-slate-600"
              >
                <X size={20} />
              </button>
            </div>

            {recoveryStep === 'request' ? (
              <>
                <p className="text-sm text-slate-600 mb-5">
                  Enviaremos um código de 8 caracteres via{' '}
                  <strong>mensagem direta no Discord</strong>. Você também deverá informar sua senha
                  atual para confirmar a redefinição.
                </p>
                <p className="text-xs text-amber-600 bg-amber-50 border border-amber-200 rounded-lg px-3 py-2 mb-5">
                  ⚠️ Após a redefinição, o 2FA será desativado. Configure um novo autenticador no seu
                  Perfil.
                </p>
                <button
                  onClick={() => void handleRequestRecoveryCode()}
                  disabled={recoveryLoading}
                  className="w-full inline-flex items-center justify-center gap-2 bg-amber-500 hover:bg-amber-600 text-white text-sm font-semibold py-2.5 rounded-xl transition-colors disabled:opacity-60"
                >
                  {recoveryLoading ? 'Enviando...' : 'Enviar código ao Discord'}
                </button>
              </>
            ) : (
              <>
                <p className="text-sm text-slate-600 mb-4">
                  Insira o código de 8 caracteres recebido no Discord e sua senha atual.
                </p>
                <div className="space-y-3">
                  <div>
                    <label className="block text-xs font-medium text-slate-600 mb-1.5">
                      Código de recuperação
                    </label>
                    <input
                      value={recoveryCode}
                      onChange={(e) => setRecoveryCode(e.target.value)}
                      className={inputClassName}
                      placeholder="XXXXXXXX"
                    />
                  </div>
                  <div>
                    <label className="block text-xs font-medium text-slate-600 mb-1.5">
                      Senha atual
                    </label>
                    <input
                      type="password"
                      value={recoveryPassword}
                      onChange={(e) => setRecoveryPassword(e.target.value)}
                      className={inputClassName}
                      placeholder="••••••••"
                    />
                  </div>
                </div>
                <div className="mt-4 flex gap-2">
                  <button
                    onClick={() => setRecoveryStep('request')}
                    disabled={recoveryLoading}
                    className="flex-1 px-4 py-2.5 text-sm font-medium text-slate-600 hover:bg-slate-100 rounded-xl border border-slate-300 transition-colors disabled:opacity-60"
                  >
                    Reenviar código
                  </button>
                  <button
                    onClick={() => void handleConfirmRecovery()}
                    disabled={recoveryLoading}
                    className="flex-1 inline-flex items-center justify-center gap-2 bg-brand-primary hover:bg-brand-primary-dark text-white text-sm font-semibold py-2.5 rounded-xl transition-colors disabled:opacity-60"
                  >
                    {recoveryLoading ? 'Verificando...' : 'Confirmar redefinição'}
                  </button>
                </div>
              </>
            )}
          </div>
        </div>
      )}
    </>
  );
}
