// Configuração de rotas e providers globais da aplicação
import { BrowserRouter, Routes, Route, Navigate, useLocation } from 'react-router-dom';
import { ToastContainer } from 'react-toastify';
import 'react-toastify/dist/ReactToastify.css';
import { AuthProvider, useAuth } from './contexts/AuthContext';
import DefaultLayout from './layouts/DefaultLayout';
import Login from './pages/Login';
import Dashboard from './pages/Dashboard';
import Tickets from './pages/Tickets';
import NewTicket from './pages/NewTicket';
import TicketDetails from './pages/TicketDetails';
import Inventory from './pages/Inventory';
import NewItem from './pages/Inventory/NewItem';
import ItemDetails from './pages/Inventory/ItemDetails';
import Users from './pages/Users';
import Sectors from './pages/Sectors';
import KnowledgeBase from './pages/KnowledgeBase';
import NewArticle from './pages/KnowledgeBase/NewArticle';
import ArticleDetails from './pages/KnowledgeBase/ArticleDetails';
import Assets from './pages/Assets';
import AssetDetails from './pages/AssetDetails';
import PrimeiroAcesso from './pages/PrimeiroAcesso';
import Profile from './pages/Profile';

// Rota de layout: protege e fornece o header/footer para páginas autenticadas
function PrivateLayoutRoute() {
  const { token } = useAuth();
  const location = useLocation();
  
  if (!token) {
    return <Navigate to="/login" state={{ from: location.pathname }} replace />;
  }
  
  return <DefaultLayout />;
}

function AppRoutes() {
  return (
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
        {/* Rotas de gestão de pessoas (requerem ADMIN) */}
        <Route path="/users" element={<Users />} />
        <Route path="/sectors" element={<Sectors />} />
        {/* Rotas da Base de Conhecimento */}
        <Route path="/knowledge-base" element={<KnowledgeBase />} />
        <Route path="/knowledge-base/new" element={<NewArticle />} />
        <Route path="/knowledge-base/:id" element={<ArticleDetails />} />
      </Route>
      {/* Redireciona a raiz para /login */}
      <Route path="*" element={<Navigate to="/login" replace />} />
    </Routes>
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

