// Página de abertura de novo chamado
import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { ArrowLeft } from 'lucide-react';
import TicketForm from './TicketForm';
import TutorialAside from './TutorialAside';
import type { TicketType } from './TicketTypeToggle';

export default function NewTicket() {
  const navigate = useNavigate();
  // Estado elevado para ser compartilhado entre formulário e aside
  const [ticketType, setTicketType] = useState<TicketType>('INCIDENT');

  return (
    <main className="w-full max-w-full px-4 sm:px-6 lg:px-8 py-8">
      {/* Navegação de retorno */}
      <div className="flex items-center gap-3 mb-6">
        <button
          onClick={() => navigate('/dashboard')}
          className="p-1.5 rounded-lg hover:bg-slate-200 text-slate-500 hover:text-slate-700 transition-colors"
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
      </div>

      {/* Grid 12 colunas: formulário + tutorial */}
      <div className="grid grid-cols-1 lg:grid-cols-12 gap-6 items-start">
        <div className="lg:col-span-8">
          <TicketForm type={ticketType} onTypeChange={setTicketType} />
        </div>
        <div className="lg:col-span-4">
          <TutorialAside ticketType={ticketType} />
        </div>
      </div>
    </main>
  );
}
