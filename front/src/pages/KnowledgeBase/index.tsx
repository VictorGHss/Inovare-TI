import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Plus, Calendar, User, Loader2 } from 'lucide-react';
import { toast } from 'react-toastify';
import { Article, getArticles } from '../../services/api';
import { useAuth } from '../../contexts/AuthContext';

export default function KnowledgeBase() {
  const navigate = useNavigate();
  const { user } = useAuth();
  const [articles, setArticles] = useState<Article[]>([]);
  const [isLoading, setIsLoading] = useState(true);

  useEffect(() => {
    const fetchArticles = async () => {
      try {
        const data = await getArticles();
        setArticles(data);
      } catch (error) {
        toast.error('Erro ao carregar artigos');
        console.error('Fetch articles error:', error);
      } finally {
        setIsLoading(false);
      }
    };

    fetchArticles();
  }, []);

  const canCreateArticle =
    user?.role === 'ADMIN' || user?.role === 'TECHNICIAN';

  if (isLoading) {
    return (
      <div className="min-h-screen bg-slate-50 flex items-center justify-center">
        <div className="flex flex-col items-center gap-2">
          <Loader2 size={40} className="text-blue-600 animate-spin" />
          <p className="text-slate-600">Carregando artigos...</p>
        </div>
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-slate-50 p-6">
      <div className="max-w-6xl mx-auto">
        {/* Cabeçalho */}
        <div className="flex items-center justify-between mb-8">
          <div>
            <h1 className="text-3xl font-bold text-slate-900">
              Base de Conhecimento
            </h1>
            <p className="text-slate-600 text-sm mt-1">
              Explore tutoriais e artigos úteis
            </p>
          </div>

          {/* Botão Novo Artigo (visível apenas para Admin/Technician) */}
          {canCreateArticle && (
            <button
              onClick={() => navigate('/knowledge-base/new')}
              className="flex items-center gap-2 px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700 transition-colors font-medium"
            >
              <Plus size={18} />
              Novo Artigo
            </button>
          )}
        </div>

        {/* Lista de Artigos */}
        {articles.length === 0 ? (
          <div className="text-center py-12">
            <p className="text-slate-600 mb-4">
              Nenhum artigo disponível no momento
            </p>
            {canCreateArticle && (
              <button
                onClick={() => navigate('/knowledge-base/new')}
                className="text-blue-600 hover:text-blue-700 font-medium"
              >
                Crie o primeiro artigo
              </button>
            )}
          </div>
        ) : (
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
            {articles.map((article) => {
              const createdAtDate = new Date(article.createdAt);
              const formattedDate = createdAtDate.toLocaleDateString('pt-BR', {
                year: 'numeric',
                month: 'short',
                day: 'numeric',
              });

              // Extrai um resumo do conteúdo (primeiras 120 caracteres)
              const summary = article.content
                .substring(0, 120)
                .replace(/!\[.*?\]\(.*?\)/g, '[imagem]')
                .trim() + (article.content.length > 120 ? '...' : '');

              return (
                <button
                  key={article.id}
                  onClick={() => navigate(`/knowledge-base/${article.id}`)}
                  className="p-5 bg-white rounded-lg shadow-sm border border-slate-200 hover:shadow-md hover:border-blue-300 transition-all text-left"
                >
                  {/* Título */}
                  <h3 className="font-semibold text-slate-900 mb-2 line-clamp-2 hover:text-blue-600">
                    {article.title}
                  </h3>

                  {/* Resumo */}
                  <p className="text-sm text-slate-600 mb-4 line-clamp-2">
                    {summary}
                  </p>

                  {/* Metadados */}
                  <div className="flex flex-wrap items-center gap-3 text-xs text-slate-500 border-t border-slate-100 pt-3">
                    <div className="flex items-center gap-1">
                      <User size={14} />
                      <span>{article.authorName}</span>
                    </div>
                    <div className="flex items-center gap-1">
                      <Calendar size={14} />
                      <span>{formattedDate}</span>
                    </div>
                  </div>
                </button>
              );
            })}
          </div>
        )}
      </div>
    </div>
  );
}
