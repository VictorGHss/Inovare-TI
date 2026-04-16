export function getApiErrorMessage(error: unknown, fallbackMessage: string): string {
  if (typeof error !== 'object' || error === null) {
    return fallbackMessage;
  }

  const maybeResponse = error as { response?: { data?: unknown } };
  const data = maybeResponse.response?.data;

  if (typeof data === 'string') {
    if (data.includes('<html')) {
      return 'Resposta inesperada do servidor. Verifique o proxy ou o NGINX.';
    }
    return data;
  }

  if (typeof data === 'object' && data !== null) {
    const payload = data as { message?: unknown; detail?: unknown; error?: unknown };
    if (typeof payload.detail === 'string' && payload.detail.trim()) {
      return payload.detail;
    }
    if (typeof payload.message === 'string' && payload.message.trim()) {
      return payload.message;
    }
    if (typeof payload.error === 'string' && payload.error.trim()) {
      return payload.error;
    }
  }

  return fallbackMessage;
}