// Card lateral com dicas para criar um bom chamado
import { Lightbulb } from 'lucide-react';

const tips = [
  'Seja específico no título — evite "não funciona".',
  'Descreva os passos para reproduzir o problema.',
  'Inclua detalhes do equipamento quando aplicável.',
  'Informe se o problema é recorrente ou pontual.',
  'Anexe capturas de tela sempre que possível.',
];

export default function TutorialAside() {
  return (
    <div className="sticky top-24">
      <div className="bg-white border border-slate-200 rounded-xl shadow-sm p-6">
        {/* Cabeçalho do card */}
        <div className="flex items-center gap-2 mb-4">
          <div className="p-1.5 bg-primary/10 rounded-lg">
            <Lightbulb size={18} className="text-primary" />
          </div>
          <h2 className="font-semibold text-slate-800 text-sm">
            Como criar um bom chamado
          </h2>
        </div>

        {/* Lista de dicas */}
        <ul className="flex flex-col gap-3">
          {tips.map((tip, i) => (
            <li key={i} className="flex items-start gap-2">
              <span className="mt-0.5 flex-shrink-0 w-4 h-4 rounded-full bg-primary/10 text-primary text-[10px] font-bold flex items-center justify-center">
                {i + 1}
              </span>
              <span className="text-sm text-gray-600 leading-snug">{tip}</span>
            </li>
          ))}
        </ul>
      </div>
    </div>
  );
}
