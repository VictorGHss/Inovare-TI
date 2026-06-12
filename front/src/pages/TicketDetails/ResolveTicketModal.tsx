// Modal para resolver chamado com opção de entregar equipamento/material
import { X, Laptop, Box, AlertCircle } from 'lucide-react';

import { useResolveTicket } from '../../hooks/useResolveTicket';
import type { ResolveTicketRequest, Ticket, User } from '../../types/models';
import SearchableDropdown from '../../components/SearchableDropdown';

interface ResolveTicketModalProps {
  isOpen: boolean;
  onClose: () => void;
  requesterId: string;
  onResolve: (request: ResolveTicketRequest) => Promise<void>;
  ticket?: Ticket;
  users?: User[];
  initialNotes?: string;
}

/**
 * Componente modal para encerramento de chamados (resolução).
 * Permite realizar a entrega nominal de ativos de património ou insumos a funcionárias originalmente vinculadas ao chamado.
 */
export default function ResolveTicketModal({
  isOpen,
  onClose,
  requesterId,
  onResolve,
  ticket,
  users = [],
  initialNotes,
}: ResolveTicketModalProps) {
  const {
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
    handleSubmit,
    itemsToDeliver,
    handleRecipientChange,
    ticketUsers,
  } = useResolveTicket({
    isOpen,
    onClose,
    requesterId,
    onResolve,
    ticket,
    users,
    initialNotes,
  });

  if (!isOpen) return null;

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-950/55 p-4 backdrop-blur-sm">
      <div className="max-h-[90vh] w-full max-w-2xl overflow-y-auto rounded-2xl border border-slate-200 bg-white shadow-2xl">
        {/* Cabeçalho */}
        <div className="sticky top-0 z-10 flex items-center justify-between border-b border-slate-200 bg-white/95 px-6 py-4 backdrop-blur">
          <div className="flex items-center gap-3">
            <span className="inline-flex h-8 w-8 items-center justify-center rounded-2xl bg-brand-primary/20">
              <AlertCircle size={16} className="text-brand-primary-dark" />
            </span>
            <h2 className="text-lg font-bold text-slate-800">Resolver Chamado</h2>
          </div>
          <button
            onClick={onClose}
            className="rounded-2xl p-2 text-slate-400 transition-colors hover:bg-brand-secondary/40 hover:text-slate-700"
            disabled={isSubmitting}
          >
            <X size={18} />
          </button>
        </div>

        {/* Formulário */}
        <form onSubmit={handleSubmit} className="flex flex-col gap-6 p-6">
          {/* Nota de resolução */}
          <div className="flex flex-col gap-2">
            <label className="text-sm font-medium text-slate-700">Nota de Resolução *</label>
            <textarea
              value={resolutionNotes}
              onChange={(event) => setResolutionNotes(event.target.value)}
              placeholder="Descreva como o problema foi resolvido..."
              className="w-full resize-none rounded-2xl border border-slate-300 px-3 py-2 text-sm focus:ring-2 focus:ring-brand-primary/50"
              rows={4}
              disabled={isSubmitting}
            />
          </div>

          {hasAutoInventoryDeduction ? (
            <div className="flex flex-col gap-4">
              <div className="flex items-start gap-3 rounded-2xl border border-brand-primary/35 bg-brand-secondary/55 p-4">
                <AlertCircle size={18} className="mt-0.5 shrink-0 text-brand-primary-dark" />
                <div>
                  <p className="text-sm font-semibold text-slate-800">Dedução Automática de Insumos</p>
                  <p className="mt-1 text-sm text-slate-700">
                    Os seguintes itens solicitados serão deduzidos automaticamente do stock ao fechar este chamado:
                  </p>
                </div>
              </div>

              <div className="flex flex-col gap-4 rounded-2xl border border-slate-200 p-4">
                {itemsToDeliver.map((item) => (
                  <div key={item.itemId} className="flex flex-col sm:flex-row gap-4 items-center justify-between border-b border-slate-100 pb-4 last:border-0 last:pb-0">
                    <div className="flex flex-col gap-0.5 flex-1">
                      <span className="text-sm font-bold text-slate-800">{item.itemName}</span>
                      <span className="text-xs text-slate-500">Quantidade: {item.quantity} unidade(s)</span>
                    </div>

                    <div className="flex flex-col gap-1.5 w-full sm:w-64">
                      <label className="text-xs font-semibold text-slate-600">Entregar a quem? *</label>
                      <SearchableDropdown
                        options={ticketUsers.map((u) => ({ id: u.id, name: u.name }))}
                        value={item.recipientUserId}
                        onChange={(val) => handleRecipientChange(item.itemId, val)}
                        placeholder="Selecione quem recebeu..."
                      />
                    </div>
                  </div>
                ))}
              </div>
            </div>
          ) : (
            <>
              <div className="flex items-center gap-2">
                <input
                  type="checkbox"
                  id="deliverEquipment"
                  checked={deliverEquipment}
                  onChange={(event) => setDeliverEquipment(event.target.checked)}
                  className="h-4 w-4 cursor-pointer rounded border-slate-300 text-brand-primary focus:ring-brand-primary"
                  disabled={isSubmitting}
                />
                <label htmlFor="deliverEquipment" className="cursor-pointer text-sm font-medium text-slate-700">
                  Entregar Equipamento ou Material nesta resolução?
                </label>
              </div>

              {deliverEquipment && (
                <div className="flex flex-col gap-4 rounded-2xl border border-brand-primary/40 bg-brand-secondary/50 p-4">
                  <div className="flex flex-wrap gap-2">
                    <button
                      type="button"
                      onClick={() => setDeliveryType('asset')}
                      className={`flex items-center gap-2 rounded-2xl px-4 py-2 text-sm font-semibold transition-colors ${
                        deliveryType === 'asset'
                          ? 'bg-brand-primary text-white'
                          : 'border border-slate-300 bg-white text-slate-700 hover:bg-slate-100'
                      }`}
                      disabled={isSubmitting}
                    >
                      <Laptop size={16} />
                      Ativo de Patrimônio
                    </button>
                    <button
                      type="button"
                      onClick={() => setDeliveryType('item')}
                      className={`flex items-center gap-2 rounded-2xl px-4 py-2 text-sm font-semibold transition-colors ${
                        deliveryType === 'item'
                          ? 'bg-brand-primary text-white'
                          : 'border border-slate-300 bg-white text-slate-700 hover:bg-slate-100'
                      }`}
                      disabled={isSubmitting}
                    >
                      <Box size={16} />
                      Item de Consumo
                    </button>
                  </div>

                  {deliveryType === 'asset' && (
                    <div className="flex flex-col gap-4">
                      <div className="flex flex-wrap gap-2">
                        <button
                          type="button"
                          onClick={() => setAssetMode('existing')}
                          className={`rounded-2xl px-3 py-1.5 text-sm font-semibold transition-colors ${
                            assetMode === 'existing'
                              ? 'bg-brand-primary text-white'
                              : 'border border-slate-300 bg-white text-slate-700 hover:bg-slate-100'
                          }`}
                          disabled={isSubmitting}
                        >
                          Selecionar Existente
                        </button>
                        <button
                          type="button"
                          onClick={() => setAssetMode('new')}
                          className={`rounded-2xl px-3 py-1.5 text-sm font-semibold transition-colors ${
                            assetMode === 'new'
                              ? 'bg-brand-primary text-white'
                              : 'border border-slate-300 bg-white text-slate-700 hover:bg-slate-100'
                          }`}
                          disabled={isSubmitting}
                        >
                          Cadastrar Novo
                        </button>
                      </div>

                      {assetMode === 'existing' && (
                        <div className="flex flex-col gap-2">
                          <label className="text-sm font-medium text-slate-700">Selecione o Equipamento *</label>
                          {loadingAssets ? (
                            <div className="text-sm text-slate-500">Carregando equipamentos...</div>
                          ) : assets.length === 0 ? (
                            <div className="text-sm text-red-600">Nenhum equipamento disponível no estoque da TI.</div>
                          ) : (
                            <select
                              value={selectedAssetId}
                              onChange={(event) => setSelectedAssetId(event.target.value)}
                              className="w-full rounded-2xl border border-slate-300 px-3 py-2 text-sm focus:ring-2 focus:ring-brand-primary/50"
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
                        <div className="grid grid-cols-1 gap-3 md:grid-cols-2">
                          <div className="flex flex-col gap-2 md:col-span-2">
                            <label className="text-sm font-medium text-slate-700">Nome do Ativo *</label>
                            <input
                              type="text"
                              value={newAssetName}
                              onChange={(event) => setNewAssetName(event.target.value)}
                              className="w-full rounded-2xl border border-slate-300 px-3 py-2 text-sm focus:ring-2 focus:ring-brand-primary/50"
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
                                onChange={(event) => setNewAssetCategoryId(event.target.value)}
                                className="w-full rounded-2xl border border-slate-300 px-3 py-2 text-sm focus:ring-2 focus:ring-brand-primary/50"
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
                              onChange={(event) => setNewAssetPatrimonyCode(event.target.value)}
                              className="w-full rounded-2xl border border-slate-300 px-3 py-2 text-sm focus:ring-2 focus:ring-brand-primary/50"
                              placeholder="Ex.: PAT-2026-001"
                              disabled={isSubmitting}
                            />
                          </div>

                          <div className="flex flex-col gap-2 md:col-span-2">
                            <label className="text-sm font-medium text-slate-700">Especificações</label>
                            <textarea
                              value={newAssetSpecifications}
                              onChange={(event) => setNewAssetSpecifications(event.target.value)}
                              className="w-full resize-none rounded-2xl border border-slate-300 px-3 py-2 text-sm focus:ring-2 focus:ring-brand-primary/50"
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
                        <label className="text-sm font-medium text-slate-700">Selecione o Material *</label>
                        {loadingItems ? (
                          <div className="text-sm text-slate-500">Carregando materiais...</div>
                        ) : items.length === 0 ? (
                          <div className="text-sm text-red-600">Nenhum material disponível em estoque.</div>
                        ) : (
                          <select
                            value={selectedItemId}
                            onChange={(event) => setSelectedItemId(event.target.value)}
                            className="w-full rounded-2xl border border-slate-300 px-3 py-2 text-sm focus:ring-2 focus:ring-brand-primary/50"
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
                        <label className="text-sm font-medium text-slate-700">Quantidade *</label>
                        <input
                          type="number"
                          value={quantity}
                          onChange={(event) => setQuantity(Math.max(1, Number.parseInt(event.target.value, 10) || 1))}
                          min="1"
                          className="w-full rounded-2xl border border-slate-300 px-3 py-2 text-sm focus:ring-2 focus:ring-brand-primary/50"
                          disabled={isSubmitting}
                        />
                      </div>

                      <div className="flex flex-col gap-2">
                        <label className="text-sm font-medium text-slate-700">Entregar a quem? *</label>
                        <SearchableDropdown
                          options={ticketUsers.map((u) => ({ id: u.id, name: u.name }))}
                          value={recipientUserId}
                          onChange={(val) => setRecipientUserId(val)}
                          placeholder="Selecione quem recebeu..."
                        />
                      </div>
                    </>
                  )}
                </div>
              )}
            </>
          )}

          {/* Ações */}
          <div className="flex justify-end gap-3 border-t border-slate-200 pt-4">
            <button
              type="button"
              onClick={onClose}
              className="rounded-2xl px-4 py-2 text-sm font-semibold text-slate-700 transition-colors hover:bg-slate-100"
              disabled={isSubmitting}
            >
              Cancelar
            </button>
            <button
              type="submit"
              disabled={isSubmitting}
              className="rounded-2xl bg-brand-primary px-4 py-2.5 text-sm font-semibold text-white transition-colors hover:bg-brand-primary-dark disabled:cursor-not-allowed disabled:opacity-50"
            >
              {isSubmitting ? 'Resolvendo...' : 'Resolver Chamado'}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
