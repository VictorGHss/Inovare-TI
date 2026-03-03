// Instância do Axios configurada para a API do Inovare TI
import axios from 'axios';

// Attachment in ticket response
export interface AttachmentResponse {
  id: string;
  originalFilename: string;
  fileUrl: string;
  fileType: string;
}

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
  attachments: AttachmentResponse[];
}

export interface TicketCategory {
  // UUID retornado pelo backend como string
  id: string;
  name: string;
}

export interface Item {
  // UUID retornado pelo backend como string
  id: string;
  name: string;
  itemCategoryId: string;
  itemCategoryName: string;
  currentStock: number;
  specifications: Record<string, unknown> | null;
}

export interface ItemCategory {
  id: string;
  name: string;
  isConsumable: boolean;
}

export interface CreateItemDto {
  itemCategoryId: string;
  name: string;
  specifications?: Record<string, unknown>;
}

export interface CreateBatchDto {
  quantity: number;
  unitPrice: number;
}

export interface Batch {
  id: string;
  originalQuantity: number;
  remainingQuantity: number;
  unitPrice: number;
  entryDate: string; // ISO string
}

export interface User {
  id: string;
  name: string;
  email: string;
  role: 'ADMIN' | 'TECHNICIAN' | 'USER';
  sectorId: string;
  sectorName: string;
  location: string;
  discordUserId: string | null;
}

export interface Sector {
  id: string;
  name: string;
}

export interface TicketAttachment {
  id: string;
  originalFilename: string;
  storedFilename: string;
  fileType: string;
  ticketId: string;
  uploadedAt: string;
}

export interface CreateUserDto {
  name: string;
  email: string;
  password: string;
  role: 'ADMIN' | 'TECHNICIAN' | 'USER';
  sectorId: string;
  location?: string;
  discordUserId?: string;
}

export interface CreateSectorDto {
  name: string;
}

export interface CreateTicketDto {
  title: string;
  description: string;
  priority: 'LOW' | 'NORMAL' | 'HIGH' | 'URGENT';
  // UUID da categoria (string) — alinhado com TicketRequestDTO do backend
  categoryId: string;
  // Campos preenchidos apenas para chamados do tipo REQUEST
  requestedItemId?: string;
  requestedQuantity?: number;
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

// Busca um item de inventário específico pelo UUID
export async function getItemById(id: string): Promise<Item> {
  const { data } = await api.get<Item>(`/api/items/${id}`);
  return data;
}

// Busca todos os lotes de estoque de um item específico
export async function getItemBatches(id: string): Promise<Batch[]> {
  const { data } = await api.get<Batch[]>(`/api/items/${id}/batches`);
  return data;
}

// Busca todas as categorias de item de inventário
export async function getItemCategories(): Promise<ItemCategory[]> {
  const { data } = await api.get<ItemCategory[]>('/api/item-categories');
  return data;
}

// Cria um novo item de inventário
export async function createItem(dto: CreateItemDto): Promise<Item> {
  const { data } = await api.post<Item>('/api/items', dto);
  return data;
}

// Registra um lote de entrada de estoque para um item
export async function addBatch(itemId: string, dto: CreateBatchDto): Promise<void> {
  await api.post(`/api/items/${itemId}/batches`, dto);
}

// Cria um novo chamado
export async function createTicket(dto: CreateTicketDto): Promise<Ticket> {
  const { data } = await api.post<Ticket>('/api/tickets', dto);
  return data;
}

// Busca um chamado específico pelo UUID
export async function getTicketById(id: string): Promise<Ticket> {
  const { data } = await api.get<Ticket>(`/api/tickets/${id}`);
  return data;
}

// Fecha um chamado existente pelo UUID
export async function closeTicket(id: string): Promise<Ticket> {
  const { data } = await api.patch<Ticket>(`/api/tickets/${id}/close`);
  return data;
}

// Busca todos os usuários cadastrados (requer ADMIN)
export async function getUsers(): Promise<User[]> {
  const { data } = await api.get<User[]>('/api/users');
  return data;
}

// Cria um novo usuário (requer ADMIN)
export async function createUser(dto: CreateUserDto): Promise<User> {
  const { data } = await api.post<User>('/api/users', dto);
  return data;
}

// Busca todos os setores cadastrados (requer ADMIN)
export async function getSectors(): Promise<Sector[]> {
  const { data } = await api.get<Sector[]>('/api/sectors');
  return data;
}

// Cria um novo setor (requer ADMIN)
export async function createSector(dto: CreateSectorDto): Promise<Sector> {
  const { data } = await api.post<Sector>('/api/sectors', dto);
  return data;
}

// Faz upload de um anexo para um chamado específico
export async function uploadTicketAttachment(ticketId: string, file: File): Promise<TicketAttachment> {
  const formData = new FormData();
  formData.append('file', file);
  
  const { data } = await api.post<TicketAttachment>(`/api/tickets/${ticketId}/attachments`, formData, {
    headers: {
      'Content-Type': 'multipart/form-data',
    },
  });
  return data;
}

// Lista todos os anexos de um chamado específico
export async function getTicketAttachments(ticketId: string): Promise<TicketAttachment[]> {
  const { data } = await api.get<TicketAttachment[]>(`/api/tickets/${ticketId}/attachments`);
  return data;
}

export default api;
