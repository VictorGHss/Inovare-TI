// Instância do Axios configurada para a API do Inovare TI
import axios from 'axios';

export interface Ticket {
  id: number;
  title: string;
  category: string;
  status: 'OPEN' | 'IN_PROGRESS' | 'RESOLVED' | 'CLOSED';
  createdAt: string;
}

export interface TicketCategory {
  id: number;
  name: string;
}

export interface Item {
  id: number;
  name: string;
}

export interface CreateTicketDto {
  title: string;
  description: string;
  priority: 'LOW' | 'NORMAL' | 'HIGH' | 'URGENT';
  categoryId: number;
  itemId?: number;
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

// Busca todas as categorias de chamado disponíveis
export async function getTicketCategories(): Promise<TicketCategory[]> {
  const { data } = await api.get<TicketCategory[]>('/api/ticket-categories');
  return data;
}

// Busca todos os itens de inventário disponíveis
export async function getItems(): Promise<Item[]> {
  const { data } = await api.get<Item[]>('/api/items');
  return data;
}

// Cria um novo chamado
export async function createTicket(dto: CreateTicketDto): Promise<Ticket> {
  const { data } = await api.post<Ticket>('/api/tickets', dto);
  return data;
}

export default api;
