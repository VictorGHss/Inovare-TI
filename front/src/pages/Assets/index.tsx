import { useCallback, useEffect, useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Download, PlusCircle } from 'lucide-react';
import { toast } from 'react-toastify';
import { useAuth } from '../../contexts/AuthContext';
import UploadInvoiceModal from '../../components/UploadInvoiceModal';
import PrintLabelModal from '../../components/PrintLabelModal';
import {
  downloadAssetInvoice,
  getAssetCategories,
  getAssets,
  getUsers,
  uploadAssetInvoice,
  type Asset,
  type AssetCategory,
  type AssetFilterStatus,
  type AssetSortBy,
  type User,
} from '../../services/api';
import AssetTable from './components/AssetTable';
import NewAssetModal from './components/NewAssetModal';

const inputClassName =
  'w-full rounded-xl border border-slate-200 bg-white px-3.5 py-2.5 text-sm text-slate-800 shadow-sm placeholder-slate-400 focus:outline-none focus:ring-2 focus:ring-primary focus:border-primary transition';

export default function Assets() {
  const { user } = useAuth();
  const navigate = useNavigate();

  const [assets, setAssets] = useState<Asset[]>([]);
  const [users, setUsers] = useState<User[]>([]);
  const [categories, setCategories] = useState<AssetCategory[]>([]);
  const [loading, setLoading] = useState(true);
  const [showModal, setShowModal] = useState(false);
  const [showInvoiceModal, setShowInvoiceModal] = useState(false);
  const [showPrintModal, setShowPrintModal] = useState(false);
  const [selectedAssetForInvoice, setSelectedAssetForInvoice] = useState<Asset | null>(null);
  const [selectedAssetForPrint, setSelectedAssetForPrint] = useState<Asset | null>(null);
  const [statusFilter, setStatusFilter] = useState<AssetFilterStatus>('ALL');
  const [categoryFilter, setCategoryFilter] = useState<string>('ALL');
  const [sortFilter, setSortFilter] = useState<'NEWEST' | 'OLDEST' | 'MOST_MAINTENANCES'>('NEWEST');

  const canManageAssets = user?.role === 'ADMIN' || user?.role === 'TECHNICIAN';

  const userNameById = useMemo(
    () => new Map(users.map((u) => [u.id, u.name])),
    [users],
  );

  const fetchInitialData = useCallback(async () => {
    if (!canManageAssets) return;
    try {
      const [usersData, categoriesData] = await Promise.all([getUsers(), getAssetCategories()]);
      setUsers(usersData);
      setCategories(categoriesData);
    } catch {
      toast.error('Erro ao carregar usuários e categorias.');
      setUsers([]);
      setCategories([]);
    }
  }, [canManageAssets]);

  const fetchAssets = useCallback(async () => {
    if (!canManageAssets) {
      setLoading(false);
      return;
    }
    setLoading(true);
    try {
      const filters: { status: AssetFilterStatus; sortBy: AssetSortBy; categoryId: string } = {
        status: statusFilter,
        sortBy: sortFilter === 'MOST_MAINTENANCES' ? 'maintenanceCount' : 'createdAt',
        categoryId: categoryFilter !== 'ALL' ? categoryFilter : '',
      };
      const assetsData = await getAssets(filters);
      setAssets(sortFilter === 'OLDEST' ? [...assetsData].reverse() : assetsData);
    } catch {
      toast.error('Erro ao carregar ativos.');
      setAssets([]);
    } finally {
      setLoading(false);
    }
  }, [canManageAssets, statusFilter, categoryFilter, sortFilter]);

  useEffect(() => { void fetchInitialData(); }, [fetchInitialData]);
  useEffect(() => { void fetchAssets(); }, [fetchAssets]);

  function formatCreatedAt(isoDate: string) {
    return new Date(isoDate).toLocaleDateString('pt-BR');
  }

  function escapeCsvValue(value: string) {
    return `"${value.replaceAll('"', '')}"`;
  }

  function handleExportCsv() {
    if (assets.length === 0) {
      toast.info('Nenhum ativo para exportar com os filtros atuais.');
      return;
    }
    const headers = ['Nome', 'Patrimônio', 'Categoria', 'Usuário Atual', 'Data de Cadastro'];
    const rows = assets.map((asset) => [
      asset.name,
      asset.patrimonyCode,
      asset.categoryName ?? 'Sem categoria',
      asset.assignedToName ?? 'No estoque (TI)',
      formatCreatedAt(asset.createdAt),
    ]);
    const csvContent = [headers, ...rows]
      .map((row) => row.map((cell) => escapeCsvValue(cell)).join(';'))
      .join('\n');
    const blob = new Blob([`\uFEFF${csvContent}`], { type: 'text/csv;charset=utf-8;' });
    const url = window.URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = 'relatorio_ativos.csv';
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
    window.URL.revokeObjectURL(url);
  }

  async function handleInvoiceUpload(file: File) {
    if (!selectedAssetForInvoice) return;
    try {
      await uploadAssetInvoice(selectedAssetForInvoice.id, file);
      setShowInvoiceModal(false);
      setSelectedAssetForInvoice(null);
      void fetchAssets();
    } catch (error) {
      const message = error instanceof Error ? error.message : 'Erro desconhecido';
      throw new Error(message);
    }
  }

  async function handleInvoiceDownload(asset: Asset, e: React.MouseEvent) {
    e.stopPropagation();
    if (!asset.invoiceFileName) {
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

  if (!canManageAssets) {
    return (
      <main className="w-full max-w-full px-4 sm:px-6 lg:px-8 py-8">
        <div className="bg-white rounded-xl border border-slate-200 shadow-sm p-8 text-center">
          <p className="text-sm text-slate-500">Você não possui permissão para acessar esta área.</p>
        </div>
      </main>
    );
  }

  return (
    <main className="w-full max-w-full px-4 sm:px-6 lg:px-8 py-8">
      <div className="flex items-center justify-between mb-6 gap-3">
        <div>
          <h1 className="text-2xl font-bold text-slate-800">Ativos (CMDB)</h1>
          <p className="text-sm text-slate-400 mt-1">Gestão de equipamentos permanentes vinculados aos usuários</p>
        </div>
        <div className="flex items-center gap-2">
          <button
            onClick={handleExportCsv}
            className="flex items-center gap-2 border border-brand-primary/20 bg-brand-secondary text-brand-primary hover:bg-brand-secondary/70 text-sm font-semibold px-4 py-2.5 rounded-xl transition-colors"
          >
            <Download size={17} />
            Exportar (CSV)
          </button>
          <button
            onClick={() => setShowModal(true)}
            className="flex items-center gap-2 bg-brand-primary hover:bg-primary-hover text-white text-sm font-semibold px-4 py-2.5 rounded-xl transition-colors"
          >
            <PlusCircle size={17} />
            Novo Equipamento
          </button>
        </div>
      </div>

      <section className="mb-4 bg-white rounded-xl border border-slate-200 shadow-sm p-4">
        <div className="flex flex-col lg:flex-row lg:items-end gap-3">
          <div className="flex-1 min-w-[180px]">
            <label className="block text-xs font-semibold text-slate-500 uppercase tracking-wide mb-1.5">Status</label>
            <select
              value={statusFilter}
              onChange={(e) => setStatusFilter(e.target.value as AssetFilterStatus)}
              className={inputClassName}
            >
              <option value="ALL">Todos</option>
              <option value="IN_USE">Em Uso</option>
              <option value="IN_STOCK">No Estoque (TI)</option>
            </select>
          </div>
          <div className="flex-1 min-w-[220px]">
            <label className="block text-xs font-semibold text-slate-500 uppercase tracking-wide mb-1.5">Categoria</label>
            <select
              value={categoryFilter}
              onChange={(e) => setCategoryFilter(e.target.value)}
              className={inputClassName}
            >
              <option value="ALL">Todas</option>
              {categories.map((cat) => (
                <option key={cat.id} value={cat.id}>{cat.name}</option>
              ))}
            </select>
          </div>
          <div className="flex-1 min-w-[220px]">
            <label className="block text-xs font-semibold text-slate-500 uppercase tracking-wide mb-1.5">Ordenar por</label>
            <select
              value={sortFilter}
              onChange={(e) => setSortFilter(e.target.value as 'NEWEST' | 'OLDEST' | 'MOST_MAINTENANCES')}
              className={inputClassName}
            >
              <option value="NEWEST">Mais Recentes</option>
              <option value="OLDEST">Mais Antigos</option>
              <option value="MOST_MAINTENANCES">Mais Manutenções (Problemáticos)</option>
            </select>
          </div>
        </div>
      </section>

      <AssetTable
        assets={assets}
        loading={loading}
        userNameById={userNameById}
        onOpenDetails={(asset) => navigate(`/assets/${asset.id}`)}
        onOpenInvoiceModal={(asset) => { setSelectedAssetForInvoice(asset); setShowInvoiceModal(true); }}
        onInvoiceDownload={handleInvoiceDownload}
        onOpenPrintModal={(asset) => { setSelectedAssetForPrint(asset); setShowPrintModal(true); }}
      />

      <NewAssetModal
        isOpen={showModal}
        onClose={() => setShowModal(false)}
        users={users}
        categories={categories}
        onCreated={() => void fetchAssets()}
      />

      <UploadInvoiceModal
        isOpen={showInvoiceModal}
        onClose={() => { setShowInvoiceModal(false); setSelectedAssetForInvoice(null); }}
        onUpload={handleInvoiceUpload}
        entityName="Ativo"
        entityId={selectedAssetForInvoice?.id ?? ''}
      />

      {selectedAssetForPrint && (
        <PrintLabelModal
          isOpen={showPrintModal}
          onClose={() => { setShowPrintModal(false); setSelectedAssetForPrint(null); }}
          asset={selectedAssetForPrint}
        />
      )}
    </main>
  );
}
