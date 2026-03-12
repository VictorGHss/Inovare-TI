import { AlertCircle, Package, Zap } from 'lucide-react';
import { motion } from 'framer-motion';
import type { InventorySummaryDTO } from '../services/api';

interface InventorySummaryCardProps {
  data: InventorySummaryDTO;
}

export default function InventorySummaryCard({ data }: InventorySummaryCardProps) {
  const containerVariants = {
    hidden: { opacity: 0 },
    show: {
      opacity: 1,
      transition: { staggerChildren: 0.06 },
    },
  };

  const cardVariants = {
    hidden: { opacity: 0, y: 12 },
    show: {
      opacity: 1,
      y: 0,
      transition: { duration: 0.24 },
    },
  };

  return (
    <div className="bg-white rounded-xl border border-slate-200 shadow-sm p-6">
      <h3 className="text-lg font-semibold text-slate-800 mb-4">Resumo do Inventário</h3>
      <motion.div
        className="grid grid-cols-1 md:grid-cols-3 gap-4"
        variants={containerVariants}
        initial="hidden"
        animate="show"
      >
        <motion.div
          variants={cardVariants}
          className="group flex items-start gap-4 p-4 bg-slate-50 rounded-xl transition-all hover:-translate-y-0.5 hover:shadow-xl"
        >
          <div className="flex-shrink-0 rounded-xl bg-brand-secondary p-2">
            <Package className="w-6 h-6 text-brand-primary-dark" />
          </div>
          <div>
            <p className="text-sm font-medium text-slate-600">Total de Itens</p>
            <p className="text-2xl font-bold text-slate-800">{data.totalItems}</p>
          </div>
        </motion.div>

        <motion.div
          variants={cardVariants}
          className="group flex items-start gap-4 p-4 bg-amber-50 rounded-xl transition-all hover:-translate-y-0.5 hover:shadow-xl"
        >
          <div className="flex-shrink-0 rounded-xl bg-amber-100 p-2">
            <Zap className="w-6 h-6 text-amber-600" />
          </div>
          <div>
            <p className="text-sm font-medium text-slate-600">Estoque Baixo</p>
            <p className="text-2xl font-bold text-slate-800">{data.lowStockItems}</p>
          </div>
        </motion.div>

        <motion.div
          variants={cardVariants}
          className="group flex items-start gap-4 p-4 bg-red-50 rounded-xl transition-all hover:-translate-y-0.5 hover:shadow-xl"
        >
          <div className="flex-shrink-0 rounded-xl bg-red-100 p-2">
            <AlertCircle className="w-6 h-6 text-red-600" />
          </div>
          <div>
            <p className="text-sm font-medium text-slate-600">Fora do Estoque</p>
            <p className="text-2xl font-bold text-slate-800">{data.outOfStockItems}</p>
          </div>
        </motion.div>
      </motion.div>
    </div>
  );
}
