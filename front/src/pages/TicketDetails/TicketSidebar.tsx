import { useState, useEffect, useMemo } from 'react';
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
import { getTickets, relateTicket, getSimilarTickets } from '../../services/ticketService';
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
  onApplyMacro?: (macroText: string) => void;
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
  onApplyMacro,
}: TicketSidebarProps) {
  const [searchQuery, setSearchQuery] = useState('');
  const [allTickets, setAllTickets] = useState<Ticket[]>([]);
  const [suggestions, setSuggestions] = useState<Ticket[]>([]);
  const [loadingTickets, setLoadingTickets] = useState(false);
  const [associatingId, setAssociatingId] = useState<string | null>(null);
  const [showAddAdditionalUser, setShowAddAdditionalUser] = useState(false);
  const [selectedAdditionalUserId, setSelectedAdditionalUserId] = useState('');
  const [additionalUserQuery, setAdditionalUserQuery] = useState('');
  const [sectorFilter, setSectorFilter] = useState('ALL');

  // Similar tickets and Knowledge base accordion states
  const [similarTickets, setSimilarTickets] = useState<Ticket[]>([]);
  const [loadingSimilar, setLoadingSimilar] = useState(false);
  const [isAccordionOpen, setIsAccordionOpen] = useState(true);

  const macroTag = ticket.tags?.find((t) => t.defaultResolution && t.defaultResolution.trim());

  useEffect(() => {
    // Cláusula de barreira: só busca se o ticket tiver um id válido e status for IN_PROGRESS
    if (!ticket?.id || ticket.status !== 'IN_PROGRESS') {
      setSimilarTickets([]);
      return;
    }

    async function loadSimilar() {
      setLoadingSimilar(true);
      try {
        const data = await getSimilarTickets(ticket.id);
        setSimilarTickets(data);
      } catch (err) {
        console.error('Erro ao carregar chamados similares:', err);
      } finally {
        setLoadingSimilar(false);
      }
    }
    void loadSimilar();
  }, [ticket?.id, ticket?.status]);

  const canManageTicket = userRole === 'ADMIN' || userRole === 'TECHNICIAN';
  const additionalUserIds = ticket.additionalUserIds ?? [];
  const additionalUserIdsSerialized = additionalUserIds.join(',');

  // Memoiza a lista de usuários disponíveis para evitar recriação de referências e loops de renderização
  const availableUsers = useMemo(() => {
    const ids = additionalUserIdsSerialized.split(',').filter(Boolean);
    return users
      .filter((user) => user.id !== ticket.requesterId)
      .filter((user) => !ids.includes(user.id))
      .sort((a, b) => a.name.localeCompare(b.name));
  }, [users, ticket.requesterId, additionalUserIdsSerialized]);

  const normalizedAdditionalQuery = additionalUserQuery.trim().toLowerCase();
  
  const sectorOptions = Array.from(
    new Set(users.map((user) => user.sectorName || 'Sem setor')),
  ).sort((a, b) => a.localeCompare(b));

  // Memoiza a lista filtrada de usuários disponíveis para evitar loops no useEffect de validação
  const filteredAvailableUsers = useMemo(() => {
    return availableUsers.filter((user) => {
      const userSector = user.sectorName || 'Sem setor';
      if (sectorFilter !== 'ALL' && userSector !== sectorFilter) return false;

      if (!normalizedAdditionalQuery) return true;
      const matchesName = user.name.toLowerCase().includes(normalizedAdditionalQuery);
      const matchesEmail = user.email.toLowerCase().includes(normalizedAdditionalQuery);
      return matchesName || matchesEmail;
    });
  }, [availableUsers, sectorFilter, normalizedAdditionalQuery]);

  const groupedAvailableUsers = filteredAvailableUsers.reduce((acc, user) => {
    const sectorName = user.sectorName || 'Sem setor';
    if (!acc[sectorName]) acc[sectorName] = [];
    acc[sectorName].push(user);
    return acc;
  }, {} as Record<string, User[]>);

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

  const relatedTicketIdsSerialized = (ticket.relatedTicketIds ?? []).join(',');

  // Filtra as sugestões conforme digita (depende do join(',') primitivo dos arrays para evitar loops por referência)
  useEffect(() => {
    const query = searchQuery.trim().toLowerCase();
    if (!query) {
      setSuggestions([]);
      return;
    }

    const ids = relatedTicketIdsSerialized.split(',').filter(Boolean);

    const filtered = allTickets.filter((t) => {
      // Não pode vincular o próprio chamado
      if (t.id === ticket.id) return false;
      // Não pode vincular se já estiver associado
      if (ids.includes(t.id)) return false;

      const matchesId = t.id.toLowerCase().includes(query);
      const matchesTitle = t.title.toLowerCase().includes(query);
      
      return matchesId || matchesTitle;
    });

    setSuggestions(filtered.slice(0, 5)); // limita a 5 sugestões
  }, [searchQuery, allTickets, ticket.id, relatedTicketIdsSerialized]);

  useEffect(() => {
    if (!selectedAdditionalUserId) return;
    const stillAvailable = filteredAvailableUsers.some((user) => user.id === selectedAdditionalUserId);
    if (!stillAvailable) {
      setSelectedAdditionalUserId('');
    }
  }, [filteredAvailableUsers, selectedAdditionalUserId]);

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

      {ticket.status === 'IN_PROGRESS' && (
        <section className="rounded-2xl border border-[#feb56c]/35 bg-white p-6 shadow-sm overflow-hidden transition-all duration-300">
          <button
            type="button"
            onClick={() => setIsAccordionOpen(!isAccordionOpen)}
            className="flex items-center justify-between w-full text-left font-semibold text-slate-700 text-sm focus:outline-none"
          >
            <span className="flex items-center gap-2">
              <span className="w-2 h-2 rounded-full bg-brand-primary animate-pulse" />
              Chamados Similares / Base de Conhecimento
            </span>
            <span className="text-xs text-slate-400 transform transition-transform duration-200" style={{ transform: isAccordionOpen ? 'rotate(180deg)' : 'rotate(0deg)' }}>
              ▼
            </span>
          </button>

          {isAccordionOpen && (
            <div className="mt-4 space-y-4">
              {macroTag && (
                <div className="rounded-xl border border-brand-primary/20 bg-brand-secondary/15 p-3 flex flex-col gap-2 shadow-sm">
                  <p className="text-xs text-slate-500 font-medium leading-relaxed">
                    Uma macro de resolução padrão foi localizada para a tag <span className="font-bold" style={{ color: macroTag.color }}>{macroTag.name}</span>.
                  </p>
                  <button
                    type="button"
                    onClick={() => onApplyMacro && onApplyMacro(macroTag.defaultResolution || '')}
                    className="w-full text-center py-2 px-3 bg-brand-primary hover:bg-brand-primary-dark text-white rounded-xl text-xs font-bold transition-all hover:scale-102 active:scale-98 shadow-sm flex items-center justify-center gap-1.5"
                  >
                    🚀 Aplicar Solução Padrão
                  </button>
                </div>
              )}

              {loadingSimilar ? (
                <p className="text-xs text-slate-400 italic">Buscando chamados similares...</p>
              ) : similarTickets.length === 0 ? (
                <p className="text-xs text-slate-400 italic">Nenhum chamado similar resolvido com estas tags.</p>
              ) : (
                <div className="space-y-3 max-h-60 overflow-y-auto pr-1">
                  {similarTickets.map((t) => (
                    <div key={t.id} className="rounded-xl border border-slate-100 bg-slate-50/50 p-3 text-xs flex flex-col gap-1.5 shadow-sm">
                      <div className="flex items-center justify-between">
                        <span className="font-bold text-slate-700 truncate max-w-[150px]">{t.title}</span>
                        <span className="text-[10px] text-brand-primary font-bold bg-brand-secondary/20 px-2 py-0.5 rounded-full shrink-0">
                          #{t.id.slice(0, 8).toUpperCase()}
                        </span>
                      </div>
                      <p className="text-slate-500 line-clamp-2 leading-relaxed">{t.description}</p>
                      {t.solutionText && (
                        <div className="border-t border-slate-100 pt-1.5 mt-1">
                          <p className="text-[10px] text-emerald-600 font-bold uppercase tracking-wider">Solução Aplicada:</p>
                          <p className="text-slate-600 leading-relaxed bg-white border border-slate-100 rounded-lg p-2 mt-1 whitespace-pre-wrap">{t.solutionText}</p>
                        </div>
                      )}
                    </div>
                  ))}
                </div>
              )}
            </div>
          )}
        </section>
      )}

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
            <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
              <div>
                <label className="block text-xs font-semibold text-slate-500 mb-2">
                  Buscar por nome
                </label>
                <input
                  type="text"
                  value={additionalUserQuery}
                  onChange={(event) => setAdditionalUserQuery(event.target.value)}
                  placeholder="Digite o nome ou e-mail"
                  className="w-full rounded-xl border border-slate-200 bg-white px-3 py-2 text-sm text-slate-700 focus:outline-none focus:ring-2 focus:ring-brand-primary/30 focus:border-brand-primary"
                />
              </div>
              <div>
                <label className="block text-xs font-semibold text-slate-500 mb-2">
                  Filtrar setor
                </label>
                <select
                  value={sectorFilter}
                  onChange={(event) => setSectorFilter(event.target.value)}
                  className="w-full rounded-xl border border-slate-200 bg-white px-3 py-2 text-sm text-slate-700 focus:outline-none focus:ring-2 focus:ring-brand-primary/30 focus:border-brand-primary"
                  disabled={loadingUsers || sectorOptions.length === 0}
                >
                  <option value="ALL">Todos os setores</option>
                  {sectorOptions.map((sector) => (
                    <option key={sector} value={sector}>
                      {sector}
                    </option>
                  ))}
                </select>
              </div>
            </div>

            <label className="mt-3 block text-xs font-semibold text-slate-500">
              Selecionar colaborador
            </label>
            <select
              value={selectedAdditionalUserId}
              onChange={(event) => setSelectedAdditionalUserId(event.target.value)}
              className="mt-2 w-full rounded-xl border border-slate-200 bg-white px-3 py-2 text-sm text-slate-700 focus:outline-none focus:ring-2 focus:ring-brand-primary/30 focus:border-brand-primary"
              disabled={loadingUsers || availableUsers.length === 0}
            >
              <option value="">Selecione um usuario</option>
              {Object.entries(groupedAvailableUsers).map(([sector, sectorUsers]) => (
                <optgroup key={sector} label={sector}>
                  {sectorUsers.map((user) => (
                    <option key={user.id} value={user.id}>
                      {user.name} - {user.sectorName || 'Sem setor'}
                    </option>
                  ))}
                </optgroup>
              ))}
            </select>
            <div className="mt-3 flex flex-wrap items-center justify-end gap-2">
              <button
                type="button"
                onClick={() => {
                  setShowAddAdditionalUser(false);
                  setSelectedAdditionalUserId('');
                  setAdditionalUserQuery('');
                  setSectorFilter('ALL');
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
            {availableUsers.length > 0 && filteredAvailableUsers.length === 0 && (
              <p className="mt-2 text-xs text-slate-400">Nenhum colaborador corresponde aos filtros.</p>
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
