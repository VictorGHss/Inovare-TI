import { useState, useEffect } from 'react';
import { X, Search } from 'lucide-react';
import { toast } from 'react-toastify';

import { createAsset, updateAsset, uploadAssetInvoice } from '../../../services/inventoryService';
import type { Asset, AssetCategory, CreateAssetDto, User } from '../../../types/models';
import SearchableDropdown from '../../../components/SearchableDropdown';

const inputClassName =
  'w-full rounded-xl border border-slate-200 bg-white px-3.5 py-2.5 text-sm text-slate-800 shadow-sm placeholder-slate-400 focus:outline-none focus:ring-2 focus:ring-brand-primary focus:border-brand-primary transition';

interface Props {
  isOpen: boolean;
  onClose: () => void;
  users: User[];
  categories: AssetCategory[];
  /** Chamado após a criação ou edição bem-sucedida para que o pai recarregue a lista. */
  onCreated: () => void;
  assetToEdit?: Asset | null;
}

/**
 * Modal de criação e edição de ativos.
 * Gerencia o estado do formulário internamente e implementa a seleção elegante de múltiplos usuários.
 */
export default function NewAssetModal({ isOpen, onClose, users, categories, onCreated, assetToEdit }: Props) {
  const [assetFileInputId] = useState(`asset-invoice-${Math.random().toString(36).slice(2)}`);
  
  const [formData, setFormData] = useState<CreateAssetDto>({
    name: '',
    patrimonyCode: '',
    categoryId: '',
    specifications: '',
    quantity: 1,
  });

  const [selectedUserIds, setSelectedUserIds] = useState<string[]>([]);
  const [userSearch, setUserSearch] = useState('');
  const [showDropdown, setShowDropdown] = useState(false);
  
  const [selectedInvoiceFile, setSelectedInvoiceFile] = useState<File | null>(null);
  const [submitting, setSubmitting] = useState(false);

  const [isInstallment, setIsInstallment] = useState(false);
  const [numInstallments, setNumInstallments] = useState(2);
  const [installments, setInstallments] = useState<{ dueDate: string; amount: string }[]>([]);
  const [totalValue, setTotalValue] = useState('');

  // Calcula parcelas automaticamente ao alterar dados do financiamento
  useEffect(() => {
    if (!isInstallment) {
      setInstallments([]);
      return;
    }
    const total = parseFloat(totalValue) || 0;
    const baseAmount = total / numInstallments;
    const list = Array.from({ length: numInstallments }).map((_, idx) => {
      const d = new Date();
      d.setMonth(d.getMonth() + idx);
      const dateStr = d.toISOString().split('T')[0];

      let amt = baseAmount.toFixed(2);
      if (idx === numInstallments - 1) {
        const sumOfPrev = parseFloat(baseAmount.toFixed(2)) * (numInstallments - 1);
        amt = (total - sumOfPrev).toFixed(2);
      }

      return {
        dueDate: dateStr,
        amount: amt,
      };
    });
    setInstallments(list);
  }, [isInstallment, numInstallments, totalValue]);

  const handleInstallmentChange = (index: number, field: 'dueDate' | 'amount', value: string) => {
    setInstallments((prev) =>
      prev.map((inst, idx) => (idx === index ? { ...inst, [field]: value } : inst))
    );
  };

  useEffect(() => {
    if (isOpen) {
      if (assetToEdit) {
        setFormData({
          name: assetToEdit.name || '',
          patrimonyCode: assetToEdit.patrimonyCode || '',
          categoryId: assetToEdit.categoryId || '',
          specifications: assetToEdit.specifications || '',
          quantity: 1,
        });
        const ids = assetToEdit.userIds || (assetToEdit.userId ? [assetToEdit.userId] : []);
        setSelectedUserIds(ids);
      } else {
        setFormData({
          name: '',
          patrimonyCode: '',
          categoryId: '',
          specifications: '',
          quantity: 1,
        });
        setSelectedUserIds([]);
      }
      setSelectedInvoiceFile(null);
      setUserSearch('');
      setShowDropdown(false);
      setIsInstallment(false);
      setNumInstallments(2);
      setInstallments([]);
      setTotalValue('');
    }
  }, [isOpen, assetToEdit]);

  function resetForm() {
    setFormData({ name: '', patrimonyCode: '', categoryId: '', specifications: '', quantity: 1 });
    setSelectedUserIds([]);
    setSelectedInvoiceFile(null);
    setUserSearch('');
    setShowDropdown(false);
    setIsInstallment(false);
    setNumInstallments(2);
    setInstallments([]);
    setTotalValue('');
  }

  function handleClose() {
    resetForm();
    onClose();
  }

  async function handleSubmit(event: React.FormEvent) {
    event.preventDefault();

    if (!formData.name.trim() || !formData.patrimonyCode.trim()) {
      toast.error('Preencha todos os campos obrigatórios.');
      return;
    }

    const total = parseFloat(totalValue) || 0;
    if (isInstallment) {
      if (total <= 0) {
        toast.error('O valor total do equipamento deve ser maior que zero para parcelamento.');
        return;
      }
      const sumOfInstallments = installments.reduce((acc, inst) => acc + (parseFloat(inst.amount) || 0), 0);
      if (Math.abs(sumOfInstallments - total) > 0.02) {
        toast.error(`A soma das parcelas (R$ ${sumOfInstallments.toFixed(2)}) deve ser igual ao valor total do equipamento (R$ ${total.toFixed(2)}).`);
        return;
      }
    }

    setSubmitting(true);
    try {
      // Garanta o envio da lista completa de userIds atualizada no payload do service.
      const payload: CreateAssetDto = {
        name: formData.name.trim(),
        patrimonyCode: formData.patrimonyCode.trim(),
        userIds: selectedUserIds || [],
        categoryId: formData.categoryId || undefined,
        specifications: formData.specifications?.trim() || undefined,
        quantity: formData.quantity && formData.quantity > 0 ? formData.quantity : 1,
        installments: isInstallment
          ? installments.map((inst) => ({
              dueDate: inst.dueDate,
              amount: parseFloat(inst.amount) || 0,
            }))
          : undefined,
      };

      if (assetToEdit) {
        await updateAsset(assetToEdit.id, payload);
        toast.success('Ativo atualizado com sucesso!');
      } else {
        const asset = await createAsset(payload);
        if (selectedInvoiceFile) {
          await uploadAssetInvoice(asset.id, selectedInvoiceFile);
          toast.success('Ativo cadastrado e nota fiscal anexada com sucesso!');
        } else {
          toast.success('Ativo cadastrado com sucesso!');
        }
      }

      handleClose();
      onCreated();
    } catch {
      toast.error(assetToEdit ? 'Erro ao atualizar ativo. Verifique os dados.' : 'Erro ao cadastrar ativo. Verifique os dados.');
    } finally {
      setSubmitting(false);
    }
  }

  const filteredUsers = users
    .filter((u) => {
      const query = userSearch.toLowerCase().trim();
      if (selectedUserIds.includes(u.id)) return false;
      if (!query) return true;
      return (
        u.name.toLowerCase().includes(query) ||
        u.email.toLowerCase().includes(query) ||
        (u.sectorName && u.sectorName.toLowerCase().includes(query))
      );
    })
    .sort((a, b) => a.name.localeCompare(b.name));

  if (!isOpen) return null;

  return (
    <div className="fixed inset-0 bg-black/50 backdrop-blur-sm flex items-center justify-center z-50 p-4">
      {showDropdown && (
        <div className="fixed inset-0 z-10" onClick={() => setShowDropdown(false)} />
      )}
      
      <div className="bg-white rounded-xl shadow-xl w-full max-w-lg max-h-[90vh] overflow-y-auto z-20">
        <div className="flex items-center justify-between border-b border-slate-200 px-6 py-4">
          <h2 className="text-lg font-bold text-slate-800">
            {assetToEdit ? 'Editar Ativo' : 'Novo Ativo'}
          </h2>
          <button
            onClick={handleClose}
            className="p-1 rounded-lg hover:bg-slate-100 text-slate-400 hover:text-slate-600 transition-colors"
          >
            <X size={18} />
          </button>
        </div>

        <form onSubmit={handleSubmit} className="p-6 flex flex-col gap-4">
          <div className="flex flex-col gap-1.5">
            <label className="text-sm font-medium text-slate-700">
              Nome <span className="text-red-500">*</span>
            </label>
            <input
              type="text"
              value={formData.name}
              onChange={(e) => setFormData((prev) => ({ ...prev, name: e.target.value }))}
              className={inputClassName}
              placeholder="Ex: Desktop Dell OptiPlex 7010"
              maxLength={150}
              required
            />
          </div>

          <div className="flex flex-col gap-1.5">
            <label className="text-sm font-medium text-slate-700">
              Código do Patrimônio <span className="text-red-500">*</span>
            </label>
            <input
              type="text"
              value={formData.patrimonyCode}
              onChange={(e) => setFormData((prev) => ({ ...prev, patrimonyCode: e.target.value }))}
              className={inputClassName}
              placeholder="Ex: INV-2026-00421"
              maxLength={80}
              required
            />
          </div>

          <div className="flex flex-col gap-1.5">
            <label className="text-sm font-medium text-slate-700">Categoria</label>
            <SearchableDropdown
              options={[{ id: '', name: 'Sem categoria' }, ...categories]}
              value={formData.categoryId ?? ''}
              onChange={(val) => setFormData((prev) => ({ ...prev, categoryId: val }))}
              placeholder="Sem categoria"
            />
          </div>

          {/* Oculta quantidade (lote) na edição */}
          {!assetToEdit && (
            <div className="flex flex-col gap-1.5">
              <label className="text-sm font-medium text-slate-700">Quantidade a cadastrar (Lote)</label>
              <input
                type="number"
                min={1}
                value={formData.quantity ?? 1}
                onChange={(e) => {
                  const value = Math.max(1, Number.parseInt(e.target.value, 10) || 1);
                  setFormData((prev) => ({ ...prev, quantity: value }));
                }}
                className={inputClassName}
              />
              <p className="text-xs text-slate-500">
                Se for maior que 1, o código de patrimônio receberá sufixo sequencial (ex.: MON-00-1, MON-00-2).
              </p>
            </div>
          )}

          {/* Múltiplos Usuários Vinculados com Busca e Chips */}
          <div className="flex flex-col gap-1.5 relative">
            <label className="text-sm font-medium text-slate-700">Colaboradores Vinculados</label>
            
            {/* Visualização de Chips dos usuários selecionados */}
            {selectedUserIds.length > 0 && (
              <div className="flex flex-wrap gap-1.5 p-2 bg-slate-50 border border-slate-200 rounded-xl mb-1">
                {selectedUserIds.map((id) => {
                  const u = users.find((user) => user.id === id);
                  const fallbackName = `Usuário ${id.slice(0, 8).toUpperCase()}`;
                  return (
                    <span
                      key={id}
                      className="inline-flex items-center gap-1.5 bg-brand-secondary/40 text-brand-primary-dark rounded-full px-3 py-1 text-xs font-semibold shadow-sm transition-all"
                    >
                      <span>{u?.name ?? fallbackName}</span>
                      <button
                        type="button"
                        onClick={() => setSelectedUserIds((prev) => prev.filter((item) => item !== id))}
                        className="text-brand-primary hover:text-brand-primary-dark focus:outline-none transition-colors"
                      >
                        <X size={13} className="stroke-[3]" />
                      </button>
                    </span>
                  );
                })}
              </div>
            )}

            {/* Input de Busca */}
            <div className="relative z-20">
              <input
                type="text"
                value={userSearch}
                onFocus={() => setShowDropdown(true)}
                onChange={(e) => setUserSearch(e.target.value)}
                placeholder={
                  selectedUserIds.length > 0
                    ? 'Pesquisar para adicionar mais colaboradores...'
                    : 'Deixar no estoque (TI) / Vincular colaboradores...'
                }
                className={`${inputClassName} pl-10`}
              />
              <div className="absolute left-3 top-1/2 -translate-y-1/2 text-slate-400">
                <Search size={16} />
              </div>
            </div>

            {/* Dropdown com os usuários filtrados */}
            {showDropdown && (
              <div className="absolute left-0 right-0 top-full mt-1 max-h-56 overflow-y-auto bg-white border border-slate-200 rounded-xl shadow-xl z-30 divide-y divide-slate-50">
                {filteredUsers.length === 0 ? (
                  <div className="p-3.5 text-sm text-slate-400 text-center">
                    Nenhum colaborador disponível encontrado
                  </div>
                ) : (
                  filteredUsers.map((u) => (
                    <button
                      key={u.id}
                      type="button"
                      onClick={() => {
                        setSelectedUserIds((prev) => [...prev, u.id]);
                        setUserSearch('');
                        setShowDropdown(false);
                      }}
                      className="w-full text-left px-4 py-2.5 hover:bg-orange-50/50 text-sm text-slate-700 flex items-center justify-between transition-colors"
                    >
                      <div>
                        <div className="font-semibold text-slate-800">{u.name}</div>
                        <div className="text-xs text-slate-400">{u.email}</div>
                      </div>
                      {u.sectorName && (
                        <span className="text-xs font-semibold bg-slate-100 text-slate-600 rounded-full px-2.5 py-0.5">
                          {u.sectorName}
                        </span>
                      )}
                    </button>
                  ))
                )}
              </div>
            )}
          </div>

          <div className="flex flex-col gap-1.5">
            <label className="text-sm font-medium text-slate-700">Especificações</label>
            <textarea
              value={formData.specifications ?? ''}
              onChange={(e) => setFormData((prev) => ({ ...prev, specifications: e.target.value }))}
              className={inputClassName}
              rows={3}
              placeholder="Ex: CPU i5 12ª gen, 16GB RAM, SSD 512GB"
            />
          </div>

          {/* Oculta anexo de NF na edição, mantendo o histórico de auditoria original do ativo */}
          {!assetToEdit && (
            <div className="flex flex-col gap-1.5">
              <label className="text-sm font-medium text-slate-700">Nota Fiscal (opcional)</label>
              <input
                id={assetFileInputId}
                type="file"
                accept="application/pdf,image/png,image/jpeg,image/jpg"
                onChange={(e) => setSelectedInvoiceFile(e.target.files?.[0] || null)}
                className="sr-only"
              />
              <div className="flex items-center gap-3">
                <label
                  htmlFor={assetFileInputId}
                  className="inline-flex items-center px-3 py-2 bg-brand-primary text-white rounded-lg text-sm cursor-pointer hover:opacity-90 transition-opacity"
                >
                  Selecionar Arquivo
                </label>
                <span className="text-xs text-slate-600 truncate">
                  {selectedInvoiceFile?.name ?? 'Nenhum arquivo anexado'}
                </span>
              </div>
              <p className="text-xs text-slate-500">Máximo 5MB. Formatos: PDF, PNG, JPG.</p>
            </div>
          )}

          {!assetToEdit && (
            <>
              {/* Valor de Aquisição */}
              <div className="flex flex-col gap-1.5">
                <label className="text-sm font-medium text-slate-700">Valor Total de Aquisição (R$)</label>
                <input
                  type="number"
                  step="0.01"
                  min="0.00"
                  value={totalValue}
                  onChange={(e) => setTotalValue(e.target.value)}
                  className={inputClassName}
                  placeholder="0.00"
                  disabled={submitting}
                />
              </div>

              {/* Compra Parcelada? */}
              <div className="flex items-center gap-2 mt-1">
                <input
                  type="checkbox"
                  id="isInstallment"
                  checked={isInstallment}
                  onChange={(e) => setIsInstallment(e.target.checked)}
                  className="h-4 w-4 cursor-pointer rounded border-slate-350 text-[#feb56c] focus:ring-[#feb56c]"
                  disabled={submitting}
                />
                <label htmlFor="isInstallment" className="cursor-pointer text-sm font-semibold text-slate-700">
                  Compra Parcelada?
                </label>
              </div>

              {isInstallment && (
                <div className="flex flex-col gap-3 rounded-2xl border border-[#feb56c]/35 bg-slate-50/50 p-4">
                  <div className="flex flex-col gap-1.5">
                    <label className="text-xs font-semibold text-slate-600">Número de Parcelas</label>
                    <input
                      type="number"
                      min={2}
                      max={24}
                      value={numInstallments}
                      onChange={(e) => setNumInstallments(Math.max(2, parseInt(e.target.value) || 2))}
                      className="w-full rounded-2xl border border-slate-200 bg-white px-3.5 py-2.5 text-sm text-slate-800 shadow-sm focus:outline-none focus:ring-2 focus:ring-[#feb56c]/60 focus:border-[#feb56c] transition-all"
                      disabled={submitting}
                    />
                  </div>

                  <div className="flex flex-col gap-2 max-h-48 overflow-y-auto mt-2 pr-1">
                    {installments.map((inst, index) => (
                      <div key={index} className="flex gap-2 items-center">
                        <span className="text-xs text-slate-500 font-bold w-12 shrink-0">Parc. {index + 1}</span>
                        <input
                          type="date"
                          value={inst.dueDate}
                          onChange={(e) => handleInstallmentChange(index, 'dueDate', e.target.value)}
                          className="rounded-2xl border border-slate-200 bg-white px-3 py-2 text-xs text-slate-800 focus:outline-none focus:ring-2 focus:ring-[#feb56c]/60 focus:border-[#feb56c] flex-1"
                          disabled={submitting}
                        />
                        <input
                          type="number"
                          step="0.01"
                          min="0.01"
                          value={inst.amount}
                          onChange={(e) => handleInstallmentChange(index, 'amount', e.target.value)}
                          className="rounded-2xl border border-slate-200 bg-white px-3 py-2 text-xs text-slate-800 focus:outline-none focus:ring-2 focus:ring-[#feb56c]/60 focus:border-[#feb56c] w-24 text-right font-semibold"
                          disabled={submitting}
                        />
                      </div>
                    ))}
                  </div>

                  <div className="flex justify-between items-center text-xs text-slate-500 font-semibold border-t border-slate-200/60 pt-2 mt-1">
                    <span>Valor Total:</span>
                    <span className="text-slate-800 font-bold">
                      R$ {(parseFloat(totalValue) || 0).toFixed(2)}
                    </span>
                  </div>
                </div>
              )}
            </>
          )}

          <div className="flex items-center justify-end gap-3 pt-2 border-t border-slate-100">
            <button
              type="button"
              onClick={handleClose}
              className="text-sm text-slate-500 hover:text-slate-700 px-4 py-2.5 rounded-xl transition-colors"
            >
              Cancelar
            </button>
            <button
              type="submit"
              disabled={submitting}
              className="bg-brand-primary hover:bg-brand-primary-dark disabled:opacity-60 text-white text-sm font-semibold px-5 py-2.5 rounded-xl transition-colors"
            >
              {submitting
                ? selectedInvoiceFile
                  ? 'Criando e anexando NF...'
                  : 'Salvando...'
                : assetToEdit
                ? 'Atualizar Ativo'
                : 'Cadastrar Ativo'}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
