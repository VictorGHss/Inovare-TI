// Contexto de autenticação global da aplicação
import {
  createContext,
  useContext,
  useState,
  useCallback,
  type ReactNode,
} from 'react';
import api from '../services/api';
import { resetInitialPassword, type AuthResponseDTO, type User } from '../services/api';

interface SignInCredentials {
  email: string;
  password: string;
}

interface SignInResult {
  status: 'AUTHENTICATED' | 'PASSWORD_RESET_REQUIRED';
  tempToken?: string;
  userId?: string;
}

interface AuthContextData {
  user: User | null;
  token: string | null;
  signIn: (credentials: SignInCredentials) => Promise<SignInResult>;
  completeInitialPasswordReset: (payload: {
    tempToken: string;
    userId: string;
    newPassword: string;
  }) => Promise<void>;
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
    const { data } = await api.post<AuthResponseDTO>(
      '/api/auth/login',
      { email, password },
    );

    if (data.status === 'PASSWORD_RESET_REQUIRED' && data.tempToken && data.userId) {
      return {
        status: 'PASSWORD_RESET_REQUIRED' as const,
        tempToken: data.tempToken,
        userId: data.userId,
      };
    }

    if (!data.token || !data.user) {
      throw new Error('Resposta de autenticação inválida.');
    }

    localStorage.setItem('@InovareTI:token', data.token);
    localStorage.setItem('@InovareTI:user', JSON.stringify(data.user));
    setToken(data.token);
    setUser(data.user);

    return { status: 'AUTHENTICATED' as const };
  }, []);

  const completeInitialPasswordReset = useCallback(async (payload: {
    tempToken: string;
    userId: string;
    newPassword: string;
  }) => {
    const response = await resetInitialPassword(payload);

    if (!response.token || !response.user) {
      throw new Error('Falha ao concluir redefinição de senha.');
    }

    localStorage.setItem('@InovareTI:token', response.token);
    localStorage.setItem('@InovareTI:user', JSON.stringify(response.user));
    setToken(response.token);
    setUser(response.user);
  }, []);

  // Remove as credenciais e desconecta o usuário
  const signOut = useCallback(() => {
    localStorage.removeItem('@InovareTI:token');
    localStorage.removeItem('@InovareTI:user');
    setToken(null);
    setUser(null);
  }, []);

  return (
    <AuthContext.Provider value={{ user, token, signIn, completeInitialPasswordReset, signOut }}>
      {children}
    </AuthContext.Provider>
  );
}

// Hook para consumir o contexto de autenticação
// eslint-disable-next-line react-refresh/only-export-components
export function useAuth() {
  return useContext(AuthContext);
}
