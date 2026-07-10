import axios from 'axios';
import { toast } from 'react-toastify';
import type { ProblemDetail } from '../lib/apiError';

// Use exatamente o valor fornecido via VITE_API_URL como baseURL.
// A variável de ambiente deve conter o sufixo '/api' (ex: http://localhost:8085/api).
const apiBaseUrl = import.meta.env.VITE_API_URL || '/api';

export function buildApiUrl(path: string): string {
  if (!path) return path;
  if (/^https?:\/\//i.test(path)) return path;

  // concatena preservando uma única barra entre base e path
  if (apiBaseUrl.endsWith('/') && path.startsWith('/')) {
    return `${apiBaseUrl}${path.substring(1)}`;
  }

  if (!apiBaseUrl.endsWith('/') && !path.startsWith('/')) {
    return `${apiBaseUrl}/${path}`;
  }

  return `${apiBaseUrl}${path}`;
}

const api = axios.create({ baseURL: apiBaseUrl });

api.interceptors.request.use((config) => {
  const token = localStorage.getItem('@InovareTI:token');
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

api.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response) {
      const status = error.response.status;
      const data = error.response.data as ProblemDetail | undefined;

      const skipInterceptor = error.config?.headers?.['X-Skip-Interceptor'] === 'true' || 
                              error.config?.headers?.['x-skip-interceptor'] === 'true';

      if ((status === 401 || status === 403) && !skipInterceptor) {
        localStorage.removeItem('@InovareTI:token');
        localStorage.removeItem('@InovareTI:user');
        window.location.href = '/login';
      }

      // Verifica se existem erros de validação granulares associados a campos específicos do formulário
      const temErrosGranulares = Array.isArray(data?.invalidParams) && data.invalidParams.length > 0;

      // Exibe a mensagem de validação contida no campo 'detail' se o erro for 400 ou 409
      // Apenas exibe o toast global como salvaguarda se não existirem erros granulares direcionados aos inputs no ecrã
      if ((status === 400 || status === 409) && data?.detail && !temErrosGranulares) {
        toast.error(data.detail);
      }

      // Adiciona logs de depuração e alertas amigáveis caso o traceId de auditoria esteja presente
      if (data?.traceId) {
        console.warn(`[Auditoria Inovare-TI] Erro na requisição. Trace ID: ${data.traceId}`);
        
        // Em caso de falha inesperada (como erros de servidor 5xx), notifica o utilizador visualmente com o traceId
        if (status >= 500) {
          toast.error(`Falha inesperada no servidor. Por favor, informe o ID de suporte para auditoria: ${data.traceId}`, {
            autoClose: 10000,
          });
        }
      }
    }
    // Garante o retorno da rejeição original da Promise para não quebrar o tratamento local nos blocos try/catch dos componentes
    return Promise.reject(error);
  },
);

export default api;
