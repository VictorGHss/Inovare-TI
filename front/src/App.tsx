// Configuração de rotas e providers globais da aplicação
import { lazy, Suspense } from 'react';
import type { ReactElement } from 'react';
import { BrowserRouter, Routes, Route, Navigate, Outlet, useLocation } from 'react-router-dom';
import { motion } from 'framer-motion';
import { ToastContainer } from 'react-toastify';
import 'react-toastify/dist/ReactToastify.css';
import { AuthProvider, useAuth } from './contexts/AuthContext';
import DefaultLayout from './layouts/DefaultLayout';
import FinancialTwoFactorChallenge from './components/FinancialTwoFactorChallenge';

// Login e PrimeiroAcesso carregados de forma imediata — são rotas de entrada leves
import Login from './pages/Login';
import PrimeiroAcesso from './pages/PrimeiroAcesso';

// eslint-disable-next-line @typescript-eslint/no-explicit-any
function lazyWithRetry<T extends React.ComponentType<any>>(
  importFunc: () => Promise<{ default: T }>
): React.LazyExoticComponent<T> {
  return lazy(async () => {
    setTimeout(() => {
      sessionStorage.removeItem('chunk-load-error-reloaded');
    }, 15000);

    try {
      return await importFunc();
    } catch (error) {
      console.error("Erro ao carregar módulo dinâmico, limpando caches e forçando recarga da página...", error);
      
      // Remove o service worker para destravar o cache
      if ('serviceWorker' in navigator) {
        try {
          navigator.serviceWorker.getRegistrations().then(function(registrations) {
            for (let reg of registrations) {
              reg.unregister();
            }
          });
        } catch (e) {
          // ignore
        }
      }

      // Limpa os caches de cacheStorage do navegador
      if ('caches' in window) {
        try {
          caches.keys().then(function(keys) {
            keys.forEach(key => caches.delete(key));
          });
        } catch (e) {
          // ignore
        }
      }

      const hasReloaded = sessionStorage.getItem('chunk-load-error-reloaded');
      if (!hasReloaded) {
        sessionStorage.setItem('chunk-load-error-reloaded', 'true');
        setTimeout(() => {
          window.location.reload();
        }, 200);
      }
      throw error;
    }
  });
}

// Carregamento sob demanda: cada página gera um chunk separado, reduzindo o bundle inicial
const Dashboard = lazyWithRetry(() => import('./pages/Dashboard'));
const Tickets = lazyWithRetry(() => import('./pages/Tickets'));
const NewTicket = lazyWithRetry(() => import('./pages/NewTicket'));
const TicketDetails = lazyWithRetry(() => import('./pages/TicketDetails'));
const Inventory = lazyWithRetry(() => import('./pages/Inventory'));
const NewItem = lazyWithRetry(() => import('./pages/Inventory/NewItem'));
const ItemDetails = lazyWithRetry(() => import('./pages/Inventory/ItemDetails'));
const Assets = lazyWithRetry(() => import('./pages/Assets'));
const AssetDetails = lazyWithRetry(() => import('./pages/AssetDetails'));
const Profile = lazyWithRetry(() => import('./pages/Profile'));
const Settings = lazyWithRetry(() => import('./pages/Settings'));
const Users = lazyWithRetry(() => import('./pages/Users'));
const Sectors = lazyWithRetry(() => import('./pages/Sectors'));
const KnowledgeBase = lazyWithRetry(() => import('./pages/KnowledgeBase'));
const NewArticle = lazyWithRetry(() => import('./pages/KnowledgeBase/NewArticle'));
const EditArticle = lazyWithRetry(() => import('./pages/KnowledgeBase/EditArticle'));
const ArticleDetails = lazyWithRetry(() => import('./pages/KnowledgeBase/ArticleDetails'));
const Vault = lazyWithRetry(() => import('./pages/Vault'));
const SystemLogs = lazyWithRetry(() => import('./pages/SystemLogs'));
const Financeiro = lazyWithRetry(() => import('./pages/Financeiro'));
const FaqManagement = lazyWithRetry(() => import('./pages/FaqManagement'));

function PageLoader() {
  return (
    <div className="flex items-center justify-center w-full py-24 text-gray-500">
      Carregando...
    </div>
  );
}

function PageTransition({ children }: { children: ReactElement }) {
  return (
    <motion.div
      initial={{ opacity: 0, y: 18 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: 0.3, ease: 'easeOut' }}
    >
      {children}
    </motion.div>
  );
}

// Rota de layout: protege e fornece o cabeçalho/estrutura para páginas autenticadas
function PrivateLayoutRoute() {
  const { token } = useAuth();
  const location = useLocation();

  if (!token) {
    return <Navigate to="/login" state={{ from: location.pathname }} replace />;
  }

  return <DefaultLayout />;
}

function RoleRoute({
  allowedRoles,
  children,
}: {
  allowedRoles: Array<'ADMIN' | 'TECHNICIAN' | 'USER'>;
  children: ReactElement;
}) {
  const { user } = useAuth();

  if (!user || !allowedRoles.includes(user.role)) {
    return <Navigate to="/dashboard" replace />;
  }

  return children;
}

function FinancialGuardRoute({ children }: { children?: ReactElement }) {
  const { user, isTwoFactorVerified } = useAuth();

  if (!user || user.role !== 'ADMIN') {
    return <Navigate to="/dashboard" replace />;
  }

  if (!isTwoFactorVerified) {
    return <FinancialTwoFactorChallenge />;
  }

  return children ?? <Outlet />;
}

function AppRoutes() {
  return (
    <Suspense fallback={<PageLoader />}>
      <Routes>
        <Route path="/login" element={<PageTransition><Login /></PageTransition>} />
        <Route path="/primeiro-acesso" element={<PageTransition><PrimeiroAcesso /></PageTransition>} />
        {/* Rotas protegidas compartilham o DefaultLayout */}
        <Route element={<PrivateLayoutRoute />}>
          <Route path="/dashboard" element={<Dashboard />} />
          <Route path="/tickets" element={<Tickets />} />
          <Route path="/tickets/new" element={<NewTicket />} />
          {/* Rota de detalhes de chamado — :id é o UUID do chamado */}
          <Route path="/tickets/:id" element={<TicketDetails />} />
          {/* Rotas de inventário */}
          <Route path="/inventory" element={<Inventory />} />
          <Route path="/inventory/new" element={<NewItem />} />
          {/* Rota de detalhes de item — :id é o UUID do item */}
          <Route path="/inventory/:id" element={<ItemDetails />} />
          {/* Rotas de ativos (CMDB) */}
          <Route path="/assets" element={<Assets />} />
          <Route path="/assets/:id" element={<AssetDetails />} />
          <Route path="/profile" element={<Profile />} />
          <Route path="/settings" element={<Settings />} />
          {/* Rotas de gestão de pessoas (requerem ADMIN) */}
          <Route path="/users" element={<Users />} />
          <Route path="/sectors" element={<Sectors />} />
          {/* Rotas da Base de Conhecimento */}
          <Route path="/knowledge-base" element={<KnowledgeBase />} />
          <Route path="/knowledge-base/new" element={<NewArticle />} />
          <Route path="/knowledge-base/:id" element={<ArticleDetails />} />
          <Route path="/knowledge-base/:id/edit" element={<EditArticle />} />
          <Route
            path="/vault"
            element={(
              <RoleRoute allowedRoles={['ADMIN', 'TECHNICIAN']}>
                <Vault />
              </RoleRoute>
            )}
          />
          <Route
            path="/system-logs"
            element={(
              <RoleRoute allowedRoles={['ADMIN']}>
                <SystemLogs />
              </RoleRoute>
            )}
          />
          <Route
            path="/financeiro"
            element={(
              <FinancialGuardRoute>
                <Financeiro />
              </FinancialGuardRoute>
            )}
          />
          <Route
            path="/faq-management"
            element={(
              <RoleRoute allowedRoles={['ADMIN', 'TECHNICIAN']}>
                <FaqManagement />
              </RoleRoute>
            )}
          />
        </Route>
        {/* Redireciona a raiz para /login */}
        <Route path="*" element={<Navigate to="/login" replace />} />
      </Routes>
    </Suspense>
  );
}

export default function App() {
  return (
    <BrowserRouter>
      <AuthProvider>
        <AppRoutes />
        {/* Container global de notificações toast */}
        <ToastContainer
          position="top-right"
          autoClose={3000}
          hideProgressBar={false}
          closeOnClick
          pauseOnHover={false}
          pauseOnFocusLoss={false}
          draggable
          theme="light"
        />
      </AuthProvider>
    </BrowserRouter>
  );
}

