import { useCallback, useState } from 'react';
import { toast } from 'react-toastify';
import { executeFinanceAutomationNow, triggerTestReceipt } from '@/services/financeService';
import { exportInventoryExitsReport } from '@/services/reportService';

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
      await triggerTestReceipt();
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
      const result = await executeFinanceAutomationNow({ dataInicio: startDate, dataFim: endDate });
      await reloadDashboardData();

      if ((result.errors ?? []).length === 0) {
        toast.success('Sincronização executada com sucesso.');
      } else {
        const semAnexoCount = result.noAttachmentWarnings ?? 0;
        toast.warn(
          `Sincronização finalizada com avisos. ${semAnexoCount} recibos não possuíam anexo no ERP. Verifique os logs.`,
        );
      }
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
