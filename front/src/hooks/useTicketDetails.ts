import { useCallback, useEffect, useState } from 'react';
import { toast } from 'react-toastify';

import { getUsers } from '../services/userService';
import {
  claimTicket,
  getTicketById,
  resolveTicket,
  transferTicket,
  uploadTicketAttachment,
} from '../services/ticketService';
import { getAssetsByUser } from '../services/inventoryService';
import type { Asset, ResolveTicketRequest, Ticket, User } from '../types/models';

interface UseTicketDetailsParams {
  ticketId?: string;
}

type TicketBackendPayload = Ticket & {
  technicianId?: string | null;
  technician_id?: string | null;
  technician?: { id?: string | null } | string | null;
};

function normalizeTechnicianId(ticketData: TicketBackendPayload): Ticket {
  const technicianFromObject =
    ticketData.technician && typeof ticketData.technician === 'object'
      ? (ticketData.technician.id ?? null)
      : null;

  return {
    ...ticketData,
    technicianId:
      ticketData.technicianId ??
      ticketData.technician_id ??
      technicianFromObject ??
      ticketData.assignedToId ??
      null,
  };
}

export function useTicketDetails({ ticketId }: UseTicketDetailsParams) {
  const [ticket, setTicket] = useState<Ticket | null>(null);
  const [loading, setLoading] = useState(true);
  const [closing, setClosing] = useState(false);
  const [claiming, setClaiming] = useState(false);
  const [transferring, setTransferring] = useState(false);
  const [uploadingAttachment, setUploadingAttachment] = useState(false);
  const [showTransfer, setShowTransfer] = useState(false);
  const [showResolveModal, setShowResolveModal] = useState(false);
  const [users, setUsers] = useState<User[]>([]);
  const [selectedUserId, setSelectedUserId] = useState('');
  const [assets, setAssets] = useState<Asset[]>([]);
  const [loadingAssets, setLoadingAssets] = useState(false);
  const [ticketNotFound, setTicketNotFound] = useState(false);

  const loadTicket = useCallback(async () => {
    if (!ticketId) return;

    try {
      const data = await getTicketById(ticketId);
      setTicket(normalizeTechnicianId(data as TicketBackendPayload));
      setTicketNotFound(false);
    } catch {
      toast.error('Chamado nao encontrado.');
      setTicketNotFound(true);
      setTicket(null);
    }
  }, [ticketId]);

  useEffect(() => {
    if (!ticketId) {
      setLoading(false);
      setTicket(null);
      setTicketNotFound(false);
      return;
    }

    setLoading(true);
    void loadTicket().finally(() => setLoading(false));
    // Dependência intencionalmente restrita ao id para evitar loops por referência de função.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [ticketId]);

  useEffect(() => {
    async function loadAssets() {
      if (!ticket?.requesterId) {
        setAssets([]);
        return;
      }

      setLoadingAssets(true);
      try {
        const data = await getAssetsByUser(ticket.requesterId);
        setAssets(data);
      } catch {
        toast.error('Erro ao carregar equipamentos do usuario.');
      } finally {
        setLoadingAssets(false);
      }
    }

    void loadAssets();
  }, [ticket?.requesterId]);

  const handleResolve = useCallback(async (request: ResolveTicketRequest) => {
    if (!ticket) return;

    setClosing(true);
    try {
      const updated = await resolveTicket(ticket.id, request);
      setTicket(normalizeTechnicianId(updated as TicketBackendPayload));
      setShowResolveModal(false);
      toast.success('Chamado resolvido com sucesso!');
    } catch (error) {
      toast.error('Erro ao resolver o chamado. Tente novamente.');
      throw error;
    } finally {
      setClosing(false);
    }
  }, [ticket]);

  const handleClaim = useCallback(async () => {
    if (!ticket) return;

    setClaiming(true);
    try {
      await claimTicket(ticket.id);
      await loadTicket();
      toast.success('Chamado assumido com sucesso!');
    } catch {
      toast.error('Erro ao assumir o chamado.');
    } finally {
      setClaiming(false);
    }
  }, [loadTicket, ticket]);

  const handleOpenTransfer = useCallback(async () => {
    try {
      const usersData = await getUsers();
      setUsers(usersData);
      setShowTransfer(true);
    } catch {
      toast.error('Erro ao carregar usuarios para transferencia.');
    }
  }, []);

  const handleCancelTransfer = useCallback(() => {
    setShowTransfer(false);
    setSelectedUserId('');
  }, []);

  const handleTransfer = useCallback(async () => {
    if (!ticket || !selectedUserId) return;

    setTransferring(true);
    try {
      await transferTicket(ticket.id, selectedUserId);
      await loadTicket();
      handleCancelTransfer();
      toast.success('Chamado transferido com sucesso!');
    } catch {
      toast.error('Erro ao transferir chamado.');
    } finally {
      setTransferring(false);
    }
  }, [handleCancelTransfer, loadTicket, selectedUserId, ticket]);

  const handleAttachmentUpload = useCallback(async (file: File) => {
    if (!ticket) return;
    if (!ticket.technicianId) {
      toast.error('Ação não permitida. Assuma o chamado primeiro.');
      return;
    }

    setUploadingAttachment(true);
    try {
      await uploadTicketAttachment(ticket.id, file);
      await loadTicket();
      toast.success('Anexo enviado com sucesso!');
    } catch {
      toast.error('Erro ao enviar anexo.');
    } finally {
      setUploadingAttachment(false);
    }
  }, [loadTicket, ticket]);

  const openResolveModal = useCallback(() => {
    setShowResolveModal(true);
  }, []);

  const closeResolveModal = useCallback(() => {
    setShowResolveModal(false);
  }, []);

  const updateSelectedUserId = useCallback((userId: string) => {
    setSelectedUserId(userId);
  }, []);

  return {
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
    isResolved: ticket?.status === 'RESOLVED',
    loadTicket,
    setSelectedUserId: updateSelectedUserId,
    handleResolve,
    handleClaim,
    handleOpenTransfer,
    handleCancelTransfer,
    handleTransfer,
    handleAttachmentUpload,
    openResolveModal,
    closeResolveModal,
  };
}
