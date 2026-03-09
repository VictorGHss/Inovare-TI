// Página de detalhes de um item — exibe especificações e histórico de lotes
import { useEffect, useState, useCallback } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { ArrowLeft, Package, FileText, Download } from 'lucide-react';
import { toast } from 'react-toastify';
import UploadInvoiceModal from '../../components/UploadInvoiceModal';
import {
  getItemById,
  getItemBatches,
  uploadBatchInvoice,
  downloadBatchInvoice,
  type Item,
  type Batch,
} from '../../services/api';

// Formata data ISO para dd/MM/yyyy
function formatDate(iso: string): string {
  try {
    const date = new Date(iso);
    return date.toLocaleDateString('pt-BR', {
      day: '2-digit',
      month: '2-digit',
      year: 'numeric',
    });
  } catch {
    return '-';
  }
}

// Formata preço em reais
function formatPrice(price: number): string {
  return new Intl.NumberFormat('pt-BR', {
    style: 'currency',
    currency: 'BRL',
  }).format(price);
}

export default function ItemDetails() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const [item, setItem] = useState<Item | null>(null);
  const [batches, setBatches] = useState<Batch[]>([]);
  const [loading, setLoading] = useState(true);
  const [showInvoiceModal, setShowInvoiceModal] = useState(false);
  const [selectedBatchForInvoice, setSelectedBatchForInvoice] = useState<Batch | null>(null);

  const loadData = useCallback(async () => {
    if (!id) return;
    
    try {
      const [itemData, batchesData] = await Promise.all([
        getItemById(id),
        getItemBatches(id),
      ]);
      setItem(itemData);
      setBatches(batchesData);
    } catch {
      toast.error('Item não encontrado.');
      navigate('/inventory');
    } finally {
      setLoading(false);
    }
  }, [id, navigate]);

  useEffect(() => {
    loadData();
  }, [loadData]);

  function openInvoiceModal(batch: Batch) {
    setSelectedBatchForInvoice(batch);
    setShowInvoiceModal(true);
  }

  async function handleInvoiceUpload(file: File) {
    if (!id || !selectedBatchForInvoice) return;

    try {
      await uploadBatchInvoice(id, selectedBatchForInvoice.id, file);
      setShowInvoiceModal(false);
      setSelectedBatchForInvoice(null);
      loadData(); // Recarrega dados para atualizar a tabela
    } catch (error) {
      const message = error instanceof Error ? error.message : 'Erro desconhecido';
      throw new Error(message);
    }
  }

  async function handleInvoiceDownload(batch: Batch, e: React.MouseEvent) {
    if (!id) return;

    e.stopPropagation();

    if (!batch.invoiceFileName) {
      toast.error('Nenhuma nota fiscal anexada a este lote.');
      return;
    }

    try {
      const blob = await downloadBatchInvoice(id, batch.id);
      const url = window.URL.createObjectURL(blob);
      const link = document.createElement('a');
      link.href = url;
      link.download = batch.invoiceFileName;
      document.body.appendChild(link);
      link.click();
      document.body.removeChild(link);
      window.URL.revokeObjectURL(url);
    } catch {
      toast.error('Erro ao baixar nota fiscal.');
    }
  }

  if (loading) {
    return (
      <main className="w-full max-w-full px-4 sm:px-6 lg:px-8 py-8">
        {/* Skeleton de carregamento */}
        <div className="animate-pulse space-y-4">
          <div className="h-6 bg-slate-200 rounded w-1/3" />
          <div className="h-40 bg-slate-100 rounded-xl" />
          <div className="h-32 bg-slate-100 rounded-xl" />
        </div>
      </main>
    );
  }

  if (!item) return null;

  const hasStock = item.currentStock > 0;
  const specs = item.specifications || {};
  const hasSpecs = Object.keys(specs).length > 0;

  return (
    <main className="w-full max-w-full px-4 sm:px-6 lg:px-8 py-8">
      {/* Navegação de retorno */}
      <div className="flex items-center gap-3 mb-6">
        <button
          onClick={() => navigate('/inventory')}
          className="p-1.5 rounded-lg hover:bg-slate-200 text-slate-500 hover:text-slate-700 transition-colors"
          aria-label="Voltar"
        >
          <ArrowLeft size={18} />
        </button>
        <div>
          <p className="text-xs text-slate-400">Detalhes do Item</p>
          <h1 className="text-base font-bold text-slate-800 leading-tight">
            {item.name}
          </h1>
        </div>
      </div>

      {/* Header do item */}
      <div className="bg-white rounded-xl border border-slate-200 shadow-sm p-6 mb-6">
        <div className="flex flex-wrap items-start justify-between gap-3 mb-3">
          <div className="flex-1">
            <h2 className="text-xl font-bold text-slate-800 mb-1">{item.name}</h2>
            <p className="text-sm text-slate-500">{item.itemCategoryName}</p>
          </div>
          <span
            className={`inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium ${
              hasStock ? 'bg-green-100 text-green-700' : 'bg-red-100 text-red-700'
            }`}
          >
            {hasStock ? 'Em Estoque' : 'Sem Estoque'}
          </span>
        </div>
        <div className="mt-4 pt-4 border-t border-slate-100">
          <div className="flex items-center gap-2">
            <Package size={16} className="text-slate-400" />
            <span className="text-sm text-slate-600">
              Estoque Atual:{' '}
              <span className={`font-semibold ${hasStock ? 'text-green-600' : 'text-red-600'}`}>
                {item.currentStock} {item.currentStock === 1 ? 'unidade' : 'unidades'}
              </span>
            </span>
          </div>
        </div>
      </div>

      {/* Card de Especificações */}
      <div className="bg-white rounded-xl border border-slate-200 shadow-sm p-6 mb-6">
        <div className="flex items-center gap-2 mb-4">
          <FileText size={18} className="text-slate-600" />
          <h3 className="text-sm font-semibold text-slate-700">Especificações</h3>
        </div>
        {hasSpecs ? (
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-x-6 gap-y-3">
            {Object.entries(specs).map(([key, value]) => (
              <div key={key} className="flex flex-col">
                <span className="text-xs font-medium text-slate-500 uppercase tracking-wide">
                  {key}
                </span>
                <span className="text-sm text-slate-800 mt-0.5">
                  {String(value)}
                </span>
              </div>
            ))}
          </div>
        ) : (
          <p className="text-sm text-slate-400 italic">Nenhuma especificação cadastrada.</p>
        )}
      </div>

      {/* Card de Histórico de Entradas */}
      <div className="bg-white rounded-xl border border-slate-200 shadow-sm p-6">
        <h3 className="text-sm font-semibold text-slate-700 mb-4">Histórico de Entradas</h3>
        {batches.length > 0 ? (
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead className="border-b border-slate-200 text-xs uppercase tracking-wider text-slate-500">
                <tr>
                  <th className="pb-3 text-left font-medium">Data de Entrada</th>
                  <th className="pb-3 text-right font-medium">Qtd. Comprada</th>
                  <th className="pb-3 text-right font-medium">Qtd. Restante</th>
                  <th className="pb-3 text-right font-medium">Preço Unitário</th>
                  <th className="pb-3 text-center font-medium">Nota Fiscal</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {batches.map((batch) => (
                  <tr key={batch.id} className="hover:bg-slate-50 transition-colors">
                    <td className="py-3 text-slate-700">{formatDate(batch.entryDate)}</td>
                    <td className="py-3 text-right text-slate-700">{batch.originalQuantity}</td>
                    <td className="py-3 text-right">
                      <span
                        className={
                          batch.remainingQuantity > 0 ? 'text-green-600 font-medium' : 'text-slate-400'
                        }
                      >
                        {batch.remainingQuantity}
                      </span>
                    </td>
                    <td className="py-3 text-right text-slate-700 font-medium">
                      {formatPrice(batch.unitPrice)}
                    </td>
                    <td className="py-3">
                      <div className="flex items-center justify-center">
                        {batch.invoiceFileName ? (
                          <button
                            onClick={(e) => handleInvoiceDownload(batch, e)}
                            className="flex items-center gap-1.5 text-xs font-medium text-green-600 hover:text-green-700 hover:bg-green-50 px-2 py-1 rounded transition-colors"
                            title="Visualizar/baixar nota fiscal"
                          >
                            <Download size={13} />
                            Ver NF
                          </button>
                        ) : (
                          <button
                            onClick={(e) => {
                              e.stopPropagation();
                              openInvoiceModal(batch);
                            }}
                            className="flex items-center gap-1.5 text-xs font-medium text-slate-500 hover:text-slate-700 hover:bg-slate-100 px-2 py-1 rounded transition-colors"
                            title="Anexar nota fiscal"
                          >
                            <FileText size={13} />
                            Anexar
                          </button>
                        )}
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        ) : (
          <p className="text-sm text-slate-400 italic">Nenhum lote registrado ainda.</p>
        )}
      </div>

      {/* Modal de Nota Fiscal */}
      <UploadInvoiceModal
        isOpen={showInvoiceModal}
        onClose={() => {
          setShowInvoiceModal(false);
          setSelectedBatchForInvoice(null);
        }}
        onUpload={handleInvoiceUpload}
        entityName="Lote"
        entityId={selectedBatchForInvoice?.id ?? ''}
      />
    </main>
  );
}
