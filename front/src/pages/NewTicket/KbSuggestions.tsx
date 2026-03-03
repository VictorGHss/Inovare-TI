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
    <div className="rounded-lg border border-blue-200 bg-blue-50 p-4 transition-all">
      <p className="text-sm font-semibold text-blue-700 mb-3 flex items-center gap-2">
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
              className="flex items-center gap-2 text-sm text-blue-700 hover:text-blue-900 hover:underline transition-colors"
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
