import { useCallback, useEffect, useMemo, useState } from 'react';
import axios from 'axios';

import { getAuditLogs } from '@/services/inventoryService';
import { getUsers } from '@/services/userService';
import type { AuditAction, AuditLogPage, AuditSeverity, User } from '@/types/models';

interface AppliedLogFilters {
  userId: string;
  action: AuditAction | '';
  severity: AuditSeverity | '';
  search: string;
  startDate: string;
  endDate: string;
}

const PAGE_SIZE = 20;

export function useSystemLogs() {
  const [filterUserId, setFilterUserId] = useState('');
  const [filterAction, setFilterAction] = useState<AuditAction | ''>('');
  const [filterSeverity, setFilterSeverity] = useState<AuditSeverity | ''>('');
  const [search, setSearch] = useState('');
  const [startDate, setStartDate] = useState('');
  const [endDate, setEndDate] = useState('');

  const [appliedFilters, setAppliedFilters] = useState<AppliedLogFilters>({
    userId: '',
    action: '',
    severity: '',
    search: '',
    startDate: '',
    endDate: '',
  });

  const [users, setUsers] = useState<User[]>([]);
  const [page, setPage] = useState<AuditLogPage | null>(null);
  const [currentPage, setCurrentPage] = useState(0);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const fetchUsers = useCallback(async () => {
    try {
      const usersData = await getUsers();
      setUsers(usersData);
    } catch {
      setUsers([]);
    }
  }, []);

  const fetchLogs = useCallback(async () => {
    setLoading(true);
    setError(null);

    try {
      const data = await getAuditLogs({
        userId: appliedFilters.userId || undefined,
        action: appliedFilters.action || undefined,
        severity: appliedFilters.severity || undefined,
        search: appliedFilters.search.trim() || undefined,
        startDate: appliedFilters.startDate ? `${appliedFilters.startDate}T00:00:00` : undefined,
        endDate: appliedFilters.endDate ? `${appliedFilters.endDate}T23:59:59` : undefined,
        page: currentPage,
        size: PAGE_SIZE,
      });

      setPage(data);
    } catch (requestError) {
      if (axios.isAxiosError(requestError) && requestError.response?.status === 500) {
        setError('Erro interno no servidor ao consultar a auditoria. Tente novamente em instantes.');
      } else {
        setError('Erro ao carregar logs. Verifique sua conexao e tente novamente.');
      }
    } finally {
      setLoading(false);
    }
  }, [appliedFilters, currentPage]);

  useEffect(() => {
    void fetchUsers();
  }, [fetchUsers]);

  useEffect(() => {
    void fetchLogs();
  }, [fetchLogs]);

  const applyFilters = useCallback(() => {
    setCurrentPage(0);
    setAppliedFilters({
      userId: filterUserId,
      action: filterAction,
      severity: filterSeverity,
      search,
      startDate,
      endDate,
    });
  }, [filterAction, filterSeverity, filterUserId, search, startDate, endDate]);

  const goToPreviousPage = useCallback(() => {
    if (page?.first) {
      return;
    }

    setCurrentPage((prevPage) => Math.max(prevPage - 1, 0));
  }, [page?.first]);

  const goToNextPage = useCallback(() => {
    if (page?.last) {
      return;
    }

    setCurrentPage((prevPage) => prevPage + 1);
  }, [page?.last]);

  const isFiltering = useMemo(
    () => Boolean(appliedFilters.userId || appliedFilters.action || appliedFilters.severity || appliedFilters.search.trim() || appliedFilters.startDate || appliedFilters.endDate),
    [appliedFilters],
  );

  return {
    filterUserId,
    setFilterUserId,
    filterAction,
    setFilterAction,
    filterSeverity,
    setFilterSeverity,
    search,
    setSearch,
    startDate,
    setStartDate,
    endDate,
    setEndDate,
    users,
    page,
    loading,
    error,
    isFiltering,
    applyFilters,
    goToPreviousPage,
    goToNextPage,
  };
}
