import { useEffect, useState, useMemo } from 'react';
import { useNavigate } from 'react-router-dom';
import { Plus, Calendar, User, Loader2, BookOpenText, FileText, ArrowRight, Search, X, ArrowDownWideNarrow } from 'lucide-react';
import { toast } from 'react-toastify';
import type { Article } from '../../types/models';
import { getArticles } from '../../services/inventoryService';
import { useAuth } from '../../contexts/AuthContext';
import PageHero from '../../components/PageHero';

export default function KnowledgeBase() {
  const navigate = useNavigate();
  const { user } = useAuth();
  const [articles, setArticles] = useState<Article[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [searchTerm, setSearchTerm] = useState('');
  const [sortOption, setSortOption] = useState<'recent' | 'old' | 'title-asc' | 'title-desc'>('recent');

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

  const publishedCount = articles.filter((article) => article.status === 'PUBLISHED').length;
  const draftCount = articles.filter((article) => article.status === 'DRAFT').length;

  const filteredAndSortedArticles = useMemo(() => {
    let result = [...articles];

    // Filter
    if (searchTerm.trim()) {
      const q = searchTerm.toLowerCase().trim();
      result = result.filter(
        (article) =>
          article.title.toLowerCase().includes(q) ||
          article.authorName.toLowerCase().includes(q) ||
          article.content.toLowerCase().includes(q)
      );
    }

    // Sort
    result.sort((a, b) => {
      if (sortOption === 'recent') {
        return new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime();
      } else if (sortOption === 'old') {
        return new Date(a.createdAt).getTime() - new Date(b.createdAt).getTime();
      } else if (sortOption === 'title-asc') {
        return (a.title ?? '').localeCompare(b.title ?? '');
      } else if (sortOption === 'title-desc') {
        return (b.title ?? '').localeCompare(a.title ?? '');
      }
      return 0;
    });

    return result;
  }, [articles, searchTerm, sortOption]);

  if (isLoading) {
    return (
      <main className="w-full max-w-full px-4 py-8 sm:px-6 lg:px-8">
        <div className="mb-6 h-40 animate-pulse rounded-3xl border border-slate-200 bg-white" />
        <div className="grid grid-cols-1 gap-4 md:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4">
          {Array.from({ length: 8 }).map((_, index) => (
            <div key={index} className="h-48 animate-pulse rounded-xl border border-slate-200 bg-white" />
          ))}
        </div>
        <div className="mt-8 flex items-center justify-center gap-2 text-slate-600">
          <Loader2 size={20} className="animate-spin text-brand-primary" />
          <p>Carregando artigos...</p>
        </div>
      </main>
    );
  }

  return (
    <main className="w-full max-w-full px-4 py-8 sm:px-6 lg:px-8">
      <PageHero
        eyebrow="Knowledge Hub"
        icon={<BookOpenText size={14} />}
        title="Base de Conhecimento"
        description="Consulte artigos, tutoriais e documentações para acelerar o atendimento e padronizar soluções."
        actions={canCreateArticle ? (
          <button
            onClick={() => navigate('/knowledge-base/new')}
            className="inline-flex items-center justify-center gap-2 rounded-xl bg-brand-primary px-5 py-3 text-sm font-semibold text-white transition-colors hover:bg-brand-primary-dark"
          >
            <Plus size={18} />
            Novo Artigo
          </button>
        ) : undefined}
      />

      <section className="mb-6 grid grid-cols-1 gap-3 sm:grid-cols-3">
        <div className="rounded-xl border border-slate-200 bg-white px-4 py-3 shadow-sm">
          <p className="text-xs uppercase tracking-wide text-slate-500">Total de artigos</p>
          <p className="mt-1 text-2xl font-bold text-slate-900">{articles.length}</p>
        </div>
        <div className="rounded-xl border border-slate-200 bg-white px-4 py-3 shadow-sm">
          <p className="text-xs uppercase tracking-wide text-slate-500">Publicados</p>
          <p className="mt-1 text-2xl font-bold text-slate-900">{publishedCount}</p>
        </div>
        <div className="rounded-xl border border-slate-200 bg-white px-4 py-3 shadow-sm">
          <p className="text-xs uppercase tracking-wide text-slate-500">Rascunhos</p>
          <p className="mt-1 text-2xl font-bold text-slate-900">{draftCount}</p>
        </div>
      </section>

      {/* ── Search & Filter Controls ── */}
      <div className="mb-6 flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between bg-white p-4 rounded-2xl border border-slate-200 shadow-sm">
        <div className="relative flex-1 max-w-md">
          <input
            type="text"
            placeholder="Pesquisar por título, autor ou conteúdo..."
            value={searchTerm}
            onChange={(e) => setSearchTerm(e.target.value)}
            className="w-full rounded-xl border border-slate-200 bg-white pl-10 pr-10 py-2.5 text-sm text-slate-800 placeholder-slate-400 focus:border-brand-primary focus:outline-none focus:ring-2 focus:ring-brand-primary/20 transition-all"
          />
          <div className="absolute left-3 top-1/2 -translate-y-1/2 text-slate-400">
            <Search size={16} />
          </div>
          {searchTerm && (
            <button
              type="button"
              onClick={() => setSearchTerm('')}
              className="absolute right-3 top-1/2 -translate-y-1/2 text-slate-400 hover:text-slate-600"
            >
              <X size={16} />
            </button>
          )}
        </div>

        <div className="flex items-center gap-2 self-start sm:self-auto">
          <span className="text-xs font-bold uppercase tracking-widest text-slate-400 flex items-center gap-1">
            <ArrowDownWideNarrow size={14} /> Ordenar:
          </span>
          <select
            value={sortOption}
            onChange={(e) => setSortOption(e.target.value as 'recent' | 'old' | 'title-asc' | 'title-desc')}
            className="cursor-pointer rounded-xl border border-slate-200 bg-white px-3.5 py-2.5 text-sm font-medium text-slate-700 shadow-sm focus:border-brand-primary focus:outline-none focus:ring-2 focus:ring-brand-primary/20 transition-all"
          >
            <option value="recent">Mais recentes</option>
            <option value="old">Mais antigos</option>
            <option value="title-asc">Título (A-Z)</option>
            <option value="title-desc">Título (Z-A)</option>
          </select>
        </div>
      </div>

      {articles.length === 0 ? (
        <div className="rounded-2xl border border-dashed border-slate-300 bg-white px-6 py-14 text-center shadow-sm">
          <div className="mx-auto mb-4 flex h-14 w-14 items-center justify-center rounded-xl bg-slate-100 text-slate-500">
            <FileText size={24} />
          </div>
          <h2 className="text-lg font-semibold text-slate-800">Nenhum artigo disponível</h2>
          <p className="mx-auto mt-2 max-w-lg text-sm text-slate-600">
            Quando novos conteúdos forem publicados, eles aparecerão aqui para consulta rápida de toda equipe.
          </p>
          {canCreateArticle && (
            <button
              onClick={() => navigate('/knowledge-base/new')}
              className="mt-5 inline-flex items-center gap-2 rounded-xl bg-brand-primary px-4 py-2.5 text-sm font-semibold text-white transition-colors hover:bg-brand-primary-dark"
            >
              <Plus size={16} />
              Criar primeiro artigo
            </button>
          )}
        </div>
      ) : filteredAndSortedArticles.length === 0 ? (
        <div className="rounded-2xl border border-slate-200 bg-white px-6 py-12 text-center shadow-sm">
          <h2 className="text-lg font-semibold text-slate-800">Nenhum artigo encontrado</h2>
          <p className="mx-auto mt-2 max-w-lg text-sm text-slate-600">
            Nenhum resultado para "{searchTerm}". Tente um termo diferente.
          </p>
        </div>
      ) : (
        <div className="grid grid-cols-1 gap-4 md:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4">
          {filteredAndSortedArticles.map((article) => {
            const createdAtDate = new Date(article.createdAt);
            const formattedDate = createdAtDate.toLocaleDateString('pt-BR', {
              year: 'numeric',
              month: 'short',
              day: 'numeric',
            });

            const summary = article.content
              .substring(0, 140)
              .replace(/!\[.*?\]\(.*?\)/g, '[imagem]')
              .replace(/[#>*_`-]/g, '')
              .trim() + (article.content.length > 140 ? '...' : '');

            return (
              <button
                key={article.id}
                onClick={() => navigate(`/knowledge-base/${article.id}`)}
                className="group rounded-xl border border-slate-200 bg-white p-5 text-left shadow-sm transition-all hover:-translate-y-0.5 hover:border-brand-primary hover:shadow-md"
              >
                <div className="mb-4 flex items-start justify-between gap-2">
                  <span className="inline-flex items-center gap-1 rounded-full border border-slate-200 bg-slate-50 px-2.5 py-1 text-[10px] font-semibold uppercase tracking-wide text-slate-500">
                    <FileText size={12} />
                    Artigo
                  </span>
                  {article.status === 'DRAFT' && canCreateArticle && (
                    <span className="inline-block rounded-full bg-amber-100 px-2 py-0.5 text-[10px] font-semibold uppercase tracking-wide text-amber-700">
                      Rascunho
                    </span>
                  )}
                </div>

                <h3 className="line-clamp-2 text-base font-semibold text-slate-900 transition-colors group-hover:text-brand-primary">
                  {article.title}
                </h3>

                <p className="mt-2 line-clamp-3 text-sm leading-6 text-slate-600">
                  {summary}
                </p>

                <div className="mt-5 flex flex-wrap items-center gap-3 border-t border-slate-100 pt-3 text-xs text-slate-500">
                  <div className="inline-flex items-center gap-1">
                    <User size={14} />
                    <span>{article.authorName}</span>
                  </div>
                  <div className="inline-flex items-center gap-1">
                    <Calendar size={14} />
                    <span>{formattedDate}</span>
                  </div>
                </div>

                <div className="mt-4 inline-flex items-center gap-1 text-xs font-semibold text-brand-primary">
                  Ler artigo
                  <ArrowRight size={14} className="transition-transform group-hover:translate-x-0.5" />
                </div>
              </button>
            );
          })}
        </div>
      )}
    </main>
  );
}

