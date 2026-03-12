// Layout padrão compartilhado entre todas as páginas autenticadas
import { useMemo, useState } from 'react';
import { Outlet, useNavigate, useLocation } from 'react-router-dom';
import {
  BookOpen,
  Building2,
  FileLock,
  Github,
  Menu,
  Monitor,
  Package,
  Ticket,
  Users,
  X,
} from 'lucide-react';
import type { LucideIcon } from 'lucide-react';
import { useAuth } from '../../contexts/AuthContext';
import NotificationBell from '../../components/NotificationBell';
import UserDropdown from '../../components/UserDropdown';

const LOGO_URL = 'http://inovare.med.br/wp-content/uploads/2023/01/Logo.png';

export default function DefaultLayout() {
  const { user } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const [isMobileSidebarOpen, setIsMobileSidebarOpen] = useState(false);

  type NavItem = {
    path: string;
    label: string;
    icon: LucideIcon;
    visible: boolean;
  };

  const navItems = useMemo<NavItem[]>(() => ([
    { path: '/tickets', label: 'Chamados', icon: Ticket, visible: true },
    { path: '/inventory', label: 'Inventário', icon: Package, visible: user?.role !== 'USER' },
    {
      path: '/assets',
      label: 'Equipamentos',
      icon: Monitor,
      visible: user?.role === 'ADMIN' || user?.role === 'TECHNICIAN',
    },
    { path: '/knowledge-base', label: 'Base de Conhecimento', icon: BookOpen, visible: true },
    {
      path: '/vault',
      label: 'Cofre',
      icon: FileLock,
      visible: user?.role === 'ADMIN' || user?.role === 'TECHNICIAN',
    },
    { path: '/users', label: 'Equipe', icon: Users, visible: user?.role === 'ADMIN' },
    { path: '/sectors', label: 'Setores', icon: Building2, visible: user?.role === 'ADMIN' },
  ]), [user?.role]);

  const navButtonClass = (isActive: boolean) =>
    `w-full px-3 py-2.5 text-sm font-medium rounded-xl transition-colors flex items-center gap-2 ${
      isActive
        ? 'bg-brand-secondary text-brand-primary'
        : 'text-slate-600 hover:bg-slate-100'
    }`;

  const navIconClass = (isActive: boolean) =>
    isActive ? 'text-brand-primary' : 'text-slate-400';

  const isPathActive = (path: string) => location.pathname.startsWith(path);

  return (
    <div className="min-h-screen bg-slate-50 flex flex-col">
      <header className="bg-white border-b border-slate-200 px-4 sm:px-6 py-3 flex items-center justify-between sticky top-0 z-40 shadow-sm">
        <div className="flex items-center gap-3">
          <button
            onClick={() => setIsMobileSidebarOpen(true)}
            className="lg:hidden inline-flex items-center justify-center rounded-lg p-2 text-slate-600 hover:bg-slate-100 transition-colors"
            aria-label="Abrir menu"
          >
            <Menu size={20} />
          </button>
          <img
            src={LOGO_URL}
            alt="Inovare TI"
            className="h-10 object-contain cursor-pointer"
            onClick={() => {
              setIsMobileSidebarOpen(false);
              navigate('/dashboard');
            }}
          />
        </div>
        <div className="flex items-center gap-2 flex-shrink-0">
          <NotificationBell />
          {user && <UserDropdown userName={user.name} userRole={user.role} />}
        </div>
      </header>

      <div className="flex flex-1 min-h-0">
        <div
          className={`fixed inset-0 z-30 bg-black/40 transition-opacity duration-300 lg:hidden ${
            isMobileSidebarOpen ? 'opacity-100 pointer-events-auto' : 'opacity-0 pointer-events-none'
          }`}
          onClick={() => setIsMobileSidebarOpen(false)}
        />

        <aside
          className={`fixed left-0 top-[65px] z-40 h-[calc(100vh-65px)] w-72 border-r border-slate-200 bg-white px-3 py-3 shadow-lg transition-transform duration-300 ease-in-out lg:static lg:top-0 lg:h-auto lg:min-h-full lg:translate-x-0 lg:shadow-none ${
            isMobileSidebarOpen ? 'translate-x-0' : '-translate-x-full'
          }`}
        >
          <div className="mb-2 flex items-center justify-between px-2 lg:hidden">
            <span className="text-sm font-semibold text-slate-700">Menu</span>
            <button
              onClick={() => setIsMobileSidebarOpen(false)}
              className="inline-flex items-center justify-center rounded-lg p-2 text-slate-600 hover:bg-slate-100 transition-colors"
              aria-label="Fechar menu"
            >
              <X size={18} />
            </button>
          </div>

          <nav className="space-y-1">
            {navItems.filter((item) => item.visible).map((item) => {
              const Icon = item.icon;
              const isActive = isPathActive(item.path);
              return (
                <button
                  key={item.path}
                  onClick={() => {
                    setIsMobileSidebarOpen(false);
                    navigate(item.path);
                  }}
                  className={navButtonClass(isActive)}
                >
                  <Icon size={16} className={navIconClass(isActive)} />
                  {item.label}
                </button>
              );
            })}
          </nav>
        </aside>

        <div className="flex min-w-0 flex-1 flex-col">
          <main className="flex-1 w-full max-w-full">
            <Outlet />
          </main>

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
      </div>
    </div>
  );
}
