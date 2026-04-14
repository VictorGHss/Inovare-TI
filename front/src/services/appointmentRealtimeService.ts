import { Client } from '@stomp/stompjs';
import SockJS from 'sockjs-client';

import type { AppointmentRealtimeEvent } from '../types/models';

/**
 * Conecta no canal de eventos de agendamento usando SockJS + STOMP.
 */
export function connectAppointmentEvents(
  onEvent: (payload: AppointmentRealtimeEvent) => void,
): () => void {
  const socketBaseUrl = getSocketBaseUrl();
  const endpoint = `${socketBaseUrl}/api/ws/appointment-events`;

  const client = new Client({
    reconnectDelay: 5000,
    webSocketFactory: () => new SockJS(endpoint),
    onConnect: () => {
      client.subscribe('/topic/appointment-events', (message) => {
        if (!message.body) {
          return;
        }

        try {
          const payload = JSON.parse(message.body) as AppointmentRealtimeEvent;
          onEvent(payload);
        } catch {
          // Ignora payloads inválidos sem derrubar o listener.
        }
      });
    },
  });

  client.activate();

  return () => {
    client.deactivate();
  };
}

function getSocketBaseUrl(): string {
  const apiBase = import.meta.env.VITE_API_URL?.trim();
  if (apiBase) {
    return apiBase.replace(/\/+$/, '').replace(/\/api$/, '');
  }

  return window.location.origin;
}
