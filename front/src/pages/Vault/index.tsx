import { useCallback, useEffect, useMemo, useState } from 'react';
import { toast } from 'react-toastify';
import type { AxiosError } from 'axios';
import { PlusCircle } from 'lucide-react';

import { useAuth } from '../../contexts/AuthContext';
import {
  getUsers,
  getVaultItemFileBlob,
  getVaultItemSecret,
  getVaultItems,
  type User,
  type VaultItem,
} from '../../services/api';

import VaultAttachmentPreview from './components/VaultAttachmentPreview';
import VaultCreateModal from './components/VaultCreateModal';
import VaultItemCard from './components/VaultItemCard';
import VaultSecurityGuard from './components/VaultSecurityGuard';

type AttachmentPreviewState = {
  itemId: string;
  url: string;
  mimeType: string;
};

export default function Vault() {
  const { user, isTwoFactorVerified, invalidateTwoFactorVerification } = useAuth();

  const [items, setItems] = useState<VaultItem[]>([]);
  const [users, setUsers] = useState<User[]>([]);
  const [loading, setLoading] = useState(true);
  const [loadingUsers, setLoadingUsers] = useState(false);
  const [showCreateModal, setShowCreateModal] = useState(false);
  const [revealingItemId, setRevealingItemId] = useState<string | null>(null);
  const [revealedSecrets, setRevealedSecrets] = useState<Record<string, string>>({});
  const [attachmentPreview, setAttachmentPreview] = useState<AttachmentPreviewState | null>(null);
  const [loadingAttachmentId, setLoadingAttachmentId] = useState<string | null>(null);

  const isAllowed = user?.role === 'ADMIN' || user?.role === 'TECHNICIAN';

  const sortedItems = useMemo(
    () => [...items].sort((a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime()),
    [items],
  );

  const loadItems = useCallback(async () => {
    setLoading(true);
    try {
      const vaultItems = await getVaultItems();
      setItems(vaultItems);
    } catch {
      toast.error('Erro ao carregar itens do cofre.');
      setItems([]);
    } finally {
      setLoading(false);
    }
  }, []);

  const loadInitialData = useCallback(async () => {
    setLoading(true);
    setLoadingUsers(true);
    try {
      const [vaultItems, collaborators] = await Promise.all([getVaultItems(), getUsers()]);
      setItems(vaultItems);
      setUsers(collaborators);
    } catch {
      toast.error('Erro ao carregar dados do cofre.');
      setItems([]);
      setUsers([]);
    } finally {
      setLoading(false);
      setLoadingUsers(false);
    }
  }, []);

  useEffect(() => {
    if (!isAllowed) {
      setLoading(false);
      return;
    }
    void loadInitialData();
  }, [isAllowed, loadInitialData]);

  useEffect(() => {
    return () => {
      if (attachmentPreview) {
        URL.revokeObjectURL(attachmentPreview.url);
      }
    };
  }, [attachmentPreview]);

  function inferMimeTypeFromPath(filePath: string) {
    const p = filePath.toLowerCase();
    if (p.endsWith('.png')) return 'image/png';
    if (p.endsWith('.jpg') || p.endsWith('.jpeg')) return 'image/jpeg';
    if (p.endsWith('.gif')) return 'image/gif';
    if (p.endsWith('.webp')) return 'image/webp';
    if (p.endsWith('.mp4')) return 'video/mp4';
    if (p.endsWith('.webm')) return 'video/webm';
    if (p.endsWith('.mov')) return 'video/quicktime';
    if (p.endsWith('.pdf')) return 'application/pdf';
    return 'application/octet-stream';
  }

  async function handleRevealSecret(itemId: string, skipGuard = false) {
    if (!isTwoFactorVerified && !skipGuard) {
      setRevealingItemId(itemId);
      toast.info('Valide o 2FA para visualizar conteúdo sensível.');
      return;
    }
    try {
      const data = await getVaultItemSecret(itemId);
      setRevealedSecrets((prev) => ({ ...prev, [itemId]: data.secretContent }));
      toast.success('Conteúdo secreto exibido com sucesso.');
    } catch (error) {
      if ((error as AxiosError)?.response?.status === 403) {
        invalidateTwoFactorVerification();
        toast.error('Seu 2FA foi resetado. Reconfigure para continuar acessando o cofre.');
        return;
      }
      toast.error('Não foi possível revelar o conteúdo secreto.');
    }
  }

  async function handlePreviewAttachment(item: VaultItem) {
    if (!item.filePath) {
      toast.error('Este item não possui anexo para visualização.');
      return;
    }
    if (!isTwoFactorVerified) {
      setRevealingItemId(item.id);
      toast.info('Valide o 2FA para visualizar anexos.');
      return;
    }
    setLoadingAttachmentId(item.id);
    try {
      const fileBlob = await getVaultItemFileBlob(item.id);
      const fileUrl = URL.createObjectURL(fileBlob);
      if (attachmentPreview) URL.revokeObjectURL(attachmentPreview.url);
      setAttachmentPreview({
        itemId: item.id,
        url: fileUrl,
        mimeType: fileBlob.type || inferMimeTypeFromPath(item.filePath),
      });
    } catch (error) {
      if ((error as AxiosError)?.response?.status === 403) {
        invalidateTwoFactorVerification();
        toast.error('Seu 2FA foi resetado. Reconfigure para continuar acessando o cofre.');
        return;
      }
      toast.error('Não foi possível carregar o anexo para visualização.');
    } finally {
      setLoadingAttachmentId(null);
    }
  }

  function handleHideSecret(itemId: string) {
    setRevealedSecrets((prev) => {
      const next = { ...prev };
      delete next[itemId];
      return next;
    });
  }

  function handleVaultUnlocked(pendingItemId: string | null) {
    setRevealingItemId(null);
    if (pendingItemId) {
      void handleRevealSecret(pendingItemId, true);
    }
  }

  if (!isAllowed) {
    return (
      <main className="w-full max-w-full px-4 sm:px-6 lg:px-8 py-8">
        <section className="bg-white rounded-xl border border-slate-200 shadow-sm p-8 text-center">
          <p className="text-sm text-slate-500">Você não possui permissão para acessar o cofre.</p>
        </section>
      </main>
    );
  }

  return (
    <main className="relative w-full max-w-full px-4 sm:px-6 lg:px-8 py-8">
      <section className="mb-6 flex items-center justify-between gap-4 flex-wrap">
        <div>
          <h1 className="text-2xl font-bold text-slate-800">Cofre de Senhas e Documentos</h1>
          <p className="text-sm text-slate-500 mt-1">
            Gerencie credenciais, documentos e notas sensíveis com proteção 2FA.
          </p>
        </div>
        <button
          onClick={() => setShowCreateModal(true)}
          className="inline-flex items-center gap-2 bg-primary hover:bg-primary-hover text-white text-sm font-semibold px-4 py-2.5 rounded-xl transition-colors"
        >
          <PlusCircle size={17} />
          Novo Item
        </button>
      </section>

      <section className="bg-white rounded-xl border border-slate-200 shadow-sm p-5">
        {loading ? (
          <div className="animate-pulse space-y-3 py-6">
            <div className="h-4 bg-slate-200 rounded w-3/4" />
            <div className="h-4 bg-slate-200 rounded w-1/2" />
            <div className="h-4 bg-slate-200 rounded w-2/3" />
          </div>
        ) : sortedItems.length === 0 ? (
          <p className="text-sm text-slate-500 py-6">Nenhum item cadastrado no cofre.</p>
        ) : (
          <div className="grid grid-cols-1 md:grid-cols-2 xl:grid-cols-3 gap-4">
            {sortedItems.map((item) => (
              <VaultItemCard
                key={item.id}
                item={item}
                revealedSecret={revealedSecrets[item.id]}
                loadingAttachmentId={loadingAttachmentId}
                onReveal={(id) => void handleRevealSecret(id)}
                onHide={handleHideSecret}
                onPreviewAttachment={(i) => void handlePreviewAttachment(i)}
              />
            ))}
          </div>
        )}
      </section>

      <VaultCreateModal
        isOpen={showCreateModal}
        onClose={() => setShowCreateModal(false)}
        users={users}
        loadingUsers={loadingUsers}
        onItemCreated={loadItems}
      />

      {!isTwoFactorVerified && (
        <VaultSecurityGuard
          revealingItemId={revealingItemId}
          onUnlocked={handleVaultUnlocked}
        />
      )}

      <VaultAttachmentPreview
        preview={attachmentPreview}
        onClose={() => setAttachmentPreview(null)}
      />
    </main>
  );
}
