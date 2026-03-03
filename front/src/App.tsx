// Configuração de rotas e providers globais da aplicação
import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { ToastContainer } from 'react-toastify';
import 'react-toastify/dist/ReactToastify.css';
import { AuthProvider, useAuth } from './contexts/AuthContext';
import DefaultLayout from './layouts/DefaultLayout';
import Login from './pages/Login';
import Dashboard from './pages/Dashboard';
import NewTicket from './pages/NewTicket';
import TicketDetails from './pages/TicketDetails';

// Rota de layout: protege e fornece o header/footer para páginas autenticadas
function PrivateLayoutRoute() {
  const { token } = useAuth();
  return token ? <DefaultLayout /> : <Navigate to="/login" replace />;
}

function AppRoutes() {
  return (
    <Routes>
      <Route path="/login" element={<Login />} />
      {/* Rotas protegidas compartilham o DefaultLayout */}
      <Route element={<PrivateLayoutRoute />}>
        <Route path="/dashboard" element={<Dashboard />} />
        <Route path="/tickets/new" element={<NewTicket />} />
        {/* Rota de detalhes de chamado — :id é o UUID do chamado */}
        <Route path="/tickets/:id" element={<TicketDetails />} />
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

