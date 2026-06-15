import { useEffect, useState, useCallback, useTransition } from 'react';
import { Search, Copy, Check, Eye, AlertCircle, RefreshCw, ChevronLeft, ChevronRight, X } from 'lucide-react';
import { toast } from 'react-toastify';
import { motion, AnimatePresence } from 'framer-motion';

import { getBlipDeliveryFailures } from '../../services/appointmentService';
import type { BlipDeliveryFailure } from '../../types/models';

/**
 * Tradução e classificação amigável das categorias em Português do Brasil (PT-BR).
 * Utilizado para exibir labels elegantes na interface ao invés das constantes puras do backend.
 */
const categoryFriendlyNames: Record<string, string> = {
  ERRO_INTERNO_OU_GATEWAY: 'Erro Interno ou Gateway',
  RATE_LIMIT_EXCEDIDO: 'Rate Limit Excedido',
  PARAMETRO_INVALIDO_TEMPLATE: 'Parâmetro de Template Inválido',
  DESTINATARIO_INVALIDO_WHATSAPP: 'Destinatário Inválido (WhatsApp)',
  CONFLITO_ATENDIMENTO_ATIVO: 'Conflito de Atendimento Ativo',
  EXPERIMENTO_META_BLOQUEADO: 'Campanha de Disparo Bloqueada',
  CONTA_BUSINESS_BLOQUEADA: 'Conta Business Bloqueada (WABA)',
  MEDIA_OU_TIPO_INCOMPATIVEL: 'Mídia ou Tipo Incompatível',
  FALHA_DESCONHECIDA: 'Outros / Falha Desconhecida'
};

/**
 * Mapeador de estilos CSS de severidade baseados na categoria do erro da Meta/Blip.
 * 
 * @param category A categoria textual do erro.
 * @returns Classes TailwindCSS correspondentes ao design system do projeto.
 */
const getCategoryBadgeStyle = (category: string) => {
  switch (category) {
    case 'CONTA_BUSINESS_BLOQUEADA':
      // Destaque crítico, vermelho com efeito pulsante (animate-pulse) para alertar imediatamente o operador do sistema.
      return 'bg-red-100 text-red-800 border border-red-200 animate-pulse font-semibold';
    case 'ERRO_INTERNO_OU_GATEWAY':
    case 'EXPERIMENTO_META_BLOQUEADO':
      return 'bg-rose-50 text-rose-700 border border-rose-200 font-medium';
    case 'RATE_LIMIT_EXCEDIDO':
    case 'PARAMETRO_INVALIDO_TEMPLATE':
      return 'bg-amber-50 text-amber-700 border border-amber-200 font-medium';
    case 'DESTINATARIO_INVALIDO_WHATSAPP':
    case 'CONFLITO_ATENDIMENTO_ATIVO':
    case 'MEDIA_OU_TIPO_INCOMPATIVEL':
      return 'bg-blue-50 text-blue-700 border border-blue-200 font-medium';
    default:
      return 'bg-slate-50 text-slate-700 border border-slate-200 font-medium';
  }
};

/**
 * Painel de auditoria que lista as falhas de entrega de mensagens do Blip de forma paginada.
 * Oferece suporte a filtros em tempo real por agendamento Feegow (appointmentId) e categoria do erro.
 */
export default function BlipDeliveryFailuresPanel() {
  // Estados para dados das falhas obtidas da API
  const [failures, setFailures] = useState<BlipDeliveryFailure[]>([]);
  const [loading, setLoading] = useState(true);
  const [copiedId, setCopiedId] = useState<string | null>(null);
  
  // Estados para gerenciar a paginação
  const [currentPage, setCurrentPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [totalElements, setTotalElements] = useState(0);
  
  // Estados para os filtros
  const [appointmentIdInput, setAppointmentIdInput] = useState('');
  const [selectedCategory, setSelectedCategory] = useState('');
  
  // Estado que gerencia a falha atualmente selecionada para exibição detalhada no Modal
  const [selectedFailure, setSelectedFailure] = useState<BlipDeliveryFailure | null>(null);

  // Hook useTransition do React 18 para permitir filtragem suave sem engasgos na renderização principal
  const [isPending, startTransition] = useTransition();

  /**
   * Hook callback para carregar os dados de falhas de entrega do backend de forma segura.
   * Executa a busca paginada respeitando os filtros ativos.
   */
  const loadFailures = useCallback(async (page: number, apptId: string, cat: string) => {
    try {
      setLoading(true);
      const data = await getBlipDeliveryFailures({
        page,
        size: 15, // Tamanho de paginação fixa em 15 itens
        appointmentId: apptId.trim() || undefined,
        category: cat || undefined
      });
      
      setFailures(data.content || []);
      setTotalPages(data.totalPages || 0);
      setTotalElements(data.totalElements || 0);
    } catch (error) {
      console.error('Erro ao carregar falhas de entrega do Blip:', error);
      toast.error('Não foi possível obter o histórico de falhas de entrega do Blip.');
    } finally {
      setLoading(false);
    }
  }, []);

  /**
   * Hook useEffect para disparar a recarga das falhas sempre que a página ou filtros forem alterados.
   * Utiliza startTransition para otimizar o fluxo de atualização visual.
   */
  useEffect(() => {
    startTransition(() => {
      void loadFailures(currentPage, appointmentIdInput, selectedCategory);
    });
  }, [currentPage, appointmentIdInput, selectedCategory, loadFailures]);

  /**
   * Copia o texto fornecido para a área de transferência do sistema e exibe um feedback visual temporário.
   * 
   * @param text O valor/identificador a ser copiado.
   * @param type Tipo de identificador (trace ou message) para a notificação de toast.
   */
  const handleCopyToClipboard = (text: string, type: 'trace' | 'message') => {
    void navigator.clipboard.writeText(text);
    setCopiedId(text);
    toast.success(`${type === 'trace' ? 'Trace ID' : 'ID da Mensagem'} copiado para a área de transferência!`);
    setTimeout(() => setCopiedId(null), 2000);
  };

  /**
   * Helper de formatação de strings de data ISO para o formato local pt-BR.
   * 
   * @param isoString String de data e hora do banco de dados.
   * @returns String amigável formatada.
   */
  const formatDateTime = (isoString: string) => {
    if (!isoString) return '-';
    try {
      const date = new Date(isoString);
      return date.toLocaleString('pt-BR');
    } catch {
      return isoString;
    }
  };

  /**
   * Reseta as condições dos filtros e retorna a paginação para o início.
   */
  const handleResetFilters = () => {
    setAppointmentIdInput('');
    setSelectedCategory('');
    setCurrentPage(0);
  };

  /**
   * Função auxiliar para traduzir o código de erro em sua correspondente categoria
   * textual que guiará a cor e o rótulo do badge.
   * 
   * @param code O código numérico de erro retornado pela Meta/Blip.
   * @returns String identificadora da categoria de falha.
   */
  const classifyErrorCode = (code: number): string => {
    if (!code) return 'FALHA_DESCONHECIDA';
    if (code === 1 || code === 2 || code === 51 || code === 81 || code === 86 || code === 1601) {
      return 'ERRO_INTERNO_OU_GATEWAY';
    }
    if (code === 38 || code === 429) {
      return 'RATE_LIMIT_EXCEDIDO';
    }
    if (code === 100) {
      return 'PARAMETRO_INVALIDO_TEMPLATE';
    }
    if (code === 1505 || code === 131026) {
      return 'DESTINATARIO_INVALIDO_WHATSAPP';
    }
    if (code === 1602) {
      return 'CONFLITO_ATENDIMENTO_ATIVO';
    }
    if (code === 130472) {
      return 'EXPERIMENTO_META_BLOQUEADO';
    }
    if (code === 131031) {
      return 'CONTA_BUSINESS_BLOQUEADA';
    }
    if (code === 131051 || code === 131052 || code === 131053) {
      return 'MEDIA_OU_TIPO_INCOMPATIVEL';
    }
    return 'FALHA_DESCONHECIDA';
  };

  /**
   * Renderização principal do componente do Painel.
   * Desenha a estrutura de cabeçalho, os filtros de pesquisa superior, a tabela de dados paginados
   * e o modal de visualização técnica de detalhes da falha.
   */
  return (
    <section className="w-full rounded-2xl border border-slate-200 bg-white shadow-sm overflow-hidden mt-6">
      {/* Cabeçalho do Painel */}
      <header className="border-b border-slate-100 px-6 py-5 flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
        <div>
          <h2 className="text-base font-bold text-slate-900">Histórico de Falhas de Entrega (Blip)</h2>
          <p className="mt-0.5 text-xs text-slate-500">Monitore erros de envio de notificações, saldo da conta Meta e inconsistências de templates.</p>
        </div>
        <button
          type="button"
          onClick={() => void loadFailures(currentPage, appointmentIdInput, selectedCategory)}
          disabled={loading || isPending}
          className="inline-flex items-center gap-1.5 self-start sm:self-center px-3.5 py-2 rounded-xl text-xs font-semibold text-slate-600 bg-slate-50 hover:bg-slate-100 border border-slate-200 transition-colors disabled:opacity-50"
        >
          <RefreshCw size={14} className={loading || isPending ? 'animate-spin' : ''} />
          Atualizar
        </button>
      </header>

      {/* Seção de Filtros */}
      <div className="p-6 bg-slate-50/50 border-b border-slate-100 flex flex-col md:flex-row items-center gap-4">
        {/* Filtro de Busca por ID do Agendamento */}
        <div className="w-full md:w-1/3 relative">
          <label htmlFor="appointment-search" className="sr-only">Filtrar por ID do Agendamento</label>
          <Search className="absolute left-3.5 top-3 text-slate-400 h-4 w-4" />
          <input
            id="appointment-search"
            type="text"
            placeholder="Buscar por ID do Agendamento..."
            value={appointmentIdInput}
            onChange={(e) => {
              setAppointmentIdInput(e.target.value);
              setCurrentPage(0); // Reseta a página para a primeira
            }}
            className="w-full pl-10 pr-3.5 py-2.5 rounded-xl border border-slate-200 bg-white text-sm text-slate-800 placeholder-slate-400 focus:outline-none focus:ring-2 focus:ring-[#feb56c]/20 focus:border-[#feb56c] transition-all shadow-sm"
          />
        </div>

        {/* Filtro Dropdown de Categorias de Erro */}
        <div className="w-full md:w-1/3">
          <label htmlFor="category-filter" className="sr-only">Filtrar por Categoria do Erro</label>
          <select
            id="category-filter"
            value={selectedCategory}
            onChange={(e) => {
              setSelectedCategory(e.target.value);
              setCurrentPage(0); // Reseta a página para a primeira
            }}
            className="w-full px-3.5 py-2.5 rounded-xl border border-slate-200 bg-white text-sm text-slate-700 focus:outline-none focus:ring-2 focus:ring-[#feb56c]/20 focus:border-[#feb56c] transition-all shadow-sm"
          >
            <option value="">Todas as Categorias</option>
            {Object.entries(categoryFriendlyNames).map(([key, label]) => (
              <option key={key} value={key}>{label}</option>
            ))}
          </select>
        </div>

        {/* Ação rápida para limpar filtros ativos */}
        {(appointmentIdInput || selectedCategory) && (
          <button
            type="button"
            onClick={handleResetFilters}
            className="text-xs font-semibold text-[#feb56c] hover:text-[#e5944c] hover:underline transition-colors"
          >
            Limpar Filtros
          </button>
        )}
      </div>

      {/* Tabela de Falhas de Entrega */}
      <div className="overflow-x-auto relative min-h-[300px]">
        {/* Overlay de carregamento animado com desfoque de fundo */}
        {(loading || isPending) && (
          <div className="absolute inset-0 bg-white/70 backdrop-blur-[1px] flex items-center justify-center z-10 transition-all">
            <div className="flex flex-col items-center gap-2">
              <RefreshCw size={24} className="animate-spin text-[#feb56c]" />
              <span className="text-xs font-medium text-slate-500">Consultando base de auditoria...</span>
            </div>
          </div>
        )}

        <table className="min-w-full text-sm">
          <thead>
            <tr className="border-b border-slate-200 bg-slate-50">
              <th className="px-6 py-3.5 text-left text-xs font-semibold uppercase tracking-wider text-slate-500">Data e Hora</th>
              <th className="px-6 py-3.5 text-left text-xs font-semibold uppercase tracking-wider text-slate-500">Agendamento (Feegow)</th>
              <th className="px-6 py-3.5 text-left text-xs font-semibold uppercase tracking-wider text-slate-500">Categoria de Erro</th>
              <th className="px-6 py-3.5 text-left text-xs font-semibold uppercase tracking-wider text-slate-500">Código</th>
              <th className="px-6 py-3.5 text-left text-xs font-semibold uppercase tracking-wider text-slate-500">Trace ID</th>
              <th className="px-6 py-3.5 text-center text-xs font-semibold uppercase tracking-wider text-slate-500">Ações</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-slate-100">
            {failures.length === 0 ? (
              <tr>
                <td colSpan={6} className="px-6 py-16 text-center">
                  <div className="flex flex-col items-center justify-center gap-3">
                    <AlertCircle size={32} className="text-slate-300" />
                    <div>
                      <p className="text-sm font-semibold text-slate-700">Nenhuma falha de entrega registrada</p>
                      <p className="text-xs text-slate-400 mt-1">Experimente alterar os critérios de filtro.</p>
                    </div>
                  </div>
                </td>
              </tr>
            ) : (
              failures.map((item) => {
                const categoryTag = classifyErrorCode(item.errorCode || 0);
                return (
                  <tr key={item.id} className="hover:bg-slate-50/50 transition-colors">
                    <td className="px-6 py-4 whitespace-nowrap text-xs text-slate-600">
                      {formatDateTime(item.createdAt)}
                    </td>
                    <td className="px-6 py-4 whitespace-nowrap text-sm font-semibold text-slate-800">
                      {item.appointmentId || <span className="text-slate-400 font-normal">N/A</span>}
                    </td>
                    <td className="px-6 py-4 whitespace-nowrap">
                      {/* Badge formatado e colorido conforme severidade e categoria de erro */}
                      <span className={`inline-flex items-center px-2.5 py-1 rounded-full text-xs font-semibold border ${getCategoryBadgeStyle(categoryTag)}`}>
                        {categoryFriendlyNames[categoryTag] || 'Desconhecido'}
                      </span>
                    </td>
                    <td className="px-6 py-4 whitespace-nowrap text-sm font-mono text-slate-700">
                      {item.errorCode ?? '-'}
                    </td>
                    <td className="px-6 py-4 whitespace-nowrap text-xs text-slate-500">
                      {item.traceId ? (
                        <div className="flex items-center gap-1.5">
                          <span className="font-mono">{item.traceId.slice(0, 8)}...</span>
                          <button
                            type="button"
                            onClick={() => handleCopyToClipboard(item.traceId, 'trace')}
                            className="p-1 text-slate-400 hover:text-[#feb56c] hover:bg-slate-100 rounded-lg transition-colors"
                            title="Copiar Trace ID completo"
                          >
                            {copiedId === item.traceId ? <Check size={12} className="text-emerald-500" /> : <Copy size={12} />}
                          </button>
                        </div>
                      ) : '-'}
                    </td>
                    <td className="px-6 py-4 whitespace-nowrap text-center">
                      <button
                        type="button"
                        onClick={() => setSelectedFailure(item)}
                        className="inline-flex items-center gap-1 text-xs font-semibold text-slate-600 hover:text-[#feb56c] px-2.5 py-1.5 rounded-lg hover:bg-slate-100 transition-colors"
                      >
                        <Eye size={13} />
                        Detalhes
                      </button>
                    </td>
                  </tr>
                );
              })
            )}
          </tbody>
        </table>
      </div>

      {/* Rodapé e Navegação da Paginação */}
      {totalPages > 1 && (
        <footer className="border-t border-slate-100 px-6 py-4 flex items-center justify-between bg-slate-50/50">
          <span className="text-xs text-slate-500">
            Página <strong className="text-slate-800">{currentPage + 1}</strong> de <strong className="text-slate-800">{totalPages}</strong> (Exibindo {failures.length} de {totalElements} falhas)
          </span>
          <div className="flex items-center gap-1.5">
            <button
              type="button"
              disabled={currentPage === 0 || loading || isPending}
              onClick={() => setCurrentPage(prev => Math.max(0, prev - 1))}
              className="p-1.5 rounded-lg border border-slate-200 bg-white text-slate-600 hover:bg-slate-50 transition-colors disabled:opacity-50 disabled:cursor-not-allowed"
            >
              <ChevronLeft size={16} />
            </button>
            <button
              type="button"
              disabled={currentPage >= totalPages - 1 || loading || isPending}
              onClick={() => setCurrentPage(prev => Math.min(totalPages - 1, prev + 1))}
              className="p-1.5 rounded-lg border border-slate-200 bg-white text-slate-600 hover:bg-slate-50 transition-colors disabled:opacity-50 disabled:cursor-not-allowed"
            >
              <ChevronRight size={16} />
            </button>
          </div>
        </footer>
      )}

      {/* Modal de Detalhes da Auditoria Técnico-Operacional (Framer Motion) */}
      <AnimatePresence>
        {selectedFailure && (
          <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-slate-900/60 backdrop-blur-sm">
            <motion.div
              initial={{ opacity: 0, scale: 0.95 }}
              animate={{ opacity: 1, scale: 1 }}
              exit={{ opacity: 0, scale: 0.95 }}
              className="w-full max-w-xl bg-white rounded-2xl shadow-xl overflow-hidden"
            >
              {/* Cabeçalho do Modal */}
              <header className="border-b border-slate-100 px-6 py-5 flex items-center justify-between bg-slate-50">
                <div>
                  <h3 className="text-base font-bold text-slate-900">Detalhes da Falha de Entrega</h3>
                  <p className="mt-0.5 text-xs text-slate-500">Auditoria técnica do erro capturado via webhook</p>
                </div>
                <button
                  type="button"
                  onClick={() => setSelectedFailure(null)}
                  className="p-1.5 text-slate-400 hover:text-slate-600 hover:bg-slate-200 rounded-lg transition-colors"
                >
                  <X size={16} />
                </button>
              </header>

              {/* Corpo de Informações Técnicas */}
              <div className="p-6 space-y-4 max-h-[70vh] overflow-y-auto">
                <div className="grid grid-cols-2 gap-4">
                  <div>
                    <span className="block text-xs font-semibold text-slate-400 uppercase tracking-wider">ID do Agendamento</span>
                    <strong className="text-sm text-slate-800">{selectedFailure.appointmentId || 'N/A'}</strong>
                  </div>
                  <div>
                    <span className="block text-xs font-semibold text-slate-400 uppercase tracking-wider">Código de Erro (Meta)</span>
                    <strong className="text-sm text-slate-800">{selectedFailure.errorCode || '-'}</strong>
                  </div>
                </div>

                <div>
                  <span className="block text-xs font-semibold text-slate-400 uppercase tracking-wider">Data do Registro</span>
                  <p className="text-sm text-slate-700 font-medium">{formatDateTime(selectedFailure.createdAt)}</p>
                </div>

                <div>
                  <span className="block text-xs font-semibold text-slate-400 uppercase tracking-wider">Descrição do Erro</span>
                  <div className="mt-1 p-3.5 bg-rose-50 border border-rose-100 rounded-xl text-xs font-mono text-rose-800 whitespace-pre-wrap break-all shadow-inner">
                    {selectedFailure.errorMessage || 'Descrição indisponível.'}
                  </div>
                </div>

                {/* Identificadores adicionais para rastreabilidade ponta a ponta */}
                <div className="border-t border-slate-100 pt-4 space-y-3">
                  <div>
                    <span className="block text-xs font-semibold text-slate-400 uppercase tracking-wider mb-1">ID da Mensagem Original (Blip)</span>
                    <div className="flex items-center justify-between p-2.5 bg-slate-50 border border-slate-200 rounded-xl text-xs font-mono text-slate-600">
                      <span className="break-all select-all">{selectedFailure.messageId}</span>
                      <button
                        type="button"
                        onClick={() => handleCopyToClipboard(selectedFailure.messageId, 'message')}
                        className="p-1.5 text-slate-400 hover:text-[#feb56c] hover:bg-slate-100 rounded-lg transition-colors ml-2"
                        title="Copiar Message ID"
                      >
                        {copiedId === selectedFailure.messageId ? <Check size={13} className="text-emerald-500" /> : <Copy size={13} />}
                      </button>
                    </div>
                  </div>

                  <div>
                    <span className="block text-xs font-semibold text-slate-400 uppercase tracking-wider mb-1">Trace ID (Rastreamento Unificado)</span>
                    <div className="flex items-center justify-between p-2.5 bg-slate-50 border border-slate-200 rounded-xl text-xs font-mono text-slate-600">
                      <span className="break-all select-all">{selectedFailure.traceId || '-'}</span>
                      {selectedFailure.traceId && (
                        <button
                          type="button"
                          onClick={() => handleCopyToClipboard(selectedFailure.traceId, 'trace')}
                          className="p-1.5 text-slate-400 hover:text-[#feb56c] hover:bg-slate-100 rounded-lg transition-colors ml-2"
                          title="Copiar Trace ID"
                        >
                          {copiedId === selectedFailure.traceId ? <Check size={13} className="text-emerald-500" /> : <Copy size={13} />}
                        </button>
                      )}
                    </div>
                  </div>
                </div>
              </div>

              {/* Ações do Modal */}
              <footer className="border-t border-slate-100 px-6 py-4 flex justify-end bg-slate-50">
                <button
                  type="button"
                  onClick={() => setSelectedFailure(null)}
                  className="px-4 py-2 rounded-xl text-sm font-semibold text-slate-700 bg-white hover:bg-slate-50 border border-slate-200 transition-colors shadow-sm"
                >
                  Fechar
                </button>
              </footer>
            </motion.div>
          </div>
        )}
      </AnimatePresence>
    </section>
  );
}
