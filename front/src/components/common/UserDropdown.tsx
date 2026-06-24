import { useState, useRef, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  LogOut,
  User,
  Settings,
  Shield,
  ChevronDown,
  ChevronUp,
} from 'lucide-react';
import { useAuth } from '../contexts/AuthContext';

interface UserDropdownProps {
  userName: string;
  userRole: string;
}

export default function UserDropdown({ userName, userRole }: UserDropdownProps) {
  const [isOpen, setIsOpen] = useState(false);
  const dropdownRef = useRef<HTMLDivElement>(null);
  const navigate = useNavigate();
  const { signOut } = useAuth();

  const userInitial = userName.charAt(0).toUpperCase();
  const roleLabel = userRole === 'ADMIN' ? 'Administrador' : userRole === 'TECHNICIAN' ? 'Técnico' : 'Usuário';

  useEffect(() => {
    function handleClickOutside(event: MouseEvent) {
      if (dropdownRef.current && !dropdownRef.current.contains(event.target as Node)) {
        setIsOpen(false);
      }
    }

    document.addEventListener('mousedown', handleClickOutside);
    return () => document.removeEventListener('mousedown', handleClickOutside);
  }, []);

  function handleLogout() {
    signOut();
    navigate('/login');
    setIsOpen(false);
  }

  function handleNavigate(path: string) {
    navigate(path);
    setIsOpen(false);
  }

  const settingsPath = '/settings';
  const canViewSystemLogs = userRole === 'ADMIN';

  return (
    <div ref={dropdownRef} className="relative flex-shrink-0">
      <button
        onClick={() => setIsOpen(!isOpen)}
        className="flex items-center gap-3 px-3 py-1.5 rounded-xl hover:bg-slate-100 transition-colors max-w-[220px] border border-transparent hover:border-slate-200"
      >
        <div className="w-8 h-8 rounded-full bg-brand-primary text-white flex items-center justify-center text-sm font-semibold shrink-0">
          {userInitial}
        </div>
        <span className="hidden sm:block text-sm font-medium text-slate-700 truncate">
          {userName}
        </span>
        <span className="hidden sm:block text-slate-400 shrink-0">
          {isOpen ? <ChevronUp size={14} /> : <ChevronDown size={14} />}
        </span>
      </button>

      {isOpen && (
        <div className="absolute right-0 top-full mt-2 w-72 bg-white rounded-2xl shadow-xl border border-slate-200 overflow-hidden z-50">
          <div className="px-4 py-3 bg-slate-50 border-b border-slate-200">
            <p className="text-sm font-semibold text-slate-800 truncate">{userName}</p>
            <p className="text-xs text-slate-500 mt-0.5">{roleLabel}</p>
          </div>

          <div className="py-1">
            <button
              onClick={() => handleNavigate('/profile')}
              className="w-full flex items-center gap-3 px-4 py-2.5 text-sm text-slate-700 hover:bg-slate-50 transition-colors"
            >
              <User size={16} className="text-brand-primary" />
              Meu Perfil
            </button>

            <button
              onClick={() => handleNavigate(settingsPath)}
              className="w-full flex items-center gap-3 px-4 py-2.5 text-sm text-slate-700 hover:bg-slate-50 transition-colors"
            >
              <Settings size={16} className="text-slate-500" />
              Configurações Globais
            </button>

            {canViewSystemLogs && (
              <button
                onClick={() => handleNavigate('/system-logs')}
                className="w-full flex items-center gap-3 px-4 py-2.5 text-sm text-slate-700 hover:bg-slate-50 transition-colors"
              >
                <Shield size={16} className="text-indigo-600" />
                Logs do Sistema
              </button>
            )}

            <div className="my-1 border-t border-slate-200" />

            <button
              onClick={handleLogout}
              className="w-full flex items-center gap-3 px-4 py-2.5 text-sm text-red-600 hover:bg-slate-100 transition-colors"
            >
              <LogOut size={16} className="text-red-500" />
              Sair
            </button>
          </div>
        </div>
      )}
    </div>
  );
}
