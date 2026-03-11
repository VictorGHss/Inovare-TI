// Configuração de rotas e providers globais da aplicação
import { lazy, Suspense } from 'react';
import type { ReactElement } from 'react';
import { BrowserRouter, Routes, Route, Navigate, useLocation } from 'react-router-dom';
import { ToastContainer } from 'react-toastify';
import 'react-toastify/dist/ReactToastify.css';
import { AuthProvider, useAuth } from './contexts/AuthContext';
import DefaultLayout from './layouts/DefaultLayout';

// Login e PrimeiroAcesso carregados de forma eager — são rotas de entrada leves
import Login from './pages/Login';
import PrimeiroAcesso from './pages/PrimeiroAcesso';

// Lazy-loaded: cada página gera um chunk separado, reduzindo o bundle inicial
const Dashboard = lazy(() => import('./pages/Dashboard'));
const Tickets = lazy(() => import('./pages/Tickets'));
const NewTicket = lazy(() => import('./pages/NewTicket'));
const TicketDetails = lazy(() => import('./pages/TicketDetails'));
const Inventory = lazy(() => import('./pages/Inventory'));
const NewItem = lazy(() => import('./pages/Inventory/NewItem'));
const ItemDetails = lazy(() => import('./pages/Inventory/ItemDetails'));
const Assets = lazy(() => import('./pages/Assets'));
const AssetDetails = lazy(() => import('./pages/AssetDetails'));
const Profile = lazy(() => import('./pages/Profile'));
const Settings = lazy(() => import('./pages/Settings'));
const Users = lazy(() => import('./pages/Users'));
const Sectors = lazy(() => import('./pages/Sectors'));
const KnowledgeBase = lazy(() => import('./pages/KnowledgeBase'));
const NewArticle = lazy(() => import('./pages/KnowledgeBase/NewArticle'));
const ArticleDetails = lazy(() => import('./pages/KnowledgeBase/ArticleDetails'));
const Vault = lazy(() => import('./pages/Vault'));

function PageLoader() {
  return (
    <div className="flex items-center justify-center w-full py-24 text-gray-500">
      Carregando...
    </div>
  );
}

// Rota de layout: protege e fornece o header/footer para páginas autenticadas
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

function AppRoutes() {
  return (
    <Suspense fallback={<PageLoader />}>
      <Routes>
        <Route path="/login" element={<Login />} />
        <Route path="/primeiro-acesso" element={<PrimeiroAcesso />} />
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
          <Route
            path="/vault"
            element={(
              <RoleRoute allowedRoles={['ADMIN', 'TECHNICIAN']}>
                <Vault />
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
          autoClose={4000}
          hideProgressBar={false}
          closeOnClick
          pauseOnHover
          draggable
          theme="light"
        />
      </AuthProvider>
    </BrowserRouter>
  );
}

