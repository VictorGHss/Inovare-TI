// Tela de login do sistema Inovare TI
import { useState, type FormEvent } from 'react';
import { useNavigate } from 'react-router-dom';
import { Mail, Lock, LogIn, Loader2 } from 'lucide-react';
import { useAuth } from '../../contexts/AuthContext';
import LoginField from './LoginField';

export default function Login() {
  const { signIn } = useAuth();
  const navigate = useNavigate();

  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    setError('');
    setLoading(true);

    try {
      await signIn({ email, password });
      navigate('/dashboard');
    } catch {
      setError('E-mail ou senha inválidos. Tente novamente.');
    } finally {
      setLoading(false);
    }
  }

  return (
    <div className="min-h-screen bg-gray-950 flex items-center justify-center px-4">
      <div className="w-full max-w-sm">
        {/* Cabeçalho */}
        <div className="text-center mb-8">
          <h1 className="text-3xl font-bold text-white tracking-tight">
            Inovare TI
          </h1>
          <p className="text-gray-400 mt-2 text-sm">
            Entre com suas credenciais para continuar
          </p>
        </div>

        {/* Card de login */}
        <form
          onSubmit={handleSubmit}
          className="bg-gray-900 rounded-2xl shadow-xl p-8 flex flex-col gap-5"
        >
          <LoginField
            id="email"
            type="email"
            label="E-mail"
            placeholder="seu@email.com"
            value={email}
            onChange={setEmail}
            icon={<Mail size={16} className="text-gray-400" />}
          />

          <LoginField
            id="password"
            type="password"
            label="Senha"
            placeholder="••••••••"
            value={password}
            onChange={setPassword}
            icon={<Lock size={16} className="text-gray-400" />}
          />

          {/* Mensagem de erro */}
          {error && (
            <p className="text-red-400 text-sm text-center -mt-1">{error}</p>
          )}

          <button
            type="submit"
            disabled={loading}
            className="mt-1 flex items-center justify-center gap-2 bg-indigo-600 hover:bg-indigo-500 disabled:opacity-60 disabled:cursor-not-allowed text-white font-semibold py-2.5 rounded-xl transition-colors"
          >
            {loading ? (
              <Loader2 size={18} className="animate-spin" />
            ) : (
              <LogIn size={18} />
            )}
            {loading ? 'Entrando...' : 'Entrar'}
          </button>
        </form>
      </div>
    </div>
  );
}
