import { ArrowLeft } from 'lucide-react';
import { useEffect } from 'react';
import { useNavigate, useParams } from 'react-router-dom';

import PageHero from '../../components/PageHero';
import { useAuth } from '../../contexts/AuthContext';
import { useTicketDetails } from '../../hooks/useTicketDetails';
import ResolveTicketModal from './ResolveTicketModal';
import TicketHeader from './TicketHeader';
import TicketSidebar from './TicketSidebar';
import TicketTimeline from './TicketTimeline';

export default function TicketDetails() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const { user } = useAuth();
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
    isResolved,
    setSelectedUserId,
    handleResolve,
    handleClaim,
    handleOpenTransfer,
    handleCancelTransfer,
    handleTransfer,
    handleAttachmentUpload,
    openResolveModal,
    closeResolveModal,
  } = useTicketDetails({
    ticketId: id,
  });

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
            className="inline-flex items-center gap-2 rounded-2xl border border-[#feb56c]/40 bg-white px-4 py-2.5 text-sm font-semibold text-slate-700 transition-colors hover:bg-[#fff8f1]"
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
          />
        </div>
      </div>

      <ResolveTicketModal
        isOpen={showResolveModal}
        onClose={closeResolveModal}
        onResolve={handleResolve}
        requesterId={ticket.requesterId}
        ticket={ticket}
      />
    </main>
  );
}

