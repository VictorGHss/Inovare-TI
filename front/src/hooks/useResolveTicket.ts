import { useEffect, useMemo, useState, type FormEvent } from 'react';
import { toast } from 'react-toastify';

import { getAssetCategories, getAssets, getItems } from '../services/inventoryService';
import type { Asset, AssetCategory, Item, ResolveTicketRequest, Ticket } from '../types/models';

interface UseResolveTicketParams {
  isOpen: boolean;
  onClose: () => void;
  requesterId: string;
  onResolve: (request: ResolveTicketRequest) => Promise<void>;
  ticket?: Ticket;
  initialNotes?: string;
}

export function useResolveTicket({
  isOpen,
  onClose,
  requesterId,
  onResolve,
  ticket,
  initialNotes = '',
}: UseResolveTicketParams) {
  const [resolutionNotes, setResolutionNotes] = useState('');

  useEffect(() => {
    if (isOpen) {
      setResolutionNotes(initialNotes);
    }
  }, [isOpen, initialNotes]);

  const [deliverEquipment, setDeliverEquipment] = useState(false);
  const [deliveryType, setDeliveryType] = useState<'asset' | 'item'>('asset');
  const [assetMode, setAssetMode] = useState<'existing' | 'new'>('existing');

  const [assets, setAssets] = useState<Asset[]>([]);
  const [items, setItems] = useState<Item[]>([]);
  const [assetCategories, setAssetCategories] = useState<AssetCategory[]>([]);

  const [selectedAssetId, setSelectedAssetId] = useState('');
  const [selectedItemId, setSelectedItemId] = useState('');
  const [quantity, setQuantity] = useState(1);

  const [newAssetName, setNewAssetName] = useState('');
  const [newAssetCategoryId, setNewAssetCategoryId] = useState('');
  const [newAssetPatrimonyCode, setNewAssetPatrimonyCode] = useState('');
  const [newAssetSpecifications, setNewAssetSpecifications] = useState('');

  const [loadingAssets, setLoadingAssets] = useState(false);
  const [loadingItems, setLoadingItems] = useState(false);
  const [loadingAssetCategories, setLoadingAssetCategories] = useState(false);
  const [isSubmitting, setIsSubmitting] = useState(false);

  const hasAutoInventoryDeduction = useMemo(
    () => !!ticket?.requestedItemId && !!ticket?.requestedQuantity,
    [ticket?.requestedItemId, ticket?.requestedQuantity],
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
        const allAssets = await getAssets();
        const availableAssets = allAssets.filter((asset) => !asset.userId);
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

  // Carrega itens de consumo com estoque disponível.
  useEffect(() => {
    async function loadItems() {
      if (!isOpen || !deliverEquipment || deliveryType !== 'item') {
        setItems([]);
        return;
      }

      setLoadingItems(true);
      try {
        const allItems = await getItems();
        const availableItems = allItems.filter((item) => item.currentStock > 0);
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
  }

  function validateBeforeSubmit() {
    if (!resolutionNotes.trim()) {
      toast.error('Digite a nota de resolução.');
      return false;
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

    if (deliveryType === 'item' && (!selectedItemId || quantity < 1)) {
      toast.error('Selecione um material e quantidade válida.');
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
      if (deliverEquipment && deliveryType === 'asset' && assetMode === 'existing') {
        await onResolve({
          resolutionNotes,
          assetIdToDeliver: selectedAssetId,
        });
      } else if (deliverEquipment && deliveryType === 'asset' && assetMode === 'new') {
        await onResolve({
          resolutionNotes,
          newAssetToDeliver: {
            userId: requesterId,
            name: newAssetName.trim(),
            patrimonyCode: newAssetPatrimonyCode.trim(),
            categoryId: newAssetCategoryId,
            specifications: newAssetSpecifications.trim() || undefined,
          },
        });
      } else if (deliverEquipment && deliveryType === 'item') {
        await onResolve({
          resolutionNotes,
          inventoryItemIdToDeliver: selectedItemId,
          quantityToDeliver: quantity,
        });
      } else {
        await onResolve({ resolutionNotes });
      }

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
    handleSubmit,
  };
}
