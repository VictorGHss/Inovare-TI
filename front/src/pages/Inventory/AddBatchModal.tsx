// Modal para registrar entrada de lote de estoque
import { useState, useEffect, type FormEvent } from 'react';
import { X } from 'lucide-react';
import { toast } from 'react-toastify';
import { addBatch, uploadBatchInvoice } from '../../services/inventoryService';
import type { Item } from '../../types/models';

interface AddBatchModalProps {
  isOpen: boolean;
  onClose: () => void;
  onSuccess: () => void;
  items: Item[];
  preselectedItemId?: string;
}

export default function AddBatchModal({
  isOpen,
  onClose,
  onSuccess,
  items,
  preselectedItemId,
}: AddBatchModalProps) {
  const [selectedItemId, setSelectedItemId] = useState(preselectedItemId || '');
  const [quantity, setQuantity] = useState(1);
  const [unitPrice, setUnitPrice] = useState('');
  const [brand, setBrand] = useState('');
  const [supplier, setSupplier] = useState('');
  const [purchaseReason, setPurchaseReason] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [selectedInvoiceFile, setSelectedInvoiceFile] = useState<File | null>(null);
  const invoiceInputId = 'batch-invoice-input';

  const [isInstallment, setIsInstallment] = useState(false);
  const [numInstallments, setNumInstallments] = useState(2);
  const [installments, setInstallments] = useState<{ dueDate: string; amount: string }[]>([]);

  // Update selected item when preselected changes
  useEffect(() => {
    if (preselectedItemId) {
      setSelectedItemId(preselectedItemId);
    }
  }, [preselectedItemId]);

  // Calcula parcelas automaticamente ao alterar dados do lote
  useEffect(() => {
    if (!isInstallment) {
      setInstallments([]);
      return;
    }
    const total = (parseFloat(unitPrice) || 0) * (quantity || 0);
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
  }, [isInstallment, numInstallments, quantity, unitPrice]);

  const handleInstallmentChange = (index: number, field: 'dueDate' | 'amount', value: string) => {
    setInstallments((prev) =>
      prev.map((inst, idx) => (idx === index ? { ...inst, [field]: value } : inst))
    );
  };

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    if (!selectedItemId || quantity < 1 || !unitPrice) {
      toast.error('Preencha todos os campos corretamente.');
      return;
    }

    const total = parseFloat(unitPrice) * quantity;
    if (isInstallment) {
      const sumOfInstallments = installments.reduce((acc, inst) => acc + (parseFloat(inst.amount) || 0), 0);
      if (Math.abs(sumOfInstallments - total) > 0.02) {
        toast.error(`A soma das parcelas (R$ ${sumOfInstallments.toFixed(2)}) deve ser igual ao valor total do lote (R$ ${total.toFixed(2)}).`);
        return;
      }
    }

    setSubmitting(true);
    try {
      const batch = await addBatch(selectedItemId, {
        quantity,
        unitPrice: parseFloat(unitPrice),
        brand: brand.trim() || undefined,
        supplier: supplier.trim() || undefined,
        purchaseReason: purchaseReason.trim() || undefined,
        installments: isInstallment
          ? installments.map((inst) => ({
              dueDate: inst.dueDate,
              amount: parseFloat(inst.amount) || 0,
            }))
          : undefined,
      });

      if (selectedInvoiceFile) {
        await uploadBatchInvoice(selectedItemId, batch.id, selectedInvoiceFile);
        toast.success('Lote registrado e nota fiscal anexada com sucesso!');
      } else {
        toast.success('Lote registrado com sucesso!');
      }

      onSuccess();
      // Limpa os campos após sucesso
      setSelectedItemId('');
      setQuantity(1);
      setUnitPrice('');
      setBrand('');
      setSupplier('');
      setPurchaseReason('');
      setSelectedInvoiceFile(null);
      setIsInstallment(false);
      setNumInstallments(2);
      setInstallments([]);
    } catch {
      toast.error('Erro ao registrar lote. Tente novamente.');
    } finally {
      setSubmitting(false);
    }
  }

  if (!isOpen) return null;

  return (
    <div className="fixed inset-0 bg-black/50 backdrop-blur-sm flex items-center justify-center z-50 p-4">
      <div className="bg-white rounded-xl shadow-xl w-full max-w-md">
        {/* Header do modal */}
        <div className="flex items-center justify-between border-b border-slate-200 px-6 py-4">
          <h2 className="text-lg font-bold text-slate-800">
            Registrar Entrada de Lote
          </h2>
          <button
            onClick={onClose}
            className="p-1 rounded-lg hover:bg-slate-100 text-slate-400 hover:text-slate-600 transition-colors"
          >
            <X size={18} />
          </button>
        </div>

        {/* Formulário */}
        <form onSubmit={handleSubmit} className="p-6 flex flex-col gap-4">
          {/* Select de item */}
          <div className="flex flex-col gap-1.5">
            <label className="text-sm font-medium text-slate-700">
              Item <span className="text-red-500">*</span>
            </label>
            <select
              className="w-full rounded-xl border border-slate-200 bg-white px-4 py-2.5 text-sm text-slate-800 shadow-sm focus:outline-none focus:ring-2 focus:ring-brand-primary focus:border-brand-primary transition"
              value={selectedItemId}
              onChange={(e) => setSelectedItemId(e.target.value)}
              required
            >
              <option value="">Selecione um item</option>
              {items.map((item) => (
                <option key={item.id} value={item.id}>
                  {item.name} (Estoque: {item.currentStock})
                </option>
              ))}
            </select>
          </div>

          {/* Quantidade */}
          <div className="flex flex-col gap-1.5">
            <label className="text-sm font-medium text-slate-700">
              Quantidade <span className="text-red-500">*</span>
            </label>
            <input
              type="number"
              min={1}
              className="w-full rounded-xl border border-slate-200 bg-white px-4 py-2.5 text-sm text-slate-800 shadow-sm focus:outline-none focus:ring-2 focus:ring-brand-primary focus:border-brand-primary transition"
              value={quantity}
              onChange={(e) => setQuantity(Math.max(1, parseInt(e.target.value) || 1))}
              required
            />
          </div>

          {/* Preço unitário */}
          <div className="flex flex-col gap-1.5">
            <label className="text-sm font-medium text-slate-700">
              Preço Unitário (R$) <span className="text-red-500">*</span>
            </label>
            <input
              type="number"
              step="0.01"
              min="0.01"
              className="w-full rounded-xl border border-slate-200 bg-white px-4 py-2.5 text-sm text-slate-800 shadow-sm focus:outline-none focus:ring-2 focus:ring-brand-primary focus:border-brand-primary transition"
              value={unitPrice}
              onChange={(e) => setUnitPrice(e.target.value)}
              placeholder="0.00"
              required
            />
          </div>

          {/* Marca */}
          <div className="flex flex-col gap-1.5">
            <label className="text-sm font-medium text-slate-700">
              Marca
            </label>
            <input
              type="text"
              className="w-full rounded-xl border border-slate-200 bg-white px-4 py-2.5 text-sm text-slate-800 shadow-sm focus:outline-none focus:ring-2 focus:ring-brand-primary focus:border-brand-primary transition"
              value={brand}
              onChange={(e) => setBrand(e.target.value)}
              placeholder="Ex: Logitech, HP, Dell"
              maxLength={100}
            />
          </div>

          {/* Fornecedor */}
          <div className="flex flex-col gap-1.5">
            <label className="text-sm font-medium text-slate-700">
              Fornecedor
            </label>
            <input
              type="text"
              className="w-full rounded-xl border border-slate-200 bg-white px-4 py-2.5 text-sm text-slate-800 shadow-sm focus:outline-none focus:ring-2 focus:ring-brand-primary focus:border-brand-primary transition"
              value={supplier}
              onChange={(e) => setSupplier(e.target.value)}
              placeholder="Ex: Kabum, Amazon, Kalunga"
              maxLength={150}
            />
          </div>

          {/* Motivo da Compra */}
          <div className="flex flex-col gap-1.5">
            <label className="text-sm font-medium text-slate-700">
              Motivo da Compra
            </label>
            <input
              type="text"
              className="w-full rounded-xl border border-slate-200 bg-white px-4 py-2.5 text-sm text-slate-800 shadow-sm focus:outline-none focus:ring-2 focus:ring-brand-primary focus:border-brand-primary transition"
              value={purchaseReason}
              onChange={(e) => setPurchaseReason(e.target.value)}
              placeholder="Ex: Reposição mensal, Expansão de TI"
              maxLength={200}
            />
          </div>

          {/* Nota Fiscal */}
          <div className="flex flex-col gap-1.5">
            <label className="text-sm font-medium text-slate-700">
              Nota Fiscal (opcional)
            </label>
            <input
              id={invoiceInputId}
              type="file"
              accept="application/pdf,image/png,image/jpeg,image/jpg"
              onChange={(e) => setSelectedInvoiceFile(e.target.files?.[0] || null)}
              className="sr-only"
            />
            <div className="flex items-center gap-3">
              <label
                htmlFor={invoiceInputId}
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
                  R$ {((parseFloat(unitPrice) || 0) * (quantity || 0)).toFixed(2)}
                </span>
              </div>
            </div>
          )}

          {/* Botões de ação */}
          <div className="flex items-center justify-end gap-3 pt-2">
            <button
              type="button"
              onClick={onClose}
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
                ? (selectedInvoiceFile ? 'Registrando e anexando NF...' : 'Salvando...') 
                : 'Registrar Lote'}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}

