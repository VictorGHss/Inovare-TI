import { useCallback, useState } from 'react';
import { toast } from 'react-toastify';
import api from '../services/api';
import { executeFinanceAutomationNow } from '../services/financeService';
import { exportInventoryExitsReport } from '../services/inventoryService';

interface UseFinancialActionsParams {
  startDate: string;
  endDate: string;
  reloadDashboardData: () => Promise<void>;
}

export function useFinancialActions({
  startDate,
  endDate,
  reloadDashboardData,
}: UseFinancialActionsParams) {
  const [triggeringTestReceipt, setTriggeringTestReceipt] = useState(false);
  const [syncingReceipts, setSyncingReceipts] = useState(false);
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

  return {
    triggeringTestReceipt,
    syncingReceipts,
    exporting,
    handleTriggerTestReceipt,
    handleSyncReceiptsNow,
    handleExportConsumption,
  };
}
