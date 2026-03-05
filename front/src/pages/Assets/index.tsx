import { useEffect, useMemo, useState } from 'react';
import { PlusCircle, X, HardDrive, FileText, Download } from 'lucide-react';
import { toast } from 'react-toastify';
import { useAuth } from '../../contexts/AuthContext';
import UploadInvoiceModal from '../../components/UploadInvoiceModal';
import {
  getAssets,
  createAsset,
  getUsers,
  uploadAssetInvoice,
  downloadAssetInvoice,
  type Asset,
  type User,
  type CreateAssetDto,
} from '../../services/api';

const inputClassName =
  'w-full rounded-xl border border-slate-200 bg-white px-3.5 py-2.5 text-sm text-slate-800 shadow-sm placeholder-slate-400 focus:outline-none focus:ring-2 focus:ring-primary focus:border-primary transition';

export default function Assets() {
  const { user } = useAuth();

  const [assets, setAssets] = useState<Asset[]>([]);
  const [users, setUsers] = useState<User[]>([]);
  const [loading, setLoading] = useState(true);
  const [showModal, setShowModal] = useState(false);
  const [showInvoiceModal, setShowInvoiceModal] = useState(false);
  const [selectedAssetForInvoice, setSelectedAssetForInvoice] = useState<Asset | null>(null);
  const [submitting, setSubmitting] = useState(false);

  const [formData, setFormData] = useState<CreateAssetDto>({
    name: '',
    patrimonyCode: '',
    userId: '',
    specifications: '',
  });

  const canManageAssets = user?.role === 'ADMIN' || user?.role === 'TECHNICIAN';

  const userNameById = useMemo(() => {
    return new Map(users.map((currentUser) => [currentUser.id, currentUser.name]));
  }, [users]);

  useEffect(() => {
    if (!canManageAssets) {
      setLoading(false);
      return;
    }

    loadData();
  }, [canManageAssets]);

  async function loadData() {
    setLoading(true);
    try {
      const [assetsData, usersData] = await Promise.all([getAssets(), getUsers()]);
      setAssets(assetsData);
      setUsers(usersData);
    } catch {
      toast.error('Erro ao carregar ativos e usuários.');
      setAssets([]);
      setUsers([]);
    } finally {
      setLoading(false);
    }
  }

  function resetForm() {
    setFormData({
      name: '',
      patrimonyCode: '',
      userId: '',
      specifications: '',
    });
  }

  function openInvoiceModal(asset: Asset) {
    setSelectedAssetForInvoice(asset);
    setShowInvoiceModal(true);
  }

  async function handleInvoiceUpload(file: File) {
    if (!selectedAssetForInvoice) return;

    try {
      await uploadAssetInvoice(selectedAssetForInvoice.id, file);
      setShowInvoiceModal(false);
      setSelectedAssetForInvoice(null);
      loadData(); // Recarrega dados para atualizar a tabela
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
      await createAsset({
        name: formData.name.trim(),
        patrimonyCode: formData.patrimonyCode.trim(),
        userId: formData.userId,
        specifications: formData.specifications?.trim() || undefined,
      });

      toast.success('Ativo cadastrado com sucesso!');
      setShowModal(false);
      resetForm();
      loadData();
    } catch {
      toast.error('Erro ao cadastrar ativo. Verifique os dados.');
    } finally {
      setSubmitting(false);
    }
  }

  if (!canManageAssets) {
    return (
      <main className="max-w-7xl mx-auto px-4 sm:px-6 py-8">
        <div className="bg-white rounded-xl border border-slate-200 shadow-sm p-8 text-center">
          <p className="text-sm text-slate-500">Você não possui permissão para acessar esta área.</p>
        </div>
      </main>
    );
  }

  return (
    <main className="max-w-7xl mx-auto px-4 sm:px-6 py-8">
      <div className="flex items-center justify-between mb-6 gap-3">
        <div>
          <h1 className="text-2xl font-bold text-slate-800">Ativos (CMDB)</h1>
          <p className="text-sm text-slate-400 mt-1">Gestão de equipamentos permanentes vinculados aos usuários</p>
        </div>

        <button
          onClick={() => setShowModal(true)}
          className="flex items-center gap-2 bg-primary hover:bg-primary-hover text-white text-sm font-semibold px-4 py-2.5 rounded-xl transition-colors"
        >
          <PlusCircle size={17} />
          Novo Ativo
        </button>
      </div>

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
                  <th className="px-4 py-3 text-left">Usuário Vinculado</th>
                  <th className="px-4 py-3 text-center">Nota Fiscal</th>
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
                    <td className="px-4 py-3 text-slate-600">{userNameById.get(asset.userId) ?? 'Usuário não encontrado'}</td>
                    <td className="px-4 py-3">
                      <div className="flex items-center justify-center gap-2">
                        {asset.invoiceFileName ? (
                          <button
                            onClick={(e) => handleInvoiceDownload(asset, e)}
                            className="flex items-center gap-1.5 text-xs font-medium text-green-600 hover:text-green-700 hover:bg-green-50 px-3 py-1.5 rounded-lg transition-colors"
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
                  {submitting ? 'Salvando...' : 'Cadastrar Ativo'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </main>
  );
}
