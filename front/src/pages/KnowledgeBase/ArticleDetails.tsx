import { useEffect, useState } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { ArrowLeft, Calendar, User, Loader2, Pencil } from 'lucide-react';
import { toast } from 'react-toastify';
import ReactMarkdown from 'react-markdown';
import type { Article } from '../../services/api';
import { getArticleById } from '../../services/api';
import { useAuth } from '../../contexts/AuthContext';

export default function ArticleDetails() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const { user } = useAuth();
  const [article, setArticle] = useState<Article | null>(null);
  const [isLoading, setIsLoading] = useState(true);

  const canEdit = user?.role === 'ADMIN' || user?.role === 'TECHNICIAN';

  useEffect(() => {
    if (!id) return;

    const fetchArticle = async () => {
      try {
        const data = await getArticleById(id);
        setArticle(data);
      } catch (error) {
        toast.error('Erro ao carregar artigo');
        console.error('Fetch article error:', error);
      } finally {
        setIsLoading(false);
      }
    };

    fetchArticle();
  }, [id]);

  if (isLoading) {
    return (
      <div className="min-h-screen bg-slate-50 flex items-center justify-center">
        <div className="flex flex-col items-center gap-2">
          <Loader2 size={40} className="text-brand-primary animate-spin" />
          <p className="text-slate-600">Carregando artigo...</p>
        </div>
      </div>
    );
  }

  if (!article) {
    return (
      <div className="min-h-screen bg-slate-50 p-6">
        <div className="w-full max-w-full px-4 sm:px-6 lg:px-8">
          <div className="text-center py-12">
            <p className="text-slate-600 mb-4">Artigo não encontrado</p>
            <button
              onClick={() => navigate('/knowledge-base')}
              className="text-brand-primary hover:text-brand-primary-dark font-medium"
            >
              Voltar à Base de Conhecimento
            </button>
          </div>
        </div>
      </div>
    );
  }

  const createdAtDate = new Date(article.createdAt);
  const formattedDate = createdAtDate.toLocaleDateString('pt-BR', {
    year: 'numeric',
    month: 'long',
    day: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  });

  // Custom image component to handle base URL
  interface ImgProps {
    src?: string;
    alt?: string;
  }
  
  const CustomImage = ({ ...props }: ImgProps) => {
    const src = props.src || '';
    const fullUrl = src.startsWith('http') 
      ? src 
      : `${import.meta.env.VITE_API_URL}${src}`;
    
    return (
      <img
        {...props}
        src={fullUrl}
        alt={props.alt || 'Imagem do artigo'}
        className="max-w-full rounded-lg border border-slate-200 shadow-sm my-4"
        loading="lazy"
      />
    );
  };

  return (
    <div className="min-h-screen bg-slate-50 p-6">
      <div className="w-full max-w-full px-4 sm:px-6 lg:px-8">
        {/* Voltar */}
        <div className="flex items-center justify-between mb-6">
          <button
            onClick={() => navigate('/knowledge-base')}
            className="flex items-center gap-2 text-slate-600 hover:text-slate-900 transition-colors"
          >
            <ArrowLeft size={20} />
            Voltar
          </button>

          {canEdit && (
            <button
              onClick={() => navigate(`/knowledge-base/${id}/edit`)}
              className="inline-flex items-center gap-2 px-4 py-2 bg-slate-100 hover:bg-slate-200 text-slate-700 rounded-lg transition-colors text-sm font-medium"
            >
              <Pencil size={15} />
              Editar
            </button>
          )}
        </div>

        {/* Container do Artigo */}
        <article className="bg-white rounded-lg shadow-sm border border-slate-200 p-8">
          {/* Título */}
          <div className="flex flex-wrap items-start gap-3 mb-4">
            <h1 className="text-4xl font-bold text-slate-900 flex-1">
              {article.title}
            </h1>
            {article.status === 'DRAFT' && (
              <span className="mt-2 inline-block text-xs font-semibold uppercase tracking-wide bg-amber-100 text-amber-700 px-2.5 py-1 rounded-full">
                Rascunho
              </span>
            )}
          </div>

          {/* Tags */}
          {article.tags && article.tags.trim() && (
            <div className="flex flex-wrap gap-2 mb-4">
              {article.tags.split(',').map((tag, index) => (
                <span
                  key={index}
                  className="bg-brand-secondary text-brand-primary text-xs font-medium px-3 py-1 rounded-full"
                >
                  {tag.trim()}
                </span>
              ))}
            </div>
          )}

          {/* Metadados */}
          <div className="flex flex-wrap items-center gap-6 text-sm text-slate-600 mb-8 pb-6 border-b border-slate-200">
            <div className="flex items-center gap-2">
              <User size={16} />
              <span>{article.authorName}</span>
            </div>
            <div className="flex items-center gap-2">
              <Calendar size={16} />
              <span>{formattedDate}</span>
            </div>
          </div>

          {/* Conteúdo renderizado em Markdown */}
          <div className="prose prose-sm max-w-none text-slate-700 whitespace-pre-wrap">
            <ReactMarkdown
              components={{
                img: CustomImage,
                p: ({ ...props }) => (
                  <p {...props} className="text-slate-700 mb-4" />
                ),
                h1: ({ ...props }) => (
                  <h1 {...props} className="text-2xl font-bold mt-6 mb-4 text-slate-900" />
                ),
                h2: ({ ...props }) => (
                  <h2 {...props} className="text-xl font-bold mt-5 mb-3 text-slate-900" />
                ),
                h3: ({ ...props }) => (
                  <h3 {...props} className="text-lg font-bold mt-4 mb-2 text-slate-900" />
                ),
                ul: ({ ...props }) => (
                  <ul {...props} className="list-disc list-inside mb-4 text-slate-700" />
                ),
                ol: ({ ...props }) => (
                  <ol {...props} className="list-decimal list-inside mb-4 text-slate-700" />
                ),
                // eslint-disable-next-line @typescript-eslint/no-explicit-any
                code: (props: any) =>
                  props.inline ? (
                    <code
                      {...props}
                      className="bg-slate-100 px-2 py-1 rounded text-sm font-mono text-red-600"
                    />
                  ) : (
                    <code
                      {...props}
                      className="block bg-slate-100 p-4 rounded-lg text-sm font-mono text-slate-800 overflow-x-auto mb-4"
                    />
                  ),
              }}
            >
              {article.content}
            </ReactMarkdown>
          </div>
        </article>
      </div>
    </div>
  );
}
