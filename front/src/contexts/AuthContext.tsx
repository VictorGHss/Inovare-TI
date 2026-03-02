// Contexto de autenticação global da aplicação
import {
  createContext,
  useContext,
  useState,
  useCallback,
  type ReactNode,
} from 'react';
import api from '../services/api';

interface User {
  id: string;
  name: string;
  email: string;
  role: string;
}

interface SignInCredentials {
  email: string;
  password: string;
}

interface AuthContextData {
  user: User | null;
  token: string | null;
  signIn: (credentials: SignInCredentials) => Promise<void>;
  signOut: () => void;
}

interface AuthProviderProps {
  children: ReactNode;
}

const AuthContext = createContext<AuthContextData>({} as AuthContextData);

export function AuthProvider({ children }: AuthProviderProps) {
  const [token, setToken] = useState<string | null>(
    () => localStorage.getItem('@InovareTI:token'),
  );
  const [user, setUser] = useState<User | null>(() => {
    const stored = localStorage.getItem('@InovareTI:user');
    return stored ? (JSON.parse(stored) as User) : null;
  });

  // Realiza login e persiste credenciais no localStorage
  const signIn = useCallback(async ({ email, password }: SignInCredentials) => {
    const { data } = await api.post<{ token: string; user: User }>(
      '/api/auth/login',
      { email, password },
    );

    localStorage.setItem('@InovareTI:token', data.token);
    localStorage.setItem('@InovareTI:user', JSON.stringify(data.user));
    setToken(data.token);
    setUser(data.user);
  }, []);

  // Remove as credenciais e desconecta o usuário
  const signOut = useCallback(() => {
    localStorage.removeItem('@InovareTI:token');
    localStorage.removeItem('@InovareTI:user');
    setToken(null);
    setUser(null);
  }, []);

  return (
    <AuthContext.Provider value={{ user, token, signIn, signOut }}>
      {children}
    </AuthContext.Provider>
  );
}

// Hook para consumir o contexto de autenticação
// eslint-disable-next-line react-refresh/only-export-components
export function useAuth() {
  return useContext(AuthContext);
}
