import { useEffect, useState } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { ArrowLeft, Calendar, User, Loader2 } from 'lucide-react';
import { toast } from 'react-toastify';
import { Article, getArticleById } from '../../services/api';
import MarkdownRenderer from '../../components/MarkdownRenderer';

export default function ArticleDetails() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const [article, setArticle] = useState<Article | null>(null);
  const [isLoading, setIsLoading] = useState(true);

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
          <Loader2 size={40} className="text-blue-600 animate-spin" />
          <p className="text-slate-600">Carregando artigo...</p>
        </div>
      </div>
    );
  }

  if (!article) {
    return (
      <div className="min-h-screen bg-slate-50 p-6">
        <div className="max-w-4xl mx-auto">
          <div className="text-center py-12">
            <p className="text-slate-600 mb-4">Artigo não encontrado</p>
            <button
              onClick={() => navigate('/knowledge-base')}
              className="text-blue-600 hover:text-blue-700 font-medium"
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

  return (
    <div className="min-h-screen bg-slate-50 p-6">
      <div className="max-w-4xl mx-auto">
        {/* Voltar */}
        <button
          onClick={() => navigate('/knowledge-base')}
          className="flex items-center gap-2 text-slate-600 hover:text-slate-900 transition-colors mb-6"
        >
          <ArrowLeft size={20} />
          Voltar
        </button>

        {/* Container do Artigo */}
        <article className="bg-white rounded-lg shadow-sm border border-slate-200 p-8">
          {/* Título */}
          <h1 className="text-4xl font-bold text-slate-900 mb-4">
            {article.title}
          </h1>

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
          <div className="prose prose-sm max-w-none text-slate-700">
            <MarkdownRenderer content={article.content} />
          </div>
        </article>
      </div>
    </div>
  );
}
