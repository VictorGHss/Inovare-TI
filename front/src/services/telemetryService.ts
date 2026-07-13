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
      headers: {
        'X-Skip-Interceptor': 'true'
      }
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

  // Expressão regular robusta para o formato plano do Prometheus (compatível com Micrometer/Actuator):
  // - Grupo 1: Nome da métrica (letras, números, sublinhados, dois-pontos)
  // - Grupo 2 (opcional): Conteúdo bruto das labels dentro das chaves { ... }
  // - Grupo 3: Valor numérico da métrica (incluindo inteiros, floats e notação científica como 1.2E7 ou NaN)
  // - Grupo 4 (opcional): Timestamp numérico no final da linha
  const prometheusLineRegex = /^([a-zA-Z_:][a-zA-Z0-9_:]*)(?:\{([^}]+)\})?\s+([0-9.eE+-]+|NaN)(?:\s+\d+)?$/;

  const lines = rawText.split('\n');
  lines.forEach((line) => {
    const trimmed = line.trim();
    // Ignora linhas em branco e comentários (# HELP, # TYPE)
    if (!trimmed || trimmed.startsWith('#')) return;

    const match = trimmed.match(prometheusLineRegex);
    if (!match) return;

    const metricName = match[1];
    const labelsStr = match[2] || '';
    const valueStr = match[3];
    const value = parseFloat(valueStr);
    if (isNaN(value)) return;

    // Métricas de tráfego de rede: network_traffic_bytes_total_total{device="mikrotik",direction="rx"} 12345
    // Com o uso do startsWith, cobrimos tanto o nome base quanto com o sufixo _total gerado pelo Micrometer
    if (metricName.startsWith('network_traffic_bytes_total')) {
      if (labelsStr.includes('device="mikrotik"') && labelsStr.includes('direction="rx"')) {
        mikrotikRx = value;
      } else if (labelsStr.includes('device="mikrotik"') && labelsStr.includes('direction="tx"')) {
        mikrotikTx = value;
      } else if (labelsStr.includes('device="ubiquiti"') && labelsStr.includes('direction="rx"')) {
        ubiquitiRx = value;
      } else if (labelsStr.includes('device="ubiquiti"') && labelsStr.includes('direction="tx"')) {
        ubiquitiTx = value;
      }
    }

    // Métricas de falhas Blip: blip_delivery_failures_total_total{category="XXX"} 42
    // Igualmente compatível com a métrica nativa e o formato do Micrometer com sufixo
    if (metricName.startsWith('blip_delivery_failures_total')) {
      blipFailuresTotal += value;
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
