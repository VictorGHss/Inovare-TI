import api from './api';
import type { Page, CreateTicketDto, ResolveTicketRequest, Ticket, TicketAttachment, TicketCategory, TicketComment, TicketTag } from '../types/models';

// Busca todos os tickets do usuário autenticado (suporta filtro opcional por tags e paginação)
export async function getTickets(tagIds?: string[], page: number = 0): Promise<Page<Ticket>> {
  const params = new URLSearchParams();
  if (tagIds && tagIds.length > 0) {
    tagIds.forEach(id => params.append('tagIds', id));
  }
  params.append('page', String(page));
  const { data } = await api.get<Page<Ticket>>('/tickets', { params });
  return data;
}

// Busca todas as categorias de chamado disponíveis
export async function getTicketCategories(): Promise<TicketCategory[]> {
  const { data } = await api.get<TicketCategory[]>('/ticket-categories');
  return data;
}

// Cria um novo chamado
export async function createTicket(dto: CreateTicketDto): Promise<Ticket> {
  const { data } = await api.post<Ticket>('/tickets', dto);
  return data;
}

// Busca um chamado específico pelo UUID
export async function getTicketById(id: string): Promise<Ticket> {
  const { data } = await api.get<Ticket>(`/tickets/${id}`);
  return data;
}

// Resolve um chamado existente pelo UUID
export async function resolveTicket(id: string, request: ResolveTicketRequest): Promise<Ticket> {
  const { data } = await api.patch<Ticket>(`/tickets/${id}/resolve`, request);
  return data;
}

// Assume um chamado para o usuário autenticado e muda para IN_PROGRESS
export async function claimTicket(id: string): Promise<Ticket> {
  const { data } = await api.patch<Ticket>(`/tickets/${id}/claim`);
  return data;
}

// Transfere um chamado para outro usuário
export async function transferTicket(id: string, userId: string): Promise<Ticket> {
  const { data } = await api.patch<Ticket>(`/tickets/${id}/transfer/${userId}`);
  return data;
}

// Altera a categoria de um chamado e recalcula o SLA
export async function changeTicketCategory(id: string, categoryId: string): Promise<Ticket> {
  const { data } = await api.patch<Ticket>(`/tickets/${id}/category/${categoryId}`);
  return data;
}

// Adiciona um colaborador afetado ao chamado
export async function addAdditionalUser(id: string, userId: string): Promise<Ticket> {
  const { data } = await api.post<Ticket>(`/tickets/${id}/additional-users/${userId}`);
  return data;
}

// Faz upload de um anexo para um chamado específico
export async function uploadTicketAttachment(ticketId: string, file: File): Promise<TicketAttachment> {
  const formData = new FormData();
  formData.append('file', file);
  
  const { data } = await api.post<TicketAttachment>(`/tickets/${ticketId}/attachments`, formData, {
    headers: {
      'Content-Type': 'multipart/form-data',
    },
  });
  return data;
}

// Lista todos os anexos de um chamado específico
export async function getTicketAttachments(ticketId: string): Promise<TicketAttachment[]> {
  const { data } = await api.get<TicketAttachment[]>(`/tickets/${ticketId}/attachments`);
  return data;
}

// Adiciona um novo comentário a um chamado
export async function addTicketComment(ticketId: string, content: string): Promise<TicketComment> {
  const { data } = await api.post<TicketComment>(`/tickets/${ticketId}/comments`, { content });
  return data;
}

// Lista todos os comentários de um chamado
export async function getTicketComments(ticketId: string): Promise<TicketComment[]> {
  const { data } = await api.get<TicketComment[]>(`/tickets/${ticketId}/comments`);
  return data;
}

// Associa um chamado a outro bidirecionalmente
export async function relateTicket(id: string, relatedId: string): Promise<void> {
  await api.post(`/tickets/${id}/relate/${relatedId}`);
}

// Atualiza a descrição de solução de um chamado
export async function updateTicketSolution(id: string, solutionText: string): Promise<Ticket> {
  const { data } = await api.patch<Ticket>(`/tickets/${id}/solution`, { solutionText });
  return data;
}

// Busca todos os tags cadastrados
export async function getTicketTags(activeOnly?: boolean): Promise<TicketTag[]> {
  const { data } = await api.get<TicketTag[]>('/ticket-tags', {
    params: activeOnly !== undefined ? { activeOnly } : undefined,
  });
  return data;
}

// Cria uma nova tag
export async function createTicketTag(tag: { name: string; color: string; active?: boolean; defaultResolution?: string | null }): Promise<TicketTag> {
  const { data } = await api.post<TicketTag>('/ticket-tags', tag);
  return data;
}

// Atualiza uma tag existente
export async function updateTicketTag(id: string, tag: { name: string; color: string; active?: boolean; defaultResolution?: string | null }): Promise<TicketTag> {
  const { data } = await api.put<TicketTag>(`/ticket-tags/${id}`, tag);
  return data;
}

// Alterna o estado ativo/inativo da tag (soft-delete)
export async function toggleTicketTagActive(id: string): Promise<TicketTag> {
  const { data } = await api.patch<TicketTag>(`/ticket-tags/${id}/toggle-active`);
  return data;
}

// Remove fisicamente uma tag
export async function deleteTicketTag(id: string): Promise<void> {
  await api.delete(`/ticket-tags/${id}`);
}

// Busca chamados similares resolvidos/fechados
export async function getSimilarTickets(id: string): Promise<Ticket[]> {
  const { data } = await api.get<Ticket[]>(`/tickets/${id}/similar`);
  return data;
}



