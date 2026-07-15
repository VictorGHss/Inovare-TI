import { useEffect, useMemo, useState, type FormEvent } from 'react';
import { toast } from 'react-toastify';

import { getAssetCategories, getAssets, getItems, getAssetById } from '@/services/inventoryService';
import type { Asset, AssetCategory, Item, ResolveTicketRequest, Ticket, User } from '@/types/models';

interface UseResolveTicketParams {
  isOpen: boolean;
  onClose: () => void;
  requesterId: string;
  onResolve: (request: ResolveTicketRequest) => Promise<void>;
  ticket?: Ticket;
  initialNotes?: string;
  users?: User[];
}

export interface ResolveTicketItemState {
  itemId: string;
  itemName: string;
  quantity: number;
  recipientUserId: string;
}

/**
 * Hook personalizado para gerir o estado da resolução do chamado.
 * Lida com a entrega de ativos de património ou insumos de consumo nominalmente.
 */
export function useResolveTicket({
  isOpen,
  onClose,
  requesterId,
  onResolve,
  ticket,
  initialNotes = '',
  users = [],
}: UseResolveTicketParams) {
  const [resolutionNotes, setResolutionNotes] = useState('');
  const [recipientUserId, setRecipientUserId] = useState('');
  const [assetUsers, setAssetUsers] = useState<{ id: string; name: string }[]>([]);
  const [loadingAssetUsers, setLoadingAssetUsers] = useState(false);

  // Lista de insumos que serão entregues e deduzidos na resolução
  const [itemsToDeliver, setItemsToDeliver] = useState<ResolveTicketItemState[]>([]);

  useEffect(() => {
    if (isOpen) {
      setResolutionNotes(initialNotes);
    }
  }, [isOpen, initialNotes]);

  useEffect(() => {
    if (isOpen && ticket?.assetId) {
      setMaintAssetId(ticket.assetId);
    }
  }, [isOpen, ticket]);

  // Inicializa a lista de insumos com base no chamado
  useEffect(() => {
    if (isOpen && ticket) {
      const initialItems: ResolveTicketItemState[] = [];
      
      // 1. Se o chamado tem múltiplos itens solicitados cadastrados
      if (ticket.requestedItems && ticket.requestedItems.length > 0) {
        ticket.requestedItems.forEach((ri) => {
          initialItems.push({
            itemId: ri.itemId,
            itemName: ri.itemName || 'Material de Consumo',
            quantity: ri.quantity,
            recipientUserId: ticket.requesterId,
          });
        });
      } 
      // 2. Fallback para chamado com item único de inventário legado
      else if (ticket.requestedItemId && ticket.requestedQuantity) {
        initialItems.push({
          itemId: ticket.requestedItemId,
          itemName: ticket.requestedItemName || 'Material de Consumo',
          quantity: ticket.requestedQuantity,
          recipientUserId: ticket.requesterId,
        });
      }

      setItemsToDeliver(initialItems);
    }
  }, [isOpen, ticket]);

  const [deliverEquipment, setDeliverEquipment] = useState(false);
  const [deliveryType, setDeliveryType] = useState<'asset' | 'item'>('asset');
  const [assetMode, setAssetMode] = useState<'existing' | 'new'>('existing');

  const [assets, setAssets] = useState<Asset[]>([]);
  const [items, setItems] = useState<Item[]>([]);
  const [assetCategories, setAssetCategories] = useState<AssetCategory[]>([]);

  const [selectedAssetId, setSelectedAssetId] = useState('');
  const [selectedItemId, setSelectedItemId] = useState('');

  // Novos estados para a Vertical 3: Módulo de Chamados — Vínculo de Alocações
  const [linkInsumosToAsset, setLinkInsumosToAsset] = useState(false);
  const [targetAssetId, setTargetAssetId] = useState('');
  const [allAssets, setAllAssets] = useState<Asset[]>([]);
  const [loadingAllAssets, setLoadingAllAssets] = useState(false);
  const [quantity, setQuantity] = useState(1);

  // Estados para a funcionalidade de registrar manutenção de ativo no fechamento
  const [registerMaintenance, setRegisterMaintenance] = useState(false);
  const [maintAssetId, setMaintAssetId] = useState('');
  const [maintType, setMaintType] = useState<'PREVENTIVE' | 'CORRECTIVE' | 'UPGRADE' | 'TRANSFER'>('CORRECTIVE');
  const [maintDescription, setMaintDescription] = useState('');
  const [maintCost, setMaintCost] = useState('');

  const [newAssetName, setNewAssetName] = useState('');
  const [newAssetCategoryId, setNewAssetCategoryId] = useState('');
  const [newAssetPatrimonyCode, setNewAssetPatrimonyCode] = useState('');
  const [newAssetSpecifications, setNewAssetSpecifications] = useState('');

  const [loadingAssets, setLoadingAssets] = useState(false);
  const [loadingItems, setLoadingItems] = useState(false);
  const [loadingAssetCategories, setLoadingAssetCategories] = useState(false);
  const [isSubmitting, setIsSubmitting] = useState(false);

  // Filtra as funcionárias originalmente vinculadas ao chamado para a entrega nominal
  const ticketUsers = useMemo(() => {
    if (!ticket) return [];
    const ids = new Set([ticket.requesterId, ...(ticket.additionalUserIds || [])].filter(Boolean) as string[]);
    
    // Filtra utilizadores globais vinculados
    const filtered = users.filter((u) => ids.has(u.id));
    
    // Assegura que a requerente esteja presente na lista
    if (!filtered.some((u) => u.id === ticket.requesterId)) {
      filtered.push({
        id: ticket.requesterId,
        name: ticket.requesterName || 'Requerente',
        email: '',
        role: 'USER',
        sectorId: '',
        sectorName: '',
        location: '',
        discordUserId: null,
        contaAzulId: null,
        receives_it_notifications: false,
      } as User);
    }
    
    return filtered.sort((a, b) => (a.name || '').localeCompare(b.name || ''));
  }, [users, ticket]);

  // Se houver insumos a entregar atrelados ao chamado, ativa a dedução automática
  const hasAutoInventoryDeduction = useMemo(
    () => itemsToDeliver.length > 0,
    [itemsToDeliver],
  );

  // Carrega ativos disponíveis quando o fluxo de entrega de patrimônio estiver ativo.
  useEffect(() => {
    async function loadAssets() {
      if (!isOpen || !deliverEquipment || deliveryType !== 'asset' || assetMode !== 'existing') {
        setAssets([]);
        return;
      }

      setLoadingAssets(true);
      try {
        const allAssetsPage = await getAssets();
        const availableAssets = allAssetsPage.content.filter((asset) => !asset.userId);
        setAssets(availableAssets);
        setSelectedAssetId('');
      } catch {
        toast.error('Erro ao carregar equipamentos disponíveis.');
        setAssets([]);
      } finally {
        setLoadingAssets(false);
      }
    }

    void loadAssets();
  }, [assetMode, deliverEquipment, deliveryType, isOpen]);

  // Carrega categorias para cadastro de novo ativo.
  useEffect(() => {
    async function loadAssetCategories() {
      if (!isOpen || !deliverEquipment || deliveryType !== 'asset' || assetMode !== 'new') {
        setAssetCategories([]);
        return;
      }

      setLoadingAssetCategories(true);
      try {
        const data = await getAssetCategories();
        setAssetCategories(data);
      } catch {
        toast.error('Erro ao carregar categorias de ativo.');
        setAssetCategories([]);
      } finally {
        setLoadingAssetCategories(false);
      }
    }

    void loadAssetCategories();
  }, [assetMode, deliverEquipment, deliveryType, isOpen]);

  // Carrega todos os ativos sem filtro de usuário para a vinculação opcional de insumos
  useEffect(() => {
    async function loadAllAssets() {
      if (!isOpen) {
        setAllAssets([]);
        return;
      }
      setLoadingAllAssets(true);
      try {
        const allAssetsPage = await getAssets({ page: 0, size: 100 });
        setAllAssets(allAssetsPage.content || []);
      } catch (err) {
        console.error('Erro ao carregar lista geral de ativos:', err);
      } finally {
        setLoadingAllAssets(false);
      }
    }
    void loadAllAssets();
  }, [isOpen]);



  // Carrega os usuários associados ao ativo do chamado
  useEffect(() => {
    async function loadAssetUsers() {
      if (!isOpen || !ticket?.assetId) {
        setAssetUsers([]);
        setRecipientUserId('');
        return;
      }

      setLoadingAssetUsers(true);
      try {
        const asset = await getAssetById(ticket.assetId);
        if (asset && asset.userIds && asset.assignedToNames) {
          const mappedUsers = asset.userIds.map((id, index) => ({
            id,
            name: asset.assignedToNames?.[index] || id,
          }));
          setAssetUsers(mappedUsers);
          if (mappedUsers.length > 0) {
            setRecipientUserId(mappedUsers[0].id);
          }
        } else {
          setAssetUsers([]);
          setRecipientUserId('');
        }
      } catch {
        setAssetUsers([]);
        setRecipientUserId('');
      } finally {
        setLoadingAssetUsers(false);
      }
    }

    void loadAssetUsers();
  }, [isOpen, ticket?.assetId]);

  // Carrega itens de consumo com estoque disponível.
  useEffect(() => {
    async function loadItems() {
      if (!isOpen || !deliverEquipment || deliveryType !== 'item') {
        setItems([]);
        return;
      }

      setLoadingItems(true);
      try {
        const allItemsPage = await getItems();
        const availableItems = allItemsPage.content.filter((item) => item.currentStock > 0);
        setItems(availableItems);
        setSelectedItemId('');
      } catch {
        toast.error('Erro ao carregar materiais disponíveis.');
        setItems([]);
      } finally {
        setLoadingItems(false);
      }
    }

    void loadItems();
  }, [deliverEquipment, deliveryType, isOpen]);

  function resetForm() {
    setResolutionNotes('');
    setDeliverEquipment(false);
    setDeliveryType('asset');
    setAssetMode('existing');
    setSelectedAssetId('');
    setSelectedItemId('');
    setQuantity(1);
    setNewAssetName('');
    setNewAssetCategoryId('');
    setNewAssetPatrimonyCode('');
    setNewAssetSpecifications('');
    setRecipientUserId('');
    setAssetUsers([]);
    setItemsToDeliver([]);
    setLinkInsumosToAsset(false);
    setTargetAssetId('');
    setRegisterMaintenance(false);
    setMaintAssetId(ticket?.assetId || '');
    setMaintType('CORRECTIVE');
    setMaintDescription('');
    setMaintCost('');
  }

  function handleRecipientChange(itemId: string, recipientId: string) {
    setItemsToDeliver((prev) =>
      prev.map((item) =>
        item.itemId === itemId ? { ...item, recipientUserId: recipientId } : item
      )
    );
  }

  function validateBeforeSubmit() {
    if (!resolutionNotes.trim()) {
      toast.error('Digite a nota de resolução.');
      return false;
    }

    if (linkInsumosToAsset && !targetAssetId) {
      toast.error('Selecione o equipamento de destino para vinculação dos insumos.');
      return false;
    }

    if (hasAutoInventoryDeduction) {
      const missingRecipient = itemsToDeliver.some((item) => !item.recipientUserId);
      if (missingRecipient) {
        toast.error('Selecione quem recebeu cada um dos insumos solicitados.');
        return false;
      }
    }

    if (!deliverEquipment) {
      return true;
    }

    if (deliveryType === 'asset') {
      if (assetMode === 'existing' && !selectedAssetId) {
        toast.error('Selecione um equipamento para entregar.');
        return false;
      }

      if (assetMode === 'new') {
        if (!newAssetName.trim()) {
          toast.error('Informe o nome do ativo.');
          return false;
        }
        if (!newAssetCategoryId) {
          toast.error('Selecione a categoria do ativo.');
          return false;
        }
        if (!newAssetPatrimonyCode.trim()) {
          toast.error('Informe o código do patrimônio.');
          return false;
        }
      }
    }

    if (deliveryType === 'item') {
      if (!selectedItemId || quantity < 1) {
        toast.error('Selecione um material e quantidade válida.');
        return false;
      }
      if (!recipientUserId) {
        toast.error('Selecione quem recebeu o material de consumo entregue.');
        return false;
      }
    }

    if (registerMaintenance && !maintAssetId) {
      toast.error('Selecione o equipamento para registrar a manutenção.');
      return false;
    }

    return true;
  }

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();

    if (!validateBeforeSubmit()) {
      return;
    }

    setIsSubmitting(true);
    try {
      const finalItemsToDeliver = [...itemsToDeliver];

      // Se o técnico adicionou manualmente a entrega de um insumo avulso
      if (deliverEquipment && deliveryType === 'item' && selectedItemId) {
        finalItemsToDeliver.push({
          itemId: selectedItemId,
          itemName: items.find((i) => i.id === selectedItemId)?.name || 'Insumo',
          quantity: quantity,
          recipientUserId: recipientUserId || requesterId,
        });
      }

      const itemsPayload = finalItemsToDeliver.map((item) => ({
        itemId: item.itemId,
        quantity: item.quantity,
        recipientUserId: item.recipientUserId || undefined,
      }));

      const payload: ResolveTicketRequest = {
        resolutionNotes: resolutionNotes.trim(),
        itemsToDeliver: itemsPayload.length > 0 ? itemsPayload : undefined,
        targetAssetId: linkInsumosToAsset ? targetAssetId : undefined,
      };

      if (registerMaintenance && maintAssetId) {
        payload.maintenance = {
          assetId: maintAssetId,
          type: maintType,
          description: maintDescription.trim() || undefined,
          cost: maintCost ? parseFloat(maintCost) : undefined,
        };
      }

      if (deliverEquipment && deliveryType === 'asset' && assetMode === 'existing') {
        payload.assetIdToDeliver = selectedAssetId;
        payload.recipientUserId = recipientUserId || undefined;
      } else if (deliverEquipment && deliveryType === 'asset' && assetMode === 'new') {
        payload.newAssetToDeliver = {
          userId: requesterId,
          name: newAssetName.trim(),
          patrimonyCode: newAssetPatrimonyCode.trim(),
          categoryId: newAssetCategoryId,
          specifications: newAssetSpecifications.trim() || undefined,
        };
        payload.recipientUserId = recipientUserId || undefined;
      } else if (deliverEquipment && deliveryType === 'item') {
        // Envia recipientUserId global se necessário, mas os itens já vão com destinatário individual
        payload.recipientUserId = recipientUserId || undefined;
      }

      await onResolve(payload);

      resetForm();
      onClose();
    } catch (error) {
      console.error('Erro ao resolver chamado:', error);
    } finally {
      setIsSubmitting(false);
    }
  }

  return {
    resolutionNotes,
    setResolutionNotes,
    deliverEquipment,
    setDeliverEquipment,
    deliveryType,
    setDeliveryType,
    assetMode,
    setAssetMode,
    assets,
    items,
    assetCategories,
    selectedAssetId,
    setSelectedAssetId,
    selectedItemId,
    setSelectedItemId,
    quantity,
    setQuantity,
    newAssetName,
    setNewAssetName,
    newAssetCategoryId,
    setNewAssetCategoryId,
    newAssetPatrimonyCode,
    setNewAssetPatrimonyCode,
    newAssetSpecifications,
    setNewAssetSpecifications,
    loadingAssets,
    loadingItems,
    loadingAssetCategories,
    isSubmitting,
    hasAutoInventoryDeduction,
    recipientUserId,
    setRecipientUserId,
    assetUsers,
    loadingAssetUsers,
    handleSubmit,
    itemsToDeliver,
    handleRecipientChange,
    ticketUsers,
    // Novos campos expostos para vinculação da alocação de insumos
    linkInsumosToAsset,
    setLinkInsumosToAsset,
    targetAssetId,
    setTargetAssetId,
    loadingAllAssets,
    // Estados de registro de manutenção
    registerMaintenance,
    setRegisterMaintenance,
    maintAssetId,
    setMaintAssetId,
    maintType,
    setMaintType,
    maintDescription,
    setMaintDescription,
    maintCost,
    setMaintCost,
    allAssets,
  };
}
