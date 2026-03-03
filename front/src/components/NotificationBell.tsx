import { useState, useEffect, useRef } from 'react';
import { Bell } from 'lucide-react';
import { getUnreadNotifications, markNotificationAsRead, type Notification } from '../services/api';
import { useNavigate } from 'react-router-dom';

export default function NotificationBell() {
  const [notifications, setNotifications] = useState<Notification[]>([]);
  const [isOpen, setIsOpen] = useState(false);
  const [isLoading, setIsLoading] = useState(false);
  const dropdownRef = useRef<HTMLDivElement>(null);
  const navigate = useNavigate();

  // Busca as notificações não lidas ao montar o componente
  useEffect(() => {
    fetchUnreadNotifications();
    
    // Configura intervalo de polling a cada 30 segundos
    const interval = setInterval(fetchUnreadNotifications, 30000);
    
    return () => clearInterval(interval);
  }, []);

  // Fecha o dropdown ao clicar fora
  useEffect(() => {
    function handleClickOutside(event: MouseEvent) {
      if (dropdownRef.current && !dropdownRef.current.contains(event.target as Node)) {
        setIsOpen(false);
      }
    }

    if (isOpen) {
      document.addEventListener('mousedown', handleClickOutside);
      return () => document.removeEventListener('mousedown', handleClickOutside);
    }
  }, [isOpen]);

  async function fetchUnreadNotifications() {
    try {
      setIsLoading(true);
      const data = await getUnreadNotifications();
      setNotifications(data);
    } catch (error) {
      console.error('Erro ao buscar notificações:', error);
    } finally {
      setIsLoading(false);
    }
  }

  async function handleNotificationClick(notification: Notification) {
    try {
      // Marca como lida
      await markNotificationAsRead(notification.id);
      
      // Remove da lista local
      setNotifications(notifications.filter(n => n.id !== notification.id));
      
      // Navega para o link se houver
      if (notification.link) {
        navigate(notification.link);
        setIsOpen(false);
      }
    } catch (error) {
      console.error('Erro ao marcar notificação como lida:', error);
    }
  }

  const unreadCount = notifications.length;

  return (
    <div ref={dropdownRef} className="relative">
      {/* Botão do Sininho */}
      <button
        onClick={() => setIsOpen(!isOpen)}
        className="relative p-2 text-slate-600 hover:bg-slate-100 rounded-lg transition-colors"
        title="Notificações"
      >
        <Bell size={20} />
        
        {/* Badge com contagem de notificações não lidas */}
        {unreadCount > 0 && (
          <span className="absolute top-0 right-0 inline-flex items-center justify-center px-2 py-1 text-xs font-bold leading-none text-white transform translate-x-1/2 -translate-y-1/2 bg-red-500 rounded-full">
            {unreadCount > 9 ? '9+' : unreadCount}
          </span>
        )}
      </button>

      {/* Dropdown com notificações */}
      {isOpen && (
        <div className="absolute right-0 mt-2 w-80 bg-white rounded-lg shadow-lg border border-slate-200 z-50">
          {/* Cabeçalho */}
          <div className="px-4 py-3 border-b border-slate-200 flex items-center justify-between">
            <h3 className="font-semibold text-slate-900">
              Notificações {unreadCount > 0 && `(${unreadCount})`}
            </h3>
          </div>

          {/* Conteúdo */}
          <div className="max-h-96 overflow-y-auto">
            {isLoading ? (
              <div className="px-4 py-8 text-center text-slate-500">
                <p>Carregando...</p>
              </div>
            ) : notifications.length === 0 ? (
              <div className="px-4 py-8 text-center text-slate-500">
                <p>Nenhuma notificação no momento</p>
              </div>
            ) : (
              <div className="divide-y divide-slate-100">
                {notifications.map((notification) => (
                  <button
                    key={notification.id}
                    onClick={() => handleNotificationClick(notification)}
                    className="w-full px-4 py-3 text-left hover:bg-slate-50 transition-colors text-sm"
                  >
                    <div className="flex items-start gap-2">
                      <div className="flex-1">
                        <p className="font-semibold text-slate-900">
                          {notification.title}
                        </p>
                        <p className="text-slate-600 mt-1">
                          {notification.message}
                        </p>
                        <p className="text-xs text-slate-400 mt-2">
                          {new Date(notification.createdAt).toLocaleDateString('pt-BR', {
                            day: 'numeric',
                            month: 'short',
                            hour: '2-digit',
                            minute: '2-digit',
                          })}
                        </p>
                      </div>
                      <div className="w-2 h-2 bg-blue-500 rounded-full mt-2 flex-shrink-0" />
                    </div>
                  </button>
                ))}
              </div>
            )}
          </div>
        </div>
      )}
    </div>
  );
}
