// Eventos de agendamento em tempo real recebidos via WebSocket.
export interface AppointmentRealtimeEvent {
  patientName: string;
  doctorName: string;
  status: string;
}
