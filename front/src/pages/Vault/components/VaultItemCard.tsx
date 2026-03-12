import { Eye, EyeOff, FileLock, Key, Lock, Paperclip, Pencil, Trash2 } from 'lucide-react';
import type { VaultItem } from '../../../services/api';

interface Props {
  item: VaultItem;
  revealedSecret: string | undefined;
  loadingAttachmentId: string | null;
  onReveal: (id: string) => void;
  onHide: (id: string) => void;
  onPreviewAttachment: (item: VaultItem) => void;
  currentUserId?: string;
  currentUserRole?: string;
  onEdit?: (item: VaultItem) => void;
  onDelete?: (item: VaultItem) => void;
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
  if (itemType === 'CREDENTIAL') return <Key size={16} className="text-brand-primary" />;
  if (itemType === 'DOCUMENT') return <FileLock size={16} className="text-slate-600" />;
  return <Lock size={16} className="text-slate-600" />;
}

export default function VaultItemCard({
  item,
  revealedSecret,
  loadingAttachmentId,
  onReveal,
  onHide,
  onPreviewAttachment,
  currentUserId,
  currentUserRole,
  onEdit,
  onDelete,
}: Props) {
  const isSecretVisible = revealedSecret !== undefined;
  const hasAttachment = Boolean(item.filePath);
  const canManage =
    Boolean(onEdit) &&
    (item.ownerId === currentUserId || currentUserRole === 'ADMIN');

  return (
    <article className="rounded-xl border border-slate-200 bg-white shadow-sm p-4 flex flex-col gap-3">
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
            onClick={() => onPreviewAttachment(item)}
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
          <p className="text-sm text-slate-700 break-all">{revealedSecret}</p>
        ) : (
          <p className="text-sm text-slate-400">Conteúdo protegido</p>
        )}
      </div>

      <div className="flex items-center justify-between gap-2 mt-auto">
        {isSecretVisible ? (
          <button
            onClick={() => onHide(item.id)}
            className="inline-flex items-center gap-1.5 text-xs font-semibold text-slate-700 hover:text-slate-900"
          >
            <EyeOff size={14} />
            Ocultar
          </button>
        ) : (
          <button
            onClick={() => onReveal(item.id)}
            className="inline-flex items-center gap-1.5 text-xs font-semibold text-brand-primary hover:text-brand-primary-dark"
          >
            <Eye size={14} />
            Ver conteúdo
          </button>
        )}

        <div className="flex items-center gap-2">
          {canManage && (
            <>
              <button
                onClick={() => onEdit?.(item)}
                title="Editar item"
                className="p-1.5 rounded-lg text-slate-400 hover:text-brand-primary hover:bg-brand-secondary transition-colors"
              >
                <Pencil size={14} />
              </button>
              <button
                onClick={() => onDelete?.(item)}
                title="Excluir item"
                className="p-1.5 rounded-lg text-slate-400 hover:text-red-600 hover:bg-red-50 transition-colors"
              >
                <Trash2 size={14} />
              </button>
            </>
          )}
          <span className="text-[11px] text-slate-400">
            {new Date(item.updatedAt).toLocaleDateString('pt-BR')}
          </span>
        </div>
      </div>
    </article>
  );
}
