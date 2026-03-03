// Instância do Axios configurada para a API do Inovare TI
import axios from 'axios';

// Espelha exatamente o TicketResponseDTO retornado pelo backend
export interface Ticket {
  id: string;            // UUID do chamado
  title: string;
  description: string | null;
  anydeskCode: string | null;
  status: 'OPEN' | 'IN_PROGRESS' | 'RESOLVED' | 'CLOSED';
  priority: 'LOW' | 'NORMAL' | 'HIGH' | 'URGENT';
  requesterId: string;
  requesterName: string;
  assignedToId: string | null;
  assignedToName: string | null;
  categoryId: string;
  categoryName: string;
  requestedItemId: string | null;
  requestedItemName: string | null;
  requestedQuantity: number | null;
  slaDeadline: string | null;
  createdAt: string;
  closedAt: string | null;
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
  ticketType: 'INCIDENT' | 'REQUEST';
  itemId?: number;
  quantity?: number;
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
