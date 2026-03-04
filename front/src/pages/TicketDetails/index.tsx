// Página de detalhes de um chamado — exibe informações completas e permite resolver
import { useEffect, useState } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { ArrowLeft, CheckCircle2, Calendar, Clock, Tag, Package, Paperclip, Download, FileText, Laptop, Monitor } from 'lucide-react';
import { toast } from 'react-toastify';
import { useAuth } from '../../contexts/AuthContext';
import {
  getTicketById,
  resolveTicket,
  claimTicket,
  transferTicket,
  getUsers,
  getAssetsByUser,
  type Ticket,
  type User,
  type Asset,
} from '../../services/api';
import StatusBadge from '../../components/StatusBadge';
import SlaBadge from '../../components/SlaBadge';
import TicketComments from '../../components/TicketComments';

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

const priorityLabels: Record<string, string> = {
  LOW: 'Baixa',
  NORMAL: 'Normal',
  HIGH: 'Alta',
  URGENT: 'Urgente',
};

const priorityColors: Record<string, string> = {
  LOW: 'text-slate-500',
  NORMAL: 'text-blue-600',
  HIGH: 'text-orange-600',
  URGENT: 'text-red-600 font-semibold',
};

export default function TicketDetails() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const { user } = useAuth();
  const [ticket, setTicket] = useState<Ticket | null>(null);
  const [loading, setLoading] = useState(true);
  const [closing, setClosing] = useState(false);
  const [claiming, setClaiming] = useState(false);
  const [transferring, setTransferring] = useState(false);
  const [showTransfer, setShowTransfer] = useState(false);
  const [users, setUsers] = useState<User[]>([]);
  const [selectedUserId, setSelectedUserId] = useState('');
  const [assets, setAssets] = useState<Asset[]>([]);
  const [loadingAssets, setLoadingAssets] = useState(false);

  // Função para buscar dados do ticket
  const fetchTicket = async () => {
    if (!id) return;
    try {
      const data = await getTicketById(id);
      setTicket(data);
    } catch {
      toast.error('Chamado não encontrado.');
      navigate('/dashboard');
    }
  };

  useEffect(() => {
    fetchTicket().finally(() => setLoading(false));
  }, [id, navigate]);

  useEffect(() => {
    async function fetchAssets() {
      if (!ticket?.requesterId) {
        setAssets([]);
        return;
      }

      setLoadingAssets(true);
      try {
        const data = await getAssetsByUser(ticket.requesterId);
        setAssets(data);
      } catch {
        toast.error('Erro ao carregar equipamentos do usuário.');
      } finally {
        setLoadingAssets(false);
      }
    }

    fetchAssets();
  }, [ticket?.requesterId]);

  async function handleResolve() {
    if (!ticket) return;
    setClosing(true);
    try {
      const updated = await resolveTicket(ticket.id);
      setTicket(updated);
      toast.success('Chamado resolvido com sucesso!');
    } catch {
      toast.error('Erro ao resolver o chamado. Tente novamente.');
    } finally {
      setClosing(false);
    }
  }

  async function handleClaim() {
    if (!ticket) return;
    setClaiming(true);
    try {
      await claimTicket(ticket.id);
      await fetchTicket(); // Recarrega o ticket
      toast.success('Chamado assumido com sucesso!');
    } catch {
      toast.error('Erro ao assumir o chamado.');
    } finally {
      setClaiming(false);
    }
  }

  async function handleOpenTransfer() {
    try {
      const usersData = await getUsers();
      setUsers(usersData);
      setShowTransfer(true);
    } catch {
      toast.error('Erro ao carregar usuários para transferência.');
    }
  }

  async function handleTransfer() {
    if (!ticket || !selectedUserId) return;
    setTransferring(true);
    try {
      await transferTicket(ticket.id, selectedUserId);
      await fetchTicket(); // Recarrega o ticket
      setShowTransfer(false);
      setSelectedUserId('');
      toast.success('Chamado transferido com sucesso!');
    } catch {
      toast.error('Erro ao transferir chamado.');
    } finally {
      setTransferring(false);
    }
  }

  if (loading) {
    return (
      <main className="max-w-5xl mx-auto px-4 sm:px-6 py-8">
        <div className="animate-pulse space-y-4">
          <div className="h-6 bg-slate-200 rounded w-1/3" />
          <div className="h-40 bg-slate-100 rounded-xl" />
          <div className="h-32 bg-slate-100 rounded-xl" />
        </div>
      </main>
    );
  }

  if (!ticket) return null;

  const isResolved = ticket.status === 'RESOLVED';
  const apiUrl = import.meta.env.VITE_API_URL;

  return (
    <main className="max-w-5xl mx-auto px-4 sm:px-6 py-8">
      <div className="flex items-center gap-3 mb-6">
        <button
          onClick={() => navigate('/tickets')}
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
        <div className="lg:col-span-8 flex flex-col gap-5">
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
              <SlaBadge deadline={ticket.slaDeadline} status={ticket.status} closedAt={ticket.closedAt} />
            </div>
          </div>

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

          {ticket.attachments && ticket.attachments.length > 0 && (
            <div className="bg-white rounded-xl border border-slate-200 shadow-sm p-6">
              <div className="flex items-center gap-2 mb-4">
                <Paperclip size={16} className="text-slate-500" />
                <h3 className="text-sm font-semibold text-slate-700">
                  Anexos ({ticket.attachments.length})
                </h3>
              </div>
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                {ticket.attachments.map((attachment) => {
                  const isImage = attachment.fileType.includes('image');
                  const fullUrl = `${apiUrl}${attachment.fileUrl}`;

                  if (isImage) {
                    return (
                      <a
                        key={attachment.id}
                        href={fullUrl}
                        target="_blank"
                        rel="noopener noreferrer"
                        className="group relative block rounded-lg border border-slate-200 overflow-hidden hover:border-primary hover:shadow-md transition-all"
                      >
                        <img
                          src={fullUrl}
                          alt={attachment.originalFilename}
                          className="w-full max-h-64 object-contain rounded-lg border bg-slate-50"
                        />
                        <div className="absolute inset-0 bg-black/0 group-hover:bg-black/10 transition-colors" />
                        <div className="absolute bottom-0 left-0 right-0 bg-gradient-to-t from-black/60 to-transparent p-2">
                          <p className="text-xs text-white truncate font-medium">
                            {attachment.originalFilename}
                          </p>
                        </div>
                      </a>
                    );
                  }

                  return (
                    <a
                      key={attachment.id}
                      href={fullUrl}
                      target="_blank"
                      download={attachment.originalFilename}
                      className="flex items-center gap-3 p-3 rounded-lg border border-slate-200 hover:border-primary hover:bg-slate-50 transition-all group"
                    >
                      <div className="p-2 rounded-lg bg-slate-100 group-hover:bg-primary/10 transition-colors">
                        <FileText size={20} className="text-slate-500 group-hover:text-primary" />
                      </div>
                      <div className="flex-1 min-w-0">
                        <p className="text-sm font-medium text-slate-700 truncate">
                          {attachment.originalFilename}
                        </p>
                        <p className="text-xs text-slate-400">
                          {attachment.fileType}
                        </p>
                      </div>
                      <Download size={16} className="text-slate-400 group-hover:text-primary shrink-0" />
                    </a>
                  );
                })}
              </div>
            </div>
          )}

          {/* Seção de histórico e comentários */}
          <TicketComments ticketId={ticket.id} ticketStatus={ticket.status} assignedToId={ticket.assignedToId} />

          {!isResolved && (user?.role === 'ADMIN' || user?.role === 'TECHNICIAN') && (
            <div className="flex flex-wrap justify-end gap-3">
              {/* Assumir Chamado - apenas se status é OPEN e user é ADMIN ou TECHNICIAN */}
              {ticket.status === 'OPEN' && (user?.role === 'ADMIN' || user?.role === 'TECHNICIAN') && (
                <button
                  onClick={handleClaim}
                  disabled={claiming || transferring || closing}
                  className="flex items-center gap-2 bg-blue-600 hover:bg-blue-700 disabled:opacity-60 text-white text-sm font-semibold px-5 py-2.5 rounded-xl transition-colors"
                >
                  {claiming ? 'Assumindo...' : 'Assumir Chamado'}
                </button>
              )}

              {/* Transfer - only if user owns ticket or is admin, and user is ADMIN or TECHNICIAN */}
              {ticket.status !== 'RESOLVED' && (ticket.assignedToId === user?.id || user?.role === 'ADMIN') && (user?.role === 'ADMIN' || user?.role === 'TECHNICIAN') && (
                <button
                  onClick={handleOpenTransfer}
                  disabled={claiming || transferring || closing}
                  className="flex items-center gap-2 bg-amber-600 hover:bg-amber-700 disabled:opacity-60 text-white text-sm font-semibold px-5 py-2.5 rounded-xl transition-colors"
                >
                  Transferir
                </button>
              )}

              {/* Resolve - only if user owns ticket, status is in progress, and user is ADMIN or TECHNICIAN */}
              {ticket.status === 'IN_PROGRESS' && ticket.assignedToId === user?.id && (user?.role === 'ADMIN' || user?.role === 'TECHNICIAN') && (
                <button
                  onClick={handleResolve}
                  disabled={closing || claiming || transferring}
                  className="flex items-center gap-2 bg-green-600 hover:bg-green-700 disabled:opacity-60 text-white text-sm font-semibold px-5 py-2.5 rounded-xl transition-colors"
                >
                  <CheckCircle2 size={16} />
                  {closing ? 'Resolvendo...' : 'Resolver Chamado'}
                </button>
              )}
            </div>
          )}

          {showTransfer && (
            <div className="bg-white rounded-xl border border-slate-200 shadow-sm p-6">
              <h3 className="text-sm font-semibold text-slate-700 mb-3">Transferir Chamado</h3>
              <div className="flex flex-col sm:flex-row gap-3">
                <select
                  value={selectedUserId}
                  onChange={(event) => setSelectedUserId(event.target.value)}
                  className="flex-1 rounded-xl border border-slate-300 px-3 py-2 text-sm text-slate-700 focus:outline-none focus:ring-2 focus:ring-primary/30"
                >
                  <option value="">Selecione um usuário</option>
                  {users.map((user) => (
                    <option key={user.id} value={user.id}>
                      {user.name} ({user.email})
                    </option>
                  ))}
                </select>
                <button
                  onClick={handleTransfer}
                  disabled={!selectedUserId || transferring}
                  className="bg-primary hover:bg-primary-hover disabled:opacity-60 text-white text-sm font-semibold px-4 py-2 rounded-xl transition-colors"
                >
                  {transferring ? 'Transferindo...' : 'Confirmar'}
                </button>
                <button
                  onClick={() => {
                    setShowTransfer(false);
                    setSelectedUserId('');
                  }}
                  disabled={transferring}
                  className="bg-slate-100 hover:bg-slate-200 disabled:opacity-60 text-slate-700 text-sm font-semibold px-4 py-2 rounded-xl transition-colors"
                >
                  Cancelar
                </button>
              </div>
            </div>
          )}
        </div>

        <aside className="lg:col-span-4 flex flex-col gap-4">
          <div className="bg-white rounded-xl border border-slate-200 shadow-sm p-6">
            <h3 className="text-sm font-semibold text-slate-700 mb-4">Informações</h3>
            <ul className="flex flex-col gap-3 text-sm">
              <li className="flex items-start gap-2.5 text-slate-600">
                <Tag size={15} className="mt-0.5 text-slate-400 shrink-0" />
                <div>
                  <p className="text-xs text-slate-400">Categoria</p>
                  <p className="font-medium text-slate-700">{ticket.categoryName}</p>
                </div>
              </li>
              <li className="flex items-start gap-2.5 text-slate-600">
                <Calendar size={15} className="mt-0.5 text-slate-400 shrink-0" />
                <div>
                  <p className="text-xs text-slate-400">Criado em</p>
                  <p className="font-medium text-slate-700">{formatDate(ticket.createdAt)}</p>
                </div>
              </li>
              <li className="flex items-start gap-2.5 text-slate-600">
                <Clock size={15} className="mt-0.5 text-slate-400 shrink-0" />
                <div>
                  <p className="text-xs text-slate-400">Prazo SLA</p>
                  <div className="flex items-center gap-2">
                    <p className="font-medium text-slate-700">
                      {ticket.slaDeadline ? formatDate(ticket.slaDeadline) : 'Sem prazo'}
                    </p>
                    <SlaBadge deadline={ticket.slaDeadline} status={ticket.status} closedAt={ticket.closedAt} />
                  </div>
                </div>
              </li>
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

          <div className="bg-white rounded-xl border border-slate-200 shadow-sm p-6">
            <h3 className="text-sm font-semibold text-slate-700 mb-4">Equipamentos do Usuário</h3>

            {loadingAssets ? (
              <p className="text-sm text-slate-400">Carregando equipamentos...</p>
            ) : assets.length === 0 ? (
              <p className="text-sm text-slate-400 italic">Nenhum equipamento vinculado.</p>
            ) : (
              <ul className="flex flex-col gap-3">
                {assets.map((asset) => {
                  const normalizedName = asset.name.toLowerCase();
                  const isLaptop = normalizedName.includes('notebook') || normalizedName.includes('laptop');
                  const AssetIcon = isLaptop ? Laptop : Monitor;

                  return (
                    <li key={asset.id} className="rounded-lg border border-slate-200 bg-slate-50 p-3">
                      <div className="flex items-start gap-2.5">
                        <AssetIcon size={16} className="mt-0.5 text-slate-500 shrink-0" />
                        <div className="min-w-0">
                          <p className="text-sm font-semibold text-slate-700 truncate">{asset.name}</p>
                          <p className="text-xs text-slate-500 mt-0.5">Patrimônio: {asset.patrimonyCode}</p>
                          <p className="text-xs text-slate-500 mt-1 whitespace-pre-wrap break-words">
                            {asset.specifications || 'Sem especificações.'}
                          </p>
                        </div>
                      </div>
                    </li>
                  );
                })}
              </ul>
            )}
          </div>
        </aside>
      </div>
    </main>
  );
}
