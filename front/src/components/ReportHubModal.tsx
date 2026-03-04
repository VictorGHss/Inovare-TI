import { useState } from 'react';
import { FileSpreadsheet, TrendingUp, TrendingDown, X } from 'lucide-react';
import { toast } from 'react-toastify';
import {
  exportTicketsReport,
  exportInventoryEntriesReport,
  exportInventoryExitsReport,
} from '../services/api';

interface ReportHubModalProps {
  isOpen: boolean;
  onClose: () => void;
}

export default function ReportHubModal({ isOpen, onClose }: ReportHubModalProps) {
  const [exporting, setExporting] = useState<string | null>(null);

  const handleExport = async (type: 'tickets' | 'entries' | 'exits') => {
    setExporting(type);
    try {
      let blob: Blob;
      let filename: string;

      if (type === 'tickets') {
        blob = await exportTicketsReport();
        filename = `historico_chamados_${new Date().toISOString().slice(0, 10)}.xlsx`;
      } else if (type === 'entries') {
        blob = await exportInventoryEntriesReport();
        filename = `entradas_estoque_${new Date().toISOString().slice(0, 10)}.xlsx`;
      } else {
        blob = await exportInventoryExitsReport();
        filename = `saidas_estoque_${new Date().toISOString().slice(0, 10)}.xlsx`;
      }

      // Trigger download
      const url = window.URL.createObjectURL(blob);
      const link = document.createElement('a');
      link.href = url;
      link.download = filename;
      document.body.appendChild(link);
      link.click();
      document.body.removeChild(link);
      window.URL.revokeObjectURL(url);

      toast.success('Relatório exportado com sucesso!');
    } catch (error) {
      console.error('Export error:', error);
      toast.error('Erro ao exportar relatório.');
    } finally {
      setExporting(null);
    }
  };

  if (!isOpen) return null;

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 backdrop-blur-sm">
      <div className="bg-white rounded-xl shadow-lg p-6 w-full max-w-md">
        <div className="flex items-center justify-between mb-6">
          <h2 className="text-xl font-bold text-slate-800">Central de Relatórios</h2>
          <button
            onClick={onClose}
            className="text-slate-500 hover:text-slate-700 transition-colors"
          >
            <X size={24} />
          </button>
        </div>

        <p className="text-slate-600 text-sm mb-6">
          Selecione o relatório que deseja exportar em formato Excel:
        </p>

        <div className="space-y-3">
          {/* Histórico de Chamados */}
          <button
            onClick={() => handleExport('tickets')}
            disabled={exporting !== null}
            className="w-full flex items-center gap-3 p-4 border border-slate-200 rounded-lg hover:bg-slate-50 hover:border-primary transition-all disabled:opacity-50 text-left"
          >
            <FileSpreadsheet size={20} className="text-primary flex-shrink-0" />
            <div className="flex-1">
              <div className="font-semibold text-slate-800">
                {exporting === 'tickets' ? 'Exportando...' : 'Histórico de Chamados'}
              </div>
              <div className="text-xs text-slate-500">
                Lista completa de todos os chamados do sistema
              </div>
            </div>
          </button>

          {/* Entradas de Estoque */}
          <button
            onClick={() => handleExport('entries')}
            disabled={exporting !== null}
            className="w-full flex items-center gap-3 p-4 border border-slate-200 rounded-lg hover:bg-slate-50 hover:border-green-600 transition-all disabled:opacity-50 text-left"
          >
            <TrendingUp size={20} className="text-green-600 flex-shrink-0" />
            <div className="flex-1">
              <div className="font-semibold text-slate-800">
                {exporting === 'entries' ? 'Exportando...' : 'Entradas de Estoque'}
              </div>
              <div className="text-xs text-slate-500">
                Histórico de compras com valores unitários
              </div>
            </div>
          </button>

          {/* Saídas de Estoque */}
          <button
            onClick={() => handleExport('exits')}
            disabled={exporting !== null}
            className="w-full flex items-center gap-3 p-4 border border-slate-200 rounded-lg hover:bg-slate-50 hover:border-orange-600 transition-all disabled:opacity-50 text-left"
          >
            <TrendingDown size={20} className="text-orange-600 flex-shrink-0" />
            <div className="flex-1">
              <div className="font-semibold text-slate-800">
                {exporting === 'exits' ? 'Exportando...' : 'Saídas de Estoque'}
              </div>
              <div className="text-xs text-slate-500">
                Consumo de itens por setor e usuário
              </div>
            </div>
          </button>
        </div>

        <div className="mt-6 pt-6 border-t border-slate-200">
          <button
            onClick={onClose}
            className="w-full bg-slate-100 hover:bg-slate-200 text-slate-800 font-semibold py-2.5 rounded-lg transition-colors"
          >
            Fechar
          </button>
        </div>
      </div>
    </div>
  );
}
