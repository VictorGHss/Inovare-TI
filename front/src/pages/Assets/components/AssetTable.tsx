import { Download, FileText, HardDrive, Printer } from 'lucide-react';
import { motion } from 'framer-motion';
import type { Asset } from '../../../services/api';

interface Props {
  assets: Asset[];
  loading: boolean;
  userNameById: Map<string, string>;
  onOpenDetails: (asset: Asset) => void;
  onOpenInvoiceModal: (asset: Asset) => void;
  onInvoiceDownload: (asset: Asset, e: React.MouseEvent) => void;
  onOpenPrintModal: (asset: Asset) => void;
}

export default function AssetTable({
  assets,
  loading,
  userNameById,
  onOpenDetails,
  onOpenInvoiceModal,
  onInvoiceDownload,
  onOpenPrintModal,
}: Props) {
  const containerVariants = {
    hidden: { opacity: 0 },
    show: {
      opacity: 1,
      transition: { staggerChildren: 0.05 },
    },
  };

  const rowVariants = {
    hidden: { opacity: 0, y: 10 },
    show: {
      opacity: 1,
      y: 0,
      transition: { duration: 0.22 },
    },
  };

  if (loading) {
    return (
      <div className="p-12 text-center">
        <div className="animate-pulse space-y-3">
          <div className="h-4 bg-slate-200 rounded w-3/4 mx-auto" />
          <div className="h-4 bg-slate-200 rounded w-1/2 mx-auto" />
        </div>
      </div>
    );
  }

  if (assets.length === 0) {
    return <p className="text-center text-slate-400 py-12 text-sm">Nenhum ativo cadastrado.</p>;
  }

  return (
    <div className="overflow-x-auto">
      <table className="min-w-full text-sm">
        <thead className="bg-slate-50 text-slate-500 uppercase text-xs tracking-wider">
          <tr>
            <th className="px-4 py-3 text-left">Nome</th>
            <th className="px-4 py-3 text-left">Patrimônio</th>
            <th className="px-4 py-3 text-left">Categoria</th>
            <th className="px-4 py-3 text-left">Usuário Vinculado</th>
            <th className="px-4 py-3 text-center">Nota Fiscal</th>
            <th className="px-4 py-3 text-center">Etiqueta</th>
          </tr>
        </thead>
        <motion.tbody
          className="divide-y divide-slate-100"
          variants={containerVariants}
          initial="hidden"
          animate="show"
        >
          {assets.map((asset) => (
            <motion.tr
              key={asset.id}
              variants={rowVariants}
              onClick={() => onOpenDetails(asset)}
              className="cursor-pointer hover:bg-gray-50 transition-colors"
            >
              <td className="px-4 py-3 font-medium text-slate-800">
                <div className="flex items-center gap-2">
                  <HardDrive size={15} className="text-slate-400" />
                  <span>{asset.name}</span>
                </div>
              </td>
              <td className="px-4 py-3 text-slate-600">{asset.patrimonyCode}</td>
              <td className="px-4 py-3 text-slate-600">{asset.categoryName ?? 'Sem categoria'}</td>
              <td className="px-4 py-3 text-slate-600">
                {asset.assignedToName ??
                  (asset.userId
                    ? (userNameById.get(asset.userId) ?? 'Usuário não encontrado')
                    : 'No estoque (TI)')}
              </td>
              <td className="px-4 py-3">
                <div className="flex items-center justify-center gap-2">
                  {asset.invoiceFileName ? (
                    <button
                      onClick={(e) => onInvoiceDownload(asset, e)}
                      className="flex items-center gap-1.5 text-xs font-medium text-brand-primary hover:text-brand-primary-dark hover:bg-brand-secondary px-3 py-1.5 rounded-lg transition-colors"
                      title="Visualizar/baixar nota fiscal"
                    >
                      <Download size={14} />
                      Ver NF
                    </button>
                  ) : (
                    <button
                      onClick={(e) => {
                        e.stopPropagation();
                        onOpenInvoiceModal(asset);
                      }}
                      className="flex items-center gap-1.5 text-xs font-medium text-slate-500 hover:text-slate-700 hover:bg-slate-100 px-3 py-1.5 rounded-lg transition-colors"
                      title="Anexar nota fiscal"
                    >
                      <FileText size={14} />
                      Anexar NF
                    </button>
                  )}
                </div>
              </td>
              <td className="px-4 py-3">
                <div className="flex items-center justify-center">
                  <button
                    onClick={(e) => {
                      e.stopPropagation();
                      onOpenPrintModal(asset);
                    }}
                    className="flex items-center gap-1.5 text-xs font-medium text-primary hover:text-primary-hover hover:bg-brand-secondary px-3 py-1.5 rounded-lg transition-colors"
                    title="Imprimir etiqueta com QR Code"
                  >
                    <Printer size={14} />
                    Imprimir
                  </button>
                </div>
              </td>
            </motion.tr>
          ))}
        </motion.tbody>
      </table>
    </div>
  );
}
