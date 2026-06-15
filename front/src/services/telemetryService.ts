import api from './api';

export interface TelemetryData {
  mikrotikRx: number;
  mikrotikTx: number;
  ubiquitiRx: number;
  ubiquitiTx: number;
  blipFailuresTotal: number;
}

/**
 * Busca os contadores macros de telemetria lendo o endpoint /actuator/prometheus do backend.
 * Filtra e extrai os valores específicos de tráfego de rede e falhas de entrega do Blip.
 */
export async function getMacroTelemetry(): Promise<TelemetryData> {
  try {
    const { data } = await api.get('/actuator/prometheus', {
      responseType: 'text',
      // Remove o prefixo da URL base do Axios se necessário, acessando diretamente o actuator exposto
      baseURL: api.defaults.baseURL?.replace('/v1', '')?.replace('/api', '') || '',
    });

    const parsed = parsePrometheusMetrics(data);
    return parsed;
  } catch (error) {
    console.error('Erro ao buscar telemetria do Prometheus:', error);
    return {
      mikrotikRx: 0,
      mikrotikTx: 0,
      ubiquitiRx: 0,
      ubiquitiTx: 0,
      blipFailuresTotal: 0,
    };
  }
}

/**
 * Processa a resposta em formato plano do Prometheus para extrair contadores específicos de telemetria.
 */
function parsePrometheusMetrics(rawText: string): TelemetryData {
  let mikrotikRx = 0;
  let mikrotikTx = 0;
  let ubiquitiRx = 0;
  let ubiquitiTx = 0;
  let blipFailuresTotal = 0;

  if (!rawText) return { mikrotikRx, mikrotikTx, ubiquitiRx, ubiquitiTx, blipFailuresTotal };

  const lines = rawText.split('\n');
  lines.forEach((line) => {
    // Ignora comentários do Prometheus (# HELP, # TYPE)
    if (line.startsWith('#')) return;

    // Métricas de tráfego de rede: network_traffic_bytes_total{device="mikrotik",direction="rx"} 12345
    if (line.startsWith('network_traffic_bytes_total')) {
      const value = extractMetricValue(line);
      if (line.includes('device="mikrotik"') && line.includes('direction="rx"')) {
        mikrotikRx = value;
      } else if (line.includes('device="mikrotik"') && line.includes('direction="tx"')) {
        mikrotikTx = value;
      } else if (line.includes('device="ubiquiti"') && line.includes('direction="rx"')) {
        ubiquitiRx = value;
      } else if (line.includes('device="ubiquiti"') && line.includes('direction="tx"')) {
        ubiquitiTx = value;
      }
    }

    // Métricas de falhas Blip: blip_delivery_failures_total{category="XXX"} 42
    if (line.startsWith('blip_delivery_failures_total')) {
      blipFailuresTotal += extractMetricValue(line);
    }
  });

  return {
    mikrotikRx,
    mikrotikTx,
    ubiquitiRx,
    ubiquitiTx,
    blipFailuresTotal,
  };
}

/**
 * Extrai o valor numérico final de uma linha de métrica do Prometheus.
 */
function extractMetricValue(line: string): number {
  const parts = line.trim().split(/\s+/);
  if (parts.length < 2) return 0;
  const numStr = parts[parts.length - 1];
  const value = parseFloat(numStr);
  return isNaN(value) ? 0 : value;
}
