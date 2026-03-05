import { useState, useEffect } from 'react';
import { X, Loader2, RefreshCw } from 'lucide-react';
import { toast } from 'react-toastify';
import {
  transferAsset,
  getUsers,
  type User,
  type TransferAssetData,
  type Asset,
} from '../../services/api';

interface TransferAssetModalProps {
  isOpen: boolean;
  asset: Asset | null;
  onClose: () => void;
  onTransferSuccess: (updatedAsset: Asset) => void;
}

export default function TransferAssetModal({
  isOpen,
  asset,
  onClose,
  onTransferSuccess,
}: TransferAssetModalProps) {
  const [loading, setLoading] = useState(false);
  const [loadingUsers, setLoadingUsers] = useState(false);
  const [users, setUsers] = useState<User[]>([]);
  const [selectedUserId, setSelectedUserId] = useState<string | null>(null);
  const [reason, setReason] = useState('');

  useEffect(() => {
    if (isOpen) {
      loadUsers();
      setSelectedUserId(null);
      setReason('');
    }
  }, [isOpen]);

  async function loadUsers() {
    setLoadingUsers(true);
    try {
      const userData = await getUsers();
      setUsers(userData);
    } catch {
      toast.error('Erro ao carregar lista de usuários.');
    } finally {
      setLoadingUsers(false);
    }
  }

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();

    if (!reason.trim()) {
      toast.error('Por favor, forneça um motivo para a transferência.');
      return;
    }

    if (!asset) {
      toast.error('Ativo não encontrado.');
      return;
    }

    setLoading(true);
    try {
      const dto: TransferAssetData = {
        newUserId: selectedUserId || null,
        reason: reason.trim(),
      };

      const updatedAsset = await transferAsset(asset.id, dto);

      const userInfo = selectedUserId
        ? users.find((u) => u.id === selectedUserId)?.name || 'Desconhecido'
        : 'Estoque da TI';

      toast.success(`Ativo transferido para ${userInfo} com sucesso!`);
      onTransferSuccess(updatedAsset);
      onClose();
    } catch {
      toast.error('Erro ao transferir ativo.');
    } finally {
      setLoading(false);
    }
  }

  if (!isOpen || !asset) return null;

  return (
    <div className="fixed inset-0 bg-black/50 flex items-center justify-center p-4 z-50">
      <div className="bg-white rounded-xl shadow-lg max-w-md w-full max-h-[90vh] overflow-y-auto">
        {/* Header */}
        <div className="flex items-center justify-between p-6 border-b border-slate-200">
          <div className="flex items-center gap-2">
            <RefreshCw size={20} className="text-slate-600" />
            <h2 className="text-lg font-bold text-slate-800">Transferir Ativo</h2>
          </div>
          <button
            onClick={onClose}
            className="p-1.5 rounded-lg hover:bg-slate-100 text-slate-500 hover:text-slate-700 transition-colors"
            aria-label="Fechar"
          >
            <X size={20} />
          </button>
        </div>

        {/* Conteúdo */}
        <form onSubmit={handleSubmit} className="p-6 space-y-4">
          {/* Informações do Ativo */}
          <div className="bg-slate-50 rounded-lg p-3 border border-slate-200">
            <p className="text-xs text-slate-500 font-medium mb-1">Ativo</p>
            <p className="text-sm font-semibold text-slate-800">{asset.name}</p>
            <p className="text-xs text-slate-500 mt-1">
              Código: <span className="font-mono">{asset.patrimonyCode}</span>
            </p>
          </div>

          {/* Selectionar Novo Usuário */}
          <div>
            <label className="block text-sm font-medium text-slate-700 mb-1.5">
              Transferir Para <span className="text-red-500">*</span>
            </label>
            {loadingUsers ? (
              <div className="px-3 py-2.5 border border-slate-300 rounded-lg bg-slate-50 flex items-center gap-2 text-sm text-slate-500">
                <Loader2 size={16} className="animate-spin" />
                Carregando usuários...
              </div>
            ) : (
              <select
                value={selectedUserId || ''}
                onChange={(e) => setSelectedUserId(e.target.value || null)}
                className="w-full px-3 py-2.5 border border-slate-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-primary/50 text-sm"
              >
                <option value="">Nenhum (Devolver ao Estoque da TI)</option>
                {users.map((user) => (
                  <option key={user.id} value={user.id}>
                    {user.name} - {user.email}
                  </option>
                ))}
              </select>
            )}
          </div>

          {/* Motivo da Transferência */}
          <div>
            <label className="block text-sm font-medium text-slate-700 mb-1.5">
              Motivo da Transferência <span className="text-red-500">*</span>
            </label>
            <textarea
              placeholder="Ex: Transferência de setor, realocação de funcionário, devolução por manutenção..."
              value={reason}
              onChange={(e) => setReason(e.target.value)}
              rows={4}
              className="w-full px-3 py-2.5 border border-slate-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-primary/50 text-sm resize-none"
            />
          </div>

          {/* Botões */}
          <div className="flex gap-3 pt-4">
            <button
              type="button"
              onClick={onClose}
              className="flex-1 px-4 py-2.5 border border-slate-300 rounded-lg text-slate-700 font-medium hover:bg-slate-50 transition-colors text-sm"
            >
              Cancelar
            </button>
            <button
              type="submit"
              disabled={loading || loadingUsers}
              className="flex-1 px-4 py-2.5 bg-primary hover:bg-primary-dark text-white font-medium rounded-lg transition-colors text-sm flex items-center justify-center gap-2 disabled:opacity-50 disabled:cursor-not-allowed"
            >
              {loading && <Loader2 size={16} className="animate-spin" />}
              {loading ? 'Transferindo...' : 'Transferir'}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
