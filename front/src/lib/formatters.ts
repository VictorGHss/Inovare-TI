/**
 * Formata um número como moeda em BRL (Real Brasileiro)
 * @param value - Valor a ser formatado
 * @returns String formatada como R$ 1.234,56
 */
export function formatCurrency(value: number | null | undefined): string {
  if (value === null || value === undefined) {
    return 'R$ 0,00';
  }

  return new Intl.NumberFormat('pt-BR', {
    style: 'currency',
    currency: 'BRL',
  }).format(value);
}

/**
 * Formata uma data no padrão pt-BR
 * @param date - Data como string ou Date
 * @returns String formatada como "01/01/2024"
 */
export function formatDate(date: string | Date): string {
  const dateObj = typeof date === 'string' ? new Date(date) : date;
  return dateObj.toLocaleDateString('pt-BR');
}

/**
 * Formata uma data e hora no padrão pt-BR
 * @param date - Data como string ou Date
 * @returns String formatada como "01/01/2024 12:34"
 */
export function formatDateTime(date: string | Date): string {
  const dateObj = typeof date === 'string' ? new Date(date) : date;
  return dateObj.toLocaleString('pt-BR');
}
