import { useEffect, useState } from 'react';
import { Send } from 'lucide-react';
import { toast } from 'react-toastify';
import { getTicketComments, addTicketComment } from '../services/ticketService';
import type { TicketComment } from '../types/domain';
import { useAuth } from '../contexts/AuthContext';

interface TicketCommentsProps {
  ticketId: string;
  ticketStatus: 'OPEN' | 'IN_PROGRESS' | 'RESOLVED';
  assignedToId: string | null;
}

function formatDate(iso: string): string {
  return new Date(iso).toLocaleDateString('pt-BR', {
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  });
}

export default function TicketComments({ ticketId, ticketStatus, assignedToId }: TicketCommentsProps) {
  const { user } = useAuth();
  const [comments, setComments] = useState<TicketComment[]>([]);
  const [loading, setLoading] = useState(true);
  const [newComment, setNewComment] = useState('');
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    const fetchComments = async () => {
      try {
        const data = await getTicketComments(ticketId);
        setComments(data);
      } catch {
        toast.error('Erro ao carregar comentários.');
      } finally {
        setLoading(false);
      }
    };

    fetchComments();
  }, [ticketId]);

  async function handleSubmitComment() {
    if (!newComment.trim()) {
      toast.warning('O comentário não pode estar vazio.');
      return;
    }

    setSubmitting(true);
    try {
      const comment = await addTicketComment(ticketId, newComment);
      setComments((prev) => [...prev, comment]);
      setNewComment('');
      toast.success('Comentário adicionado com sucesso!');
    } catch {
      toast.error('Erro ao adicionar comentário.');
    } finally {
      setSubmitting(false);
    }
  }

  const isTicketResolved = ticketStatus === 'RESOLVED';
  const isAdmin = user?.role === 'ADMIN' || user?.role === 'TECHNICIAN';
  const isCommentDisabled = isAdmin && assignedToId !== user?.id;

  return (
    <div className="bg-white rounded-xl border border-slate-200 shadow-sm p-6">
      <h3 className="text-sm font-semibold text-slate-700 mb-4">Histórico e Comentários</h3>

      {/* Timeline de comentários */}
      <div className="flex flex-col gap-4 mb-6 max-h-80 overflow-y-auto">
        {loading ? (
          <div className="text-center text-slate-400 text-sm py-8">Carregando comentários...</div>
        ) : comments.length === 0 ? (
          <div className="text-center text-slate-400 text-sm py-8">Nenhum comentário ainda.</div>
        ) : (
          comments.map((comment) => {
            const isCurrentUser = user?.id === comment.authorId;
            const isSystemComment = /sistema|system/i.test(comment.authorName) || comment.authorId.toUpperCase?.() === 'SYSTEM';
            const isTechnicianComment = !isSystemComment && Boolean(assignedToId) && comment.authorId === assignedToId;

            let bubbleClass = 'bg-white border border-slate-200';
            let authorClass = 'text-slate-700';

            if (isSystemComment) {
              bubbleClass = 'bg-slate-50 border border-slate-200';
              authorClass = 'text-slate-500';
            } else if (isTechnicianComment) {
              bubbleClass = 'bg-brand-secondary/20 border border-brand-primary/20';
              authorClass = 'text-brand-primary-dark';
            } else if (isCurrentUser) {
              bubbleClass = 'bg-brand-secondary/20 border border-brand-primary/20';
              authorClass = 'text-brand-primary-dark';
            }

            return (
              <div
                key={comment.id}
                className={`flex gap-3 ${isCurrentUser ? 'flex-row-reverse' : ''}`}
              >
                <div
                  className={`flex-1 rounded-2xl p-3 ${bubbleClass}`}
                >
                  <div className="flex items-center justify-between mb-1">
                    <span className={`text-xs font-semibold ${authorClass}`}>
                      {comment.authorName}
                      {isCurrentUser && <span className="ml-1 text-brand-primary-dark">(Você)</span>}
                    </span>
                    <span className="text-xs text-slate-400">{formatDate(comment.createdAt)}</span>
                  </div>
                  <p className="text-sm text-slate-700 whitespace-pre-wrap break-words">
                    {comment.content}
                  </p>
                </div>
              </div>
            );
          })
        )}
      </div>

      {/* Campo de novo comentário */}
      {!isTicketResolved && (
        <div className="flex flex-col gap-3 border-t border-slate-100 pt-4">
          {isCommentDisabled && (
            <p className="text-xs text-slate-400 italic bg-slate-50 p-2 rounded">
              Você precisa assumir o chamado para enviar mensagens.
            </p>
          )}
          <textarea
            value={newComment}
            onChange={(e) => setNewComment(e.target.value)}
            placeholder="Adicione um comentário para tirar dúvidas..."
            rows={3}
            disabled={submitting || isCommentDisabled}
            className="w-full rounded-xl border border-slate-200 px-3.5 py-2.5 text-sm text-slate-700 placeholder-slate-400 shadow-sm focus:outline-none focus:ring-2 focus:ring-brand-primary/30 focus:border-brand-primary disabled:opacity-60 disabled:bg-slate-50"
          />
          <button
            onClick={handleSubmitComment}
            disabled={submitting || !newComment.trim() || isCommentDisabled}
            className="flex items-center justify-center gap-2 rounded-xl bg-brand-primary px-4 py-2.5 text-sm font-semibold text-white transition-colors hover:bg-brand-primary-dark disabled:opacity-60"
          >
            <Send size={16} />
            {submitting ? 'Enviando...' : 'Enviar Comentário'}
          </button>
        </div>
      )}

      {isTicketResolved && (
        <div className="border-t border-slate-100 pt-4">
          <p className="text-xs text-slate-400 text-center italic">
            Comentários desabilitados para chamados resolvidos.
          </p>
        </div>
      )}
    </div>
  );
}
