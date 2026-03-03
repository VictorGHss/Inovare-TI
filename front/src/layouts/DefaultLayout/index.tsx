// Layout padrão compartilhado entre todas as páginas autenticadas
import { Outlet, useNavigate } from 'react-router-dom';
import { LogOut, Github } from 'lucide-react';
import { useAuth } from '../../contexts/AuthContext';

const LOGO_URL = 'http://inovare.med.br/wp-content/uploads/2023/01/Logo.png';

export default function DefaultLayout() {
  const { signOut, user } = useAuth();
  const navigate = useNavigate();

  function handleLogout() {
    signOut();
    navigate('/login');
  }

  return (
    <div className="min-h-screen bg-slate-50 flex flex-col">
      {/* Cabeçalho global fixo no topo */}
      <header className="bg-white border-b border-slate-200 px-6 py-3 flex items-center justify-between sticky top-0 z-10 shadow-sm">
        <img
          src={LOGO_URL}
          alt="Inovare TI"
          className="h-10 object-contain cursor-pointer"
          onClick={() => navigate('/dashboard')}
        />
        <div className="flex items-center gap-3">
          <span className="text-sm text-slate-500 hidden sm:block">
            {user?.name ?? 'Usuário'}
          </span>
          <button
            onClick={handleLogout}
            className="flex items-center gap-1.5 text-sm text-slate-500 hover:text-red-500 transition-colors"
          >
            <LogOut size={16} />
            Sair
          </button>
        </div>
      </header>

      {/* Conteúdo da página atual — cresce para empurrar o rodapé */}
      <div className="flex-grow">
        <Outlet />
      </div>

      {/* Rodapé */}
      <footer className="mt-auto py-4 text-center border-t border-slate-200 bg-white">
        <p className="text-xs text-slate-400 flex items-center justify-center gap-1">
          Feito por
          <Github className="inline w-4 h-4 mx-1" />
          <a
            href="https://github.com/VictorGHss"
            target="_blank"
            rel="noopener noreferrer"
            className="text-primary hover:text-primary-hover font-medium transition-colors underline underline-offset-2"
          >
            VictorGHss
          </a>
        </p>
      </footer>
    </div>
  );
}
