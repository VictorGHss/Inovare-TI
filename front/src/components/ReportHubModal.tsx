import { useState } from 'react';
import { FileSpreadsheet, TrendingUp, TrendingDown, Wrench, X } from 'lucide-react';
import { toast } from 'react-toastify';
import { exportTicketsReport, exportInventoryEntriesReport, exportInventoryExitsReport, exportAssetMaintenancesReport } from '../services/reportService';

function getDefaultCycleDates() {
  const now = new Date();
  const day = now.getDate();
  let start: Date;
  if (day >= 12) {
    start = new Date(now.getFullYear(), now.getMonth(), 12);
  } else {
    start = new Date(now.getFullYear(), now.getMonth() - 1, 12);
  }
  const end = now;
  const format = (d: Date) => `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
  return { start: format(start), end: format(end) };
}

interface ReportHubModalProps {
  isOpen: boolean;
  onClose: () => void;
}

export default function ReportHubModal({ isOpen, onClose }: ReportHubModalProps) {
  const [exporting, setExporting] = useState<string | null>(null);
  const defaults = getDefaultCycleDates();
  const [startDate, setStartDate] = useState<string>(defaults.start);
  const [endDate, setEndDate] = useState<string>(defaults.end);

  const handleExport = async (type: 'tickets' | 'entries' | 'exits' | 'maintenances') => {
    if (startDate > endDate) {
      toast.error('Data Início não pode ser maior que Data Fim.');
      return;
    }

    setExporting(type);
    try {
      let blob: Blob;
      let filename: string;

      if (type === 'tickets') {
        blob = await exportTicketsReport({ startDate, endDate });
        filename = `historico_chamados_${startDate}_to_${endDate}.xlsx`;
      } else if (type === 'entries') {
        blob = await exportInventoryEntriesReport({ startDate, endDate });
        filename = `entradas_estoque_${startDate}_to_${endDate}.xlsx`;
      } else if (type === 'exits') {
        blob = await exportInventoryExitsReport({ startDate, endDate });
        filename = `saidas_estoque_${startDate}_to_${endDate}.xlsx`;
      } else {
        blob = await exportAssetMaintenancesReport({ startDate, endDate });
        filename = `manutencoes_ativos_${startDate}_to_${endDate}.pdf`;
      }

      // Iniciar download
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

        <div className="mb-4 grid grid-cols-2 gap-3">
          <div className="flex flex-col gap-1 text-xs font-semibold uppercase tracking-[0.12em] text-slate-500">
            <label className="text-[11px] text-slate-500">Data Início</label>
            <input
              type="date"
              value={startDate}
              onChange={(e) => setStartDate(e.target.value)}
              className="rounded-xl border border-slate-300 px-3 py-2 text-sm font-medium text-slate-700 focus:border-brand-primary focus:outline-none"
            />
          </div>
          <div className="flex flex-col gap-1 text-xs font-semibold uppercase tracking-[0.12em] text-slate-500">
            <label className="text-[11px] text-slate-500">Data Fim</label>
            <input
              type="date"
              value={endDate}
              onChange={(e) => setEndDate(e.target.value)}
              className="rounded-xl border border-slate-300 px-3 py-2 text-sm font-medium text-slate-700 focus:border-brand-primary focus:outline-none"
            />
          </div>
        </div>

        <p className="text-slate-600 text-sm mb-6">
          Selecione o relatório que deseja exportar em formato Excel:
        </p>

        <div className="space-y-3">
          {/* Histórico de Chamados */}
          <button
            onClick={() => handleExport('tickets')}
            disabled={exporting !== null}
            className="w-full flex items-center gap-3 p-4 border border-slate-200 rounded-lg hover:bg-slate-50 hover:border-brand-primary transition-all disabled:opacity-50 text-left"
          >
            <FileSpreadsheet size={20} className="text-brand-primary flex-shrink-0" />
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
            className="w-full flex items-center gap-3 p-4 border border-slate-200 rounded-lg hover:bg-slate-50 hover:border-brand-secondary transition-all disabled:opacity-50 text-left"
          >
            <TrendingUp size={20} className="text-brand-primary flex-shrink-0" />
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

          {/* Custos de Manutenção */}
          <button
            onClick={() => handleExport('maintenances')}
            disabled={exporting !== null}
            className="w-full flex items-center gap-3 p-4 border border-slate-200 rounded-lg hover:bg-slate-50 hover:border-brand-primary transition-all disabled:opacity-50 text-left"
          >
            <Wrench size={20} className="text-brand-primary flex-shrink-0" />
            <div className="flex-1">
              <div className="font-semibold text-slate-800">
                {exporting === 'maintenances' ? 'Exportando...' : 'Custos de Manutenção'}
              </div>
              <div className="text-xs text-slate-500">
                Consolidado de custos agrupado por equipamento (PDF)
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
