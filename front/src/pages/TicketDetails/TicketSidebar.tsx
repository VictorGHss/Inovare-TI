import { useState, useEffect } from 'react';
import {
  Calendar,
  CheckCircle2,
  Clock,
  Laptop,
  Monitor,
  Package,
  Tag,
  UserRound,
  Search,
  Link2,
  Loader2,
  Users,
  Plus,
  X,
} from 'lucide-react';
import { Link } from 'react-router-dom';
import { toast } from 'react-toastify';

import SlaBadge from '../../components/SlaBadge';
import { getTickets, relateTicket } from '../../services/ticketService';
import type { Asset, Ticket, TicketCategory, User } from '../../types/models';

interface TicketSidebarProps {
  ticket: Ticket;
  assets: Asset[];
  loadingAssets: boolean;
  userRole?: string;
  categories: TicketCategory[];
  loadingCategories: boolean;
  updatingCategory: boolean;
  users: User[];
  loadingUsers: boolean;
  addingAdditionalUser: boolean;
  onChangeCategory: (categoryId: string) => void;
  onAddAdditionalUser: (userId: string) => Promise<void>;
  onRefresh?: () => void;
}

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

export default function TicketSidebar({
  ticket,
  assets,
  loadingAssets,
  userRole,
  categories,
  loadingCategories,
  updatingCategory,
  users,
  loadingUsers,
  addingAdditionalUser,
  onChangeCategory,
  onAddAdditionalUser,
  onRefresh,
}: TicketSidebarProps) {
  const [searchQuery, setSearchQuery] = useState('');
  const [allTickets, setAllTickets] = useState<Ticket[]>([]);
  const [suggestions, setSuggestions] = useState<Ticket[]>([]);
  const [loadingTickets, setLoadingTickets] = useState(false);
  const [associatingId, setAssociatingId] = useState<string | null>(null);
  const [showAddAdditionalUser, setShowAddAdditionalUser] = useState(false);
  const [selectedAdditionalUserId, setSelectedAdditionalUserId] = useState('');

  const canManageTicket = userRole === 'ADMIN' || userRole === 'TECHNICIAN';
  const additionalUserIds = ticket.additionalUserIds ?? [];
  const availableUsers = users
    .filter((user) => user.id !== ticket.requesterId)
    .filter((user) => !additionalUserIds.includes(user.id))
    .sort((a, b) => a.name.localeCompare(b.name));

  // Carrega a lista de chamados ao montar o componente
  useEffect(() => {
    async function loadAllTickets() {
      try {
        setLoadingTickets(true);
        const data = await getTickets();
        setAllTickets(Array.isArray(data) ? data : []);
      } catch {
        console.error('Erro ao carregar chamados para vinculacao.');
      } finally {
        setLoadingTickets(false);
      }
    }
    void loadAllTickets();
  }, []);

  // Filtra as sugestões conforme digita
  useEffect(() => {
    const query = searchQuery.trim().toLowerCase();
    if (!query) {
      setSuggestions([]);
      return;
    }

    const filtered = allTickets.filter((t) => {
      // Não pode vincular o próprio chamado
      if (t.id === ticket.id) return false;
      // Não pode vincular se já estiver associado
      if (ticket.relatedTicketIds?.includes(t.id)) return false;

      const matchesId = t.id.toLowerCase().includes(query);
      const matchesTitle = t.title.toLowerCase().includes(query);
      
      return matchesId || matchesTitle;
    });

    setSuggestions(filtered.slice(0, 5)); // limita a 5 sugestões
  }, [searchQuery, allTickets, ticket.id, ticket.relatedTicketIds]);

  const handleAssociate = async (relatedId: string) => {
    try {
      setAssociatingId(relatedId);
      await relateTicket(ticket.id, relatedId);
      toast.success('Chamado associado com sucesso!');
      setSearchQuery('');
      setSuggestions([]);
      if (onRefresh) onRefresh();
    } catch {
      toast.error('Erro ao associar os chamados.');
    } finally {
      setAssociatingId(null);
    }
  };

  const handleCategoryChange = (categoryId: string) => {
    if (categoryId === ticket.categoryId) return;
    void onChangeCategory(categoryId);
  };

  const handleConfirmAdditionalUser = async () => {
    if (!selectedAdditionalUserId) return;
    await onAddAdditionalUser(selectedAdditionalUserId);
    setSelectedAdditionalUserId('');
    setShowAddAdditionalUser(false);
  };

  return (
    <aside className="flex flex-col gap-4">
      <section className="rounded-2xl border border-[#feb56c]/35 bg-white p-6 shadow-sm">
        <h3 className="mb-4 text-sm font-semibold text-slate-700">Informacoes</h3>
        <ul className="flex flex-col gap-3 text-sm">
          <li className="flex items-start gap-2.5 text-slate-600">
            <UserRound size={15} className="mt-0.5 shrink-0 text-slate-400" />
            <div>
              <p className="text-xs text-slate-400">Solicitante</p>
              <p className="font-medium text-slate-700">{ticket.requesterName}</p>
            </div>
          </li>
          <li className="flex items-start gap-2.5 text-slate-600">
            <Tag size={15} className="mt-0.5 shrink-0 text-slate-400" />
            <div>
              <p className="text-xs text-slate-400">Categoria</p>
              {canManageTicket ? (
                <div className="mt-1">
                  <select
                    value={ticket.categoryId}
                    onChange={(event) => handleCategoryChange(event.target.value)}
                    disabled={loadingCategories || updatingCategory}
                    className="w-full rounded-xl border border-slate-200 bg-white px-3 py-2 text-xs font-semibold text-slate-700 shadow-sm focus:outline-none focus:ring-2 focus:ring-brand-primary/30 focus:border-brand-primary disabled:opacity-60"
                  >
                    {loadingCategories && <option value={ticket.categoryId}>Carregando...</option>}
                    {!loadingCategories && categories.length === 0 && (
                      <option value={ticket.categoryId}>{ticket.categoryName}</option>
                    )}
                    {categories.map((category) => (
                      <option key={category.id} value={category.id}>
                        {category.name}
                      </option>
                    ))}
                  </select>
                </div>
              ) : (
                <p className="font-medium text-slate-700">{ticket.categoryName}</p>
              )}
            </div>
          </li>
          <li className="flex items-start gap-2.5 text-slate-600">
            <Calendar size={15} className="mt-0.5 shrink-0 text-slate-400" />
            <div>
              <p className="text-xs text-slate-400">Criado em</p>
              <p className="font-medium text-slate-700">{formatDate(ticket.createdAt)}</p>
            </div>
          </li>
          <li className="flex items-start gap-2.5 text-slate-600">
            <Clock size={15} className="mt-0.5 shrink-0 text-slate-400" />
            <div>
              <p className="text-xs text-slate-400">Prazo SLA</p>
              <div className="flex items-center gap-2">
                <p className="font-medium text-slate-700">
                  {ticket.slaDeadline ? formatDate(ticket.slaDeadline) : 'Sem prazo'}
                </p>
                <SlaBadge
                  deadline={ticket.slaDeadline}
                  status={ticket.status}
                  closedAt={ticket.closedAt}
                />
              </div>
            </div>
          </li>
          {ticket.requestedItemName && (
            <li className="flex items-start gap-2.5 text-slate-600">
              <Package size={15} className="mt-0.5 shrink-0 text-slate-400" />
              <div>
                <p className="text-xs text-slate-400">Item Solicitado</p>
                <p className="font-medium text-slate-700">
                  {ticket.requestedItemName}
                  {ticket.requestedQuantity != null && (
                    <span className="font-normal text-slate-400"> x {ticket.requestedQuantity}</span>
                  )}
                </p>
              </div>
            </li>
          )}
          {ticket.closedAt && (
            <li className="flex items-start gap-2.5 text-slate-600">
              <CheckCircle2 size={15} className="mt-0.5 shrink-0 text-green-500" />
              <div>
                <p className="text-xs text-slate-400">Fechado em</p>
                <p className="font-medium text-slate-700">{formatDate(ticket.closedAt)}</p>
              </div>
            </li>
          )}
        </ul>
      </section>

      <section className="rounded-2xl border border-[#feb56c]/35 bg-white p-6 shadow-sm">
        <div className="mb-4 flex items-center justify-between gap-2">
          <h3 className="text-sm font-semibold text-slate-700 flex items-center gap-2">
            <Users size={16} className="text-brand-primary" />
            Colaboradores Afetados
          </h3>
          {canManageTicket && (
            <button
              type="button"
              onClick={() => setShowAddAdditionalUser((prev) => !prev)}
              disabled={addingAdditionalUser || loadingUsers || availableUsers.length === 0}
              className="inline-flex items-center gap-1 rounded-xl border border-brand-primary/20 bg-brand-secondary/20 px-2.5 py-1.5 text-xs font-semibold text-brand-primary hover:bg-brand-secondary/40 transition-colors disabled:opacity-60"
            >
              {showAddAdditionalUser ? <X size={14} /> : <Plus size={14} />}
              {showAddAdditionalUser ? 'Fechar' : 'Adicionar'}
            </button>
          )}
        </div>

        {loadingUsers ? (
          <p className="text-sm text-slate-400">Carregando colaboradores...</p>
        ) : additionalUserIds.length === 0 ? (
          <p className="text-sm italic text-slate-400">Nenhum colaborador adicional vinculado.</p>
        ) : (
          <div className="flex flex-wrap gap-2">
            {additionalUserIds.map((userId) => {
              const user = users.find((u) => u.id === userId);
              const fallbackName = `Usuario ${userId.slice(0, 8).toUpperCase()}`;
              return (
                <span
                  key={userId}
                  className="inline-flex items-center rounded-full bg-slate-100 px-3 py-1 text-xs font-semibold text-slate-700"
                >
                  {user?.name ?? fallbackName}
                </span>
              );
            })}
          </div>
        )}

        {showAddAdditionalUser && canManageTicket && (
          <div className="mt-4 rounded-2xl border border-slate-200 bg-slate-50 p-3">
            <label className="block text-xs font-semibold text-slate-500 mb-2">
              Selecionar colaborador
            </label>
            <select
              value={selectedAdditionalUserId}
              onChange={(event) => setSelectedAdditionalUserId(event.target.value)}
              className="w-full rounded-xl border border-slate-200 bg-white px-3 py-2 text-sm text-slate-700 focus:outline-none focus:ring-2 focus:ring-brand-primary/30 focus:border-brand-primary"
              disabled={loadingUsers || availableUsers.length === 0}
            >
              <option value="">Selecione um usuario</option>
              {availableUsers.map((user) => (
                <option key={user.id} value={user.id}>
                  {user.name} ({user.email})
                </option>
              ))}
            </select>
            <div className="mt-3 flex flex-wrap items-center justify-end gap-2">
              <button
                type="button"
                onClick={() => {
                  setShowAddAdditionalUser(false);
                  setSelectedAdditionalUserId('');
                }}
                disabled={addingAdditionalUser}
                className="rounded-xl bg-white px-3 py-1.5 text-xs font-semibold text-slate-600 border border-slate-200 hover:bg-slate-100 transition-colors disabled:opacity-60"
              >
                Cancelar
              </button>
              <button
                type="button"
                onClick={() => void handleConfirmAdditionalUser()}
                disabled={!selectedAdditionalUserId || addingAdditionalUser}
                className="rounded-xl bg-brand-primary px-3 py-1.5 text-xs font-semibold text-white hover:bg-brand-primary-dark transition-colors disabled:opacity-60"
              >
                {addingAdditionalUser ? 'Adicionando...' : 'Confirmar'}
              </button>
            </div>
            {availableUsers.length === 0 && (
              <p className="mt-2 text-xs text-slate-400">Nenhum usuario disponivel para adicionar.</p>
            )}
          </div>
        )}
      </section>

      <section className="rounded-2xl border border-[#feb56c]/35 bg-white p-6 shadow-sm">
        <h3 className="mb-4 text-sm font-semibold text-slate-700">Equipamentos do Solicitante</h3>

        {loadingAssets ? (
          <p className="text-sm text-slate-400">Carregando equipamentos...</p>
        ) : assets.length === 0 ? (
          <p className="text-sm italic text-slate-400">Nenhum equipamento registrado.</p>
        ) : (
          <ul className="flex flex-col gap-3">
            {assets.map((asset) => {
              const normalizedName = asset.name.toLowerCase();
              const isLaptop =
                normalizedName.includes('notebook') || normalizedName.includes('laptop');
              const AssetIcon = isLaptop ? Laptop : Monitor;

              return (
                <li key={asset.id} className="rounded-2xl border border-slate-200 bg-[#fff8f1] p-3">
                  <div className="flex items-start gap-2.5">
                    <AssetIcon size={16} className="mt-0.5 shrink-0 text-slate-500" />
                    <div className="min-w-0">
                      <p className="truncate text-sm font-semibold text-slate-700">{asset.name}</p>
                      <p className="mt-0.5 text-xs text-slate-500">Patrimonio: {asset.patrimonyCode}</p>
                      <p className="mt-1 whitespace-pre-wrap break-words text-xs text-slate-500">
                        {asset.specifications || 'Sem especificacoes.'}
                      </p>
                    </div>
                  </div>
                </li>
              );
            })}
          </ul>
        )}
      </section>

      {/* ── Bloco: Associar Chamado ── */}
      <section className="rounded-2xl border border-[#feb56c]/35 bg-white p-6 shadow-sm">
        <h3 className="mb-4 text-sm font-semibold text-slate-700 flex items-center gap-1.5">
          <Link2 size={16} className="text-brand-primary" />
          Associar Chamado
        </h3>
        
        <div className="relative">
          <div className="relative">
            <input
              type="text"
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              placeholder="Pesquisar por ID ou título..."
              className="w-full rounded-xl border border-slate-200 bg-white pl-9 pr-3.5 py-2 text-sm text-slate-800 placeholder-slate-400 focus:outline-none focus:ring-2 focus:ring-brand-primary/30 focus:border-brand-primary transition-all shadow-sm"
            />
            <div className="absolute left-3 top-1/2 -translate-y-1/2 text-slate-400">
              <Search size={15} />
            </div>
          </div>

          {suggestions.length > 0 && (
            <ul className="absolute left-0 right-0 mt-2 z-10 rounded-2xl border border-slate-200 bg-white p-1.5 shadow-lg max-h-60 overflow-y-auto">
              {suggestions.map((s) => (
                <li key={s.id}>
                  <button
                    type="button"
                    onClick={() => void handleAssociate(s.id)}
                    disabled={associatingId !== null}
                    className="flex flex-col items-start w-full text-left rounded-xl p-2.5 hover:bg-slate-50 transition-colors text-sm"
                  >
                    <span className="font-semibold text-slate-800 line-clamp-1">{s.title}</span>
                    <span className="mt-0.5 text-xs text-slate-400 flex items-center gap-1">
                      {associatingId === s.id ? (
                        <Loader2 size={12} className="animate-spin text-brand-primary" />
                      ) : (
                        `#${s.id.slice(0, 8).toUpperCase()}`
                      )}
                      · {s.requesterName}
                    </span>
                  </button>
                </li>
              ))}
            </ul>
          )}

          {searchQuery.trim() !== '' && suggestions.length === 0 && !loadingTickets && (
            <p className="mt-2 text-xs italic text-slate-400">Nenhum chamado localizado.</p>
          )}
        </div>
      </section>

      {ticket.relatedTicketIds && ticket.relatedTicketIds.length > 0 && (
        <section className="rounded-2xl border border-[#feb56c]/35 bg-white p-6 shadow-sm">
          <h3 className="mb-4 text-sm font-semibold text-slate-700">Chamados Relacionados</h3>
          <ul className="flex flex-col gap-2">
            {ticket.relatedTicketIds.map((relId) => (
              <li key={relId}>
                <Link
                  to={`/tickets/${relId}`}
                  className="inline-flex items-center gap-1.5 text-xs font-semibold text-brand-primary hover:text-brand-primary-dark transition-colors break-all bg-brand-secondary/20 hover:bg-brand-secondary/40 px-3 py-2 rounded-xl border border-brand-primary/10 w-full"
                >
                  Chamado #{relId.slice(0, 8).toUpperCase()}
                </Link>
              </li>
            ))}
          </ul>
        </section>
      )}
    </aside>
  );
}
