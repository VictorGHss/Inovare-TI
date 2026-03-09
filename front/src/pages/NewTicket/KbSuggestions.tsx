// Sugestões dinâmicas da Base de Conhecimento — desvia de criação de chamado (Ticket Deflection)
import { Lightbulb, ExternalLink } from 'lucide-react';
import type { ArticleSearchResult } from '../../services/api';

interface Props {
  articles: ArticleSearchResult[];
}

export default function KbSuggestions({ articles }: Props) {
  if (articles.length === 0) {
    return null;
  }

  return (
    <div className="rounded-lg border border-brand-primary bg-brand-secondary p-4 transition-all">
      <p className="text-sm font-semibold text-brand-primary mb-3 flex items-center gap-2">
        <Lightbulb size={16} />
        Antes de abrir o chamado, veja se estes artigos ajudam:
      </p>
      <ul className="flex flex-col gap-2">
        {articles.map((article) => (
          <li key={article.id}>
            <a
              href={`/knowledge-base/${article.id}`}
              target="_blank"
              rel="noopener noreferrer"
              className="flex items-center gap-2 text-sm text-brand-primary hover:text-brand-primary-dark hover:underline transition-colors"
            >
              <ExternalLink size={14} className="shrink-0" />
              {article.title}
            </a>
          </li>
        ))}
      </ul>
    </div>
  );
}
