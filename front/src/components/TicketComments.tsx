import { useEffect, useState } from 'react';
import { Send } from 'lucide-react';
import { toast } from 'react-toastify';
import { getTicketComments, addTicketComment, type TicketComment } from '../services/api';
import { useAuth } from '../contexts/AuthContext';

interface TicketCommentsProps {
  ticketId: string;
  ticketStatus: 'OPEN' | 'IN_PROGRESS' | 'RESOLVED' | 'CLOSED';
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

  const isTicketClosed = ticketStatus === 'CLOSED';
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

            return (
              <div
                key={comment.id}
                className={`flex gap-3 ${isCurrentUser ? 'flex-row-reverse' : ''}`}
              >
                <div
                  className={`flex-1 rounded-lg p-3 ${
                    isCurrentUser
                      ? 'bg-primary/10 border border-primary/20'
                      : 'bg-slate-50 border border-slate-100'
                  }`}
                >
                  <div className="flex items-center justify-between mb-1">
                    <span
                      className={`text-xs font-semibold ${
                        isCurrentUser ? 'text-primary' : 'text-slate-700'
                      }`}
                    >
                      {comment.authorName}
                      {isCurrentUser && <span className="ml-1 text-primary">(Você)</span>}
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
      {!isTicketClosed && (
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
            className="w-full rounded-lg border border-slate-300 px-3 py-2 text-sm text-slate-700 placeholder-slate-400 focus:outline-none focus:ring-2 focus:ring-primary/30 disabled:opacity-60 disabled:bg-slate-50"
          />
          <button
            onClick={handleSubmitComment}
            disabled={submitting || !newComment.trim() || isCommentDisabled}
            className="flex items-center justify-center gap-2 bg-primary hover:bg-primary-hover disabled:opacity-60 text-white text-sm font-semibold px-4 py-2 rounded-xl transition-colors"
          >
            <Send size={16} />
            {submitting ? 'Enviando...' : 'Enviar Comentário'}
          </button>
        </div>
      )}

      {isTicketClosed && (
        <div className="border-t border-slate-100 pt-4">
          <p className="text-xs text-slate-400 text-center italic">
            Comentários desabilitados para chamados fechados.
          </p>
        </div>
      )}
    </div>
  );
}
