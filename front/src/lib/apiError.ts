export interface InvalidParam {
  /**
   * O nome do campo que falhou na validação de dados no ecrã.
   */
  name: string;
  /**
   * A razão pela qual a validação deste campo falhou.
   */
  reason: string;
}

export interface ProblemDetail {
  /**
   * Um URI que identifica o tipo do problema.
   */
  type?: string;
  /**
   * Um resumo curto e legível para o utilizador sobre o problema.
   */
  title?: string;
  /**
   * O código de estado HTTP original.
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
   * Identificador de rastreio (traceId) para fins de auditoria e depuração no servidor.
   */
  traceId?: string;
  /**
   * Lista de parâmetros inválidos para validações granulares de dados no ecrã.
   */
  invalidParams?: InvalidParam[];
  /**
   * Permite capturar outras propriedades dinâmicas adicionais estendidas pelo backend.
   */
  [key: string]: unknown;
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