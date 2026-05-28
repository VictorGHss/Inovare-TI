import { useState, useEffect } from 'react';
import { X, Search } from 'lucide-react';
import { toast } from 'react-toastify';

import { createAsset, updateAsset, uploadAssetInvoice } from '../../../services/inventoryService';
import type { Asset, AssetCategory, CreateAssetDto, User } from '../../../types/models';

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
    }
  }, [isOpen, assetToEdit]);

  function resetForm() {
    setFormData({ name: '', patrimonyCode: '', categoryId: '', specifications: '', quantity: 1 });
    setSelectedUserIds([]);
    setSelectedInvoiceFile(null);
    setUserSearch('');
    setShowDropdown(false);
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

    setSubmitting(true);
    try {
      const payload: CreateAssetDto = {
        name: formData.name.trim(),
        patrimonyCode: formData.patrimonyCode.trim(),
        userIds: selectedUserIds,
        categoryId: formData.categoryId || undefined,
        specifications: formData.specifications?.trim() || undefined,
        quantity: formData.quantity && formData.quantity > 0 ? formData.quantity : 1,
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

  const filteredUsers = users.filter((u) => {
    const query = userSearch.toLowerCase().trim();
    if (selectedUserIds.includes(u.id)) return false;
    if (!query) return true;
    return (
      u.name.toLowerCase().includes(query) ||
      u.email.toLowerCase().includes(query) ||
      (u.sectorName && u.sectorName.toLowerCase().includes(query))
    );
  });

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
            <select
              value={formData.categoryId ?? ''}
              onChange={(e) => setFormData((prev) => ({ ...prev, categoryId: e.target.value }))}
              className={inputClassName}
            >
              <option value="">Sem categoria</option>
              {categories.map((cat) => (
                <option key={cat.id} value={cat.id}>
                  {cat.name}
                </option>
              ))}
            </select>
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
