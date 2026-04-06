import { useEffect, useState, useCallback } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import {
  ArrowLeft,
  HardDrive,
  FileText,
  Download,
  Loader2,
  Wrench,
  RefreshCw,
  Printer,
  User,
  PackageOpen,
  ClipboardList,
} from 'lucide-react';
import { toast } from 'react-toastify';
import { getAssetById, downloadAssetInvoice, getAssetMaintenances } from '../../services/inventoryService';
import type { Asset, AssetMaintenance } from '../../types/domain';
import NewMaintenanceModal from './NewMaintenanceModal';
import MaintenanceTimeline from './MaintenanceTimeline';
import TransferAssetModal from './TransferAssetModal';
import PrintLabelModal from '../../components/PrintLabelModal';
import PageHero from '../../components/PageHero';

export default function AssetDetails() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const [asset, setAsset] = useState<Asset | null>(null);
  const [loading, setLoading] = useState(true);
  const [maintenances, setMaintenances] = useState<AssetMaintenance[]>([]);
  const [loadingMaintenances, setLoadingMaintenances] = useState(false);
  const [isMaintenanceModalOpen, setIsMaintenanceModalOpen] = useState(false);
  const [isTransferModalOpen, setIsTransferModalOpen] = useState(false);
  const [isPrintModalOpen, setIsPrintModalOpen] = useState(false);

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

  async function handleTransferSuccess(updatedAsset: Asset) {
    setAsset(updatedAsset);
    await loadMaintenances();
    setIsTransferModalOpen(false);
  }

  if (loading) {
    return (
      <main className="w-full max-w-full px-4 sm:px-6 lg:px-8 py-8 bg-orange-50/20 min-h-screen">
        <div className="flex items-center justify-center h-64">
          <div className="flex flex-col items-center gap-3">
            <Loader2 size={36} className="text-brand-primary animate-spin" />
            <p className="text-sm text-slate-500">Carregando ativo...</p>
          </div>
        </div>
      </main>
    );
  }

  if (!asset) return null;

  const operationalStatus = maintenances[0]?.type === 'Corretiva' ? 'Manutenção' : 'Ativo';
  const allocationStatus = asset.assignedToName || asset.userId ? 'Em Uso' : 'Em Estoque';

  return (
    <main className="w-full max-w-full px-4 sm:px-6 lg:px-8 py-8 bg-orange-50/20 min-h-screen">

      <PageHero
        eyebrow="CMDB"
        title={asset.name}
        description="Consulte informações do ativo, histórico de manutenção, transferência e anexos fiscais."
        actions={(
          <button
            onClick={() => navigate('/assets')}
            className="inline-flex items-center gap-2 rounded-xl border border-slate-200 bg-white px-4 py-2.5 text-sm font-semibold text-slate-700 shadow-sm transition-colors hover:bg-slate-50"
            aria-label="Voltar"
          >
            <ArrowLeft size={15} />
            Voltar
          </button>
        )}
      />

      {/* ── Asset identity card ── */}
      <div className="bg-white rounded-2xl border border-slate-200 shadow-sm p-6 mb-5">
        <div className="flex flex-wrap items-center justify-between gap-4">
          <div className="flex items-center gap-3">
            <span className="inline-flex h-10 w-10 shrink-0 items-center justify-center rounded-xl bg-brand-primary/10">
              <HardDrive size={20} className="text-brand-primary-dark" />
            </span>
            <div>
              <h2 className="text-lg font-extrabold text-slate-800">{asset.name}</h2>
              <p className="mt-0.5 text-xs text-slate-400">Detalhes e histórico do ativo</p>
              <div className="mt-2 flex flex-wrap gap-2">
                <span className="inline-flex items-center rounded-full bg-brand-secondary/40 px-2.5 py-0.5 text-xs font-semibold text-brand-primary-dark">
                  {operationalStatus}
                </span>
                <span className="inline-flex items-center rounded-full bg-brand-secondary/40 px-2.5 py-0.5 text-xs font-semibold text-brand-primary-dark">
                  {allocationStatus}
                </span>
              </div>
            </div>
          </div>

          <div className="flex flex-wrap gap-2">
            <button
              onClick={() => setIsPrintModalOpen(true)}
              className="inline-flex items-center gap-2 rounded-xl border border-slate-200 bg-white px-4 py-2.5 text-sm font-semibold text-slate-700 shadow-sm transition-colors hover:bg-slate-50"
            >
              <Printer size={15} />
              Imprimir Etiqueta
            </button>
            <button
              onClick={() => setIsTransferModalOpen(true)}
              className="inline-flex items-center gap-2 rounded-xl bg-amber-500 px-4 py-2.5 text-sm font-semibold text-white shadow-sm transition-colors hover:bg-amber-600"
            >
              <RefreshCw size={15} />
              Transferir Ativo
            </button>
          </div>
        </div>

        <div className="mt-6 grid gap-4 sm:grid-cols-2">
          {/* Patrimony code */}
          <div className="rounded-xl border border-brand-primary/20 bg-gradient-to-br from-brand-secondary/40 to-brand-secondary/10 p-4">
            <p className="text-[10px] font-bold uppercase tracking-widest text-brand-primary-dark mb-1.5">
              Código de Patrimônio
            </p>
            <p className="font-mono text-xl font-extrabold text-slate-900 tracking-wide">
              {asset.patrimonyCode}
            </p>
          </div>

          {/* Assigned user */}
          <div className="rounded-xl border border-slate-200 bg-slate-50 p-4">
            <p className="text-[10px] font-bold uppercase tracking-widest text-slate-400 mb-2">
              Usuário Vinculado
            </p>
            <div className="flex items-center gap-2.5">
              <span className="inline-flex h-8 w-8 shrink-0 items-center justify-center rounded-lg bg-brand-primary/10">
                <User size={15} className="text-brand-primary-dark" />
              </span>
              <p className="text-sm font-semibold text-slate-800">
                {asset.assignedToName || 'Em Estoque (TI)'}
              </p>
            </div>
          </div>
        </div>
      </div>

      {/* ── Specifications ── */}
      {asset.specifications && asset.specifications.trim() && (
        <div className="bg-white rounded-2xl border border-slate-200 shadow-sm p-6 mb-5">
          <div className="mb-4 flex items-center gap-2.5">
            <span className="inline-flex h-8 w-8 items-center justify-center rounded-lg bg-brand-primary/10">
              <ClipboardList size={16} className="text-brand-primary-dark" />
            </span>
            <h3 className="text-sm font-bold text-slate-800">Especificações</h3>
          </div>

          <div className="rounded-xl border border-slate-200 bg-slate-50 p-4">
            <p className="text-sm leading-6 text-slate-700 whitespace-pre-wrap">
              {asset.specifications}
            </p>
          </div>
        </div>
      )}

      {/* ── Invoice ── */}
      {asset.invoiceFileName ? (
        <div className="bg-white rounded-2xl border border-slate-200 shadow-sm p-6 mb-5">
          <div className="mb-4 flex items-center gap-2.5">
            <span className="inline-flex h-8 w-8 items-center justify-center rounded-lg bg-brand-primary/10">
              <FileText size={16} className="text-brand-primary-dark" />
            </span>
            <h3 className="text-sm font-bold text-slate-800">Nota Fiscal</h3>
          </div>

          <div className="flex items-center justify-between gap-4">
            <div>
              <p className="text-[10px] font-bold uppercase tracking-widest text-slate-400 mb-1">
                Arquivo anexado
              </p>
              <p className="text-sm font-medium text-slate-700">{asset.invoiceFileName}</p>
            </div>
            <button
              onClick={handleInvoiceDownload}
              className="inline-flex shrink-0 items-center gap-2 rounded-xl bg-brand-primary px-4 py-2.5 text-sm font-bold text-white shadow-sm transition-colors hover:bg-brand-primary-dark"
            >
              <Download size={15} />
              Baixar NF
            </button>
          </div>
        </div>
      ) : (
        <div className="rounded-2xl border border-dashed border-slate-200 bg-slate-50 p-8 text-center mb-5">
          <span className="inline-flex h-10 w-10 items-center justify-center rounded-xl bg-slate-200 mx-auto mb-3">
            <FileText size={20} className="text-slate-400" />
          </span>
          <p className="text-sm text-slate-400">Nenhuma nota fiscal anexada a este ativo.</p>
        </div>
      )}

      {/* ── Maintenance history ── */}
      <div className="bg-white rounded-2xl border border-slate-200 shadow-sm p-6">
        <div className="mb-4 flex flex-wrap items-center justify-between gap-3">
          <div className="flex items-center gap-2.5">
            <span className="inline-flex h-9 w-9 items-center justify-center rounded-xl bg-brand-primary/10">
              <Wrench size={17} className="text-brand-primary-dark" />
            </span>
            <div>
              <h3 className="text-base font-bold text-slate-800">Histórico de Manutenções</h3>
              <p className="mt-0.5 text-xs text-slate-400">
                {maintenances.length} registro{maintenances.length !== 1 ? 's' : ''} encontrado{maintenances.length !== 1 ? 's' : ''}
              </p>
            </div>
          </div>
          <button
            onClick={() => setIsMaintenanceModalOpen(true)}
            className="inline-flex items-center gap-2 rounded-xl bg-brand-primary px-4 py-2.5 text-sm font-bold text-white shadow-sm transition-colors hover:bg-brand-primary-dark"
          >
            <Wrench size={14} />
            Registrar Manutenção
          </button>
        </div>

        {loadingMaintenances ? (
          <div className="flex flex-col items-center justify-center gap-3 py-12">
            <Loader2 size={28} className="text-brand-primary animate-spin" />
            <p className="text-sm text-slate-400">Carregando manutenções...</p>
          </div>
        ) : maintenances.length === 0 ? (
          <div className="flex flex-col items-center justify-center gap-3 py-12 rounded-xl border border-dashed border-slate-200 bg-slate-50">
            <span className="inline-flex h-10 w-10 items-center justify-center rounded-xl bg-slate-200">
              <PackageOpen size={20} className="text-slate-400" />
            </span>
            <p className="text-sm text-slate-400">Nenhuma manutenção registrada para este ativo.</p>
          </div>
        ) : (
          <MaintenanceTimeline maintenances={maintenances} />
        )}
      </div>

      {/* Modals */}
      {id && (
        <NewMaintenanceModal
          isOpen={isMaintenanceModalOpen}
          assetId={id}
          onClose={() => setIsMaintenanceModalOpen(false)}
          onMaintenanceCreated={handleMaintenanceCreated}
        />
      )}

      {asset && (
        <TransferAssetModal
          isOpen={isTransferModalOpen}
          asset={asset}
          onClose={() => setIsTransferModalOpen(false)}
          onTransferSuccess={handleTransferSuccess}
        />
      )}

      {asset && (
        <PrintLabelModal
          isOpen={isPrintModalOpen}
          onClose={() => setIsPrintModalOpen(false)}
          asset={asset}
        />
      )}
    </main>
  );
}
