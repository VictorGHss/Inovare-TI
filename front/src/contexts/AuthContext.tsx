// Contexto de autenticação global da aplicação
import {
  createContext,
  useContext,
  useState,
  useCallback,
  useMemo,
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
  isTwoFactorVerified: boolean;
  signIn: (credentials: SignInCredentials) => Promise<SignInResult>;
  completeInitialPasswordReset: (payload: {
    tempToken: string;
    userId: string;
    newPassword: string;
  }) => Promise<void>;
  updateAuthToken: (nextToken: string, nextUser?: User | null) => void;
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

  const isTwoFactorVerified = useMemo(() => getTwoFactorClaimFromToken(token), [token]);

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

  const updateAuthToken = useCallback((nextToken: string, nextUser?: User | null) => {
    localStorage.setItem('@InovareTI:token', nextToken);
    setToken(nextToken);

    if (nextUser) {
      localStorage.setItem('@InovareTI:user', JSON.stringify(nextUser));
      setUser(nextUser);
    }
  }, []);

  // Remove as credenciais e desconecta o usuário
  const signOut = useCallback(() => {
    localStorage.removeItem('@InovareTI:token');
    localStorage.removeItem('@InovareTI:user');
    setToken(null);
    setUser(null);
  }, []);

  return (
    <AuthContext.Provider
      value={{
        user,
        token,
        isTwoFactorVerified,
        signIn,
        completeInitialPasswordReset,
        updateAuthToken,
        signOut,
      }}
    >
      {children}
    </AuthContext.Provider>
  );
}

// Hook para consumir o contexto de autenticação
// eslint-disable-next-line react-refresh/only-export-components
export function useAuth() {
  return useContext(AuthContext);
}

function getTwoFactorClaimFromToken(token: string | null): boolean {
  if (!token) {
    return false;
  }

  try {
    const parts = token.split('.');
    if (parts.length !== 3) {
      return false;
    }

    const base64Url = parts[1];
    const base64 = base64Url.replace(/-/g, '+').replace(/_/g, '/');
    const payload = JSON.parse(atob(base64)) as { two_factor_verified?: boolean };
    return payload.two_factor_verified === true;
  } catch {
    return false;
  }
}
