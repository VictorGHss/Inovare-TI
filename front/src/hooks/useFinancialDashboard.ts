import { useCallback, useEffect, useMemo, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { toast } from 'react-toastify';
import {
  getDashboardAnalytics,
  getDoctorMappings,
  getFinanceAlerts,
  getFinanceConnectionStatus,
  getFinanceReceipts,
  getFinancialSummary,
} from '../services/financeService';
import type {
  DashboardAnalyticsDTO,
  DoctorMapping,
  FinanceAlert,
  FinanceConnectionStatus,
  FinanceReceipt,
  FinancialSummaryDTO,
} from '../types/models';

function formatDateForInput(value: Date): string {
  const year = value.getFullYear();
  const month = String(value.getMonth() + 1).padStart(2, '0');
  const day = String(value.getDate()).padStart(2, '0');
  return `${year}-${month}-${day}`;
}

function getDefaultCycleDates() {
  const now = new Date();
  const day = now.getDate();

  let start: Date;
  if (day >= 12) {
    start = new Date(now.getFullYear(), now.getMonth(), 12);
  } else {
    start = new Date(now.getFullYear(), now.getMonth() - 1, 12);
  }

  return {
    start: formatDateForInput(start),
    end: formatDateForInput(now),
  };
}

export function useFinancialDashboard() {
  const [searchParams, setSearchParams] = useSearchParams();

  const [loading, setLoading] = useState(true);
  const [connectionStatus, setConnectionStatus] = useState<FinanceConnectionStatus | null>(null);
  const [summary, setSummary] = useState<FinancialSummaryDTO | null>(null);
  const [analytics, setAnalytics] = useState<DashboardAnalyticsDTO | null>(null);
  const [receipts, setReceipts] = useState<FinanceReceipt[]>([]);
  const [alerts, setAlerts] = useState<FinanceAlert[]>([]);
  const [doctorMappings, setDoctorMappings] = useState<DoctorMapping[]>([]);
  const [loadingDoctorMappings, setLoadingDoctorMappings] = useState(false);

  const defaultCycle = useMemo(() => getDefaultCycleDates(), []);
  const [startDate, setStartDate] = useState(defaultCycle.start);
  const [endDate, setEndDate] = useState(defaultCycle.end);
  const [activeTab, setActiveTab] = useState<'local' | 'integration'>('local');

  const hasContaAzulLinked =
    summary?.integrationActive ?? (connectionStatus?.authorized === true);
  const unresolvedAlerts = useMemo(() => alerts.filter((alert) => !alert.resolved), [alerts]);

  useEffect(() => {
    if (searchParams.get('success') === 'true') {
      toast.success('Conexão com Conta Azul estabelecida com sucesso!');
      const nextParams = new URLSearchParams(searchParams);
      nextParams.delete('success');
      setSearchParams(nextParams, { replace: true });
      return;
    }

    if (searchParams.get('success') === 'false') {
      const errorDescription = searchParams.get('error_description');
      toast.error(errorDescription ?? 'Não foi possível concluir a integração com a ContaAzul.');

      const nextParams = new URLSearchParams(searchParams);
      nextParams.delete('success');
      nextParams.delete('error');
      nextParams.delete('error_description');
      setSearchParams(nextParams, { replace: true });
    }
  }, [searchParams, setSearchParams]);

  const reloadDoctorMappings = useCallback(async () => {
    try {
      setLoadingDoctorMappings(true);
      const dados = await getDoctorMappings();
      setDoctorMappings(Array.isArray(dados) ? dados : []);
    } catch {
      toast.error('Não foi possível carregar os mapeamentos de médicos.');
      setDoctorMappings([]);
    } finally {
      setLoadingDoctorMappings(false);
    }
  }, []);

  const reloadDashboardData = useCallback(async () => {
    try {
      const status = await getFinanceConnectionStatus();
      setConnectionStatus(status);

      const summaryData = await getFinancialSummary();
      setSummary(summaryData ?? null);

      const integrationActive = summaryData?.integrationActive ?? status?.authorized === true;

      if (!integrationActive) {
        setReceipts([]);
        setAlerts([]);
        setAnalytics(null);
        await reloadDoctorMappings();
        return;
      }

      const [receiptsData, alertsData, analyticsData] = await Promise.all([
        getFinanceReceipts(),
        getFinanceAlerts(),
        getDashboardAnalytics(),
      ]);

      setReceipts(Array.isArray(receiptsData) ? receiptsData : []);
      setAlerts(Array.isArray(alertsData) ? alertsData : []);
      setAnalytics(analyticsData ?? null);
      await reloadDoctorMappings();
    } catch (err) {
      console.error('Erro ao carregar dados do dashboard financeiro', err);
      toast.error('Não foi possível carregar dados financeiros.');
      setSummary(null);
      setReceipts([]);
      setAlerts([]);
      setAnalytics(null);
      setDoctorMappings([]);
    }
  }, [reloadDoctorMappings]);

  useEffect(() => {
    async function loadDashboard() {
      try {
        setLoading(true);
        await reloadDashboardData();
      } catch {
        toast.error('Não foi possível carregar o módulo financeiro.');
      } finally {
        setLoading(false);
      }
    }

    void loadDashboard();
  }, [reloadDashboardData]);

  return {
    loading,
    connectionStatus,
    summary,
    analytics,
    receipts,
    alerts,
    doctorMappings,
    loadingDoctorMappings,
    startDate,
    endDate,
    setStartDate,
    setEndDate,
    activeTab,
    setActiveTab,
    hasContaAzulLinked,
    unresolvedAlerts,
    reloadDashboardData,
    reloadDoctorMappings,
  };
}

