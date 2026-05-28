import { ArrowLeft } from 'lucide-react';
import { useEffect, useState, useCallback } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { toast } from 'react-toastify';

import PageHero from '../../components/PageHero';
import { useAuth } from '../../contexts/AuthContext';
import { useTicketDetails } from '../../hooks/useTicketDetails';
import { updateTicketSolution } from '../../services/ticketService';
import ResolveTicketModal from './ResolveTicketModal';
import TicketHeader from './TicketHeader';
import TicketSidebar from './TicketSidebar';
import TicketTimeline from './TicketTimeline';

export default function TicketDetails() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const { user } = useAuth();
  const [isEditingSolution, setIsEditingSolution] = useState(false);
  const [solutionInput, setSolutionInput] = useState('');
  const [isSavingSolution, setIsSavingSolution] = useState(false);
  const [resolveInitialNotes, setResolveInitialNotes] = useState('');

  const {
    ticket,
    ticketNotFound,
    loading,
    closing,
    claiming,
    transferring,
    uploadingAttachment,
    showTransfer,
    showResolveModal,
    users,
    selectedUserId,
    assets,
    loadingAssets,
    categories,
    loadingCategories,
    loadingUsers,
    updatingCategory,
    addingAdditionalUser,
    isResolved,
    loadTicket,
    setSelectedUserId,
    handleResolve,
    handleClaim,
    handleOpenTransfer,
    handleCancelTransfer,
    handleTransfer,
    handleAttachmentUpload,
    handleChangeCategory,
    handleAddAdditionalUser,
    openResolveModal,
    closeResolveModal,
  } = useTicketDetails({
    ticketId: id,
  });

  const canEditSolution = user && (user.role === 'ADMIN' || user.role === 'TECHNICIAN');

  const handleApplyMacro = useCallback((macroText: string) => {
    setResolveInitialNotes(macroText);
    openResolveModal();
  }, [openResolveModal]);

  const handleSaveSolution = useCallback(async () => {
    if (!ticket) return;
    setIsSavingSolution(true);
    try {
      await updateTicketSolution(ticket.id, solutionInput);
      toast.success('Descrição da solução salva com sucesso!');
      setIsEditingSolution(false);
      loadTicket();
    } catch (err) {
      toast.error('Falha ao salvar a descrição da solução.');
    } finally {
      setIsSavingSolution(false);
    }
  }, [ticket, solutionInput, loadTicket]);

  useEffect(() => {
    if (ticketNotFound) {
      navigate('/dashboard');
    }
  }, [ticketNotFound, navigate]);

  if (loading) {
    return (
      <main className="w-full max-w-full px-4 sm:px-6 lg:px-8 py-8">
        <div className="animate-pulse space-y-4">
          <div className="h-6 bg-slate-200 rounded w-1/3" />
          <div className="h-40 bg-slate-100 rounded-xl" />
          <div className="h-32 bg-slate-100 rounded-xl" />
        </div>
      </main>
    );
  }

  if (!ticket) return null;

  return (
    <main className="w-full max-w-full px-4 sm:px-6 lg:px-8 py-8">
      <PageHero
        eyebrow="Atendimento"
        title={`Chamado #${ticket.id.slice(0, 8).toUpperCase()}`}
        description="Visualize detalhes completos, histórico, comentários e ações disponíveis para este atendimento."
        actions={(
          <button
            onClick={() => navigate('/tickets')}
            className="inline-flex items-center gap-2 rounded-2xl border border-brand-primary/40 bg-white px-4 py-2.5 text-sm font-semibold text-slate-700 transition-colors hover:bg-brand-secondary/30"
            aria-label="Voltar"
          >
            <ArrowLeft size={16} />
            Voltar
          </button>
        )}
      />

      <div className="grid grid-cols-1 lg:grid-cols-12 gap-6 items-start">
        <div className="lg:col-span-8 flex flex-col gap-5">
          <TicketHeader ticket={ticket} />
          {/* Solução Aplicada Section */}
          {(ticket.status === 'CLOSED' || ticket.status === 'RESOLVED') && (
            <div className={`rounded-2xl border p-5 shadow-sm transition-all duration-300 ${
              ticket.solutionText 
                ? 'border-emerald-200 bg-emerald-50/70' 
                : 'border-amber-200 bg-amber-50/60'
            }`}>
              <div className="flex items-center justify-between">
                <h3 className={`text-sm font-bold flex items-center gap-2 ${
                  ticket.solutionText ? 'text-emerald-800' : 'text-amber-800'
                }`}>
                  <span className={`w-2 h-2 rounded-full animate-pulse ${
                    ticket.solutionText ? 'bg-emerald-500' : 'bg-amber-500'
                  }`} />
                  Solução Aplicada
                </h3>
                {canEditSolution && !isEditingSolution && (
                  <button
                    onClick={() => {
                      setSolutionInput(ticket.solutionText || '');
                      setIsEditingSolution(true);
                    }}
                    className={`text-xs font-semibold px-3 py-1.5 rounded-xl border transition-all duration-200 ${
                      ticket.solutionText
                        ? 'border-emerald-300 text-emerald-700 hover:bg-emerald-100/60'
                        : 'border-amber-300 text-amber-700 hover:bg-amber-100/60'
                    }`}
                  >
                    {ticket.solutionText ? 'Editar Descrição' : 'Adicionar Solução'}
                  </button>
                )}
              </div>

              {isEditingSolution ? (
                <div className="mt-3 flex flex-col gap-3">
                  <textarea
                    value={solutionInput}
                    onChange={(e) => setSolutionInput(e.target.value)}
                    rows={4}
                    placeholder="Descreva detalhadamente a solução aplicada a este chamado..."
                    className="w-full text-sm rounded-xl border border-slate-200 p-3 shadow-sm focus:border-brand-primary focus:ring-1 focus:ring-brand-primary outline-none resize-none transition-all"
                  />
                  <div className="flex gap-2 justify-end">
                    <button
                      onClick={() => setIsEditingSolution(false)}
                      disabled={isSavingSolution}
                      className="px-4 py-1.5 rounded-xl text-xs font-semibold border border-slate-200 bg-white text-slate-700 hover:bg-slate-50 transition-colors disabled:opacity-50"
                    >
                      Cancelar
                    </button>
                    <button
                      onClick={handleSaveSolution}
                      disabled={isSavingSolution}
                      className="px-4 py-1.5 rounded-xl text-xs font-semibold bg-brand-primary text-white hover:bg-brand-primary-hover transition-colors disabled:opacity-50 inline-flex items-center gap-1.5"
                    >
                      {isSavingSolution ? 'Salvando...' : 'Salvar'}
                    </button>
                  </div>
                </div>
              ) : (
                <p className={`mt-2 text-sm whitespace-pre-wrap leading-relaxed ${
                  ticket.solutionText ? 'text-emerald-700' : 'text-amber-700 italic font-medium'
                }`}>
                  {ticket.solutionText || 'Nenhuma descrição de solução foi informada para este chamado solucionado.'}
                </p>
              )}
            </div>
          )}
          <TicketTimeline
            ticket={ticket}
            isResolved={isResolved}
            userId={user?.id}
            userRole={user?.role}
            claiming={claiming}
            transferring={transferring}
            closing={closing}
            uploadingAttachment={uploadingAttachment}
            showTransfer={showTransfer}
            users={users}
            selectedUserId={selectedUserId}
            onClaim={handleClaim}
            onOpenTransfer={handleOpenTransfer}
            onOpenResolveModal={openResolveModal}
            onSelectedUserIdChange={setSelectedUserId}
            onTransfer={handleTransfer}
            onCancelTransfer={handleCancelTransfer}
            onUploadAttachment={handleAttachmentUpload}
          />
        </div>

        <div className="lg:col-span-4">
          <TicketSidebar
            ticket={ticket}
            assets={assets}
            loadingAssets={loadingAssets}
            userRole={user?.role}
            categories={categories}
            loadingCategories={loadingCategories}
            updatingCategory={updatingCategory}
            users={users}
            loadingUsers={loadingUsers}
            addingAdditionalUser={addingAdditionalUser}
            onChangeCategory={handleChangeCategory}
            onAddAdditionalUser={handleAddAdditionalUser}
            onRefresh={loadTicket}
            onApplyMacro={handleApplyMacro}
          />
        </div>
      </div>

      <ResolveTicketModal
        isOpen={showResolveModal}
        onClose={() => {
          closeResolveModal();
          setResolveInitialNotes('');
        }}
        onResolve={handleResolve}
        requesterId={ticket.requesterId}
        ticket={ticket}
        initialNotes={resolveInitialNotes}
      />
    </main>
  );
}

