import { useEffect, useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { PlusCircle, X, HardDrive, FileText, Download, Printer, Eye } from 'lucide-react';
import { toast } from 'react-toastify';
import { useAuth } from '../../contexts/AuthContext';
import UploadInvoiceModal from '../../components/UploadInvoiceModal';
import PrintLabelModal from '../../components/PrintLabelModal';
import {
  getAssets,
  createAsset,
  getUsers,
  getAssetCategories,
  uploadAssetInvoice,
  downloadAssetInvoice,
  type Asset,
  type AssetCategory,
  type AssetFilterStatus,
  type AssetSortBy,
  type User,
  type CreateAssetDto,
} from '../../services/api';

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
  const [submitting, setSubmitting] = useState(false);
  const [selectedInvoiceFile, setSelectedInvoiceFile] = useState<File | null>(null);
  const [assetFileInputId] = useState(`asset-invoice-${Math.random().toString(36).slice(2)}`);
  const [statusFilter, setStatusFilter] = useState<AssetFilterStatus>('ALL');
  const [categoryFilter, setCategoryFilter] = useState<string>('ALL');
  const [sortFilter, setSortFilter] = useState<'NEWEST' | 'OLDEST' | 'MOST_MAINTENANCES'>('NEWEST');

  const [formData, setFormData] = useState<CreateAssetDto>({
    name: '',
    patrimonyCode: '',
    userId: '',
    specifications: '',
    quantity: 1,
  });

  const canManageAssets = user?.role === 'ADMIN' || user?.role === 'TECHNICIAN';

  const userNameById = useMemo(() => {
    return new Map(users.map((currentUser) => [currentUser.id, currentUser.name]));
  }, [users]);

  useEffect(() => {
    fetchInitialData();
    fetchAssets();
  }, []);

  useEffect(() => {
    fetchInitialData();
    fetchAssets();
  }, [canManageAssets]);

  useEffect(() => {
    fetchAssets();
  }, [statusFilter, categoryFilter, sortFilter]);

  async function fetchInitialData() {
    if (!canManageAssets) {
      return;
    }

    try {
      const [usersData, categoriesData] = await Promise.all([getUsers(), getAssetCategories()]);
      setUsers(usersData);
      setCategories(categoriesData);
    } catch {
      toast.error('Erro ao carregar usuários e categorias.');
      setUsers([]);
      setCategories([]);
    }
  }

  async function fetchAssets() {
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

      // O backend retorna createdAt DESC; para "Mais Antigos" invertemos localmente.
      const sortedAssets = sortFilter === 'OLDEST' ? [...assetsData].reverse() : assetsData;

      setAssets(sortedAssets);
    } catch {
      toast.error('Erro ao carregar ativos.');
      setAssets([]);
    } finally {
      setLoading(false);
    }
  }

  function formatCreatedAt(isoDate: string): string {
    return new Date(isoDate).toLocaleDateString('pt-BR');
  }

  function escapeCsvValue(value: string): string {
    const sanitized = value.replaceAll('"', '');
    return `"${sanitized}"`;
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

    const csvWithBom = `\uFEFF${csvContent}`;
    const blob = new Blob([csvWithBom], { type: 'text/csv;charset=utf-8;' });
    const url = window.URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = 'relatorio_ativos.csv';
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
    window.URL.revokeObjectURL(url);
  }

  function resetForm() {
    setFormData({
      name: '',
      patrimonyCode: '',
      userId: '',
      specifications: '',
      quantity: 1,
    });
    setSelectedInvoiceFile(null);
  }

  function openInvoiceModal(asset: Asset) {
    setSelectedAssetForInvoice(asset);
    setShowInvoiceModal(true);
  }

  function openPrintModal(asset: Asset) {
    setSelectedAssetForPrint(asset);
    setShowPrintModal(true);
  }

  async function handleInvoiceUpload(file: File) {
    if (!selectedAssetForInvoice) return;

    try {
      await uploadAssetInvoice(selectedAssetForInvoice.id, file);
      setShowInvoiceModal(false);
      setSelectedAssetForInvoice(null);
      fetchAssets(); // Recarrega dados para atualizar a tabela
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

  async function handleSubmit(event: React.FormEvent) {
    event.preventDefault();

    if (!formData.name.trim() || !formData.patrimonyCode.trim() || !formData.userId) {
      toast.error('Preencha todos os campos obrigatórios.');
      return;
    }

    setSubmitting(true);
    try {
      const asset = await createAsset({
        name: formData.name.trim(),
        patrimonyCode: formData.patrimonyCode.trim(),
        userId: formData.userId,
        specifications: formData.specifications?.trim() || undefined,
        quantity: formData.quantity && formData.quantity > 0 ? formData.quantity : 1,
      });

      if (selectedInvoiceFile) {
        await uploadAssetInvoice(asset.id, selectedInvoiceFile);
        toast.success('Ativo cadastrado e nota fiscal anexada com sucesso!');
      } else {
        toast.success('Ativo cadastrado com sucesso!');
      }

      setShowModal(false);
      resetForm();
      fetchAssets();
    } catch {
      toast.error('Erro ao cadastrar ativo. Verifique os dados.');
    } finally {
      setSubmitting(false);
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
              onChange={(event) => setStatusFilter(event.target.value as AssetFilterStatus)}
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
              onChange={(event) => setCategoryFilter(event.target.value)}
              className={inputClassName}
            >
              <option value="ALL">Todas</option>
              {categories.map((category) => (
                <option key={category.id} value={category.id}>
                  {category.name}
                </option>
              ))}
            </select>
          </div>

          <div className="flex-1 min-w-[220px]">
            <label className="block text-xs font-semibold text-slate-500 uppercase tracking-wide mb-1.5">Ordenar por</label>
            <select
              value={sortFilter}
              onChange={(event) => setSortFilter(event.target.value as 'NEWEST' | 'OLDEST' | 'MOST_MAINTENANCES')}
              className={inputClassName}
            >
              <option value="NEWEST">Mais Recentes</option>
              <option value="OLDEST">Mais Antigos</option>
              <option value="MOST_MAINTENANCES">Mais Manutenções (Problemáticos)</option>
            </select>
          </div>
        </div>
      </section>

      <div className="bg-white rounded-xl border border-slate-200 shadow-sm">
        {loading ? (
          <div className="p-12 text-center">
            <div className="animate-pulse space-y-3">
              <div className="h-4 bg-slate-200 rounded w-3/4 mx-auto" />
              <div className="h-4 bg-slate-200 rounded w-1/2 mx-auto" />
            </div>
          </div>
        ) : assets.length === 0 ? (
          <p className="text-center text-slate-400 py-12 text-sm">Nenhum ativo cadastrado.</p>
        ) : (
          <div className="overflow-x-auto">
            <table className="min-w-full text-sm">
              <thead className="bg-slate-50 text-slate-500 uppercase text-xs tracking-wider">
                <tr>
                  <th className="px-4 py-3 text-left">Nome</th>
                  <th className="px-4 py-3 text-left">Patrimônio</th>
                  <th className="px-4 py-3 text-left">Categoria</th>
                  <th className="px-4 py-3 text-left">Usuário Vinculado</th>
                  <th className="px-4 py-3 text-center">Nota Fiscal</th>
                  <th className="px-4 py-3 text-center">Etiqueta</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {assets.map((asset) => (
                  <tr key={asset.id} className="hover:bg-slate-50 transition-colors">
                    <td className="px-4 py-3 font-medium text-slate-800">
                      <div className="flex items-center gap-2">
                        <HardDrive size={15} className="text-slate-400" />
                        <span>{asset.name}</span>
                      </div>
                    </td>
                    <td className="px-4 py-3 text-slate-600">{asset.patrimonyCode}</td>
                    <td className="px-4 py-3 text-slate-600">{asset.categoryName ?? 'Sem categoria'}</td>
                    <td className="px-4 py-3 text-slate-600">{asset.assignedToName ?? (asset.userId ? userNameById.get(asset.userId) ?? 'Usuário não encontrado' : 'No estoque (TI)')}</td>
                    <td className="px-4 py-3">
                      <div className="flex items-center justify-center gap-2">
                                                <button
                                                  onClick={(e) => {
                                                    e.stopPropagation();
                                                    navigate(`/assets/${asset.id}`);
                                                  }}
                                                  className="flex items-center gap-1.5 text-xs font-medium bg-brand-secondary text-orange-800 hover:bg-orange-200 px-3 py-1.5 rounded-lg transition-colors"
                                                  title="Ver detalhes do ativo"
                                                >
                                                  <Eye size={14} />
                                                  Detalhes
                                                </button>
                        {asset.invoiceFileName ? (
                          <button
                            onClick={(e) => handleInvoiceDownload(asset, e)}
                            className="flex items-center gap-1.5 text-xs font-medium text-brand-primary hover:text-brand-primary-dark hover:bg-brand-secondary px-3 py-1.5 rounded-lg transition-colors"
                            title="Visualizar/baixar nota fiscal"
                          >
                            <Download size={14} />
                            Ver NF
                          </button>
                        ) : (
                          <button
                            onClick={(e) => {
                              e.stopPropagation();
                              openInvoiceModal(asset);
                            }}
                            className="flex items-center gap-1.5 text-xs font-medium text-slate-500 hover:text-slate-700 hover:bg-slate-100 px-3 py-1.5 rounded-lg transition-colors"
                            title="Anexar nota fiscal"
                          >
                            <FileText size={14} />
                            Anexar NF
                          </button>
                        )}
                      </div>
                    </td>
                    <td className="px-4 py-3">
                      <div className="flex items-center justify-center">
                        <button
                          onClick={(e) => {
                            e.stopPropagation();
                            openPrintModal(asset);
                          }}
                          className="flex items-center gap-1.5 text-xs font-medium text-primary hover:text-primary-hover hover:bg-brand-secondary px-3 py-1.5 rounded-lg transition-colors"
                          title="Imprimir etiqueta com QR Code"
                        >
                          <Printer size={14} />
                          Imprimir
                        </button>
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>

      {/* Modal de Nota Fiscal */}
      <UploadInvoiceModal
        isOpen={showInvoiceModal}
        onClose={() => {
          setShowInvoiceModal(false);
          setSelectedAssetForInvoice(null);
        }}
        onUpload={handleInvoiceUpload}
        entityName="Ativo"
        entityId={selectedAssetForInvoice?.id ?? ''}
      />

      {/* Modal de Impressão de Etiqueta */}
      {selectedAssetForPrint && (
        <PrintLabelModal
          isOpen={showPrintModal}
          onClose={() => {
            setShowPrintModal(false);
            setSelectedAssetForPrint(null);
          }}
          asset={selectedAssetForPrint}
        />
      )}

      {showModal && (
        <div className="fixed inset-0 bg-black/50 backdrop-blur-sm flex items-center justify-center z-50 p-4">
          <div className="bg-white rounded-xl shadow-xl w-full max-w-lg">
            <div className="flex items-center justify-between border-b border-slate-200 px-6 py-4">
              <h2 className="text-lg font-bold text-slate-800">Novo Ativo</h2>
              <button
                onClick={() => {
                  setShowModal(false);
                  resetForm();
                }}
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
                  onChange={(event) => setFormData((prev) => ({ ...prev, name: event.target.value }))}
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
                  onChange={(event) => setFormData((prev) => ({ ...prev, patrimonyCode: event.target.value }))}
                  className={inputClassName}
                  placeholder="Ex: INV-2026-00421"
                  maxLength={80}
                  required
                />
              </div>

              <div className="flex flex-col gap-1.5">
                <label className="text-sm font-medium text-slate-700">
                  Quantidade a cadastrar (Lote)
                </label>
                <input
                  type="number"
                  min={1}
                  value={formData.quantity ?? 1}
                  onChange={(event) => {
                    const value = Math.max(1, Number.parseInt(event.target.value, 10) || 1);
                    setFormData((prev) => ({ ...prev, quantity: value }));
                  }}
                  className={inputClassName}
                />
                <p className="text-xs text-slate-500">
                  Se for maior que 1, o código de patrimônio receberá sufixo sequencial (ex.: MON-00-1, MON-00-2).
                </p>
              </div>

              <div className="flex flex-col gap-1.5">
                <label className="text-sm font-medium text-slate-700">
                  Usuário Vinculado <span className="text-red-500">*</span>
                </label>
                <select
                  value={formData.userId}
                  onChange={(event) => setFormData((prev) => ({ ...prev, userId: event.target.value }))}
                  className={inputClassName}
                  required
                >
                  <option value="">Selecione um usuário</option>
                  {users.map((currentUser) => (
                    <option key={currentUser.id} value={currentUser.id}>
                      {currentUser.name} ({currentUser.email})
                    </option>
                  ))}
                </select>
              </div>

              <div className="flex flex-col gap-1.5">
                <label className="text-sm font-medium text-slate-700">Especificações</label>
                <textarea
                  value={formData.specifications ?? ''}
                  onChange={(event) => setFormData((prev) => ({ ...prev, specifications: event.target.value }))}
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
                    className="inline-flex items-center px-3 py-2 bg-gray-100 border border-gray-300 rounded-lg text-sm text-slate-700 cursor-pointer hover:bg-gray-200 transition-colors"
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
                  onClick={() => {
                    setShowModal(false);
                    resetForm();
                  }}
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
                    ? (selectedInvoiceFile ? 'Criando e anexando NF...' : 'Salvando...') 
                    : 'Cadastrar Ativo'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </main>
  );
}
