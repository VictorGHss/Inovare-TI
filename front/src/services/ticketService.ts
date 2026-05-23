import api from './api';
import type { CreateTicketDto, ResolveTicketRequest, Ticket, TicketAttachment, TicketCategory, TicketComment } from '../types/models';

// Busca todos os tickets do usuário autenticado
export async function getTickets(): Promise<Ticket[]> {
  const { data } = await api.get<Ticket[]>('/tickets');
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

