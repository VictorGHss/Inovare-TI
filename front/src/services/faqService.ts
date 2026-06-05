import api from './api';
import type { FaqTi } from '../types/models';

/**
 * Busca todos os FAQs cadastrados da TI.
 */
export async function getFaqs(): Promise<FaqTi[]> {
  const { data } = await api.get<FaqTi[]>('/v1/admin/faqs');
  return data;
}

/**
 * Cria um novo FAQ da TI.
 */
export async function createFaq(faq: FaqTi): Promise<FaqTi> {
  const { data } = await api.post<FaqTi>('/v1/admin/faqs', faq);
  return data;
}

/**
 * Remove um FAQ da TI pelo ID ou palavra-chave.
 */
export async function deleteFaq(id: string): Promise<void> {
  await api.delete(`/v1/admin/faqs/${id}`);
}

/**
 * Atualiza um FAQ da TI pelo ID.
 */
export async function updateFaq(id: string, faq: FaqTi): Promise<FaqTi> {
  const { data } = await api.put<FaqTi>(`/v1/admin/faqs/${id}`, faq);
  return data;
}
