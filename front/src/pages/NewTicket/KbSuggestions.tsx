// Sugestões fictícias da base de conhecimento exibidas ao digitar um problema
import { Lightbulb, BookOpen } from 'lucide-react';

const articles = [
  'Como reiniciar serviços de rede corporativa',
  'Impressora não imprime — diagnóstico rápido',
  'VPN: configuração e solução de erros comuns',
];

export default function KbSuggestions() {
  return (
    <div className="rounded-xl border border-amber-200 bg-amber-50 p-4 transition-all">
      <p className="text-xs font-semibold text-amber-700 mb-3 flex items-center gap-1.5">
        <Lightbulb size={14} />
        Sugestões da Base de Conhecimento
      </p>
      <ul className="flex flex-col gap-2">
        {articles.map((title, i) => (
          <li
            key={i}
            className="flex items-center gap-2 text-sm text-slate-700 hover:text-primary cursor-pointer transition-colors"
          >
            <BookOpen size={13} className="text-amber-500 shrink-0" />
            {title}
          </li>
        ))}
      </ul>
    </div>
  );
}
