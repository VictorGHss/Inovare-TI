import { useEffect, useMemo, useState } from 'react';
import { toast } from 'react-toastify';
import {
  Eye,
  EyeOff,
  FileLock,
  Key,
  Lock,
  PlusCircle,
  ShieldCheck,
  X,
} from 'lucide-react';

import { useAuth } from '../../contexts/AuthContext';
import {
  createVaultItem,
  getVaultItemSecret,
  getVaultItems,
  verify2FA,
  type VaultCreateItemRequestDTO,
  type VaultItem,
} from '../../services/api';

type VaultItemType = VaultCreateItemRequestDTO['itemType'];
type VaultSharingType = VaultCreateItemRequestDTO['sharingType'];

const inputClassName =
  'w-full rounded-xl border border-slate-300 bg-white px-3.5 py-2.5 text-sm text-slate-800 shadow-sm placeholder-slate-400 focus:outline-none focus:ring-2 focus:ring-primary focus:border-primary transition';

export default function Vault() {
  const { user, isTwoFactorVerified, updateAuthToken } = useAuth();

  const [items, setItems] = useState<VaultItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [showCreateModal, setShowCreateModal] = useState(false);
  const [unlockCode, setUnlockCode] = useState('');
  const [unlocking, setUnlocking] = useState(false);
  const [revealingItemId, setRevealingItemId] = useState<string | null>(null);
  const [revealedSecrets, setRevealedSecrets] = useState<Record<string, string>>({});

  const [formTitle, setFormTitle] = useState('');
  const [formDescription, setFormDescription] = useState('');
  const [formType, setFormType] = useState<VaultItemType>('CREDENTIAL');
  const [formSecretContent, setFormSecretContent] = useState('');
  const [formSharingType, setFormSharingType] = useState<VaultSharingType>('PRIVATE');
  const [formSharedUsers, setFormSharedUsers] = useState('');
  const [creating, setCreating] = useState(false);

  const isAllowed = user?.role === 'ADMIN' || user?.role === 'TECHNICIAN';

  const sortedItems = useMemo(
    () => [...items].sort((a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime()),
    [items],
  );

  useEffect(() => {
    if (!isAllowed) {
      setLoading(false);
      return;
    }

    void loadItems();
  }, [isAllowed]);

  async function loadItems() {
    setLoading(true);
    try {
      const data = await getVaultItems();
      setItems(data);
    } catch {
      toast.error('Erro ao carregar itens do cofre.');
      setItems([]);
    } finally {
      setLoading(false);
    }
  }

  async function handleCreateItem() {
    if (!formTitle.trim()) {
      toast.error('O título é obrigatório.');
      return;
    }

    if ((formType === 'CREDENTIAL' || formType === 'NOTE') && !formSecretContent.trim()) {
      toast.error('Preencha o conteúdo secreto para este tipo de item.');
      return;
    }

    const sharedWithUserIds = formSharedUsers
      .split(',')
      .map((value) => value.trim())
      .filter(Boolean);

    if (formSharingType === 'CUSTOM' && sharedWithUserIds.length === 0) {
      toast.error('Informe ao menos um ID de usuário para compartilhamento customizado.');
      return;
    }

    const payload: VaultCreateItemRequestDTO = {
      title: formTitle.trim(),
      description: formDescription.trim() || undefined,
      itemType: formType,
      secretContent: formSecretContent.trim() || undefined,
      sharingType: formSharingType,
      sharedWithUserIds: formSharingType === 'CUSTOM' ? sharedWithUserIds : undefined,
    };

    setCreating(true);
    try {
      await createVaultItem(payload);
      toast.success('Item do cofre criado com sucesso.');
      resetCreateForm();
      setShowCreateModal(false);
      await loadItems();
    } catch {
      toast.error('Não foi possível criar o item do cofre.');
    } finally {
      setCreating(false);
    }
  }

  async function handleUnlockVault() {
    if (!/^\d{6}$/.test(unlockCode)) {
      toast.error('Informe um código 2FA com 6 dígitos.');
      return;
    }

    setUnlocking(true);
    try {
      const response = await verify2FA(unlockCode);
      if (!response.token) {
        throw new Error('Token inválido');
      }

      updateAuthToken(response.token, response.user);
      setUnlockCode('');
      toast.success('Cofre desbloqueado com sucesso.');

      if (revealingItemId) {
        const currentItemId = revealingItemId;
        setRevealingItemId(null);
        await handleRevealSecret(currentItemId, true);
      }
    } catch {
      toast.error('Código 2FA inválido.');
    } finally {
      setUnlocking(false);
    }
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
    } catch {
      toast.error('Não foi possível revelar o conteúdo secreto.');
    }
  }

  function handleHideSecret(itemId: string) {
    setRevealedSecrets((prev) => {
      const next = { ...prev };
      delete next[itemId];
      return next;
    });
  }

  function resetCreateForm() {
    setFormTitle('');
    setFormDescription('');
    setFormType('CREDENTIAL');
    setFormSecretContent('');
    setFormSharingType('PRIVATE');
    setFormSharedUsers('');
  }

  function getItemTypeLabel(itemType: VaultItem['itemType']) {
    if (itemType === 'CREDENTIAL') return 'Credencial';
    if (itemType === 'DOCUMENT') return 'Documento';
    return 'Nota';
  }

  function getSharingTypeLabel(sharingType: VaultItem['sharingType']) {
    if (sharingType === 'PRIVATE') return 'Privado';
    if (sharingType === 'ALL_TECH_ADMIN') return 'Todos Técnicos/Admins';
    return 'Customizado';
  }

  function getItemTypeIcon(itemType: VaultItem['itemType']) {
    if (itemType === 'CREDENTIAL') {
      return <Key size={16} className="text-brand-primary" />;
    }

    if (itemType === 'DOCUMENT') {
      return <FileLock size={16} className="text-slate-600" />;
    }

    return <Lock size={16} className="text-slate-600" />;
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
            {sortedItems.map((item) => {
              const isSecretVisible = revealedSecrets[item.id] !== undefined;

              return (
                <article
                  key={item.id}
                  className="rounded-xl border border-slate-200 bg-white shadow-sm p-4 flex flex-col gap-3"
                >
                  <div className="flex items-start justify-between gap-3">
                    <div>
                      <h3 className="text-sm font-semibold text-slate-800">{item.title}</h3>
                      <p className="text-xs text-slate-500 mt-1">{item.description || 'Sem descrição.'}</p>
                    </div>
                    {getItemTypeIcon(item.itemType)}
                  </div>

                  <div className="flex flex-wrap gap-2">
                    <span className="inline-flex items-center rounded-full bg-slate-100 text-slate-700 px-2.5 py-1 text-xs font-medium">
                      {getItemTypeLabel(item.itemType)}
                    </span>
                    <span className="inline-flex items-center rounded-full bg-brand-secondary text-brand-primary px-2.5 py-1 text-xs font-medium">
                      {getSharingTypeLabel(item.sharingType)}
                    </span>
                  </div>

                  <div className="rounded-lg border border-slate-200 bg-slate-50 p-3 min-h-[58px]">
                    {isSecretVisible ? (
                      <p className="text-sm text-slate-700 break-all">{revealedSecrets[item.id]}</p>
                    ) : (
                      <p className="text-sm text-slate-400">Conteúdo protegido</p>
                    )}
                  </div>

                  <div className="flex items-center justify-between gap-2 mt-auto">
                    {isSecretVisible ? (
                      <button
                        onClick={() => handleHideSecret(item.id)}
                        className="inline-flex items-center gap-1.5 text-xs font-semibold text-slate-700 hover:text-slate-900"
                      >
                        <EyeOff size={14} />
                        Ocultar
                      </button>
                    ) : (
                      <button
                        onClick={() => void handleRevealSecret(item.id)}
                        className="inline-flex items-center gap-1.5 text-xs font-semibold text-brand-primary hover:text-brand-primary-dark"
                      >
                        <Eye size={14} />
                        Ver conteúdo
                      </button>
                    )}

                    <span className="text-[11px] text-slate-400">
                      {new Date(item.updatedAt).toLocaleDateString('pt-BR')}
                    </span>
                  </div>
                </article>
              );
            })}
          </div>
        )}
      </section>

      {showCreateModal && (
        <div className="fixed inset-0 z-50 bg-black/50 backdrop-blur-sm flex items-center justify-center px-4">
          <div className="bg-white rounded-xl border border-slate-200 shadow-sm w-full max-w-xl p-6">
            <div className="flex items-center justify-between mb-4">
              <h2 className="text-lg font-semibold text-slate-800">Novo Item do Cofre</h2>
              <button
                onClick={() => {
                  setShowCreateModal(false);
                  resetCreateForm();
                }}
                className="text-slate-400 hover:text-slate-600"
              >
                <X size={20} />
              </button>
            </div>

            <div className="space-y-4">
              <div>
                <label className="block text-xs font-medium text-slate-600 mb-1.5">Título</label>
                <input
                  value={formTitle}
                  onChange={(event) => setFormTitle(event.target.value)}
                  className={inputClassName}
                  maxLength={150}
                />
              </div>

              <div>
                <label className="block text-xs font-medium text-slate-600 mb-1.5">Descrição</label>
                <input
                  value={formDescription}
                  onChange={(event) => setFormDescription(event.target.value)}
                  className={inputClassName}
                  maxLength={300}
                />
              </div>

              <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                <div>
                  <label className="block text-xs font-medium text-slate-600 mb-1.5">Tipo</label>
                  <select
                    value={formType}
                    onChange={(event) => setFormType(event.target.value as VaultItemType)}
                    className={inputClassName}
                  >
                    <option value="CREDENTIAL">Credencial</option>
                    <option value="DOCUMENT">Documento</option>
                    <option value="NOTE">Nota</option>
                  </select>
                </div>

                <div>
                  <label className="block text-xs font-medium text-slate-600 mb-1.5">Compartilhamento</label>
                  <select
                    value={formSharingType}
                    onChange={(event) => setFormSharingType(event.target.value as VaultSharingType)}
                    className={inputClassName}
                  >
                    <option value="PRIVATE">Privado</option>
                    <option value="ALL_TECH_ADMIN">Todos Técnicos/Admins</option>
                    <option value="CUSTOM">Customizado</option>
                  </select>
                </div>
              </div>

              <div>
                <label className="block text-xs font-medium text-slate-600 mb-1.5">
                  Conteúdo Secreto / Senha
                </label>
                <textarea
                  value={formSecretContent}
                  onChange={(event) => setFormSecretContent(event.target.value)}
                  className={`${inputClassName} min-h-28 resize-y`}
                />
              </div>

              {formSharingType === 'CUSTOM' && (
                <div>
                  <label className="block text-xs font-medium text-slate-600 mb-1.5">
                    IDs de usuários (separados por vírgula)
                  </label>
                  <input
                    value={formSharedUsers}
                    onChange={(event) => setFormSharedUsers(event.target.value)}
                    className={inputClassName}
                    placeholder="UUID1, UUID2"
                  />
                </div>
              )}
            </div>

            <div className="mt-6 flex items-center justify-end gap-2">
              <button
                onClick={() => {
                  setShowCreateModal(false);
                  resetCreateForm();
                }}
                className="px-4 py-2 text-sm font-medium text-slate-600 hover:text-slate-800 hover:bg-slate-100 rounded-lg"
              >
                Cancelar
              </button>
              <button
                onClick={() => void handleCreateItem()}
                disabled={creating}
                className="px-4 py-2 text-sm font-semibold text-white bg-brand-primary hover:bg-brand-primary-dark rounded-lg disabled:opacity-60"
              >
                {creating ? 'Salvando...' : 'Salvar Item'}
              </button>
            </div>
          </div>
        </div>
      )}

      {!isTwoFactorVerified && (
        <div className="fixed inset-0 z-40 bg-black/60 backdrop-blur-md flex items-center justify-center px-4">
          <div className="w-full max-w-md bg-white rounded-xl border border-slate-200 shadow-sm p-6">
            <div className="flex items-center gap-2 mb-3">
              <ShieldCheck className="text-brand-primary" size={20} />
              <h2 className="text-lg font-semibold text-slate-800">Desbloqueio de Segurança</h2>
            </div>

            <p className="text-sm text-slate-600 mb-4">
              Para acessar o cofre, confirme sua autenticação em dois fatores com o código de 6 dígitos.
            </p>

            <label className="block text-xs font-medium text-slate-600 mb-1.5">Código do Autenticador</label>
            <input
              value={unlockCode}
              onChange={(event) => setUnlockCode(event.target.value.replace(/\D/g, '').slice(0, 6))}
              className={inputClassName}
              placeholder="000000"
              inputMode="numeric"
            />

            <button
              onClick={() => void handleUnlockVault()}
              disabled={unlocking}
              className="mt-4 w-full inline-flex items-center justify-center gap-2 bg-brand-primary hover:bg-brand-primary-dark text-white text-sm font-semibold py-2.5 rounded-xl transition-colors disabled:opacity-60"
            >
              <Lock size={16} />
              {unlocking ? 'Validando...' : 'Desbloquear Cofre'}
            </button>
          </div>
        </div>
      )}
    </main>
  );
}
