import { useEffect, useMemo, useState } from 'react';
import { Search, X } from 'lucide-react';
import { toast } from 'react-toastify';

import {
  createVaultItem,
  type User,
  type VaultCreateItemRequestDTO,
} from '../../../services/api';

type VaultItemType = VaultCreateItemRequestDTO['itemType'];
type VaultSharingType = VaultCreateItemRequestDTO['sharingType'];

const inputClassName =
  'w-full rounded-xl border border-slate-300 bg-white px-3.5 py-2.5 text-sm text-slate-800 shadow-sm placeholder-slate-400 focus:outline-none focus:ring-2 focus:ring-primary focus:border-primary transition';

const dropzoneClassName =
  'block w-full rounded-xl border-2 border-dashed border-slate-300 bg-slate-50 px-4 py-5 text-center text-sm text-slate-500 hover:border-brand-primary hover:bg-brand-secondary/30 transition cursor-pointer';

interface Props {
  isOpen: boolean;
  onClose: () => void;
  users: User[];
  loadingUsers: boolean;
  /** Chamado após a criação bem-sucedida de um item para que o pai recarregue a lista. */
  onItemCreated: () => Promise<void>;
}

/**
 * Modal completo de criação de item do cofre.
 * Gerencia todo o estado do formulário internamente.
 */
export default function VaultCreateModal({
  isOpen,
  onClose,
  users,
  loadingUsers,
  onItemCreated,
}: Props) {
  const [formTitle, setFormTitle] = useState('');
  const [formDescription, setFormDescription] = useState('');
  const [formType, setFormType] = useState<VaultItemType>('CREDENTIAL');
  const [formSecretContent, setFormSecretContent] = useState('');
  const [formSharingType, setFormSharingType] = useState<VaultSharingType>('PRIVATE');
  const [formSelectedUserIds, setFormSelectedUserIds] = useState<string[]>([]);
  const [formUserSearch, setFormUserSearch] = useState('');
  const [formAttachmentFile, setFormAttachmentFile] = useState<File | null>(null);
  const [creating, setCreating] = useState(false);

  const selectableUsers = useMemo(() => {
    const normalizedSearch = formUserSearch.trim().toLowerCase();
    return users
      .filter((u) => !formSelectedUserIds.includes(u.id))
      .filter((u) => {
        if (!normalizedSearch) return true;
        return (
          u.name.toLowerCase().includes(normalizedSearch) ||
          u.email.toLowerCase().includes(normalizedSearch)
        );
      })
      .slice(0, 8);
  }, [users, formSelectedUserIds, formUserSearch]);

  useEffect(() => {
    if (!isOpen) return;

    function handlePaste(event: ClipboardEvent) {
      const pastedFile = event.clipboardData?.files?.[0];
      if (!pastedFile) return;
      setFormAttachmentFile(pastedFile);
      toast.success('Arquivo colado e preparado para upload.');
    }

    window.addEventListener('paste', handlePaste);
    return () => window.removeEventListener('paste', handlePaste);
  }, [isOpen]);

  function resetForm() {
    setFormTitle('');
    setFormDescription('');
    setFormType('CREDENTIAL');
    setFormSecretContent('');
    setFormSharingType('PRIVATE');
    setFormSelectedUserIds([]);
    setFormUserSearch('');
    setFormAttachmentFile(null);
  }

  function handleClose() {
    resetForm();
    onClose();
  }

  function handleDropFile(event: React.DragEvent<HTMLLabelElement>) {
    event.preventDefault();
    const droppedFile = event.dataTransfer.files?.[0];
    if (!droppedFile) return;
    setFormAttachmentFile(droppedFile);
    toast.success('Arquivo anexado com sucesso.');
  }

  async function handleCreate() {
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
      resetForm();
      onClose();
      await onItemCreated();
    } catch {
      toast.error('Não foi possível criar o item do cofre.');
    } finally {
      setCreating(false);
    }
  }

  if (!isOpen) return null;

  return (
    <div className="fixed inset-0 z-50 bg-black/50 backdrop-blur-sm flex items-center justify-center px-4">
      <div className="bg-white rounded-xl border border-slate-200 shadow-sm w-full max-w-xl p-6 max-h-[95vh] overflow-y-auto">
        <div className="flex items-center justify-between mb-4">
          <h2 className="text-lg font-semibold text-slate-800">Novo Item do Cofre</h2>
          <button onClick={handleClose} className="text-slate-400 hover:text-slate-600">
            <X size={20} />
          </button>
        </div>

        <div className="space-y-4">
          <div>
            <label className="block text-xs font-medium text-slate-600 mb-1.5">Título</label>
            <input
              value={formTitle}
              onChange={(e) => setFormTitle(e.target.value)}
              className={inputClassName}
              maxLength={150}
            />
          </div>

          <div>
            <label className="block text-xs font-medium text-slate-600 mb-1.5">Descrição</label>
            <input
              value={formDescription}
              onChange={(e) => setFormDescription(e.target.value)}
              className={inputClassName}
              maxLength={300}
            />
          </div>

          <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
            <div>
              <label className="block text-xs font-medium text-slate-600 mb-1.5">Tipo</label>
              <select
                value={formType}
                onChange={(e) => setFormType(e.target.value as VaultItemType)}
                className={inputClassName}
              >
                <option value="CREDENTIAL">Credencial</option>
                <option value="DOCUMENT">Documento</option>
                <option value="NOTE">Nota</option>
              </select>
            </div>
            <div>
              <label className="block text-xs font-medium text-slate-600 mb-1.5">
                Compartilhamento
              </label>
              <select
                value={formSharingType}
                onChange={(e) => setFormSharingType(e.target.value as VaultSharingType)}
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
              onChange={(e) => setFormSecretContent(e.target.value)}
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
                  onChange={(e) => setFormUserSearch(e.target.value)}
                  className={`${inputClassName} pl-8`}
                  placeholder={loadingUsers ? 'Carregando usuários...' : 'Buscar por nome ou e-mail'}
                />
              </div>
              <div className="mt-2 rounded-lg border border-slate-200 bg-white max-h-40 overflow-y-auto">
                {selectableUsers.length === 0 ? (
                  <p className="px-3 py-2 text-xs text-slate-500">Nenhum colaborador encontrado.</p>
                ) : (
                  selectableUsers.map((u) => (
                    <button
                      key={u.id}
                      onClick={() => {
                        setFormSelectedUserIds((prev) => [...prev, u.id]);
                        setFormUserSearch('');
                      }}
                      className="w-full text-left px-3 py-2 hover:bg-slate-50 border-b border-slate-100 last:border-b-0"
                    >
                      <p className="text-sm font-medium text-slate-700">{u.name}</p>
                      <p className="text-xs text-slate-500">{u.email}</p>
                    </button>
                  ))
                )}
              </div>
              {formSelectedUserIds.length > 0 && (
                <div className="mt-2 flex flex-wrap gap-2">
                  {formSelectedUserIds.map((selectedId) => {
                    const selectedUser = users.find((u) => u.id === selectedId);
                    return (
                      <span
                        key={selectedId}
                        className="inline-flex items-center gap-1 rounded-full bg-brand-secondary text-brand-primary px-2.5 py-1 text-xs font-medium"
                      >
                        {selectedUser?.name ?? selectedId}
                        <button
                          onClick={() =>
                            setFormSelectedUserIds((prev) => prev.filter((v) => v !== selectedId))
                          }
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
              onDragOver={(e) => e.preventDefault()}
              onDrop={handleDropFile}
            >
              <input
                type="file"
                className="hidden"
                onChange={(e) => {
                  const f = e.target.files?.[0];
                  if (f) {
                    setFormAttachmentFile(f);
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
            onClick={handleClose}
            className="px-4 py-2 text-sm font-medium text-slate-600 hover:text-slate-800 hover:bg-slate-100 rounded-lg"
          >
            Cancelar
          </button>
          <button
            onClick={() => void handleCreate()}
            disabled={creating}
            className="px-4 py-2 text-sm font-semibold text-white bg-brand-primary hover:bg-brand-primary-dark rounded-lg disabled:opacity-60"
          >
            {creating ? 'Salvando...' : 'Salvar Item'}
          </button>
        </div>
      </div>
    </div>
  );
}
