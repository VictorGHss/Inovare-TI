// Página de detalhes de um chamado — exibe informações completas e permite fechar
import { useEffect, useState } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { ArrowLeft, CheckCircle2, Calendar, Clock, Tag, Package } from 'lucide-react';
import { toast } from 'react-toastify';
import { getTicketById, closeTicket, type Ticket } from '../../services/api';
import StatusBadge from '../../components/StatusBadge';

// Formata data ISO para exibição em português — retorna '-' para valores nulos
function formatDate(iso: string | null | undefined): string {
  if (!iso) return '-';
  try {
    return new Date(iso).toLocaleDateString('pt-BR', {
      day: '2-digit',
      month: '2-digit',
      year: 'numeric',
      hour: '2-digit',
      minute: '2-digit',
    });
  } catch {
    return '-';
  }
}

// Traduz prioridade para português
const priorityLabels: Record<string, string> = {
  LOW: 'Baixa',
  NORMAL: 'Normal',
  HIGH: 'Alta',
  URGENT: 'Urgente',
};

// Cores de prioridade
const priorityColors: Record<string, string> = {
  LOW: 'text-slate-500',
  NORMAL: 'text-blue-600',
  HIGH: 'text-orange-600',
  URGENT: 'text-red-600 font-semibold',
};

export default function TicketDetails() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const [ticket, setTicket] = useState<Ticket | null>(null);
  const [loading, setLoading] = useState(true);
  const [closing, setClosing] = useState(false);

  useEffect(() => {
    if (!id) return;
    // Carrega os dados do chamado pelo UUID da URL
    getTicketById(id)
      .then(setTicket)
      .catch(() => {
        toast.error('Chamado não encontrado.');
        navigate('/dashboard');
      })
      .finally(() => setLoading(false));
  }, [id, navigate]);

  async function handleClose() {
    if (!ticket) return;
    setClosing(true);
    try {
      // Chama o endpoint de fechar e atualiza o estado local com o retorno
      const updated = await closeTicket(ticket.id);
      setTicket(updated);
      toast.success('Chamado fechado com sucesso!');
    } catch {
      toast.error('Erro ao fechar o chamado. Tente novamente.');
    } finally {
      setClosing(false);
    }
  }

  if (loading) {
    return (
      <main className="max-w-5xl mx-auto px-4 sm:px-6 py-8">
        {/* Skeleton de carregamento */}
        <div className="animate-pulse space-y-4">
          <div className="h-6 bg-slate-200 rounded w-1/3" />
          <div className="h-40 bg-slate-100 rounded-xl" />
          <div className="h-32 bg-slate-100 rounded-xl" />
        </div>
      </main>
    );
  }

  if (!ticket) return null;

  const isClosed = ticket.status === 'CLOSED';

  return (
    <main className="max-w-5xl mx-auto px-4 sm:px-6 py-8">
      {/* Navegação de retorno */}
      <div className="flex items-center gap-3 mb-6">
        <button
          onClick={() => navigate('/dashboard')}
          className="p-1.5 rounded-lg hover:bg-slate-200 text-slate-500 hover:text-slate-700 transition-colors"
          aria-label="Voltar"
        >
          <ArrowLeft size={18} />
        </button>
        <div>
          <p className="text-xs text-slate-400">Detalhes do Chamado</p>
          <h1 className="text-base font-bold text-slate-800 leading-tight">
            #{ticket.id.slice(0, 8).toUpperCase()}
          </h1>
        </div>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-12 gap-6 items-start">
        {/* Coluna principal — título, status, descrição */}
        <div className="lg:col-span-8 flex flex-col gap-5">
          {/* Header do chamado */}
          <div className="bg-white rounded-xl border border-slate-200 shadow-sm p-6">
            <div className="flex flex-wrap items-start justify-between gap-3 mb-4">
              <h2 className="text-xl font-bold text-slate-800 leading-snug flex-1">
                {ticket.title}
              </h2>
              <StatusBadge status={ticket.status} />
            </div>
            <div className="flex flex-wrap items-center gap-x-4 gap-y-1 text-sm text-slate-500">
              <span>
                Solicitante:{' '}
                <span className="font-medium text-slate-700">{ticket.requesterName}</span>
              </span>
              {ticket.assignedToName && (
                <span>
                  Técnico:{' '}
                  <span className="font-medium text-slate-700">{ticket.assignedToName}</span>
                </span>
              )}
              <span className={priorityColors[ticket.priority]}>
                Prioridade: {priorityLabels[ticket.priority] ?? ticket.priority}
              </span>
            </div>
          </div>

          {/* Bloco de descrição detalhada */}
          <div className="bg-white rounded-xl border border-slate-200 shadow-sm p-6">
            <h3 className="text-sm font-semibold text-slate-700 mb-3">Descrição</h3>
            {ticket.description ? (
              <p className="text-sm text-slate-600 whitespace-pre-wrap leading-relaxed">
                {ticket.description}
              </p>
            ) : (
              <p className="text-sm text-slate-400 italic">Nenhuma descrição fornecida.</p>
            )}
          </div>

          {/* Botão de ação — visível apenas para chamados não fechados */}
          {!isClosed && (
            <div className="flex justify-end">
              <button
                onClick={handleClose}
                disabled={closing}
                className="flex items-center gap-2 bg-green-600 hover:bg-green-700 disabled:opacity-60 text-white text-sm font-semibold px-5 py-2.5 rounded-xl transition-colors"
              >
                <CheckCircle2 size={16} />
                {closing ? 'Fechando...' : 'Resolver e Fechar Chamado'}
              </button>
            </div>
          )}
        </div>

        {/* Aside lateral — meta-informações */}
        <aside className="lg:col-span-4 flex flex-col gap-4">
          <div className="bg-white rounded-xl border border-slate-200 shadow-sm p-6">
            <h3 className="text-sm font-semibold text-slate-700 mb-4">Informações</h3>
            <ul className="flex flex-col gap-3 text-sm">
              {/* Categoria */}
              <li className="flex items-start gap-2.5 text-slate-600">
                <Tag size={15} className="mt-0.5 text-slate-400 shrink-0" />
                <div>
                  <p className="text-xs text-slate-400">Categoria</p>
                  <p className="font-medium text-slate-700">{ticket.categoryName}</p>
                </div>
              </li>
              {/* Data de criação */}
              <li className="flex items-start gap-2.5 text-slate-600">
                <Calendar size={15} className="mt-0.5 text-slate-400 shrink-0" />
                <div>
                  <p className="text-xs text-slate-400">Criado em</p>
                  <p className="font-medium text-slate-700">{formatDate(ticket.createdAt)}</p>
                </div>
              </li>
              {/* Prazo SLA */}
              <li className="flex items-start gap-2.5 text-slate-600">
                <Clock size={15} className="mt-0.5 text-slate-400 shrink-0" />
                <div>
                  <p className="text-xs text-slate-400">Prazo SLA</p>
                  <p className="font-medium text-slate-700">
                    {ticket.slaDeadline ? formatDate(ticket.slaDeadline) : 'Sem prazo'}
                  </p>
                </div>
              </li>
              {/* Item solicitado — exibido apenas quando presente */}
              {ticket.requestedItemName && (
                <li className="flex items-start gap-2.5 text-slate-600">
                  <Package size={15} className="mt-0.5 text-slate-400 shrink-0" />
                  <div>
                    <p className="text-xs text-slate-400">Item Solicitado</p>
                    <p className="font-medium text-slate-700">
                      {ticket.requestedItemName}
                      {ticket.requestedQuantity != null && (
                        <span className="text-slate-400 font-normal">
                          {' '}× {ticket.requestedQuantity}
                        </span>
                      )}
                    </p>
                  </div>
                </li>
              )}
              {/* Data de fechamento — exibido apenas quando presente */}
              {ticket.closedAt && (
                <li className="flex items-start gap-2.5 text-slate-600">
                  <CheckCircle2 size={15} className="mt-0.5 text-green-500 shrink-0" />
                  <div>
                    <p className="text-xs text-slate-400">Fechado em</p>
                    <p className="font-medium text-slate-700">{formatDate(ticket.closedAt)}</p>
                  </div>
                </li>
              )}
            </ul>
          </div>
        </aside>
      </div>
    </main>
  );
}
