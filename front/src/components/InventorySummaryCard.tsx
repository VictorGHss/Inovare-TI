import { AlertCircle, Package, Zap } from 'lucide-react';
import type { InventorySummaryDTO } from '../services/api';

interface InventorySummaryCardProps {
  data: InventorySummaryDTO;
}

export default function InventorySummaryCard({ data }: InventorySummaryCardProps) {
  return (
    <div className="bg-white rounded-lg shadow p-6">
      <h3 className="text-lg font-semibold text-slate-800 mb-4">Resumo do Inventário</h3>
      <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
        {/* Total Items */}
        <div className="flex items-start gap-4 p-4 bg-slate-50 rounded-lg">
          <div className="flex-shrink-0">
            <Package className="w-8 h-8 text-brand-primary" />
          </div>
          <div>
            <p className="text-sm font-medium text-slate-600">Total de Itens</p>
            <p className="text-2xl font-bold text-slate-800">{data.totalItems}</p>
          </div>
        </div>

        {/* Low Stock Items */}
        <div className="flex items-start gap-4 p-4 bg-amber-50 rounded-lg">
          <div className="flex-shrink-0">
            <Zap className="w-8 h-8 text-amber-500" />
          </div>
          <div>
            <p className="text-sm font-medium text-slate-600">Estoque Baixo</p>
            <p className="text-2xl font-bold text-slate-800">{data.lowStockItems}</p>
          </div>
        </div>

        {/* Out of Stock Items */}
        <div className="flex items-start gap-4 p-4 bg-red-50 rounded-lg">
          <div className="flex-shrink-0">
            <AlertCircle className="w-8 h-8 text-red-500" />
          </div>
          <div>
            <p className="text-sm font-medium text-slate-600">Fora do Estoque</p>
            <p className="text-2xl font-bold text-slate-800">{data.outOfStockItems}</p>
          </div>
        </div>
      </div>
    </div>
  );
}
