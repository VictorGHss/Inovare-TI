import { useCallback, useState } from 'react';
import { toast } from 'react-toastify';
import api from '../services/api';
import { executeFinanceAutomationNow, syncDoctorsBaseFromContaAzul } from '../services/financeService';
import { exportInventoryExitsReport } from '../services/inventoryService';

interface UseFinancialActionsParams {
  startDate: string;
  endDate: string;
  reloadDashboardData: () => Promise<void>;
  reloadDoctorMappings: () => Promise<void>;
}

export function useFinancialActions({
  startDate,
  endDate,
  reloadDashboardData,
  reloadDoctorMappings,
}: UseFinancialActionsParams) {
  const [triggeringTestReceipt, setTriggeringTestReceipt] = useState(false);
  const [syncingReceipts, setSyncingReceipts] = useState(false);
  const [syncingDoctors, setSyncingDoctors] = useState(false);
  const [exporting, setExporting] = useState(false);

  const handleTriggerTestReceipt = useCallback(async () => {
    try {
      setTriggeringTestReceipt(true);
      await api.get('/api/financeiro/trigger-test-receipt');
      toast.success('Recibo de teste enviado com sucesso.');
    } catch {
      toast.error('Falha ao enviar recibo de teste.');
    } finally {
      setTriggeringTestReceipt(false);
    }
  }, []);

  const handleSyncReceiptsNow = useCallback(async () => {
    if (startDate > endDate) {
      toast.error('Data Início não pode ser maior que Data Fim.');
      return;
    }

    try {
      setSyncingReceipts(true);
      await executeFinanceAutomationNow({ dataInicio: startDate, dataFim: endDate });
      await reloadDashboardData();
      toast.success('Sincronização executada com sucesso.');
    } catch {
      toast.error('Falha ao executar sincronização manual de recibos.');
    } finally {
      setSyncingReceipts(false);
    }
  }, [endDate, reloadDashboardData, startDate]);

  const handleExportConsumption = useCallback(async () => {
    if (startDate > endDate) {
      toast.error('Data Início não pode ser maior que Data Fim.');
      return;
    }

    try {
      setExporting(true);
      const blob = await exportInventoryExitsReport({ startDate, endDate });
      const url = window.URL.createObjectURL(new Blob([blob]));
      const link = document.createElement('a');
      link.href = url;
      const filename = `relatorio_consumo_${startDate}_to_${endDate}.xlsx`;
      link.setAttribute('download', filename);
      document.body.appendChild(link);
      link.click();
      link.parentNode?.removeChild(link);
      window.URL.revokeObjectURL(url);
    } catch {
      toast.error('Falha ao exportar relatório de consumo.');
    } finally {
      setExporting(false);
    }
  }, [endDate, startDate]);

  const handleSyncDoctors = useCallback(async () => {
    try {
      setSyncingDoctors(true);
      const result = await syncDoctorsBaseFromContaAzul();
      await reloadDoctorMappings();
      toast.success(`Sincronização concluída. Novos: ${result.novos}. Atualizados: ${result.atualizados}.`);
    } catch {
      toast.error('Falha ao sincronizar médicos da Conta Azul.');
    } finally {
      setSyncingDoctors(false);
    }
  }, [reloadDoctorMappings]);

  return {
    triggeringTestReceipt,
    syncingReceipts,
    syncingDoctors,
    exporting,
    handleTriggerTestReceipt,
    handleSyncReceiptsNow,
    handleSyncDoctors,
    handleExportConsumption,
  };
}
