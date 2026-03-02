// Página de abertura de novo chamado
import { useNavigate } from 'react-router-dom';
import { ArrowLeft } from 'lucide-react';
import TicketForm from './TicketForm';
import TutorialAside from './TutorialAside';

export default function NewTicket() {
  const navigate = useNavigate();

  return (
    <div className="min-h-screen bg-slate-50">
      {/* Cabeçalho da página */}
      <header className="bg-white border-b border-slate-200 px-6 py-4 flex items-center gap-3">
        <button
          onClick={() => navigate('/dashboard')}
          className="p-1.5 rounded-lg hover:bg-slate-100 text-slate-500 hover:text-slate-700 transition-colors"
          aria-label="Voltar"
        >
          <ArrowLeft size={18} />
        </button>
        <div>
          <h1 className="text-base font-bold text-slate-800">Novo Chamado</h1>
          <p className="text-xs text-slate-400">
            Preencha os campos abaixo para abrir um chamado
          </p>
        </div>
      </header>

      {/* Conteúdo: grid 12 colunas */}
      <main className="max-w-5xl mx-auto px-4 sm:px-6 py-8">
        <div className="grid grid-cols-1 lg:grid-cols-12 gap-6 items-start">
          {/* Formulário — 8 colunas */}
          <div className="lg:col-span-8">
            <TicketForm />
          </div>

          {/* Dicas — 4 colunas */}
          <div className="lg:col-span-4">
            <TutorialAside />
          </div>
        </div>
      </main>
    </div>
  );
}
