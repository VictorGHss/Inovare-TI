import { CheckCircle2, Download, FileText, Paperclip, UploadCloud } from 'lucide-react';

import TicketComments from '../../components/TicketComments';
import type { Ticket, User } from '../../types/models';

interface TicketTimelineProps {
  ticket: Ticket;
  isResolved: boolean;
  userId?: string;
  userRole?: string;
  claiming: boolean;
  transferring: boolean;
  closing: boolean;
  uploadingAttachment: boolean;
  showTransfer: boolean;
  users: User[];
  selectedUserId: string;
  onClaim: () => void;
  onOpenTransfer: () => void;
  onOpenResolveModal: () => void;
  onSelectedUserIdChange: (userId: string) => void;
  onTransfer: () => void;
  onCancelTransfer: () => void;
  onUploadAttachment: (file: File) => Promise<void>;
}

export default function TicketTimeline({
  ticket,
  isResolved,
  userId,
  userRole,
  claiming,
  transferring,
  closing,
  uploadingAttachment,
  showTransfer,
  users,
  selectedUserId,
  onClaim,
  onOpenTransfer,
  onOpenResolveModal,
  onSelectedUserIdChange,
  onTransfer,
  onCancelTransfer,
  onUploadAttachment,
}: TicketTimelineProps) {
  const apiUrl = import.meta.env.VITE_API_URL;
  const canManageTicket = userRole === 'ADMIN' || userRole === 'TECHNICIAN';
  const hasAssignedTechnician = Boolean(
    ticket.assignedToId ||
      (ticket as Ticket & { technicianId?: string | null; technician?: unknown }).technicianId ||
      (ticket as Ticket & { technicianId?: string | null; technician?: unknown }).technician,
  );
  const canClaim = ticket.status === 'OPEN' && canManageTicket;
  const canTransfer = !isResolved && canManageTicket && (ticket.assignedToId === userId || userRole === 'ADMIN');
  const canResolve =
    ticket.status === 'IN_PROGRESS' &&
    ticket.assignedToId === userId &&
    canManageTicket;
  const canUploadAttachment = !isResolved && canManageTicket && hasAssignedTechnician;

  return (
    <section className="flex flex-col gap-5">
      <div className="rounded-2xl border border-[#feb56c]/35 bg-white p-6 shadow-sm">
        <h3 className="mb-3 text-sm font-semibold text-slate-700">Descricao</h3>
        {ticket.description ? (
          <p className="whitespace-pre-wrap text-sm leading-relaxed text-slate-600">{ticket.description}</p>
        ) : (
          <p className="text-sm italic text-slate-400">Nenhuma descricao fornecida.</p>
        )}
      </div>

      {(ticket.attachments.length > 0 || (!isResolved && canManageTicket)) && (
        <div className="rounded-2xl border border-[#feb56c]/35 bg-white p-6 shadow-sm">
          <div className="mb-4 flex flex-wrap items-center justify-between gap-3">
            <div className="flex items-center gap-2">
              <Paperclip size={16} className="text-[#d98635]" />
              <h3 className="text-sm font-semibold text-slate-700">Upload de Anexos ({ticket.attachments.length})</h3>
            </div>

            {canUploadAttachment && (
              <label className="inline-flex cursor-pointer items-center gap-2 rounded-2xl border border-[#feb56c]/45 bg-[#fff4e8] px-3 py-2 text-xs font-semibold text-amber-800 transition-colors hover:bg-[#ffe8d1]">
                <UploadCloud size={14} />
                {uploadingAttachment ? 'Enviando...' : 'Adicionar anexo'}
                <input
                  type="file"
                  className="hidden"
                  disabled={uploadingAttachment}
                  onChange={(event) => {
                    const file = event.target.files?.[0];
                    if (file) {
                      void onUploadAttachment(file);
                    }
                    event.target.value = '';
                  }}
                />
              </label>
            )}
          </div>

          {!hasAssignedTechnician && !isResolved && canManageTicket && (
            <p
              className="mb-4 rounded-xl border border-amber-200 bg-amber-50 px-3 py-2 text-xs font-medium text-amber-800"
              title="Assuma o chamado para adicionar anexos ou interagir."
            >
              Assuma o chamado para adicionar anexos ou interagir.
            </p>
          )}

          {ticket.attachments.length > 0 ? (
            <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
              {ticket.attachments.map((attachment) => {
                const isImage = attachment.fileType.includes('image');
                const fullUrl = `${apiUrl}${attachment.fileUrl}`;

                if (isImage) {
                  return (
                    <a
                      key={attachment.id}
                      href={fullUrl}
                      target="_blank"
                      rel="noopener noreferrer"
                      className="group relative block overflow-hidden rounded-2xl border border-slate-200 transition-all hover:border-[#feb56c] hover:shadow-md"
                    >
                      <img
                        src={fullUrl}
                        alt={attachment.originalFilename}
                        className="max-h-64 w-full rounded-2xl border bg-slate-50 object-contain"
                      />
                      <div className="absolute inset-0 bg-black/0 transition-colors group-hover:bg-black/10" />
                      <div className="absolute bottom-0 left-0 right-0 bg-gradient-to-t from-black/60 to-transparent p-2">
                        <p className="truncate text-xs font-medium text-white">{attachment.originalFilename}</p>
                      </div>
                    </a>
                  );
                }

                return (
                  <a
                    key={attachment.id}
                    href={fullUrl}
                    target="_blank"
                    download={attachment.originalFilename}
                    className="group flex items-center gap-3 rounded-2xl border border-slate-200 p-3 transition-all hover:border-[#feb56c] hover:bg-[#fff9f2]"
                  >
                    <div className="rounded-2xl bg-slate-100 p-2 transition-colors group-hover:bg-[#feb56c]/30">
                      <FileText size={20} className="text-slate-500 group-hover:text-amber-800" />
                    </div>
                    <div className="min-w-0 flex-1">
                      <p className="truncate text-sm font-medium text-slate-700">{attachment.originalFilename}</p>
                      <p className="text-xs text-slate-400">{attachment.fileType}</p>
                    </div>
                    <Download size={16} className="shrink-0 text-slate-400 group-hover:text-amber-800" />
                  </a>
                );
              })}
            </div>
          ) : (
            <p className="text-sm italic text-slate-400">Nenhum anexo enviado ainda.</p>
          )}
        </div>
      )}

      <TicketComments
        ticketId={ticket.id}
        ticketStatus={ticket.status}
        assignedToId={ticket.assignedToId}
      />

      {!isResolved && canManageTicket && (
        <div className="flex flex-wrap justify-end gap-3">
          {canClaim && (
            <button
              onClick={onClaim}
              disabled={claiming || transferring || closing || uploadingAttachment}
              className="rounded-2xl bg-[#feb56c] px-5 py-2.5 text-sm font-semibold text-slate-900 transition-colors hover:bg-[#f6a455] disabled:opacity-60"
            >
              {claiming ? 'Assumindo...' : 'Assumir Chamado'}
            </button>
          )}

          {canTransfer && (
            <button
              onClick={onOpenTransfer}
              disabled={claiming || transferring || closing || uploadingAttachment}
              className="rounded-2xl bg-amber-600 px-5 py-2.5 text-sm font-semibold text-white transition-colors hover:bg-amber-700 disabled:opacity-60"
            >
              Transferir
            </button>
          )}

          {canResolve && (
            <button
              onClick={onOpenResolveModal}
              disabled={closing || claiming || transferring || uploadingAttachment}
              className="flex items-center gap-2 rounded-2xl bg-[#feb56c] px-5 py-2.5 text-sm font-semibold text-slate-900 transition-colors hover:bg-[#f6a455] disabled:opacity-60"
            >
              <CheckCircle2 size={16} />
              {closing ? 'Resolvendo...' : 'Resolver Chamado'}
            </button>
          )}
        </div>
      )}

      {showTransfer && (
        <div className="rounded-2xl border border-[#feb56c]/35 bg-white p-6 shadow-sm">
          <h3 className="mb-3 text-sm font-semibold text-slate-700">Transferir Chamado</h3>
          <div className="flex flex-col gap-3 sm:flex-row">
            <select
              value={selectedUserId}
              onChange={(event) => onSelectedUserIdChange(event.target.value)}
              className="flex-1 rounded-2xl border border-slate-300 px-3 py-2 text-sm text-slate-700 focus:outline-none focus:ring-2 focus:ring-[#feb56c]/60"
            >
              <option value="">Selecione um usuario</option>
              {users.map((transferUser) => (
                <option key={transferUser.id} value={transferUser.id}>
                  {transferUser.name} ({transferUser.email})
                </option>
              ))}
            </select>
            <button
              onClick={onTransfer}
              disabled={!selectedUserId || transferring}
              className="rounded-2xl bg-[#feb56c] px-4 py-2 text-sm font-semibold text-slate-900 transition-colors hover:bg-[#f6a455] disabled:opacity-60"
            >
              {transferring ? 'Transferindo...' : 'Confirmar'}
            </button>
            <button
              onClick={onCancelTransfer}
              disabled={transferring}
              className="rounded-2xl bg-slate-100 px-4 py-2 text-sm font-semibold text-slate-700 transition-colors hover:bg-slate-200 disabled:opacity-60"
            >
              Cancelar
            </button>
          </div>
        </div>
      )}
    </section>
  );
}
