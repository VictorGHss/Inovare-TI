// Página de detalhes de um item — exibe especificações e histórico de lotes
import { useEffect, useState, useCallback } from 'react';
import { useParams, useNavigate, Link } from 'react-router-dom';
import { ArrowLeft, Package, FileText, Download, Link as LinkIcon, Unlink, Cpu, Plus, Calendar, User, Ticket as TicketIcon, Wrench, History } from 'lucide-react';
import { toast } from 'react-toastify';
import UploadInvoiceModal from '../../components/UploadInvoiceModal';
import SearchableDropdown from '../../components/SearchableDropdown';
import {
  getItemById,
  getItemBatches,
  getItemOutMovements,
  uploadBatchInvoice,
  downloadBatchInvoice,
  linkAssetComponent,
  allocateConsumable,
  getItemAllocations,
  getItems
} from '../../services/inventoryService';
import { getItemTickets } from '../../services/ticketService';
import type { Item, Batch, StockMovement, Ticket, ItemAllocation } from '../../types/models';
import { motion } from 'framer-motion';


// Formata data ISO para dd/MM/yyyy
function formatDate(iso: string): string {
  try {
    const date = new Date(iso);
    return date.toLocaleDateString('pt-BR', {
      day: '2-digit',
      month: '2-digit',
      year: 'numeric',
    });
  } catch {
    return '-';
  }
}

// Formata preço em reais
function formatPrice(price: number): string {
  return new Intl.NumberFormat('pt-BR', {
    style: 'currency',
    currency: 'BRL',
  }).format(price);
}

export default function ItemDetails() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const [item, setItem] = useState<Item | null>(null);
  const [batches, setBatches] = useState<Batch[]>([]);
  const [outMovements, setOutMovements] = useState<StockMovement[]>([]);
  const [loading, setLoading] = useState(true);
  const [activeTab, setActiveTab] = useState<'IN' | 'OUT' | 'TICKETS'>('IN');
  const [showInvoiceModal, setShowInvoiceModal] = useState(false);
  const [selectedBatchForInvoice, setSelectedBatchForInvoice] = useState<Batch | null>(null);

  // Estados locais para controlar os chamados vinculados a este equipamento
  const [tickets, setTickets] = useState<Ticket[]>([]);
  const [ticketsPage, setTicketsPage] = useState(0);
  const [ticketsTotalPages, setTicketsTotalPages] = useState(1);
  const [ticketsLoading, setTicketsLoading] = useState(false);

  // Estados para o Módulo de Relacionamentos Avançados de Inventário
  const [allocations, setAllocations] = useState<ItemAllocation[]>([]);
  const [allocationsLoading, setAllocationsLoading] = useState(false);

  // Controle de modais e listas de itens livres para vincular/alocar
  const [showLinkModal, setShowLinkModal] = useState(false);
  const [showAllocateModal, setShowAllocateModal] = useState(false);

  const [availableItems, setAvailableItems] = useState<Item[]>([]);
  const [availableItemsLoading, setAvailableItemsLoading] = useState(false);

  const [availableConsumables, setAvailableConsumables] = useState<Item[]>([]);
  const [availableConsumablesLoading, setAvailableConsumablesLoading] = useState(false);

  // Estados dos formulários de vínculos
  const [selectedChildId, setSelectedChildId] = useState('');
  const [linking, setLinking] = useState(false);

  const [selectedConsumableId, setSelectedConsumableId] = useState('');
  const [allocationQuantity, setAllocationQuantity] = useState(1);
  const [allocationTicketId, setAllocationTicketId] = useState('');
  const [allocating, setAllocating] = useState(false);

  const loadAllocations = useCallback(async () => {
    if (!id || !item) return;
    setAllocationsLoading(true);
    try {
      // Se for consumível, busca onde ele foi distribuído (asParent = false)
      // Se for ativo principal, busca os consumíveis alocados a ele (asParent = true)
      const data = await getItemAllocations(id, !item.isConsumable);
      setAllocations(data);
    } catch (error) {
      console.error('Erro ao carregar alocações:', error);
      toast.error('Erro ao carregar histórico de alocações.');
    } finally {
      setAllocationsLoading(false);
    }
  }, [id, item]);

  useEffect(() => {
    if (item) {
      loadAllocations();
    }
  }, [item, loadAllocations]);

  const loadAvailableItems = useCallback(async () => {
    setAvailableItemsLoading(true);
    try {
      const data = await getItems({ size: 100 });
      // Apenas ativos (não consumíveis) sem pai e que não sejam o próprio item atual
      const filtered = data.content.filter(
        (i) => !i.isConsumable && !i.parent && i.id !== id
      );
      setAvailableItems(filtered);
    } catch (error) {
      console.error('Erro ao carregar itens:', error);
      toast.error('Erro ao buscar equipamentos livres.');
    } finally {
      setAvailableItemsLoading(false);
    }
  }, [id]);

  const loadAvailableConsumables = useCallback(async () => {
    setAvailableConsumablesLoading(true);
    try {
      const data = await getItems({ size: 100 });
      // Apenas consumíveis com estoque disponível
      const filtered = data.content.filter((i) => i.isConsumable && i.currentStock > 0);
      setAvailableConsumables(filtered);
    } catch (error) {
      console.error('Erro ao carregar consumíveis:', error);
      toast.error('Erro ao buscar consumíveis em estoque.');
    } finally {
      setAvailableConsumablesLoading(false);
    }
  }, []);

  async function handleLinkComponent(e: React.FormEvent) {
    e.preventDefault();
    if (!id || !selectedChildId) return;

    setLinking(true);
    try {
      await linkAssetComponent(id, selectedChildId);
      toast.success('Equipamento acoplado com sucesso!');
      setShowLinkModal(false);
      setSelectedChildId('');
      loadData(); // Atualiza a lista de componentes do pai
    } catch (error) {
      console.error('Erro ao acoplar equipamento:', error);
      toast.error('Erro ao acoplar equipamento.');
    } finally {
      setLinking(false);
    }
  }

  async function handleAllocateConsumable(e: React.FormEvent) {
    e.preventDefault();
    if (!id || !selectedConsumableId || allocationQuantity <= 0) return;

    setAllocating(true);
    try {
      await allocateConsumable(id, {
        childItemId: selectedConsumableId,
        quantity: allocationQuantity,
        ticketId: allocationTicketId || undefined,
      });
      toast.success('Insumo alocado com sucesso!');
      setShowAllocateModal(false);
      setSelectedConsumableId('');
      setAllocationQuantity(1);
      setAllocationTicketId('');
      loadData(); // Atualiza o estoque físico geral
      loadAllocations(); // Atualiza a tabela local de alocações
    } catch (error) {
      console.error('Erro ao alocar insumo:', error);
      toast.error('Erro ao alocar insumo. Verifique se o saldo em estoque é suficiente.');
    } finally {
      setAllocating(false);
    }
  }


  const loadData = useCallback(async () => {
    if (!id) return;
    
    try {
      const [itemData, batchesData, outMovementsData] = await Promise.all([
        getItemById(id),
        getItemBatches(id),
        getItemOutMovements(id),
      ]);
      setItem(itemData);
      setBatches(batchesData);
      setOutMovements(outMovementsData);
    } catch {
      toast.error('Item não encontrado.');
      navigate('/inventory');
    } finally {
      setLoading(false);
    }
  }, [id, navigate]);

  useEffect(() => {
    loadData();
  }, [loadData]);

  // Função para carregar chamados associados a este item do inventário
  const loadTicketsData = useCallback(async () => {
    if (!id) return;
    setTicketsLoading(true);
    try {
      const data = await getItemTickets(id, ticketsPage);
      setTickets(data.content);
      setTicketsTotalPages(data.totalPages);
    } catch (error) {
      console.error('Erro ao buscar chamados associados ao item:', error);
      toast.error('Erro ao carregar chamados associados.');
      setTickets([]);
    } finally {
      setTicketsLoading(false);
    }
  }, [id, ticketsPage]);

  // Efeito para recarregar chamados caso a página atual de chamados ou a aba ativa mude
  useEffect(() => {
    if (activeTab === 'TICKETS') {
      loadTicketsData();
    }
  }, [activeTab, loadTicketsData]);


  function openInvoiceModal(batch: Batch) {
    setSelectedBatchForInvoice(batch);
    setShowInvoiceModal(true);
  }

  async function handleInvoiceUpload(file: File) {
    if (!id || !selectedBatchForInvoice) return;

    try {
      await uploadBatchInvoice(id, selectedBatchForInvoice.id, file);
      setShowInvoiceModal(false);
      setSelectedBatchForInvoice(null);
      loadData(); // Recarrega dados para atualizar a tabela
    } catch (error) {
      const message = error instanceof Error ? error.message : 'Erro desconhecido';
      throw new Error(message);
    }
  }

  async function handleInvoiceDownload(batch: Batch, e: React.MouseEvent) {
    if (!id) return;

    e.stopPropagation();

    if (!batch.invoiceFileName) {
      toast.error('Nenhuma nota fiscal anexada a este lote.');
      return;
    }

    try {
      const blob = await downloadBatchInvoice(id, batch.id);
      const url = window.URL.createObjectURL(blob);
      const link = document.createElement('a');
      link.href = url;
      link.download = batch.invoiceFileName;
      document.body.appendChild(link);
      link.click();
      document.body.removeChild(link);
      window.URL.revokeObjectURL(url);
    } catch {
      toast.error('Erro ao baixar nota fiscal.');
    }
  }

  function renderReference(ref: string) {
    if (!ref) return '-';
    if (ref.startsWith('TICKET:')) {
      const ticketId = ref.replace('TICKET:', '');
      const shortId = ticketId.slice(0, 8);
      return (
        <Link
          to={`/tickets/${ticketId}`}
          className="inline-flex items-center gap-1 rounded-2xl bg-brand-secondary/70 border border-brand-primary/30 px-2.5 py-0.5 text-xs font-semibold text-orange-800 hover:bg-orange-100 hover:text-brand-primary-dark transition-colors shadow-sm"
        >
          🎟️ Chamado #{shortId}
        </Link>
      );
    }
    return ref;
  }

  if (loading) {
    return (
      <main className="w-full max-w-full px-4 sm:px-6 lg:px-8 py-8">
        {/* Skeleton de carregamento */}
        <div className="animate-pulse space-y-4">
          <div className="h-6 bg-slate-200 rounded w-1/3" />
          <div className="h-40 bg-slate-100 rounded-xl" />
          <div className="h-32 bg-slate-100 rounded-xl" />
        </div>
      </main>
    );
  }

  if (!item) return null;

  const hasStock = item.currentStock > 0;
  const specs = item.specifications || {};
  const hasSpecs = Object.keys(specs).length > 0;

  return (
    <main className="w-full max-w-full px-4 sm:px-6 lg:px-8 py-8">
      {/* Navegação de retorno */}
      <div className="flex items-center gap-3 mb-6">
        <button
          onClick={() => navigate('/inventory')}
          className="p-1.5 rounded-lg hover:bg-slate-200 text-slate-500 hover:text-slate-700 transition-colors"
          aria-label="Voltar"
        >
          <ArrowLeft size={18} />
        </button>
        <div>
          <p className="text-xs text-slate-400">Detalhes do Item</p>
          <h1 className="text-base font-bold text-slate-800 leading-tight">
            {item.name}
          </h1>
        </div>
      </div>

      {/* Header do item */}
      <div className="bg-white rounded-xl border border-slate-200 shadow-sm p-6 mb-6">
        <div className="flex flex-wrap items-start justify-between gap-3 mb-3">
          <div className="flex-1">
            <h2 className="text-xl font-bold text-slate-800 mb-1">{item.name}</h2>
            <p className="text-sm text-slate-500">{item.itemCategoryName}</p>
          </div>
          <span
            className={`inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium ${
              hasStock ? 'bg-green-100 text-green-700' : 'bg-red-100 text-red-700'
            }`}
          >
            {hasStock ? 'Em Estoque' : 'Sem Estoque'}
          </span>
        </div>
        <div className="mt-4 pt-4 border-t border-slate-100">
          <div className="flex items-center gap-2">
            <Package size={16} className="text-slate-400" />
            <span className="text-sm text-slate-600">
              Estoque Atual:{' '}
              <span className={`font-semibold ${hasStock ? 'text-green-600' : 'text-red-600'}`}>
                {item.currentStock} {item.currentStock === 1 ? 'unidade' : 'unidades'}
              </span>
            </span>
          </div>
        </div>
      </div>

      {/* Cenário B - Banner de Destaque se possuir pai associado (Monitor/Nobreak acoplado a um PC/Servidor) */}
      {!item.isConsumable && item.parent && (
        <div className="bg-blue-50 text-brand-primary border border-blue-100 rounded-xl p-4 mb-6 flex items-center justify-between shadow-sm">
          <div>
            <p className="text-sm font-semibold text-blue-900">Equipamento Vinculado</p>
            <p className="text-xs text-blue-800 mt-0.5 font-medium">
              Este dispositivo está atualmente acoplado ao ativo principal:{' '}
              <strong className="text-brand-primary font-bold">{item.parent.name}</strong>.
            </p>
          </div>
          <Link
            to={`/inventory/${item.parent.id}`}
            className="text-xs font-bold text-brand-primary hover:text-brand-primary-dark hover:underline bg-white border border-blue-200 px-3 py-1.5 rounded-lg shadow-sm transition-colors"
          >
            Ver Equipamento Pai
          </Link>
        </div>
      )}

      {/* Card de Especificações */}
      <div className="bg-white rounded-xl border border-slate-200 shadow-sm p-6 mb-6">
        <div className="flex items-center gap-2 mb-4">
          <FileText size={18} className="text-slate-600" />
          <h3 className="text-sm font-semibold text-slate-700">Especificações</h3>
        </div>
        {hasSpecs ? (
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-x-6 gap-y-3">
            {Object.entries(specs).map(([key, value]) => (
              <div key={key} className="flex flex-col">
                <span className="text-xs font-medium text-slate-500 uppercase tracking-wide">
                  {key}
                </span>
                <span className="text-sm text-slate-800 mt-0.5">
                  {String(value)}
                </span>
              </div>
            ))}
          </div>
        ) : (
          <p className="text-sm text-slate-400 italic">Nenhuma especificação cadastrada.</p>
        )}
      </div>

      {/* Seção: Componentes e Vínculos */}
      <div className="bg-white rounded-xl border border-slate-200 shadow-sm p-6 mb-6">
        <div className="flex flex-wrap items-center justify-between gap-3 border-b border-slate-100 pb-4 mb-4">
          <div className="flex items-center gap-2">
            <Cpu size={18} className="text-slate-600" />
            <h3 className="text-sm font-semibold text-slate-700">Componentes e Vínculos</h3>
          </div>
          
          {/* Ações apenas para ativos principais (não consumíveis) */}
          {!item.isConsumable && (
            <div className="flex items-center gap-2">
              <button
                type="button"
                onClick={() => {
                  loadAvailableItems();
                  setShowLinkModal(true);
                }}
                className="inline-flex items-center gap-1 text-xs font-bold text-brand-primary bg-brand-secondary/50 hover:bg-brand-secondary px-3 py-1.5 rounded-lg border border-brand-primary/30 transition-all shadow-sm"
              >
                <Plus size={14} />
                Acoplar Ativo Componente
              </button>
              <button
                type="button"
                onClick={() => {
                  loadAvailableConsumables();
                  setShowAllocateModal(true);
                }}
                className="inline-flex items-center gap-1 text-xs font-bold text-green-700 bg-green-50 hover:bg-green-100 px-3 py-1.5 rounded-lg border border-green-200 transition-all shadow-sm"
              >
                <Plus size={14} />
                Alocar Consumível / Periférico
              </button>
            </div>
          )}
        </div>

        {/* CENÁRIO C - Se for um consumível (ex.: Toner) */}
        {item.isConsumable ? (
          <div>
            <p className="text-xs text-slate-400 font-bold uppercase tracking-wider mb-3 flex items-center gap-1.5">
              <History size={14} /> HISTÓRICO DE DISTRIBUIÇÃO E CONSUMO
            </p>
            {allocationsLoading ? (
              <div className="p-4 text-center text-xs text-slate-400 animate-pulse">Carregando histórico...</div>
            ) : allocations.length > 0 ? (
              <div className="overflow-x-auto">
                <table className="w-full text-sm">
                  <thead className="border-b border-slate-200 text-xs uppercase tracking-wider text-slate-500">
                    <tr>
                      <th className="pb-2 text-left font-medium">Equipamento Destinatário</th>
                      <th className="pb-2 text-right font-medium">Qtd. Alocada</th>
                      <th className="pb-2 text-center font-medium">Data de Alocação</th>
                      <th className="pb-2 text-left font-medium pl-4">Técnico</th>
                      <th className="pb-2 text-center font-medium">Chamado</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-slate-100">
                    {allocations.map((alloc) => (
                      <tr key={alloc.id} className="hover:bg-slate-50 transition-colors">
                        <td className="py-2.5 text-slate-700 font-medium">
                          <Link to={`/inventory/${alloc.parentItemId}`} className="hover:underline text-brand-primary">
                            {alloc.parentItemName}
                          </Link>
                        </td>
                        <td className="py-2.5 text-right text-slate-700 font-semibold">{alloc.quantity}</td>
                        <td className="py-2.5 text-center text-slate-500 text-xs">{formatDate(alloc.allocatedAt)}</td>
                        <td className="py-2.5 text-slate-600 pl-4 text-xs">{alloc.allocatedByName}</td>
                        <td className="py-2.5 text-center">
                          {alloc.ticketId ? (
                            <Link
                              to={`/tickets/${alloc.ticketId}`}
                              className="inline-flex items-center gap-1 rounded-2xl bg-orange-50 border border-orange-200 px-2 py-0.5 text-[10px] font-bold text-orange-700 hover:bg-orange-100"
                            >
                              🎟️ Chamado #{alloc.ticketId.slice(0, 8).toUpperCase()}
                            </Link>
                          ) : (
                            <span className="text-slate-400 text-xs">-</span>
                          )}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            ) : (
              <p className="text-xs text-slate-400 italic py-2">Este consumível ainda não foi alocado a nenhuma impressora ou equipamento.</p>
            )}
          </div>
        ) : (
          /* CENÁRIO A - Se for ativo principal (ex.: Computador) */
          <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
            
            {/* Bloco 1: Componentes Acoplados (Filhos) */}
            <div>
              <p className="text-xs font-bold text-slate-500 uppercase tracking-wider mb-3 flex items-center gap-1.5">
                <Cpu size={14} className="text-slate-400" /> Ativos Filhos Acoplados
              </p>
              {item.components && item.components.length > 0 ? (
                <div className="border border-slate-100 rounded-xl overflow-hidden shadow-sm">
                  <table className="w-full text-xs">
                    <thead className="bg-slate-50 text-slate-500 uppercase tracking-wider font-semibold border-b border-slate-100">
                      <tr>
                        <th className="px-4 py-2 text-left">Nome do Dispositivo</th>
                        <th className="px-4 py-2 text-left">Categoria</th>
                      </tr>
                    </thead>
                    <tbody className="divide-y divide-slate-100 bg-white">
                      {item.components.map((comp) => (
                        <tr key={comp.id} className="hover:bg-slate-50">
                          <td className="px-4 py-2.5 font-medium">
                            <Link to={`/inventory/${comp.id}`} className="text-brand-primary hover:underline">
                              {comp.name}
                            </Link>
                          </td>
                          <td className="px-4 py-2.5 text-slate-500">{comp.itemCategoryName}</td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              ) : (
                <p className="text-xs text-slate-400 italic py-4 bg-slate-50/50 rounded-xl border border-dashed border-slate-200 text-center">
                  Nenhum componente (ex.: Monitor, Nobreak) acoplado a este ativo.
                </p>
              )}
            </div>

            {/* Bloco 2: Consumíveis / Insumos Instalados */}
            <div>
              <p className="text-xs font-bold text-slate-500 uppercase tracking-wider mb-3 flex items-center gap-1.5">
                <Package size={14} className="text-slate-400" /> Insumos e Periféricos Alocados
              </p>
              {allocationsLoading ? (
                <div className="p-4 text-center text-xs text-slate-400 animate-pulse">Carregando alocações...</div>
              ) : allocations.length > 0 ? (
                <div className="border border-slate-100 rounded-xl overflow-hidden shadow-sm">
                  <table className="w-full text-xs">
                    <thead className="bg-slate-50 text-slate-500 uppercase tracking-wider font-semibold border-b border-slate-100">
                      <tr>
                        <th className="px-4 py-2 text-left">Insumo</th>
                        <th className="px-4 py-2 text-right">Qtd</th>
                        <th className="px-4 py-2 text-center">Instalação</th>
                      </tr>
                    </thead>
                    <tbody className="divide-y divide-slate-100 bg-white">
                      {allocations.map((alloc) => (
                        <tr key={alloc.id} className="hover:bg-slate-50">
                          <td className="px-4 py-2.5 font-medium">
                            <Link to={`/inventory/${alloc.childItemId}`} className="text-brand-primary hover:underline">
                              {alloc.childItemName}
                            </Link>
                          </td>
                          <td className="px-4 py-2.5 text-right font-bold text-slate-700">{alloc.quantity}</td>
                          <td className="px-4 py-2.5 text-center text-slate-400 text-[10px]">{formatDate(alloc.allocatedAt)}</td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              ) : (
                <p className="text-xs text-slate-400 italic py-4 bg-slate-50/50 rounded-xl border border-dashed border-slate-200 text-center">
                  Nenhum consumível (ex.: Toner, Mouse) alocado a este ativo.
                </p>
              )}
            </div>

          </div>
        )}
      </div>

      {/* Card de Movimentações */}
      <div className="bg-white rounded-xl border border-slate-200 shadow-sm p-6">
        <div className="flex items-center gap-1.5 mb-6 relative border-b border-slate-100 pb-2">
          {[
            { id: 'IN', label: 'Lotes de Entrada' },
            { id: 'OUT', label: 'Histórico de Saídas' },
            { id: 'TICKETS', label: 'Chamados de Suporte' }
          ].map((tab) => (
            <button
              key={tab.id}
              type="button"
              onClick={() => setActiveTab(tab.id as any)}
              className={`relative px-4 py-2 text-xs font-bold transition-all z-10 ${
                activeTab === tab.id ? 'text-brand-primary' : 'text-slate-500 hover:text-slate-700'
              }`}
            >
              {/* Linha indicadora inferior deslizante utilizando Framer Motion */}
              {activeTab === tab.id && (
                <motion.div
                  layoutId="activeTabUnderline"
                  className="absolute bottom-0 left-0 right-0 h-0.5 bg-brand-primary"
                  transition={{ type: 'spring', stiffness: 300, damping: 30 }}
                />
              )}
              {tab.label}
            </button>
          ))}
        </div>


        {activeTab === 'IN' && batches.length > 0 ? (
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead className="border-b border-slate-200 text-xs uppercase tracking-wider text-slate-500">
                <tr>
                  <th className="pb-3 text-left font-medium">Data de Entrada</th>
                  <th className="pb-3 text-right font-medium">Qtd. Comprada</th>
                  <th className="pb-3 text-right font-medium">Qtd. Restante</th>
                  <th className="pb-3 text-right font-medium">Preço Unitário</th>
                  <th className="pb-3 text-center font-medium">Nota Fiscal</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {batches.map((batch) => (
                  <tr key={batch.id} className="hover:bg-slate-50 transition-colors">
                    <td className="py-3 text-slate-700">{formatDate(batch.entryDate)}</td>
                    <td className="py-3 text-right text-slate-700">{batch.originalQuantity}</td>
                    <td className="py-3 text-right">
                      <span
                        className={
                          batch.remainingQuantity > 0 ? 'text-green-600 font-medium' : 'text-slate-400'
                        }
                      >
                        {batch.remainingQuantity}
                      </span>
                    </td>
                    <td className="py-3 text-right text-slate-700 font-medium">
                      {formatPrice(batch.unitPrice)}
                    </td>
                    <td className="py-3">
                      <div className="flex items-center justify-center">
                        {batch.invoiceFileName ? (
                          <button
                            onClick={(e) => handleInvoiceDownload(batch, e)}
                            className="flex items-center gap-1.5 text-xs font-medium bg-brand-secondary text-orange-800 hover:bg-orange-200 px-2 py-1 rounded transition-colors"
                            title="Visualizar/baixar nota fiscal"
                          >
                            <Download size={13} />
                            Ver NF
                          </button>
                        ) : (
                          <button
                            onClick={(e) => {
                              e.stopPropagation();
                              openInvoiceModal(batch);
                            }}
                            className="flex items-center gap-1.5 text-xs font-medium text-slate-500 hover:text-slate-700 hover:bg-slate-100 px-2 py-1 rounded transition-colors"
                            title="Anexar nota fiscal"
                          >
                            <FileText size={13} />
                            Anexar
                          </button>
                        )}
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        ) : activeTab === 'IN' ? (
          <p className="text-sm text-slate-400 italic">Nenhum lote registrado ainda.</p>
        ) : outMovements.length > 0 ? (
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead className="border-b border-slate-200 text-xs uppercase tracking-wider text-slate-500">
                <tr>
                  <th className="pb-3 text-left font-medium">Data da Saída</th>
                  <th className="pb-3 text-right font-medium">Quantidade</th>
                  <th className="pb-3 text-left font-medium">Referência</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {outMovements.map((movement) => (
                  <tr key={movement.id} className="hover:bg-slate-50 transition-colors">
                    <td className="py-3 text-slate-700">{formatDate(movement.date)}</td>
                    <td className="py-3 text-right text-red-600 font-medium">-{movement.quantity}</td>
                    <td className="py-3 text-slate-600">{renderReference(movement.reference)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        ) : (
          <p className="text-sm text-slate-400 italic">Nenhuma saída registrada ainda.</p>
        )}

        {activeTab === 'TICKETS' && (
          <div className="flex flex-col gap-4">
            {ticketsLoading ? (
              <div className="p-12 text-center">
                <div className="animate-pulse space-y-3">
                  <div className="h-4 bg-slate-200 rounded w-3/4 mx-auto" />
                  <div className="h-4 bg-slate-200 rounded w-1/2 mx-auto" />
                </div>
              </div>
            ) : tickets.length > 0 ? (
              <>
                <div className="overflow-x-auto">
                  <table className="w-full text-sm">
                    <thead className="border-b border-slate-200 text-xs uppercase tracking-wider text-slate-500">
                      <tr>
                        <th className="pb-3 text-left font-medium">Chamado</th>
                        <th className="pb-3 text-left font-medium">Status</th>
                        <th className="pb-3 text-left font-medium">Título</th>
                        <th className="pb-3 text-left font-medium">Solicitante</th>
                        <th className="pb-3 text-left font-medium">Data de Abertura</th>
                      </tr>
                    </thead>
                    <tbody className="divide-y divide-slate-100">
                      {tickets.map((ticket) => {
                        const statusColors: Record<string, string> = {
                          OPEN: 'bg-emerald-50 text-emerald-700 border-emerald-200',
                          IN_PROGRESS: 'bg-blue-50 text-blue-700 border-blue-200',
                          RESOLVED: 'bg-slate-100 text-slate-600 border-slate-200',
                        };

                        const statusLabels: Record<string, string> = {
                          OPEN: 'Aberto',
                          IN_PROGRESS: 'Em Progresso',
                          RESOLVED: 'Resolvido',
                        };

                        const statusCls = statusColors[ticket.status] || 'bg-amber-50 text-amber-700 border-amber-200';
                        const statusLabel = statusLabels[ticket.status] || 'Desconhecido';

                        const shortId = ticket.id.slice(0, 8).toUpperCase();

                        return (
                          <tr key={ticket.id} className="hover:bg-slate-50 transition-colors">
                            <td className="py-4 pr-3 text-slate-700">
                              <Link
                                to={`/tickets/${ticket.id}`}
                                className="inline-flex items-center gap-1 rounded-2xl bg-brand-secondary/70 border border-brand-primary/30 px-2.5 py-0.5 text-xs font-semibold text-orange-800 hover:bg-orange-100 hover:text-brand-primary-dark transition-colors shadow-sm"
                              >
                                🎟️ Chamado #{shortId}
                              </Link>
                            </td>
                            <td className="py-4 pr-3">
                              <span className={`inline-flex items-center rounded-full border px-2 py-0.5 text-xs font-semibold ${statusCls}`}>
                                {statusLabel}
                              </span>
                            </td>
                            <td className="py-4 pr-3 text-slate-700 max-w-xs truncate" title={ticket.title}>
                              {ticket.title}
                            </td>
                            <td className="py-4 pr-3 text-slate-600">
                              {ticket.requesterName || 'Sistema'}
                            </td>
                            <td className="py-4 pr-3 text-slate-600">
                              {formatDate(ticket.createdAt)}
                            </td>
                          </tr>
                        );
                      })}
                    </tbody>
                  </table>
                </div>

                {/* Notas de Solução para chamados finalizados */}
                {tickets.some(t => t.status === 'RESOLVED' && t.solutionText) && (
                  <div className="mt-4 border-t border-slate-150 pt-6">
                    <h4 className="text-xs font-bold uppercase tracking-widest text-slate-400 mb-3">Notas de Solução (Histórico de Reparos)</h4>
                    <div className="flex flex-col gap-3">
                      {tickets
                        .filter(t => t.status === 'RESOLVED' && t.solutionText)
                        .map(t => (
                          <div key={t.id} className="bg-slate-50 border border-slate-200 rounded-xl p-4">
                            <div className="flex items-center justify-between mb-2">
                              <span className="text-xs font-bold text-slate-700">Chamado #{t.id.slice(0, 8).toUpperCase()}</span>
                              <span className="text-xs text-slate-400">{formatDate(t.closedAt || '')}</span>
                            </div>
                            <p className="text-sm text-slate-800 font-semibold">{t.title}</p>
                            <div className="mt-2 text-xs text-slate-600 bg-white border border-slate-150 p-3 rounded-lg italic shadow-sm">
                              "{t.solutionText}"
                            </div>
                          </div>
                        ))}
                    </div>
                  </div>
                )}

                {/* Barra de Paginação para os chamados do item */}
                {ticketsTotalPages > 1 && (
                  <div className="flex items-center justify-between border-t border-slate-100 pt-4 mt-2">
                    <button
                      type="button"
                      onClick={() => setTicketsPage((prev) => Math.max(0, prev - 1))}
                      disabled={ticketsPage === 0 || ticketsLoading}
                      className="px-4 py-2 text-xs font-semibold text-slate-700 bg-slate-50 hover:bg-slate-100 border border-slate-200 disabled:opacity-50 disabled:cursor-not-allowed rounded-xl transition-colors"
                    >
                      Anterior
                    </button>
                    <span className="text-xs text-slate-500 font-semibold">
                      Página {ticketsPage + 1} de {ticketsTotalPages}
                    </span>
                    <button
                      type="button"
                      onClick={() => setTicketsPage((prev) => Math.min(ticketsTotalPages - 1, prev + 1))}
                      disabled={ticketsPage >= ticketsTotalPages - 1 || ticketsLoading}
                      className="px-4 py-2 text-xs font-semibold text-slate-700 bg-slate-50 hover:bg-slate-100 border border-slate-200 disabled:opacity-50 disabled:cursor-not-allowed rounded-xl transition-colors"
                    >
                      Seguinte
                    </button>
                  </div>
                )}
              </>
            ) : (
              <p className="text-sm text-slate-400 italic py-4">Nenhum chamado registrado para este equipamento.</p>
            )}
          </div>
        )}
      </div>


      {/* Modal de Nota Fiscal */}
      <UploadInvoiceModal
        isOpen={showInvoiceModal}
        onClose={() => {
          setShowInvoiceModal(false);
          setSelectedBatchForInvoice(null);
        }}
        onUpload={handleInvoiceUpload}
        entityName="Lote"
        entityId={selectedBatchForInvoice?.id ?? ''}
      />

      {/* Modal de Vincular Componente (Ativo-Ativo) */}
      {showLinkModal && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-slate-900/60 backdrop-blur-sm">
          <div className="bg-white rounded-2xl border border-slate-200 shadow-2xl w-full max-w-md overflow-hidden animate-in fade-in zoom-in-95 duration-200">
            <div className="p-6 border-b border-slate-100">
              <h3 className="text-base font-bold text-slate-800">Acoplar Equipamento Componente</h3>
              <p className="text-xs text-slate-500 mt-1">
                Selecione um ativo disponível no estoque para vincular a **{item.name}**.
              </p>
            </div>
            
            <form onSubmit={handleLinkComponent}>
              <div className="p-6 space-y-4">
                <div>
                  <label className="block text-xs font-bold text-slate-500 uppercase tracking-wide mb-2">
                    Dispositivo Componente
                  </label>
                  {availableItemsLoading ? (
                    <div className="text-xs text-slate-400 animate-pulse py-2">Buscando equipamentos...</div>
                  ) : (
                    <SearchableDropdown
                      options={availableItems}
                      value={selectedChildId}
                      onChange={setSelectedChildId}
                      placeholder="Escolha um Monitor, Nobreak, etc..."
                    />
                  )}
                </div>
              </div>

              <div className="p-6 bg-slate-50 border-t border-slate-100 flex items-center justify-end gap-3">
                <button
                  type="button"
                  onClick={() => {
                    setShowLinkModal(false);
                    setSelectedChildId('');
                  }}
                  className="px-4 py-2 text-xs font-semibold text-slate-700 bg-white border border-slate-200 hover:bg-slate-50 rounded-xl transition-colors"
                >
                  Cancelar
                </button>
                <button
                  type="submit"
                  disabled={linking || !selectedChildId}
                  className="px-4 py-2 text-xs font-bold text-white bg-brand-primary hover:bg-brand-primary-dark disabled:opacity-50 disabled:cursor-not-allowed rounded-xl transition-colors shadow-sm"
                >
                  {linking ? 'Salvando...' : 'Confirmar Vínculo'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* Modal de Alocar Consumível (Ativo-Consumível) */}
      {showAllocateModal && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-slate-900/60 backdrop-blur-sm">
          <div className="bg-white rounded-2xl border border-slate-200 shadow-2xl w-full max-w-md overflow-hidden animate-in fade-in zoom-in-95 duration-200">
            <div className="p-6 border-b border-slate-100">
              <h3 className="text-base font-bold text-slate-800">Alocar Consumível ou Suprimento</h3>
              <p className="text-xs text-slate-500 mt-1">
                Instale um consumível em **{item.name}** efetuando a baixa física em lote (FIFO).
              </p>
            </div>

            <form onSubmit={handleAllocateConsumable}>
              <div className="p-6 space-y-4">
                <div>
                  <label className="block text-xs font-bold text-slate-500 uppercase tracking-wide mb-2">
                    Insumo / Suprimento
                  </label>
                  {availableConsumablesLoading ? (
                    <div className="text-xs text-slate-400 animate-pulse py-2">Buscando insumos...</div>
                  ) : (
                    <SearchableDropdown
                      options={availableConsumables.map(c => ({
                        id: c.id,
                        name: `${c.name} (Saldo: ${c.currentStock})`
                      }))}
                      value={selectedConsumableId}
                      onChange={setSelectedConsumableId}
                      placeholder="Escolha um Toner, Teclado, Mouse..."
                    />
                  )}
                </div>

                <div className="grid grid-cols-2 gap-4">
                  <div>
                    <label className="block text-xs font-bold text-slate-500 uppercase tracking-wide mb-2">
                      Quantidade
                    </label>
                    <input
                      type="number"
                      min={1}
                      value={allocationQuantity}
                      onChange={(e) => setAllocationQuantity(Math.max(1, parseInt(e.target.value) || 1))}
                      className="w-full rounded-xl border border-slate-200 bg-white px-4 py-2.5 text-sm text-slate-800 shadow-sm focus:outline-none focus:ring-2 focus:ring-brand-primary/30 focus:border-brand-primary transition font-medium"
                    />
                  </div>

                  <div>
                    <label className="block text-xs font-bold text-slate-500 uppercase tracking-wide mb-2">
                      ID do Chamado (Opcional)
                    </label>
                    <input
                      type="text"
                      placeholder="UUID do chamado..."
                      value={allocationTicketId}
                      onChange={(e) => setAllocationTicketId(e.target.value)}
                      className="w-full rounded-xl border border-slate-200 bg-white px-4 py-2.5 text-sm text-slate-800 shadow-sm focus:outline-none focus:ring-2 focus:ring-brand-primary/30 focus:border-brand-primary transition text-xs font-medium"
                    />
                  </div>
                </div>
              </div>

              <div className="p-6 bg-slate-50 border-t border-slate-100 flex items-center justify-end gap-3">
                <button
                  type="button"
                  onClick={() => {
                    setShowAllocateModal(false);
                    setSelectedConsumableId('');
                    setAllocationQuantity(1);
                    setAllocationTicketId('');
                  }}
                  className="px-4 py-2 text-xs font-semibold text-slate-700 bg-white border border-slate-200 hover:bg-slate-50 rounded-xl transition-colors"
                >
                  Cancelar
                </button>
                <button
                  type="submit"
                  disabled={allocating || !selectedConsumableId || allocationQuantity <= 0}
                  className="px-4 py-2 text-xs font-bold text-white bg-green-600 hover:bg-green-700 disabled:opacity-50 disabled:cursor-not-allowed rounded-xl transition-colors shadow-sm"
                >
                  {allocating ? 'Alocando...' : 'Confirmar Alocação'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </main>
  );
}

