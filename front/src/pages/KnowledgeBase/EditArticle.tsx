import { useState, useRef, useEffect } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { ArrowLeft, Loader2 } from 'lucide-react';
import { toast } from 'react-toastify';
import { getArticleById, updateArticle, uploadGenericFile, type ArticleStatus } from '../../services/api';

export default function EditArticle() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const [title, setTitle] = useState('');
  const [content, setContent] = useState('');
  const [tags, setTags] = useState('');
  const [status, setStatus] = useState<ArticleStatus>('PUBLISHED');
  const [isLoading, setIsLoading] = useState(false);
  const [isUploading, setIsUploading] = useState(false);
  const [isFetching, setIsFetching] = useState(true);
  const textareaRef = useRef<HTMLTextAreaElement>(null);

  useEffect(() => {
    if (!id) return;
    const fetchArticle = async () => {
      try {
        const article = await getArticleById(id);
        setTitle(article.title);
        setContent(article.content);
        setTags(article.tags ?? '');
        setStatus(article.status ?? 'PUBLISHED');
      } catch {
        toast.error('Erro ao carregar artigo para edição.');
        navigate('/knowledge-base');
      } finally {
        setIsFetching(false);
      }
    };
    void fetchArticle();
  }, [id, navigate]);

  const insertAtCursor = (text: string) => {
    if (!textareaRef.current) return;
    const textarea = textareaRef.current;
    const start = textarea.selectionStart;
    const end = textarea.selectionEnd;
    const newContent = content.substring(0, start) + text + content.substring(end);
    setContent(newContent);
    setTimeout(() => {
      textarea.selectionStart = textarea.selectionEnd = start + text.length;
      textarea.focus();
    }, 0);
  };

  const handlePaste = async (e: React.ClipboardEvent<HTMLTextAreaElement>) => {
    const items = e.clipboardData.items;
    let hasImage = false;
    for (let i = 0; i < items.length; i++) {
      const item = items[i];
      if (item.type.includes('image')) {
        hasImage = true;
        e.preventDefault();
        const file = item.getAsFile();
        if (!file) continue;
        setIsUploading(true);
        toast.info('Fazendo upload...');
        try {
          const response = await uploadGenericFile(file);
          insertAtCursor(`![Imagem](${response.url})`);
          toast.success('Imagem inserida com sucesso!');
        } catch {
          toast.error('Erro ao fazer upload da imagem');
        } finally {
          setIsUploading(false);
        }
      }
    }
    if (!hasImage) return;
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!title.trim() || !content.trim()) {
      toast.error('Título e conteúdo são obrigatórios');
      return;
    }
    if (!id) return;
    setIsLoading(true);
    try {
      await updateArticle(id, {
        title: title.trim(),
        content: content.trim(),
        tags: tags.trim() || undefined,
        status,
      });
      toast.success(status === 'DRAFT' ? 'Rascunho atualizado!' : 'Artigo atualizado e publicado!');
      navigate(`/knowledge-base/${id}`);
    } catch {
      toast.error('Erro ao atualizar artigo');
    } finally {
      setIsLoading(false);
    }
  };

  if (isFetching) {
    return (
      <div className="min-h-screen bg-slate-50 flex items-center justify-center">
        <div className="flex flex-col items-center gap-2">
          <Loader2 size={40} className="text-brand-primary animate-spin" />
          <p className="text-slate-600">Carregando artigo...</p>
        </div>
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-slate-50 p-6">
      <div className="w-full max-w-full px-4 sm:px-6 lg:px-8">
        <div className="flex items-center gap-4 mb-6">
          <button
            onClick={() => navigate(`/knowledge-base/${id}`)}
            className="flex items-center gap-2 text-slate-600 hover:text-slate-900 transition-colors"
          >
            <ArrowLeft size={20} />
          </button>
          <h1 className="text-3xl font-bold text-slate-900">Editar Artigo</h1>
        </div>

        <form
          onSubmit={handleSubmit}
          className="bg-white rounded-lg shadow-sm border border-slate-200 p-6"
        >
          <div className="mb-6">
            <label className="block text-sm font-medium text-slate-700 mb-2">Título</label>
            <input
              type="text"
              value={title}
              onChange={(e) => setTitle(e.target.value)}
              placeholder="Digite o título do artigo"
              className="w-full px-4 py-2 border border-slate-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-brand-primary focus:border-transparent"
              disabled={isLoading || isUploading}
            />
          </div>

          <div className="mb-6">
            <label className="block text-sm font-medium text-slate-700 mb-2">
              Tags (separadas por vírgula)
            </label>
            <input
              type="text"
              value={tags}
              onChange={(e) => setTags(e.target.value)}
              placeholder="ex: tutorial, configuração, rede, vpn"
              className="w-full px-4 py-2 border border-slate-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-brand-primary focus:border-transparent"
              disabled={isLoading || isUploading}
            />
          </div>

          <div className="mb-6">
            <label className="block text-sm font-medium text-slate-700 mb-2">
              Status de Publicação
            </label>
            <select
              value={status}
              onChange={(e) => setStatus(e.target.value as ArticleStatus)}
              className="w-full px-4 py-2 border border-slate-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-brand-primary focus:border-transparent bg-white"
              disabled={isLoading || isUploading}
            >
              <option value="PUBLISHED">Publicado — visível para todos</option>
              <option value="DRAFT">Rascunho — visível apenas para Técnicos/Admins</option>
            </select>
          </div>

          <div className="mb-6">
            <label className="block text-sm font-medium text-slate-700 mb-2">Conteúdo</label>
            <p className="text-xs text-slate-500 mb-2">
              💡 Cole imagens diretamente (Ctrl+V) para inserir: ![Imagem](url)
            </p>
            <textarea
              ref={textareaRef}
              value={content}
              onChange={(e) => setContent(e.target.value)}
              onPaste={handlePaste}
              placeholder="Digite o conteúdo do artigo em Markdown. Cole imagens com Ctrl+V!"
              className="w-full h-96 px-4 py-2 border border-slate-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-brand-primary focus:border-transparent font-mono text-sm"
              disabled={isLoading || isUploading}
            />
          </div>

          <div className="flex gap-3 justify-end">
            <button
              type="button"
              onClick={() => navigate(`/knowledge-base/${id}`)}
              className="px-6 py-2 border border-slate-300 rounded-lg text-slate-700 hover:bg-slate-50 transition-colors font-medium disabled:opacity-50"
              disabled={isLoading || isUploading}
            >
              Cancelar
            </button>
            <button
              type="submit"
              className="px-6 py-2 bg-brand-primary text-white rounded-lg hover:bg-brand-primary-dark transition-colors font-medium flex items-center gap-2 disabled:opacity-50"
              disabled={isLoading || isUploading}
            >
              {isLoading ? (
                <>
                  <Loader2 size={16} className="animate-spin" />
                  Salvando...
                </>
              ) : (
                status === 'DRAFT' ? 'Salvar Rascunho' : 'Publicar Alterações'
              )}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
