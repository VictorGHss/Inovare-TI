// Modal para resolver chamado com opção de entregar equipamento/material
import { useState, useEffect, type FormEvent } from 'react';
import { X, Laptop, Box } from 'lucide-react';
import { toast } from 'react-toastify';
import { getAssets, getItems, type Asset, type Item } from '../../services/api';

interface ResolveTicketModalProps {
  isOpen: boolean;
  onClose: () => void;
  onResolve: (resolutionNotes: string, assetId?: string, itemId?: string, quantity?: number) => Promise<void>;
  isSubmitting: boolean;
}

export default function ResolveTicketModal({
  isOpen,
  onClose,
  onResolve,
  isSubmitting,
}: ResolveTicketModalProps) {
  const [resolutionNotes, setResolutionNotes] = useState('');
  const [deliverEquipment, setDeliverEquipment] = useState(false);
  const [deliveryType, setDeliveryType] = useState<'asset' | 'item'>('asset');
  const [assets, setAssets] = useState<Asset[]>([]);
  const [items, setItems] = useState<Item[]>([]);
  const [selectedAssetId, setSelectedAssetId] = useState('');
  const [selectedItemId, setSelectedItemId] = useState('');
  const [quantity, setQuantity] = useState(1);
  const [loadingAssets, setLoadingAssets] = useState(false);
  const [loadingItems, setLoadingItems] = useState(false);

  // Carrega assets ao abrir modal ou ao mudar deliverEquipment
  useEffect(() => {
    async function loadAssets() {
      if (!deliverEquipment || deliveryType !== 'asset') {
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
  }, [deliverEquipment, deliveryType]);

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
      if (deliveryType === 'asset' && !selectedAssetId) {
        toast.error('Selecione um equipamento para entregar.');
        return;
      }

      if (deliveryType === 'item' && (!selectedItemId || quantity < 1)) {
        toast.error('Selecione um material e quantidade válida.');
        return;
      }
    }

    try {
      if (deliverEquipment && deliveryType === 'asset') {
        await onResolve(resolutionNotes, selectedAssetId, undefined, undefined);
      } else if (deliverEquipment && deliveryType === 'item') {
        await onResolve(resolutionNotes, undefined, selectedItemId, quantity);
      } else {
        await onResolve(resolutionNotes, undefined, undefined, undefined);
      }

      setResolutionNotes('');
      setDeliverEquipment(false);
      setSelectedAssetId('');
      setSelectedItemId('');
      setQuantity(1);
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
              className="w-full px-3 py-2 border border-slate-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 resize-none"
              rows={4}
              disabled={isSubmitting}
            />
          </div>

          {/* Deliver Equipment Checkbox */}
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

          {/* Delivery Options */}
          {deliverEquipment && (
            <div className="border border-blue-200 bg-blue-50 rounded-lg p-4 flex flex-col gap-4">
              {/* Delivery Type Tabs */}
              <div className="flex gap-2">
                <button
                  type="button"
                  onClick={() => setDeliveryType('asset')}
                  className={`flex items-center gap-2 px-4 py-2 rounded-lg font-medium transition-colors ${
                    deliveryType === 'asset'
                      ? 'bg-blue-600 text-white'
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
                      ? 'bg-blue-600 text-white'
                      : 'bg-white text-slate-700 border border-slate-300 hover:bg-slate-100'
                  }`}
                  disabled={isSubmitting}
                >
                  <Box size={16} />
                  Item de Consumo
                </button>
              </div>

              {/* Asset Selection */}
              {deliveryType === 'asset' && (
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
                      className="w-full px-3 py-2 border border-slate-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
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

              {/* Item Selection */}
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
                        className="w-full px-3 py-2 border border-slate-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
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

                  {/* Quantity Input */}
                  <div className="flex flex-col gap-2">
                    <label className="text-sm font-medium text-slate-700">
                      Quantidade *
                    </label>
                    <input
                      type="number"
                      value={quantity}
                      onChange={(e) => setQuantity(Math.max(1, parseInt(e.target.value) || 1))}
                      min="1"
                      className="w-full px-3 py-2 border border-slate-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
                      disabled={isSubmitting}
                    />
                  </div>
                </>
              )}
            </div>
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
              className="px-4 py-2.5 bg-blue-600 hover:bg-blue-700 text-white font-medium rounded-lg transition-colors disabled:opacity-50 disabled:cursor-not-allowed"
            >
              {isSubmitting ? 'Resolvendo...' : 'Resolver Chamado'}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
