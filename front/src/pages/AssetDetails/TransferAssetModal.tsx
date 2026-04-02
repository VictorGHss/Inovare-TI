import { useState, useEffect } from 'react';
import { X, Loader2, RefreshCw, HardDrive } from 'lucide-react';
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

const inputClassName =
  'w-full rounded-xl border border-slate-200 bg-white px-3.5 py-2.5 text-sm text-slate-800 shadow-sm placeholder-slate-400 focus:outline-none focus:ring-2 focus:ring-brand-primary/30 focus:border-brand-primary transition';

const labelClassName = 'block text-[10px] font-bold uppercase tracking-widest text-slate-400 mb-2';

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
    <div className="fixed inset-0 bg-black/40 flex items-center justify-center p-4 z-50 backdrop-blur-sm">
      <div className="bg-white rounded-2xl shadow-2xl max-w-md w-full max-h-[90vh] overflow-y-auto overflow-hidden">

        {/* Brand accent strip — amber for transfer action */}
        <div className="h-1.5 w-full bg-gradient-to-r from-amber-400 to-amber-500" />

        {/* Header */}
        <div className="flex items-center justify-between border-b border-slate-100 px-6 py-5">
          <div className="flex items-center gap-3">
            <span className="inline-flex h-9 w-9 items-center justify-center rounded-xl bg-amber-50">
              <RefreshCw size={17} className="text-amber-600" />
            </span>
            <div>
              <h2 className="text-base font-bold text-slate-800">Transferir Ativo</h2>
              <p className="mt-0.5 text-xs text-slate-400">Reatribuir posse ou devolver ao estoque</p>
            </div>
          </div>
          <button
            onClick={onClose}
            className="rounded-lg p-1.5 text-slate-400 transition-colors hover:bg-slate-100 hover:text-slate-600"
            aria-label="Fechar"
          >
            <X size={17} />
          </button>
        </div>

        {/* Form */}
        <form onSubmit={handleSubmit} className="p-6 space-y-4">

          {/* Asset info pill */}
          <div className="flex items-center gap-3 rounded-xl border border-brand-primary/20 bg-gradient-to-br from-brand-secondary/40 to-brand-secondary/10 p-4">
            <span className="inline-flex h-9 w-9 shrink-0 items-center justify-center rounded-lg bg-brand-primary/10">
              <HardDrive size={16} className="text-brand-primary-dark" />
            </span>
            <div className="min-w-0">
              <p className="text-[10px] font-bold uppercase tracking-widest text-brand-primary-dark">Ativo</p>
              <p className="text-sm font-bold text-slate-800 truncate">{asset.name}</p>
              <p className="text-xs font-mono text-slate-400">{asset.patrimonyCode}</p>
            </div>
          </div>

          {/* Destination user */}
          <div>
            <label className={labelClassName}>
              Transferir Para <span className="text-red-400 normal-case tracking-normal font-bold">*</span>
            </label>
            {loadingUsers ? (
              <div className="flex items-center gap-2 rounded-xl border border-slate-200 bg-slate-50 px-3.5 py-2.5 text-sm text-slate-400">
                <Loader2 size={14} className="animate-spin text-brand-primary" />
                Carregando usuários...
              </div>
            ) : (
              <select
                value={selectedUserId || ''}
                onChange={(e) => setSelectedUserId(e.target.value || null)}
                className={inputClassName}
              >
                <option value="">Nenhum — Devolver ao Estoque da TI</option>
                {users.map((user) => (
                  <option key={user.id} value={user.id}>
                    {user.name} — {user.email}
                  </option>
                ))}
              </select>
            )}
          </div>

          {/* Reason */}
          <div>
            <label className={labelClassName}>
              Motivo da Transferência <span className="text-red-400 normal-case tracking-normal font-bold">*</span>
            </label>
            <textarea
              placeholder="Ex: Transferência de setor, realocação de funcionário, devolução por manutenção..."
              value={reason}
              onChange={(e) => setReason(e.target.value)}
              rows={4}
              className={`${inputClassName} resize-none`}
            />
          </div>

          {/* Actions */}
          <div className="flex gap-2.5 pt-2 border-t border-slate-100">
            <button
              type="button"
              onClick={onClose}
              className="flex-1 rounded-xl border border-slate-200 px-4 py-2.5 text-sm font-semibold text-slate-600 transition-colors hover:bg-slate-50"
            >
              Cancelar
            </button>
            <button
              type="submit"
              disabled={loading || loadingUsers}
              className="flex-1 inline-flex items-center justify-center gap-2 rounded-xl bg-amber-500 px-4 py-2.5 text-sm font-bold text-white shadow-sm transition-colors hover:bg-amber-600 disabled:cursor-not-allowed disabled:opacity-60"
            >
              {loading && <Loader2 size={15} className="animate-spin" />}
              {loading ? 'Transferindo...' : 'Transferir'}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
