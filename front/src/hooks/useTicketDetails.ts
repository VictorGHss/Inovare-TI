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
  onTicketNotFound?: () => void;
}

export function useTicketDetails({ ticketId, onTicketNotFound }: UseTicketDetailsParams) {
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

  const fetchTicket = useCallback(async () => {
    if (!ticketId) return;

    try {
      const data = await getTicketById(ticketId);
      setTicket(data);
    } catch {
      toast.error('Chamado nao encontrado.');
      onTicketNotFound?.();
    }
  }, [onTicketNotFound, ticketId]);

  useEffect(() => {
    if (!ticketId) {
      setLoading(false);
      setTicket(null);
      return;
    }

    setLoading(true);
    void fetchTicket().finally(() => setLoading(false));
  }, [fetchTicket, ticketId]);

  useEffect(() => {
    async function fetchAssets() {
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

    void fetchAssets();
  }, [ticket?.requesterId]);

  async function handleResolve(request: ResolveTicketRequest) {
    if (!ticket) return;

    setClosing(true);
    try {
      const updated = await resolveTicket(ticket.id, request);
      setTicket(updated);
      setShowResolveModal(false);
      toast.success('Chamado resolvido com sucesso!');
    } catch (error) {
      toast.error('Erro ao resolver o chamado. Tente novamente.');
      throw error;
    } finally {
      setClosing(false);
    }
  }

  async function handleClaim() {
    if (!ticket) return;

    setClaiming(true);
    try {
      await claimTicket(ticket.id);
      await fetchTicket();
      toast.success('Chamado assumido com sucesso!');
    } catch {
      toast.error('Erro ao assumir o chamado.');
    } finally {
      setClaiming(false);
    }
  }

  async function handleOpenTransfer() {
    try {
      const usersData = await getUsers();
      setUsers(usersData);
      setShowTransfer(true);
    } catch {
      toast.error('Erro ao carregar usuarios para transferencia.');
    }
  }

  function handleCancelTransfer() {
    setShowTransfer(false);
    setSelectedUserId('');
  }

  async function handleTransfer() {
    if (!ticket || !selectedUserId) return;

    setTransferring(true);
    try {
      await transferTicket(ticket.id, selectedUserId);
      await fetchTicket();
      handleCancelTransfer();
      toast.success('Chamado transferido com sucesso!');
    } catch {
      toast.error('Erro ao transferir chamado.');
    } finally {
      setTransferring(false);
    }
  }

  async function handleAttachmentUpload(file: File) {
    if (!ticket) return;

    setUploadingAttachment(true);
    try {
      await uploadTicketAttachment(ticket.id, file);
      await fetchTicket();
      toast.success('Anexo enviado com sucesso!');
    } catch {
      toast.error('Erro ao enviar anexo.');
    } finally {
      setUploadingAttachment(false);
    }
  }

  function openResolveModal() {
    setShowResolveModal(true);
  }

  function closeResolveModal() {
    setShowResolveModal(false);
  }

  return {
    ticket,
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
    setSelectedUserId,
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
