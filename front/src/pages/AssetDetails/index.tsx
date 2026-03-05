import { useEffect, useState, useCallback } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { ArrowLeft, HardDrive, FileText, Download, Loader2, Wrench } from 'lucide-react';
import { toast } from 'react-toastify';
import {
  getAssetById,
  downloadAssetInvoice,
  getAssetMaintenances,
  type Asset,
  type AssetMaintenance,
} from '../../services/api';
import NewMaintenanceModal from './NewMaintenanceModal';
import MaintenanceTimeline from './MaintenanceTimeline';

export default function AssetDetails() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const [asset, setAsset] = useState<Asset | null>(null);
  const [loading, setLoading] = useState(true);
  const [maintenances, setMaintenances] = useState<AssetMaintenance[]>([]);
  const [loadingMaintenances, setLoadingMaintenances] = useState(false);
  const [isMaintenanceModalOpen, setIsMaintenanceModalOpen] = useState(false);

  const loadAsset = useCallback(async () => {
    if (!id) return;
    
    try {
      const assetData = await getAssetById(id);
      setAsset(assetData);
    } catch {
      toast.error('Ativo não encontrado.');
      navigate('/assets');
    } finally {
      setLoading(false);
    }
  }, [id, navigate]);

  const loadMaintenances = useCallback(async () => {
    if (!id) return;
    setLoadingMaintenances(true);
    try {
      const data = await getAssetMaintenances(id);
      setMaintenances(data);
    } catch {
      toast.error('Erro ao carregar manutenções.');
    } finally {
      setLoadingMaintenances(false);
    }
  }, [id]);

  useEffect(() => {
    loadAsset();
    loadMaintenances();
  }, [loadAsset, loadMaintenances]);

  async function handleInvoiceDownload(e: React.MouseEvent) {
    e.stopPropagation();

    if (!asset?.invoiceFileName) {
      toast.error('Nenhuma nota fiscal anexada a este ativo.');
      return;
    }

    try {
      const blob = await downloadAssetInvoice(asset.id);
      const url = window.URL.createObjectURL(blob);
      const link = document.createElement('a');
      link.href = url;
      link.download = asset.invoiceFileName;
      document.body.appendChild(link);
      link.click();
      document.body.removeChild(link);
      window.URL.revokeObjectURL(url);
    } catch {
      toast.error('Erro ao baixar nota fiscal.');
    }
  }

  function handleMaintenanceCreated(newMaintenance: AssetMaintenance) {
    setMaintenances([newMaintenance, ...maintenances]);
    setIsMaintenanceModalOpen(false);
  }

  if (loading) {
    return (
      <main className="max-w-5xl mx-auto px-4 sm:px-6 py-8">
        <div className="flex items-center justify-center h-64">
          <div className="flex flex-col items-center gap-3">
            <Loader2 size={40} className="text-primary animate-spin" />
            <p className="text-slate-600">Carregando ativo...</p>
          </div>
        </div>
      </main>
    );
  }

  if (!asset) return null;

  return (
    <main className="max-w-5xl mx-auto px-4 sm:px-6 py-8">
      {/* Navegação de retorno */}
      <div className="flex items-center gap-3 mb-6">
        <button
          onClick={() => navigate('/assets')}
          className="p-1.5 rounded-lg hover:bg-slate-200 text-slate-500 hover:text-slate-700 transition-colors"
          aria-label="Voltar"
        >
          <ArrowLeft size={18} />
        </button>
        <div>
          <p className="text-xs text-slate-400">Detalhes do Ativo</p>
          <h1 className="text-base font-bold text-slate-800 leading-tight">
            {asset.name}
          </h1>
        </div>
      </div>

      {/* Header do ativo */}
      <div className="bg-white rounded-xl border border-slate-200 shadow-sm p-6 mb-6">
        <div className="flex flex-wrap items-start justify-between gap-4 mb-4">
          <div className="flex-1">
            <div className="flex items-center gap-2 mb-2">
              <HardDrive size={20} className="text-primary" />
              <h2 className="text-xl font-bold text-slate-800">{asset.name}</h2>
            </div>
            <p className="text-sm text-slate-500">Código do Patrimônio</p>
          </div>
        </div>

        {/* Código do patrimônio em destaque */}
        <div className="bg-slate-50 rounded-lg p-3 mb-4 border border-slate-200">
          <p className="text-xs font-medium text-slate-500 uppercase tracking-wide mb-1">
            Patrimônio
          </p>
          <p className="text-lg font-bold text-slate-800 font-mono">
            {asset.patrimonyCode}
          </p>
        </div>

        {/* Usuário vinculado */}
        <div className="flex items-center gap-2 pt-3 border-t border-slate-100">
          <div className="w-8 h-8 rounded-full bg-primary/10 flex items-center justify-center">
            <span className="text-sm font-semibold text-primary">U</span>
          </div>
          <div className="flex-1">
            <p className="text-xs text-slate-500">Usuário Vinculado</p>
            <p className="text-sm font-medium text-slate-700">{asset.userId}</p>
          </div>
        </div>
      </div>

      {/* Card de Especificações */}
      {asset.specifications && asset.specifications.trim() && (
        <div className="bg-white rounded-xl border border-slate-200 shadow-sm p-6 mb-6">
          <div className="flex items-center gap-2 mb-4">
            <FileText size={18} className="text-slate-600" />
            <h3 className="text-sm font-semibold text-slate-700">Especificações</h3>
          </div>
          <div className="bg-slate-50 rounded-lg p-4 border border-slate-200">
            <p className="text-sm text-slate-700 whitespace-pre-wrap">
              {asset.specifications}
            </p>
          </div>
        </div>
      )}

      {/* Card de Nota Fiscal */}
      {asset.invoiceFileName && (
        <div className="bg-white rounded-xl border border-slate-200 shadow-sm p-6">
          <div className="flex items-center justify-between">
            <div>
              <h3 className="text-sm font-semibold text-slate-700 mb-1">Nota Fiscal</h3>
              <p className="text-sm text-slate-500">{asset.invoiceFileName}</p>
            </div>
            <button
              onClick={handleInvoiceDownload}
              className="flex items-center gap-2 bg-green-600 hover:bg-green-700 text-white text-sm font-semibold px-4 py-2.5 rounded-lg transition-colors"
            >
              <Download size={16} />
              Baixar NF
            </button>
          </div>
        </div>
      )}

      {/* Mensagem quando não há nota fiscal */}
      {!asset.invoiceFileName && (
        <div className="bg-slate-50 rounded-xl border border-slate-200 p-6 text-center">
          <FileText size={24} className="mx-auto text-slate-400 mb-2" />
          <p className="text-sm text-slate-500">
            Nenhuma nota fiscal anexada a este ativo.
          </p>
        </div>
      )}

      {/* Seção de Histórico de Manutenções */}
      <div className="mt-8">
        <div className="flex items-center justify-between mb-4">
          <div className="flex items-center gap-2">
            <Wrench size={20} className="text-slate-600" />
            <h3 className="text-lg font-bold text-slate-800">Histórico de Manutenções</h3>
          </div>
          <button
            onClick={() => setIsMaintenanceModalOpen(true)}
            className="px-4 py-2 bg-primary hover:bg-primary-dark text-white text-sm font-semibold rounded-lg transition-colors"
          >
            Registrar Manutenção
          </button>
        </div>

        {loadingMaintenances ? (
          <div className="flex items-center justify-center h-48">
            <div className="flex flex-col items-center gap-3">
              <Loader2 size={32} className="text-primary animate-spin" />
              <p className="text-slate-600 text-sm">Carregando manutenções...</p>
            </div>
          </div>
        ) : (
          <MaintenanceTimeline maintenances={maintenances} />
        )}
      </div>

      {/* Modal de Nova Manutenção */}
      {id && (
        <NewMaintenanceModal
          isOpen={isMaintenanceModalOpen}
          assetId={id}
          onClose={() => setIsMaintenanceModalOpen(false)}
          onMaintenanceCreated={handleMaintenanceCreated}
        />
      )}
    </main>
  );
}
