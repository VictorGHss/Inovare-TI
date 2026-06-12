// Modal para registrar entrada de lote de estoque
import { useState, useEffect, useRef, type FormEvent } from 'react';
import { X } from 'lucide-react';
import { toast } from 'react-toastify';
import { addBatch, uploadBatchInvoice } from '../../services/inventoryService';
import type { Item } from '../../types/models';
import SearchableDropdown from '../../components/SearchableDropdown';

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

  const [showSupplierDropdown, setShowSupplierDropdown] = useState(false);
  const supplierRef = useRef<HTMLDivElement>(null);
  
  const defaultSuppliers = [
    'Amazon',
    'Dell Store',
    'HP',
    'Kabum',
    'Kalunga',
    'Lenovo',
    'Logitech',
    'Mercado Livre',
  ];

  // Fechar o dropdown de fornecedores quando o utilizador clica fora
  useEffect(() => {
    function handleClickOutside(event: MouseEvent) {
      if (supplierRef.current && !supplierRef.current.contains(event.target as Node)) {
        setShowSupplierDropdown(false);
      }
    }
    document.addEventListener('mousedown', handleClickOutside);
    return () => document.removeEventListener('mousedown', handleClickOutside);
  }, []);

  // Ordena e filtra os fornecedores alfabeticamente de forma estrita
  const filteredSuppliers = defaultSuppliers
    .filter((sup) => sup.toLowerCase().includes(supplier.toLowerCase()))
    .sort((a, b) => a.localeCompare(b));

  // Mapeia os itens para o formato do dropdown contendo a informação do stock
  const itemOptions = items.map((item) => ({
    id: item.id,
    name: `${item.name} (Estoque: ${item.currentStock})`,
  }));

  // Update selected item when preselected changes
  useEffect(() => {
    if (preselectedItemId) {
      setSelectedItemId(preselectedItemId);
    }
  }, [preselectedItemId]);



  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    if (!selectedItemId || quantity < 1 || !unitPrice) {
      toast.error('Preencha todos os campos corretamente.');
      return;
    }

    setSubmitting(true);
    try {
      const batch = await addBatch(selectedItemId, {
        quantity,
        unitPrice: parseFloat(unitPrice),
        brand: brand.trim() || undefined,
        supplier: supplier.trim() || undefined,
        purchaseReason: purchaseReason.trim() || undefined,
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
          {/* Select de item (pesquisável e ordenado alfabeticamente) */}
          <div className="flex flex-col gap-1.5">
            <label className="text-sm font-medium text-slate-700">
              Item <span className="text-red-500">*</span>
            </label>
            <SearchableDropdown
              options={itemOptions}
              value={selectedItemId}
              onChange={(val) => setSelectedItemId(val)}
              placeholder="Selecione um item..."
            />
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

          {/* Fornecedor (dropdown com sugestões filtradas e ordenadas) */}
          <div className="flex flex-col gap-1.5 relative" ref={supplierRef}>
            <label className="text-sm font-medium text-slate-700">
              Fornecedor
            </label>
            <div className="relative">
              <input
                type="text"
                className="w-full rounded-xl border border-slate-200 bg-white px-4 py-2.5 text-sm text-slate-800 shadow-sm focus:outline-none focus:ring-2 focus:ring-brand-primary focus:border-brand-primary transition"
                value={supplier}
                onChange={(e) => {
                  setSupplier(e.target.value);
                  setShowSupplierDropdown(true);
                }}
                onFocus={() => setShowSupplierDropdown(true)}
                placeholder="Ex: Kabum, Amazon, Kalunga"
                maxLength={150}
              />
              {showSupplierDropdown && (
                <ul className="absolute z-50 mt-1 max-h-48 w-full overflow-y-auto rounded-xl border border-slate-200 bg-white shadow-lg top-full left-0 divide-y divide-slate-50">
                  {filteredSuppliers.length === 0 ? (
                    <li className="px-4 py-2.5 text-xs text-slate-400 text-center">
                      Nenhum fornecedor pré-definido encontrado (pode digitar livremente)
                    </li>
                  ) : (
                    filteredSuppliers.map((sup) => (
                      <li key={sup}>
                        <button
                          type="button"
                          onClick={() => {
                            setSupplier(sup);
                            setShowSupplierDropdown(false);
                          }}
                          className="w-full text-left px-4 py-2.5 text-xs text-slate-700 transition-colors hover:bg-brand-secondary/30 hover:text-brand-primary-dark"
                        >
                          {sup}
                        </button>
                      </li>
                    ))
                  )}
                </ul>
              )}
            </div>
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

