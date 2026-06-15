// Eventos de agendamento em tempo real recebidos via WebSocket.
export interface AppointmentRealtimeEvent {
  patientName: string;
  doctorName: string;
  status: string;
}

/**
 * Interface que representa uma falha de entrega de notificação/mensagem da Blip.
 */
export interface BlipDeliveryFailure {
  id: string;
  messageId: string;
  appointmentId: string;
  errorCode: number;
  errorMessage: string;
  traceId: string;
  createdAt: string;
}

/**
 * Interface para representar respostas paginadas vindas do backend Spring.
 */
export interface PaginatedResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
  first: boolean;
  last: boolean;
}

