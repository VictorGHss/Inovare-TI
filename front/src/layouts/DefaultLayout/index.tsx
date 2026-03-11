// Layout padrão compartilhado entre todas as páginas autenticadas
import { Outlet, useNavigate, useLocation } from 'react-router-dom';
import {
  BookOpen,
  Building2,
  Github,
  Monitor,
  Package,
  Ticket,
  Users,
} from 'lucide-react';
import { useAuth } from '../../contexts/AuthContext';
import NotificationBell from '../../components/NotificationBell';
import UserDropdown from '../../components/UserDropdown';

const LOGO_URL = 'http://inovare.med.br/wp-content/uploads/2023/01/Logo.png';

export default function DefaultLayout() {
  const { user } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();

  const isTicketsActive = location.pathname.startsWith('/tickets');
  const isInventoryActive = location.pathname.startsWith('/inventory');
  const isAssetsActive = location.pathname.startsWith('/assets');
  const isKnowledgeBaseActive = location.pathname.startsWith('/knowledge-base');
  const isUsersActive = location.pathname.startsWith('/users');
  const isSectorsActive = location.pathname.startsWith('/sectors');

  const navButtonClass = (isActive: boolean) =>
    `px-3 py-1.5 text-sm font-medium rounded-lg transition-colors flex items-center gap-2 ${
      isActive
        ? 'bg-primary/10 text-primary'
        : 'text-slate-600 hover:bg-slate-100'
    }`;

  const navIconClass = (isActive: boolean) =>
    isActive ? 'text-primary' : 'text-slate-400';

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
              className={navButtonClass(isTicketsActive)}
            >
              <Ticket size={14} className={navIconClass(isTicketsActive)} />
              Chamados
            </button>
            {user?.role !== 'USER' && (
              <button
                onClick={() => navigate('/inventory')}
                className={navButtonClass(isInventoryActive)}
              >
                <Package size={14} className={navIconClass(isInventoryActive)} />
                Inventário
              </button>
            )}
            {(user?.role === 'ADMIN' || user?.role === 'TECHNICIAN') && (
              <button
                onClick={() => navigate('/assets')}
                className={navButtonClass(isAssetsActive)}
              >
                <Monitor size={14} className={navIconClass(isAssetsActive)} />
                Equipamentos
              </button>
            )}
            <button
              onClick={() => navigate('/knowledge-base')}
              className={navButtonClass(isKnowledgeBaseActive)}
            >
              <BookOpen size={14} className={navIconClass(isKnowledgeBaseActive)} />
              Base de Conhecimento
            </button>
            {/* Links administrativos — visíveis apenas para ADMIN */}
            {user?.role === 'ADMIN' && (
              <>
                <button
                  onClick={() => navigate('/users')}
                  className={navButtonClass(isUsersActive)}
                >
                  <Users size={14} className={navIconClass(isUsersActive)} />
                  Equipe
                </button>
                <button
                  onClick={() => navigate('/sectors')}
                  className={navButtonClass(isSectorsActive)}
                >
                  <Building2 size={14} className={navIconClass(isSectorsActive)} />
                  Setores
                </button>
              </>
            )}
          </nav>
        </div>
        <div className="flex items-center gap-2 flex-shrink-0">
          <NotificationBell />
          {user && <UserDropdown userName={user.name} userRole={user.role} />}
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
