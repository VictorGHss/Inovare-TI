import { useState, useRef, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { LogOut, User, Settings, ChevronDown, ChevronUp } from 'lucide-react';
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

  return (
    <div ref={dropdownRef} className="relative flex-shrink-0">
      {/* Trigger Button */}
      <button
        onClick={() => setIsOpen(!isOpen)}
        className="flex items-center gap-3 px-3 py-1.5 rounded-lg hover:bg-slate-100 transition-colors max-w-[220px]"
      >
        {/* Avatar com Initial */}
        <div className="w-8 h-8 rounded-full bg-brand-primary text-white flex items-center justify-center text-sm font-semibold shrink-0">
          {userInitial}
        </div>
        {/* Nome do usuário */}
        <span className="hidden sm:block text-sm font-medium text-slate-700 truncate">
          {userName}
        </span>
        {/* Chevron indica estado do menu */}
        <span className="hidden sm:block text-slate-400 shrink-0">
          {isOpen ? <ChevronUp size={14} /> : <ChevronDown size={14} />}
        </span>
      </button>

      {/* Dropdown Menu — abre abaixo do trigger, alinhado à direita */}
      {isOpen && (
        <div className="absolute right-0 top-full mt-2 w-64 bg-white rounded-xl shadow-lg border border-slate-200 overflow-hidden z-50">
          {/* Header com Nome e Role */}
          <div className="px-4 py-3 bg-slate-50 border-b border-slate-200">
            <p className="text-sm font-semibold text-slate-800 truncate">{userName}</p>
            <p className="text-xs text-slate-500 mt-0.5">{roleLabel}</p>
          </div>

          {/* Menu Items */}
          <div className="py-1">
            {/* Meu Perfil */}
            <button
              onClick={() => handleNavigate('/profile')}
              className="w-full flex items-center gap-3 px-4 py-2.5 text-sm text-slate-700 hover:bg-slate-50 transition-colors"
            >
              <User size={16} />
              Meu Perfil
            </button>

            {/* Preferências / Configurações */}
            <button
              onClick={() => handleNavigate(settingsPath)}
              className="w-full flex items-center gap-3 px-4 py-2.5 text-sm text-slate-700 hover:bg-slate-50 transition-colors"
            >
              <Settings size={16} />
              {userRole === 'ADMIN' ? 'Configurações Globais' : 'Preferências'}
            </button>

            {/* Divisor */}
            <div className="my-1 border-t border-slate-200" />

            {/* Sair */}
            <button
              onClick={handleLogout}
              className="w-full flex items-center gap-3 px-4 py-2.5 text-sm text-red-600 hover:bg-red-50 transition-colors"
            >
              <LogOut size={16} />
              Sair
            </button>
          </div>
        </div>
      )}
    </div>
  );
}
