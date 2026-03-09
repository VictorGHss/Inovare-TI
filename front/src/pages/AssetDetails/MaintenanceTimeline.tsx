import type { AssetMaintenance } from '../../services/api';
import { formatCurrency } from '../../lib/formatters';

interface MaintenanceTimelineProps {
  maintenances: AssetMaintenance[];
}

const typeColors = {
  'Preventiva': { bg: 'bg-brand-secondary', border: 'border-brand-primary', badge: 'bg-brand-secondary text-brand-primary' },
  'Corretiva': { bg: 'bg-red-50', border: 'border-red-200', badge: 'bg-red-100 text-red-800' },
  'Upgrade': { bg: 'bg-brand-secondary', border: 'border-brand-primary', badge: 'bg-brand-secondary text-brand-primary' },
  'Transferência': { bg: 'bg-amber-50', border: 'border-amber-200', badge: 'bg-amber-100 text-amber-800' },
};

export default function MaintenanceTimeline({ maintenances }: MaintenanceTimelineProps) {
  if (maintenances.length === 0) {
    return (
      <div className="bg-slate-50 rounded-xl border border-slate-200 p-6 text-center">
        <p className="text-sm text-slate-500">
          Nenhuma manutenção registrada para este ativo.
        </p>
      </div>
    );
  }

  return (
    <div className="space-y-3">
      {maintenances.map((maintenance) => {
        const typeKey = maintenance.type as keyof typeof typeColors;
        const colors = typeColors[typeKey] || typeColors['Preventiva'];
        const formattedDate = new Date(maintenance.maintenanceDate).toLocaleDateString('pt-BR');

        return (
          <div
            key={maintenance.id}
            className={`${colors.bg} border ${colors.border} rounded-lg p-4`}
          >
            {/* Cabeçalho do card */}
            <div className="flex items-start justify-between gap-3 mb-3">
              <div>
                <div className="flex items-center gap-2 mb-1">
                  <span className={`${colors.badge} text-xs font-semibold px-2.5 py-1 rounded`}>
                    {maintenance.type}
                  </span>
                </div>
                <p className="text-sm font-medium text-slate-700">
                  {formattedDate}
                </p>
              </div>
              {maintenance.cost && (
                <p className="text-sm font-bold text-slate-800">
                  {formatCurrency(maintenance.cost)}
                </p>
              )}
            </div>

            {/* Técnico */}
            <div className="flex items-center gap-2 mb-3 pb-3 border-b border-white/50">
              <div className="w-7 h-7 rounded-full bg-white/50 flex items-center justify-center">
                <span className="text-xs font-semibold text-slate-600">T</span>
              </div>
              <div className="flex-1 min-w-0">
                <p className="text-xs text-slate-500">Técnico</p>
                <p className="text-sm font-medium text-slate-700 truncate">
                  {maintenance.technicianName}
                </p>
                <p className="text-xs text-slate-500 truncate">
                  {maintenance.technicianEmail}
                </p>
              </div>
            </div>

            {/* Descrição */}
            {maintenance.description && (
              <div className="bg-white/50 rounded p-3">
                <p className="text-xs text-slate-500 font-medium mb-1">Descrição</p>
                <p className="text-sm text-slate-700 whitespace-pre-wrap">
                  {maintenance.description}
                </p>
              </div>
            )}
          </div>
        );
      })}
    </div>
  );
}
