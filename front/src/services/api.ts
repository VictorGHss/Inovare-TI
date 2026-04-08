import axios from 'axios';

const rawApiBaseUrl = import.meta.env.VITE_API_URL?.trim();

// Normaliza a base removendo sufixo /api para evitar duplicacao quando as rotas ja usam /api/...
const normalizedApiBaseUrl = rawApiBaseUrl
  ? rawApiBaseUrl.replace(/\/+$/, '').replace(/\/api$/, '')
  : undefined;

export function buildApiUrl(path: string): string {
  if (!path) {
    return path;
  }

  if (/^https?:\/\//i.test(path)) {
    return path;
  }

  if (!normalizedApiBaseUrl) {
    return path;
  }

  return `${normalizedApiBaseUrl}${path.startsWith('/') ? path : `/${path}`}`;
}

const api = axios.create({
  baseURL: normalizedApiBaseUrl,
});

api.interceptors.request.use((config) => {
  const token = localStorage.getItem('@InovareTI:token');
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

api.interceptors.response.use(
  (response) => response,
  (error) => Promise.reject(error),
);

export default api;
