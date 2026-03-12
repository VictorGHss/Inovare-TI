import { useState } from 'react';
import { X } from 'lucide-react';
import { toast } from 'react-toastify';

import {
  createAsset,
  uploadAssetInvoice,
  type AssetCategory,
  type CreateAssetDto,
  type User,
} from '../../../services/api';

const inputClassName =
  'w-full rounded-xl border border-slate-200 bg-white px-3.5 py-2.5 text-sm text-slate-800 shadow-sm placeholder-slate-400 focus:outline-none focus:ring-2 focus:ring-primary focus:border-primary transition';

interface Props {
  isOpen: boolean;
  onClose: () => void;
  users: User[];
  categories: AssetCategory[];
  /** Chamado após a criação bem-sucedida para que o pai recarregue a lista. */
  onCreated: () => void;
}

/**
 * Modal de criação de novo ativo.
 * Gerencia todo o estado do formulário internamente, mantendo o pai limpo.
 */
export default function NewAssetModal({ isOpen, onClose, users, categories, onCreated }: Props) {
  const [assetFileInputId] = useState(`asset-invoice-${Math.random().toString(36).slice(2)}`);
  const [formData, setFormData] = useState<CreateAssetDto>({
    name: '',
    patrimonyCode: '',
    userId: '',
    categoryId: '',
    specifications: '',
    quantity: 1,
  });
  const [selectedInvoiceFile, setSelectedInvoiceFile] = useState<File | null>(null);
  const [submitting, setSubmitting] = useState(false);

  function resetForm() {
    setFormData({ name: '', patrimonyCode: '', userId: '', categoryId: '', specifications: '', quantity: 1 });
    setSelectedInvoiceFile(null);
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
      const asset = await createAsset({
        name: formData.name.trim(),
        patrimonyCode: formData.patrimonyCode.trim(),
        userId: formData.userId || undefined,
        categoryId: formData.categoryId || undefined,
        specifications: formData.specifications?.trim() || undefined,
        quantity: formData.quantity && formData.quantity > 0 ? formData.quantity : 1,
      });

      if (selectedInvoiceFile) {
        await uploadAssetInvoice(asset.id, selectedInvoiceFile);
        toast.success('Ativo cadastrado e nota fiscal anexada com sucesso!');
      } else {
        toast.success('Ativo cadastrado com sucesso!');
      }

      handleClose();
      onCreated();
    } catch {
      toast.error('Erro ao cadastrar ativo. Verifique os dados.');
    } finally {
      setSubmitting(false);
    }
  }

  if (!isOpen) return null;

  return (
    <div className="fixed inset-0 bg-black/50 backdrop-blur-sm flex items-center justify-center z-50 p-4">
      <div className="bg-white rounded-xl shadow-xl w-full max-w-lg">
        <div className="flex items-center justify-between border-b border-slate-200 px-6 py-4">
          <h2 className="text-lg font-bold text-slate-800">Novo Ativo</h2>
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
              Se for maior que 1, o código de patrimônio receberá sufixo sequencial (ex.: MON-00-1,
              MON-00-2).
            </p>
          </div>

          <div className="flex flex-col gap-1.5">
            <label className="text-sm font-medium text-slate-700">Usuário Vinculado</label>
            <select
              value={formData.userId}
              onChange={(e) => setFormData((prev) => ({ ...prev, userId: e.target.value }))}
              className={inputClassName}
            >
              <option value="">Deixar no Estoque da TI</option>
              {users.map((u) => (
                <option key={u.id} value={u.id}>
                  {u.name} ({u.email})
                </option>
              ))}
            </select>
          </div>

          <div className="flex flex-col gap-1.5">
            <label className="text-sm font-medium text-slate-700">Especificações</label>
            <textarea
              value={formData.specifications ?? ''}
              onChange={(e) => setFormData((prev) => ({ ...prev, specifications: e.target.value }))}
              className={inputClassName}
              rows={4}
              placeholder="Ex: CPU i5 12ª gen, 16GB RAM, SSD 512GB"
            />
          </div>

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

          <div className="flex items-center justify-end gap-3 pt-2">
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
              className="bg-primary hover:bg-primary-hover disabled:opacity-60 text-white text-sm font-semibold px-5 py-2.5 rounded-xl transition-colors"
            >
              {submitting
                ? selectedInvoiceFile
                  ? 'Criando e anexando NF...'
                  : 'Salvando...'
                : 'Cadastrar Ativo'}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
