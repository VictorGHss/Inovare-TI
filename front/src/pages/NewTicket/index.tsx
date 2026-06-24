// Página de abertura de novo chamado
import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { ArrowLeft } from 'lucide-react';
import TicketForm from './TicketForm';
import TutorialAside from './TutorialAside';
import type { TicketType } from './TicketTypeToggle';
import PageHero from '@/components/ui/PageHero';

export default function NewTicket() {
  const navigate = useNavigate();
  // Estado elevado para ser compartilhado entre formulário e aside
  const [ticketType, setTicketType] = useState<TicketType>('INCIDENT');

  return (
    <main className="w-full max-w-full px-4 sm:px-6 lg:px-8 py-8">
      <PageHero
        eyebrow="Atendimento"
        title="Novo Chamado"
        description="Preencha os campos abaixo para registrar o chamado e iniciar o fluxo de atendimento."
        actions={(
          <button
            onClick={() => navigate('/dashboard')}
            className="inline-flex items-center gap-2 rounded-xl border border-slate-200 bg-white px-4 py-2.5 text-sm font-semibold text-slate-700 transition-colors hover:bg-slate-50"
            aria-label="Voltar"
          >
            <ArrowLeft size={16} />
            Voltar
          </button>
        )}
      />

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
