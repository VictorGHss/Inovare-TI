// Layout padrão compartilhado entre todas as páginas autenticadas
import { Outlet, useNavigate, useLocation } from 'react-router-dom';
import { LogOut, Github, HardDrive } from 'lucide-react';
import { useAuth } from '../../contexts/AuthContext';
import NotificationBell from '../../components/NotificationBell';

const LOGO_URL = 'http://inovare.med.br/wp-content/uploads/2023/01/Logo.png';

export default function DefaultLayout() {
  const { signOut, user } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();

  function handleLogout() {
    signOut();
    navigate('/login');
  }

  return (
    <div className="min-h-screen bg-slate-50 flex flex-col">
      {/* Cabeçalho global fixo no topo */}
      <header className="bg-white border-b border-slate-200 px-6 py-3 flex items-center justify-between sticky top-0 z-10 shadow-sm">
        <div className="flex items-center gap-6">
          <img
            src={LOGO_URL}
            alt="Inovare TI"
            className="h-10 object-contain cursor-pointer"
            onClick={() => navigate('/dashboard')}
          />
          {/* Links de navegação */}
          <nav className="hidden sm:flex items-center gap-1">
            <button
              onClick={() => navigate('/tickets')}
              className={`px-3 py-1.5 text-sm font-medium rounded-lg transition-colors ${
                location.pathname.startsWith('/tickets')
                  ? 'bg-primary text-white'
                  : 'text-slate-600 hover:bg-slate-100'
              }`}
            >
              Chamados
            </button>
            {user?.role !== 'USER' && (
              <button
                onClick={() => navigate('/inventory')}
                className={`px-3 py-1.5 text-sm font-medium rounded-lg transition-colors ${
                  location.pathname.startsWith('/inventory')
                    ? 'bg-primary text-white'
                    : 'text-slate-600 hover:bg-slate-100'
                }`}
              >
                Inventário
              </button>
            )}
            {(user?.role === 'ADMIN' || user?.role === 'TECHNICIAN') && (
              <button
                onClick={() => navigate('/assets')}
                className={`px-3 py-1.5 text-sm font-medium rounded-lg transition-colors flex items-center gap-1.5 ${
                  location.pathname.startsWith('/assets')
                    ? 'bg-primary text-white'
                    : 'text-slate-600 hover:bg-slate-100'
                }`}
              >
                <HardDrive size={14} />
                Ativos
              </button>
            )}
            <button
              onClick={() => navigate('/knowledge-base')}
              className={`px-3 py-1.5 text-sm font-medium rounded-lg transition-colors ${
                location.pathname.startsWith('/knowledge-base')
                  ? 'bg-primary text-white'
                  : 'text-slate-600 hover:bg-slate-100'
              }`}
            >
              Tutoriais
            </button>
          {/* Links administrativos — visíveis apenas para ADMIN */}
            {user?.role === 'ADMIN' && (
              <>
                <button
                  onClick={() => navigate('/users')}
                  className={`px-3 py-1.5 text-sm font-medium rounded-lg transition-colors ${
                    location.pathname.startsWith('/users')
                      ? 'bg-primary text-white'
                      : 'text-slate-600 hover:bg-slate-100'
                  }`}
                >
                  Equipe
                </button>
                <button
                  onClick={() => navigate('/sectors')}
                  className={`px-3 py-1.5 text-sm font-medium rounded-lg transition-colors ${
                    location.pathname.startsWith('/sectors')
                      ? 'bg-primary text-white'
                      : 'text-slate-600 hover:bg-slate-100'
                  }`}
                >
                  Setores
                </button>
              </>
            )}
          </nav>
        </div>
        <div className="flex items-center gap-3">
          <NotificationBell />
          <span className="text-sm text-slate-500 hidden sm:block">
            {user?.name ?? 'Usuário'}
          </span>
          <button
            onClick={() => navigate('/profile')}
            className={`text-sm font-medium px-3 py-1.5 rounded-lg transition-colors ${
              location.pathname.startsWith('/profile')
                ? 'bg-brand-primary text-white'
                : 'text-slate-600 hover:bg-slate-100'
            }`}
          >
            Meu Perfil
          </button>
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
      <main className="flex-1 w-full max-w-full">
        <Outlet />
      </main>

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
