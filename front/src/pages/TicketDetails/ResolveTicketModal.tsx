// Modal para resolver chamado com opção de entregar equipamento/material
import { useState, useEffect, type FormEvent } from 'react';
import { X, Laptop, Box, AlertCircle } from 'lucide-react';
import { toast } from 'react-toastify';
import {
  getAssets,
  getItems,
  getAssetCategories,
  type Asset,
  type Item,
  type AssetCategory,
  type ResolveTicketRequest,
  type Ticket,
} from '../../services/api';

interface ResolveTicketModalProps {
  isOpen: boolean;
  onClose: () => void;
  requesterId: string;
  onResolve: (request: ResolveTicketRequest) => Promise<void>;
  isSubmitting: boolean;
  ticket?: Ticket;
}

export default function ResolveTicketModal({
  isOpen,
  onClose,
  requesterId,
  onResolve,
  isSubmitting,
  ticket,
}: ResolveTicketModalProps) {
  const [resolutionNotes, setResolutionNotes] = useState('');
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
  const hasAutoInventoryDeduction = !!ticket?.requestedItemId && !!ticket?.requestedQuantity;

  // Carrega assets ao abrir modal ou ao mudar deliverEquipment
  useEffect(() => {
    async function loadAssets() {
      if (!deliverEquipment || deliveryType !== 'asset' || assetMode !== 'existing') {
        setAssets([]);
        return;
      }

      setLoadingAssets(true);
      try {
        const allAssets = await getAssets();
        // Filtra assets sem dono (disponíveis no estoque da TI)
        const availableAssets = allAssets.filter(a => !a.userId);
        setAssets(availableAssets);
        setSelectedAssetId('');
      } catch {
        toast.error('Erro ao carregar equipamentos disponíveis.');
        setAssets([]);
      } finally {
        setLoadingAssets(false);
      }
    }

    loadAssets();
  }, [deliverEquipment, deliveryType, assetMode]);

  useEffect(() => {
    async function loadAssetCategories() {
      if (!deliverEquipment || deliveryType !== 'asset' || assetMode !== 'new') {
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

    loadAssetCategories();
  }, [deliverEquipment, deliveryType, assetMode]);

  // Carrega items ao abrir modal ou ao mudar deliverEquipment
  useEffect(() => {
    async function loadItems() {
      if (!deliverEquipment || deliveryType !== 'item') {
        setItems([]);
        return;
      }

      setLoadingItems(true);
      try {
        const allItems = await getItems();
        // Filtra items com estoque disponível
        const availableItems = allItems.filter(i => i.currentStock > 0);
        setItems(availableItems);
        setSelectedItemId('');
      } catch {
        toast.error('Erro ao carregar materiais disponíveis.');
        setItems([]);
      } finally {
        setLoadingItems(false);
      }
    }

    loadItems();
  }, [deliverEquipment, deliveryType]);

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();

    if (!resolutionNotes.trim()) {
      toast.error('Digite a nota de resolução.');
      return;
    }

    if (deliverEquipment) {
      if (deliveryType === 'asset') {
        if (assetMode === 'existing' && !selectedAssetId) {
          toast.error('Selecione um equipamento para entregar.');
          return;
        }

        if (assetMode === 'new') {
          if (!newAssetName.trim()) {
            toast.error('Informe o nome do ativo.');
            return;
          }
          if (!newAssetCategoryId) {
            toast.error('Selecione a categoria do ativo.');
            return;
          }
          if (!newAssetPatrimonyCode.trim()) {
            toast.error('Informe o código do patrimônio.');
            return;
          }
        }
      }

      if (deliveryType === 'item' && (!selectedItemId || quantity < 1)) {
        toast.error('Selecione um material e quantidade válida.');
        return;
      }
    }

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
      onClose();
    } catch (error) {
      console.error('Erro ao resolver chamado:', error);
    }
  }

  if (!isOpen) return null;

  return (
    <div className="fixed inset-0 bg-black/50 backdrop-blur-sm flex items-center justify-center z-50 p-4">
      <div className="bg-white rounded-xl shadow-xl w-full max-w-2xl max-h-[90vh] overflow-y-auto">
        {/* Header */}
        <div className="flex items-center justify-between border-b border-slate-200 px-6 py-4 sticky top-0 bg-white">
          <h2 className="text-lg font-bold text-slate-800">Resolver Chamado</h2>
          <button
            onClick={onClose}
            className="p-1 rounded-lg hover:bg-slate-100 text-slate-400 hover:text-slate-600 transition-colors"
          >
            <X size={18} />
          </button>
        </div>

        {/* Form */}
        <form onSubmit={handleSubmit} className="p-6 flex flex-col gap-6">
          {/* Resolution Notes */}
          <div className="flex flex-col gap-2">
            <label className="text-sm font-medium text-slate-700">
              Nota de Resolução *
            </label>
            <textarea
              value={resolutionNotes}
              onChange={(e) => setResolutionNotes(e.target.value)}
              placeholder="Descreva como o problema foi resolvido..."
              className="w-full px-3 py-2 border border-slate-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-brand-primary resize-none"
              rows={4}
              disabled={isSubmitting}
            />
          </div>

          {hasAutoInventoryDeduction ? (
            <div className="flex items-start gap-3 p-4 bg-blue-50 border border-blue-200 rounded-lg">
              <AlertCircle size={18} className="text-blue-600 flex-shrink-0 mt-0.5" />
              <div>
                <p className="text-sm font-medium text-blue-900">Dedução Automática</p>
                <p className="text-sm text-blue-700 mt-1">
                  O item <strong>{ticket?.requestedItemName ?? 'selecionado'}</strong> ({ticket?.requestedQuantity} unidade{ticket?.requestedQuantity && ticket.requestedQuantity > 1 ? 's' : ''}) será deduzido automaticamente do stock ao fechar este chamado.
                </p>
              </div>
            </div>
          ) : (
            <>
              <div className="flex items-center gap-2">
                <input
                  type="checkbox"
                  id="deliverEquipment"
                  checked={deliverEquipment}
                  onChange={(e) => setDeliverEquipment(e.target.checked)}
                  className="w-4 h-4 cursor-pointer"
                  disabled={isSubmitting}
                />
                <label htmlFor="deliverEquipment" className="text-sm font-medium text-slate-700 cursor-pointer">
                  Entregar Equipamento ou Material nesta resolução?
                </label>
              </div>

              {deliverEquipment && (
                <div className="border border-brand-primary bg-brand-secondary rounded-lg p-4 flex flex-col gap-4">
                  <div className="flex gap-2">
                    <button
                      type="button"
                      onClick={() => setDeliveryType('asset')}
                      className={`flex items-center gap-2 px-4 py-2 rounded-lg font-medium transition-colors ${
                        deliveryType === 'asset'
                          ? 'bg-brand-primary text-white'
                          : 'bg-white text-slate-700 border border-slate-300 hover:bg-slate-100'
                      }`}
                      disabled={isSubmitting}
                    >
                      <Laptop size={16} />
                      Ativo de Patrimônio
                    </button>
                    <button
                      type="button"
                      onClick={() => setDeliveryType('item')}
                      className={`flex items-center gap-2 px-4 py-2 rounded-lg font-medium transition-colors ${
                        deliveryType === 'item'
                          ? 'bg-brand-primary text-white'
                          : 'bg-white text-slate-700 border border-slate-300 hover:bg-slate-100'
                      }`}
                      disabled={isSubmitting}
                    >
                      <Box size={16} />
                      Item de Consumo
                    </button>
                  </div>

                  {deliveryType === 'asset' && (
                    <div className="flex flex-col gap-4">
                      <div className="flex gap-2">
                        <button
                          type="button"
                          onClick={() => setAssetMode('existing')}
                          className={`px-3 py-1.5 rounded-lg text-sm font-medium transition-colors ${
                            assetMode === 'existing'
                              ? 'bg-brand-primary text-white'
                              : 'bg-white text-slate-700 border border-slate-300 hover:bg-slate-100'
                          }`}
                          disabled={isSubmitting}
                        >
                          Selecionar Existente
                        </button>
                        <button
                          type="button"
                          onClick={() => setAssetMode('new')}
                          className={`px-3 py-1.5 rounded-lg text-sm font-medium transition-colors ${
                            assetMode === 'new'
                              ? 'bg-brand-primary text-white'
                              : 'bg-white text-slate-700 border border-slate-300 hover:bg-slate-100'
                          }`}
                          disabled={isSubmitting}
                        >
                          Cadastrar Novo
                        </button>
                      </div>

                      {assetMode === 'existing' && (
                        <div className="flex flex-col gap-2">
                          <label className="text-sm font-medium text-slate-700">
                            Selecione o Equipamento *
                          </label>
                          {loadingAssets ? (
                            <div className="text-sm text-slate-500">Carregando equipamentos...</div>
                          ) : assets.length === 0 ? (
                            <div className="text-sm text-red-600">Nenhum equipamento disponível no estoque da TI.</div>
                          ) : (
                            <select
                              value={selectedAssetId}
                              onChange={(e) => setSelectedAssetId(e.target.value)}
                              className="w-full px-3 py-2 border border-slate-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-brand-primary"
                              disabled={isSubmitting}
                            >
                              <option value="">-- Selecione um equipamento --</option>
                              {assets.map((asset) => (
                                <option key={asset.id} value={asset.id}>
                                  {asset.name} ({asset.patrimonyCode})
                                </option>
                              ))}
                            </select>
                          )}
                        </div>
                      )}

                      {assetMode === 'new' && (
                        <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
                          <div className="flex flex-col gap-2 md:col-span-2">
                            <label className="text-sm font-medium text-slate-700">Nome do Ativo *</label>
                            <input
                              type="text"
                              value={newAssetName}
                              onChange={(e) => setNewAssetName(e.target.value)}
                              className="w-full px-3 py-2 border border-slate-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-brand-primary"
                              placeholder="Ex.: Notebook Dell Latitude"
                              disabled={isSubmitting}
                            />
                          </div>

                          <div className="flex flex-col gap-2">
                            <label className="text-sm font-medium text-slate-700">Categoria *</label>
                            {loadingAssetCategories ? (
                              <div className="text-sm text-slate-500">Carregando categorias...</div>
                            ) : (
                              <select
                                value={newAssetCategoryId}
                                onChange={(e) => setNewAssetCategoryId(e.target.value)}
                                className="w-full px-3 py-2 border border-slate-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-brand-primary"
                                disabled={isSubmitting}
                              >
                                <option value="">-- Selecione a categoria --</option>
                                {assetCategories.map((category) => (
                                  <option key={category.id} value={category.id}>
                                    {category.name}
                                  </option>
                                ))}
                              </select>
                            )}
                          </div>

                          <div className="flex flex-col gap-2">
                            <label className="text-sm font-medium text-slate-700">Código do Patrimônio *</label>
                            <input
                              type="text"
                              value={newAssetPatrimonyCode}
                              onChange={(e) => setNewAssetPatrimonyCode(e.target.value)}
                              className="w-full px-3 py-2 border border-slate-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-brand-primary"
                              placeholder="Ex.: PAT-2026-001"
                              disabled={isSubmitting}
                            />
                          </div>

                          <div className="flex flex-col gap-2 md:col-span-2">
                            <label className="text-sm font-medium text-slate-700">Especificações</label>
                            <textarea
                              value={newAssetSpecifications}
                              onChange={(e) => setNewAssetSpecifications(e.target.value)}
                              className="w-full px-3 py-2 border border-slate-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-brand-primary resize-none"
                              rows={3}
                              placeholder="CPU, memória, armazenamento, etc."
                              disabled={isSubmitting}
                            />
                          </div>
                        </div>
                      )}
                    </div>
                  )}

                  {deliveryType === 'item' && (
                    <>
                      <div className="flex flex-col gap-2">
                        <label className="text-sm font-medium text-slate-700">
                          Selecione o Material *
                        </label>
                        {loadingItems ? (
                          <div className="text-sm text-slate-500">Carregando materiais...</div>
                        ) : items.length === 0 ? (
                          <div className="text-sm text-red-600">Nenhum material disponível em estoque.</div>
                        ) : (
                          <select
                            value={selectedItemId}
                            onChange={(e) => setSelectedItemId(e.target.value)}
                            className="w-full px-3 py-2 border border-slate-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-brand-primary"
                            disabled={isSubmitting}
                          >
                            <option value="">-- Selecione um material --</option>
                            {items.map((item) => (
                              <option key={item.id} value={item.id}>
                                {item.name} (Estoque: {item.currentStock})
                              </option>
                            ))}
                          </select>
                        )}
                      </div>

                      <div className="flex flex-col gap-2">
                        <label className="text-sm font-medium text-slate-700">
                          Quantidade *
                        </label>
                        <input
                          type="number"
                          value={quantity}
                          onChange={(e) => setQuantity(Math.max(1, parseInt(e.target.value, 10) || 1))}
                          min="1"
                          className="w-full px-3 py-2 border border-slate-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-brand-primary"
                          disabled={isSubmitting}
                        />
                      </div>
                    </>
                  )}
                </div>
              )}
            </>
          )}

          {/* Actions */}
          <div className="flex gap-3 justify-end pt-4 border-t border-slate-200">
            <button
              type="button"
              onClick={onClose}
              className="px-4 py-2 text-slate-700 hover:bg-slate-100 rounded-lg font-medium transition-colors"
              disabled={isSubmitting}
            >
              Cancelar
            </button>
            <button
              type="submit"
              disabled={isSubmitting}
              className="px-4 py-2.5 bg-brand-primary hover:bg-brand-primary-dark text-white font-medium rounded-lg transition-colors disabled:opacity-50 disabled:cursor-not-allowed"
            >
              {isSubmitting ? 'Resolvendo...' : 'Resolver Chamado'}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
