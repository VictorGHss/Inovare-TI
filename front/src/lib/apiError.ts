export interface ProblemDetail {
  /**
   * Um URI que identifica o tipo do problema.
   */
  type?: string;
  /**
   * Um resumo curto e legível para humanos sobre o problema.
   */
  title?: string;
  /**
   * O código de status HTTP original.
   */
  status?: number;
  /**
   * Uma explicação detalhada sobre a ocorrência deste erro.
   */
  detail?: string;
  /**
   * Um URI que identifica a ocorrência específica do problema.
   */
  instance?: string;
  /**
   * Identificador de rastreamento (traceId) para fins de auditoria e depuração.
   */
  traceId?: string;
  /**
   * Permite capturar outras propriedades dinâmicas adicionais estendidas pelo backend.
   */
  [key: string]: any;
}

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
    // Faz o cast para ProblemDetail combinado com outras propriedades comuns de erros legados
    const payload = data as ProblemDetail & { message?: unknown; error?: unknown };
    
    if (typeof payload.detail === 'string' && payload.detail.trim()) {
      return payload.detail;
    }
    if (typeof payload.title === 'string' && payload.title.trim()) {
      return payload.title;
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