import axios from 'axios';

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
  (error) => Promise.reject(error),
);

export default api;
