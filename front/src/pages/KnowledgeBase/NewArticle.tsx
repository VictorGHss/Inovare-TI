import { useState, useRef } from 'react';
import { useNavigate } from 'react-router-dom';
import { ArrowLeft, Loader2 } from 'lucide-react';
import { toast } from 'react-toastify';
import { createArticle, uploadGenericFile } from '../../services/api';

export default function NewArticle() {
  const navigate = useNavigate();
  const [title, setTitle] = useState('');
  const [content, setContent] = useState('');
  const [tags, setTags] = useState('');
  const [isLoading, setIsLoading] = useState(false);
  const [isUploading, setIsUploading] = useState(false);
  const textareaRef = useRef<HTMLTextAreaElement>(null);

  // Insere texto no textarea na posição do cursor
  const insertAtCursor = (text: string) => {
    if (!textareaRef.current) return;

    const textarea = textareaRef.current;
    const start = textarea.selectionStart;
    const end = textarea.selectionEnd;
    const newContent =
      content.substring(0, start) + text + content.substring(end);

    setContent(newContent);

    // Reposiciona o cursor após o texto inserido
    setTimeout(() => {
      textarea.selectionStart = textarea.selectionEnd = start + text.length;
      textarea.focus();
    }, 0);
  };

  // Manipula cola de imagens (Ctrl+V com imagem)
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
          const markdownImage = `![Imagem](${response.url})`;
          insertAtCursor(markdownImage);
          toast.success('Imagem inserida com sucesso!');
        } catch (error) {
          toast.error('Erro ao fazer upload da imagem');
          console.error('Upload error:', error);
        } finally {
          setIsUploading(false);
        }
      }
    }

    if (!hasImage) {
      // Deixa o comportamento padrão para texto
      return;
    }
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();

    if (!title.trim() || !content.trim()) {
      toast.error('Título e conteúdo são obrigatórios');
      return;
    }

    setIsLoading(true);

    try {
      await createArticle({
        title: title.trim(),
        content: content.trim(),
        tags: tags.trim() || undefined,
      });

      toast.success('Artigo criado com sucesso!');
      navigate('/knowledge-base');
    } catch (error) {
      toast.error('Erro ao criar artigo');
      console.error('Create article error:', error);
    } finally {
      setIsLoading(false);
    }
  };

  return (
    <div className="min-h-screen bg-slate-50 p-6">
      <div className="w-full max-w-full px-4 sm:px-6 lg:px-8">
        {/* Cabeçalho */}
        <div className="flex items-center gap-4 mb-6">
          <button
            onClick={() => navigate('/knowledge-base')}
            className="flex items-center gap-2 text-slate-600 hover:text-slate-900 transition-colors"
          >
            <ArrowLeft size={20} />
          </button>
          <h1 className="text-3xl font-bold text-slate-900">Novo Artigo</h1>
        </div>

        {/* Formulário */}
        <form
          onSubmit={handleSubmit}
          className="bg-white rounded-lg shadow-sm border border-slate-200 p-6"
        >
          {/* Campo de Título */}
          <div className="mb-6">
            <label className="block text-sm font-medium text-slate-700 mb-2">
              Título
            </label>
            <input
              type="text"
              value={title}
              onChange={(e) => setTitle(e.target.value)}
              placeholder="Digite o título do artigo"
              className="w-full px-4 py-2 border border-slate-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-brand-primary focus:border-transparent"
              disabled={isLoading || isUploading}
            />
          </div>

          {/* Campo de Tags */}
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
            <p className="text-xs text-slate-500 mt-1">
              Use tags para facilitar a busca. Exemplo: "vpn, tutorial, windows"
            </p>
          </div>

          {/* Campo de Conteúdo */}
          <div className="mb-6">
            <label className="block text-sm font-medium text-slate-700 mb-2">
              Conteúdo
            </label>
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

          {/* Botões */}
          <div className="flex gap-3 justify-end">
            <button
              type="button"
              onClick={() => navigate('/knowledge-base')}
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
                'Publicar Artigo'
              )}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
