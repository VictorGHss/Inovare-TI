/**
 * Componente para renderizar conteúdo em Markdown com suporte a imagens.
 * Procura por padrão ![Imagem](url) e converte para <img />.
 * Mantém quebras de linha (whitespace-pre-wrap).
 */

interface MarkdownRendererProps {
  content: string;
}

export default function MarkdownRenderer({ content }: MarkdownRendererProps) {
  // Split por padrão de imagem: ![...](...)
  const parts = content.split(/(!\[.*?\]\([^)]+\))/g);

  return (
    <div className="whitespace-pre-wrap text-slate-700">
      {parts.map((part, index) => {
        // Verifica se é um padrão de imagem
        const imageMatch = part.match(/!\[(.*?)\]\(([^)]+)\)/);

        if (imageMatch) {
          // É uma imagem
          const [, altText, url] = imageMatch;
          return (
            <img
              key={index}
              src={url}
              alt={altText}
              className="max-w-full rounded-lg my-4 border border-slate-200 shadow-sm"
              loading="lazy"
            />
          );
        }

        // Texto normal
        return <span key={index}>{part}</span>;
      })}
    </div>
  );
}
