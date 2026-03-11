import { useEffect, useMemo, useState } from 'react';
import { toast } from 'react-toastify';
import {
  Eye,
  EyeOff,
  FileLock,
  Key,
  Lock,
  Paperclip,
  PlusCircle,
  Search,
  ShieldCheck,
  ShieldX,
  Video,
  X,
} from 'lucide-react';

import { useAuth } from '../../contexts/AuthContext';
import {
  confirm2FAReset,
  createVaultItem,
  getUsers,
  getVaultItemFileBlob,
  getVaultItemSecret,
  getVaultItems,
  request2FAReset,
  verify2FA,
  type User,
  type VaultCreateItemRequestDTO,
  type VaultItem,
} from '../../services/api';

type VaultItemType = VaultCreateItemRequestDTO['itemType'];
type VaultSharingType = VaultCreateItemRequestDTO['sharingType'];

type AttachmentPreviewState = {
  itemId: string;
  url: string;
  mimeType: string;
};

const inputClassName =
  'w-full rounded-xl border border-slate-300 bg-white px-3.5 py-2.5 text-sm text-slate-800 shadow-sm placeholder-slate-400 focus:outline-none focus:ring-2 focus:ring-primary focus:border-primary transition';

const dropzoneClassName =
  'block w-full rounded-xl border-2 border-dashed border-slate-300 bg-slate-50 px-4 py-5 text-center text-sm text-slate-500 hover:border-brand-primary hover:bg-brand-secondary/30 transition cursor-pointer';

export default function Vault() {
  const { user, isTwoFactorVerified, updateAuthToken } = useAuth();

  const [items, setItems] = useState<VaultItem[]>([]);
  const [users, setUsers] = useState<User[]>([]);
  const [loading, setLoading] = useState(true);
  const [loadingUsers, setLoadingUsers] = useState(false);
  const [showCreateModal, setShowCreateModal] = useState(false);
  const [unlockCode, setUnlockCode] = useState('');
  const [unlocking, setUnlocking] = useState(false);
  const [revealingItemId, setRevealingItemId] = useState<string | null>(null);
  const [revealedSecrets, setRevealedSecrets] = useState<Record<string, string>>({});
  const [attachmentPreview, setAttachmentPreview] = useState<AttachmentPreviewState | null>(null);
  const [loadingAttachmentId, setLoadingAttachmentId] = useState<string | null>(null);

  // Estado do modal de recuperação de 2FA
  const [showRecoveryModal, setShowRecoveryModal] = useState(false);
  const [recoveryStep, setRecoveryStep] = useState<'request' | 'confirm'>('request');
  const [recoveryCode, setRecoveryCode] = useState('');
  const [recoveryPassword, setRecoveryPassword] = useState('');
  const [recoveryLoading, setRecoveryLoading] = useState(false);

  const [formTitle, setFormTitle] = useState('');
  const [formDescription, setFormDescription] = useState('');
  const [formType, setFormType] = useState<VaultItemType>('CREDENTIAL');
  const [formSecretContent, setFormSecretContent] = useState('');
  const [formSharingType, setFormSharingType] = useState<VaultSharingType>('PRIVATE');
  const [formSelectedUserIds, setFormSelectedUserIds] = useState<string[]>([]);
  const [formUserSearch, setFormUserSearch] = useState('');
  const [formAttachmentFile, setFormAttachmentFile] = useState<File | null>(null);
  const [creating, setCreating] = useState(false);

  const isAllowed = user?.role === 'ADMIN' || user?.role === 'TECHNICIAN';

  const sortedItems = useMemo(
    () => [...items].sort((a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime()),
    [items],
  );

  const selectableUsers = useMemo(() => {
    const normalizedSearch = formUserSearch.trim().toLowerCase();

    return users
      .filter((currentUser) => !formSelectedUserIds.includes(currentUser.id))
      .filter((currentUser) => {
        if (!normalizedSearch) {
          return true;
        }

        return (
          currentUser.name.toLowerCase().includes(normalizedSearch)
          || currentUser.email.toLowerCase().includes(normalizedSearch)
        );
      })
      .slice(0, 8);
  }, [users, formSelectedUserIds, formUserSearch]);

  useEffect(() => {
    if (!isAllowed) {
      setLoading(false);
      return;
    }

    void loadInitialData();
  }, [isAllowed]);

  useEffect(() => {
    if (!showCreateModal) {
      return;
    }

    function handlePaste(event: ClipboardEvent) {
      const pastedFile = event.clipboardData?.files?.[0];
      if (!pastedFile) {
        return;
      }

      setFormAttachmentFile(pastedFile);
      toast.success('Arquivo colado e preparado para upload.');
    }

    window.addEventListener('paste', handlePaste);
    return () => {
      window.removeEventListener('paste', handlePaste);
    };
  }, [showCreateModal]);

  useEffect(() => {
    return () => {
      if (attachmentPreview) {
        URL.revokeObjectURL(attachmentPreview.url);
      }
    };
  }, [attachmentPreview]);

  async function loadInitialData() {
    setLoading(true);
    setLoadingUsers(true);

    try {
      const [vaultItems, collaborators] = await Promise.all([
        getVaultItems(),
        getUsers(),
      ]);

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
  }

  async function loadItems() {
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

    if (formSharingType === 'CUSTOM' && formSelectedUserIds.length === 0) {
      toast.error('Selecione ao menos um colaborador para compartilhamento customizado.');
      return;
    }

    const payload: VaultCreateItemRequestDTO = {
      title: formTitle.trim(),
      description: formDescription.trim() || undefined,
      itemType: formType,
      secretContent: formSecretContent.trim() || undefined,
      sharingType: formSharingType,
      sharedWithUserIds: formSharingType === 'CUSTOM' ? formSelectedUserIds : undefined,
    };

    setCreating(true);
    try {
      await createVaultItem(payload, formAttachmentFile);
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

  function openRecoveryModal() {
    setRecoveryStep('request');
    setRecoveryCode('');
    setRecoveryPassword('');
    setShowRecoveryModal(true);
  }

  async function handleRequestRecoveryCode() {
    setRecoveryLoading(true);
    try {
      await request2FAReset();
      toast.success('Código de recuperação enviado ao seu Discord. Verifique suas mensagens diretas.');
      setRecoveryStep('confirm');
    } catch {
      toast.error('Não foi possível enviar o código. Verifique se sua conta Discord está vinculada ou contacte um administrador.');
    } finally {
      setRecoveryLoading(false);
    }
  }

  async function handleConfirmRecovery() {
    if (!recoveryCode.trim()) {
      toast.error('Informe o código de recuperação.');
      return;
    }
    if (!recoveryPassword.trim()) {
      toast.error('Informe sua senha atual.');
      return;
    }

    setRecoveryLoading(true);
    try {
      const response = await confirm2FAReset(recoveryCode.trim(), recoveryPassword);
      if (!response.token) throw new Error('Token inválido');
      updateAuthToken(response.token, response.user);
      toast.success('2FA redefinido com sucesso! Configure um novo autenticador na página de Perfil.');
      setShowRecoveryModal(false);
    } catch {
      toast.error('Código ou senha inválidos. Tente novamente.');
    } finally {
      setRecoveryLoading(false);
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
        const pendingItemId = revealingItemId;
        setRevealingItemId(null);
        await handleRevealSecret(pendingItemId, true);
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

      if (attachmentPreview) {
        URL.revokeObjectURL(attachmentPreview.url);
      }

      setAttachmentPreview({
        itemId: item.id,
        url: fileUrl,
        mimeType: fileBlob.type || inferMimeTypeFromPath(item.filePath),
      });
    } catch {
      toast.error('Não foi possível carregar o anexo para visualização.');
    } finally {
      setLoadingAttachmentId(null);
    }
  }

  function inferMimeTypeFromPath(filePath: string) {
    const normalizedPath = filePath.toLowerCase();

    if (normalizedPath.endsWith('.png')) return 'image/png';
    if (normalizedPath.endsWith('.jpg') || normalizedPath.endsWith('.jpeg')) return 'image/jpeg';
    if (normalizedPath.endsWith('.gif')) return 'image/gif';
    if (normalizedPath.endsWith('.webp')) return 'image/webp';
    if (normalizedPath.endsWith('.mp4')) return 'video/mp4';
    if (normalizedPath.endsWith('.webm')) return 'video/webm';
    if (normalizedPath.endsWith('.mov')) return 'video/quicktime';
    if (normalizedPath.endsWith('.pdf')) return 'application/pdf';

    return 'application/octet-stream';
  }

  function handleHideSecret(itemId: string) {
    setRevealedSecrets((prev) => {
      const nextState = { ...prev };
      delete nextState[itemId];
      return nextState;
    });
  }

  function resetCreateForm() {
    setFormTitle('');
    setFormDescription('');
    setFormType('CREDENTIAL');
    setFormSecretContent('');
    setFormSharingType('PRIVATE');
    setFormSelectedUserIds([]);
    setFormUserSearch('');
    setFormAttachmentFile(null);
  }

  function handleDropFile(event: React.DragEvent<HTMLLabelElement>) {
    event.preventDefault();

    const droppedFile = event.dataTransfer.files?.[0];
    if (!droppedFile) {
      return;
    }

    setFormAttachmentFile(droppedFile);
    toast.success('Arquivo anexado com sucesso.');
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
              const hasAttachment = Boolean(item.filePath);

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
                    {hasAttachment && (
                      <button
                        onClick={() => void handlePreviewAttachment(item)}
                        disabled={loadingAttachmentId === item.id}
                        className="inline-flex items-center rounded-full bg-slate-100 text-slate-700 px-2.5 py-1 text-xs font-medium hover:bg-slate-200"
                      >
                        {loadingAttachmentId === item.id ? (
                          <span>Carregando anexo...</span>
                        ) : (
                          <>
                            <Paperclip size={12} className="mr-1" />
                            <span>Anexo</span>
                          </>
                        )}
                      </button>
                    )}
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
          <div className="bg-white rounded-xl border border-slate-200 shadow-sm w-full max-w-xl p-6 max-h-[95vh] overflow-y-auto">
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
                  className={`${inputClassName} min-h-24 resize-y`}
                />
              </div>

              {formSharingType === 'CUSTOM' && (
                <div className="rounded-xl border border-slate-200 bg-slate-50 p-3">
                  <label className="block text-xs font-medium text-slate-600 mb-1.5">
                    Compartilhar com colaboradores
                  </label>

                  <div className="relative">
                    <Search size={14} className="absolute left-3 top-3 text-slate-400" />
                    <input
                      value={formUserSearch}
                      onChange={(event) => setFormUserSearch(event.target.value)}
                      className={`${inputClassName} pl-8`}
                      placeholder={loadingUsers ? 'Carregando usuários...' : 'Buscar por nome ou e-mail'}
                    />
                  </div>

                  <div className="mt-2 rounded-lg border border-slate-200 bg-white max-h-40 overflow-y-auto">
                    {selectableUsers.length === 0 ? (
                      <p className="px-3 py-2 text-xs text-slate-500">Nenhum colaborador encontrado.</p>
                    ) : (
                      selectableUsers.map((currentUser) => (
                        <button
                          key={currentUser.id}
                          onClick={() => {
                            setFormSelectedUserIds((prev) => [...prev, currentUser.id]);
                            setFormUserSearch('');
                          }}
                          className="w-full text-left px-3 py-2 hover:bg-slate-50 border-b border-slate-100 last:border-b-0"
                        >
                          <p className="text-sm font-medium text-slate-700">{currentUser.name}</p>
                          <p className="text-xs text-slate-500">{currentUser.email}</p>
                        </button>
                      ))
                    )}
                  </div>

                  {formSelectedUserIds.length > 0 && (
                    <div className="mt-2 flex flex-wrap gap-2">
                      {formSelectedUserIds.map((selectedUserId) => {
                        const selectedUser = users.find((currentUser) => currentUser.id === selectedUserId);
                        return (
                          <span
                            key={selectedUserId}
                            className="inline-flex items-center gap-1 rounded-full bg-brand-secondary text-brand-primary px-2.5 py-1 text-xs font-medium"
                          >
                            {selectedUser?.name ?? selectedUserId}
                            <button
                              onClick={() => {
                                setFormSelectedUserIds((prev) => prev.filter((value) => value !== selectedUserId));
                              }}
                              className="hover:text-brand-primary-dark"
                            >
                              <X size={12} />
                            </button>
                          </span>
                        );
                      })}
                    </div>
                  )}
                </div>
              )}

              <div>
                <label
                  className={dropzoneClassName}
                  onDragOver={(event) => event.preventDefault()}
                  onDrop={handleDropFile}
                >
                  <input
                    type="file"
                    className="hidden"
                    onChange={(event) => {
                      const selectedFile = event.target.files?.[0];
                      if (selectedFile) {
                        setFormAttachmentFile(selectedFile);
                        toast.success('Arquivo anexado com sucesso.');
                      }
                    }}
                  />
                  Arraste um arquivo aqui, clique para selecionar ou cole com Ctrl+V
                </label>

                {formAttachmentFile && (
                  <div className="mt-2 rounded-lg bg-brand-secondary/50 border border-brand-secondary px-3 py-2 text-xs text-slate-700">
                    Arquivo preparado: <strong>{formAttachmentFile.name}</strong>
                  </div>
                )}
              </div>
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

            {/* Link de recuperação quando o usuário não tem acesso ao autenticador */}
            <div className="mt-4 text-center">
              <button
                onClick={openRecoveryModal}
                className="text-xs text-slate-500 hover:text-brand-primary underline transition-colors"
              >
                Perdi meu acesso ao autenticador
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Modal de recuperação do 2FA via código Discord */}
      {showRecoveryModal && (
        <div className="fixed inset-0 z-50 bg-black/60 backdrop-blur-sm flex items-center justify-center px-4">
          <div className="w-full max-w-md bg-white rounded-xl border border-slate-200 shadow-sm p-6">
            <div className="flex items-center justify-between mb-4">
              <div className="flex items-center gap-2">
                <ShieldX className="text-amber-500" size={20} />
                <h2 className="text-lg font-semibold text-slate-800">Recuperação de Acesso 2FA</h2>
              </div>
              <button
                onClick={() => setShowRecoveryModal(false)}
                className="text-slate-400 hover:text-slate-600"
              >
                <X size={20} />
              </button>
            </div>

            {recoveryStep === 'request' ? (
              <>
                <p className="text-sm text-slate-600 mb-5">
                  Enviaremos um código de 8 caracteres via <strong>mensagem direta no Discord</strong>.
                  Você também deverá informar sua senha atual para confirmar a redefinição.
                </p>
                <p className="text-xs text-amber-600 bg-amber-50 border border-amber-200 rounded-lg px-3 py-2 mb-5">
                  ⚠️ Após a redefinição, o 2FA será desativado. Configure um novo autenticador no seu Perfil.
                </p>
                <button
                  onClick={() => void handleRequestRecoveryCode()}
                  disabled={recoveryLoading}
                  className="w-full inline-flex items-center justify-center gap-2 bg-amber-500 hover:bg-amber-600 text-white text-sm font-semibold py-2.5 rounded-xl transition-colors disabled:opacity-60"
                >
                  {recoveryLoading ? 'Enviando...' : 'Enviar código ao Discord'}
                </button>
              </>
            ) : (
              <>
                <p className="text-sm text-slate-600 mb-4">
                  Insira o código de 8 caracteres recebido no Discord e sua senha atual.
                </p>

                <div className="space-y-3">
                  <div>
                    <label className="block text-xs font-medium text-slate-600 mb-1.5">
                      Código de recuperação
                    </label>
                    <input
                      value={recoveryCode}
                      onChange={(e) => setRecoveryCode(e.target.value.toUpperCase().slice(0, 8))}
                      className={inputClassName}
                      placeholder="Ex: A3BH7KWP"
                      autoFocus
                    />
                  </div>

                  <div>
                    <label className="block text-xs font-medium text-slate-600 mb-1.5">
                      Senha atual
                    </label>
                    <input
                      type="password"
                      value={recoveryPassword}
                      onChange={(e) => setRecoveryPassword(e.target.value)}
                      className={inputClassName}
                      placeholder="••••••••"
                    />
                  </div>
                </div>

                <div className="mt-4 flex gap-2">
                  <button
                    onClick={() => setRecoveryStep('request')}
                    disabled={recoveryLoading}
                    className="flex-1 px-4 py-2.5 text-sm font-medium text-slate-600 hover:bg-slate-100 rounded-xl border border-slate-300 transition-colors disabled:opacity-60"
                  >
                    Reenviar código
                  </button>
                  <button
                    onClick={() => void handleConfirmRecovery()}
                    disabled={recoveryLoading}
                    className="flex-1 inline-flex items-center justify-center gap-2 bg-brand-primary hover:bg-brand-primary-dark text-white text-sm font-semibold py-2.5 rounded-xl transition-colors disabled:opacity-60"
                  >
                    {recoveryLoading ? 'Verificando...' : 'Confirmar redefinição'}
                  </button>
                </div>
              </>
            )}
          </div>
        </div>
      )}

      {attachmentPreview && (
        <div className="fixed inset-0 z-50 bg-black/70 backdrop-blur-sm flex items-center justify-center px-4">
          <div className="bg-white rounded-xl border border-slate-200 shadow-sm w-full max-w-4xl p-4">
            <div className="flex items-center justify-between mb-3">
              <h3 className="text-sm font-semibold text-slate-800">Pré-visualização do Anexo</h3>
              <button
                onClick={() => {
                  URL.revokeObjectURL(attachmentPreview.url);
                  setAttachmentPreview(null);
                }}
                className="text-slate-400 hover:text-slate-600"
              >
                <X size={20} />
              </button>
            </div>

            <div className="rounded-xl border border-slate-200 bg-slate-50 p-3 min-h-[320px] flex items-center justify-center">
              {attachmentPreview.mimeType.startsWith('image/') ? (
                <img
                  src={attachmentPreview.url}
                  alt="Pré-visualização do anexo do cofre"
                  className="max-h-[70vh] rounded-lg"
                />
              ) : attachmentPreview.mimeType.startsWith('video/') ? (
                <video
                  src={attachmentPreview.url}
                  controls
                  className="w-full max-h-[70vh] rounded-lg"
                />
              ) : (
                <div className="text-center">
                  <Video className="mx-auto mb-2 text-slate-400" size={24} />
                  <p className="text-sm text-slate-600 mb-3">Este tipo de arquivo não possui pré-visualização embutida.</p>
                  <a
                    href={attachmentPreview.url}
                    target="_blank"
                    rel="noreferrer"
                    className="inline-flex items-center gap-1 rounded-lg bg-primary hover:bg-primary-hover text-white text-sm font-semibold px-3 py-2"
                  >
                    <Paperclip size={14} />
                    Abrir arquivo
                  </a>
                </div>
              )}
            </div>
          </div>
        </div>
      )}
    </main>
  );
}
