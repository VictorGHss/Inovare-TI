// Instância do Axios configurada para a API do Inovare TI
import axios from 'axios';

export interface Ticket {
  id: number;
  title: string;
  category: string;
  status: 'OPEN' | 'IN_PROGRESS' | 'RESOLVED' | 'CLOSED';
  createdAt: string;
}

const api = axios.create({
  baseURL: import.meta.env.VITE_API_URL,
});

// Interceptor: injeta o token JWT em todas as requisições autenticadas
api.interceptors.request.use((config) => {
  const token = localStorage.getItem('@InovareTI:token');
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

// Busca todos os tickets do usuário autenticado
export async function getTickets(): Promise<Ticket[]> {
  const { data } = await api.get<Ticket[]>('/api/tickets');
  return data;
}

export default api;
