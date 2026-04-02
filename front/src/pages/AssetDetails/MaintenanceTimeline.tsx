import type { AssetMaintenance } from '../../services/api';
import { formatCurrency } from '../../lib/formatters';
import { Stethoscope, AlertTriangle, ArrowUpCircle, MoveRight } from 'lucide-react';

interface MaintenanceTimelineProps {
  maintenances: AssetMaintenance[];
}

const typeConfig = {
  'Preventiva': {
    bg: 'bg-gradient-to-br from-brand-secondary/50 to-brand-secondary/20',
    border: 'border-brand-primary/25 border-l-brand-primary',
    badge: 'bg-brand-secondary text-brand-primary-dark',
    icon: Stethoscope,
    iconBg: 'bg-brand-primary/10',
    iconColor: 'text-brand-primary-dark',
  },
  'Corretiva': {
    bg: 'bg-gradient-to-br from-red-50 to-red-50/30',
    border: 'border-red-200 border-l-red-400',
    badge: 'bg-red-100 text-red-700',
    icon: AlertTriangle,
    iconBg: 'bg-red-50',
    iconColor: 'text-red-500',
  },
  'Upgrade': {
    bg: 'bg-gradient-to-br from-brand-secondary/50 to-brand-secondary/20',
    border: 'border-brand-primary/25 border-l-brand-primary',
    badge: 'bg-brand-secondary text-brand-primary-dark',
    icon: ArrowUpCircle,
    iconBg: 'bg-brand-primary/10',
    iconColor: 'text-brand-primary-dark',
  },
  'Transferência': {
    bg: 'bg-gradient-to-br from-amber-50 to-amber-50/30',
    border: 'border-amber-200 border-l-amber-400',
    badge: 'bg-amber-100 text-amber-700',
    icon: MoveRight,
    iconBg: 'bg-amber-50',
    iconColor: 'text-amber-500',
  },
};

export default function MaintenanceTimeline({ maintenances }: MaintenanceTimelineProps) {
  if (maintenances.length === 0) {
    return null; // handled by parent
  }

  return (
    <div className="space-y-3">
      {maintenances.map((maintenance) => {
        const typeKey = maintenance.type as keyof typeof typeConfig;
        const config = typeConfig[typeKey] ?? typeConfig['Preventiva'];
        const Icon = config.icon;
        const formattedDate = new Date(maintenance.maintenanceDate).toLocaleDateString('pt-BR', {
          day: '2-digit',
          month: 'long',
          year: 'numeric',
        });

        return (
          <div
            key={maintenance.id}
            className={`${config.bg} border border-l-4 ${config.border} rounded-xl p-4 transition-colors hover:bg-orange-50/40`}
          >
            {/* Header row */}
            <div className="flex items-start justify-between gap-3 mb-3">
              <div className="flex items-center gap-2.5">
                <span className={`inline-flex h-8 w-8 shrink-0 items-center justify-center rounded-lg ${config.iconBg}`}>
                  <Icon size={15} className={config.iconColor} />
                </span>
                <div>
                  <span className={`inline-block text-[10px] font-bold uppercase tracking-wider px-2 py-0.5 rounded-md ${config.badge}`}>
                    {maintenance.type}
                  </span>
                  <p className="mt-0.5 text-xs text-slate-500">{formattedDate}</p>
                </div>
              </div>

              {maintenance.cost != null && (
                <div className="text-right shrink-0">
                  <p className="text-[10px] font-bold uppercase tracking-widest text-slate-400">Custo</p>
                  <p className="text-sm font-extrabold text-slate-800">{formatCurrency(maintenance.cost)}</p>
                </div>
              )}
            </div>

            {/* Technician */}
            <div className="flex items-center gap-2.5 mb-3 pb-3 border-b border-black/5">
              <div className="h-7 w-7 rounded-full bg-white/70 border border-black/5 flex items-center justify-center shrink-0">
                <span className="text-[10px] font-extrabold text-slate-500">
                  {maintenance.technicianName?.charAt(0)?.toUpperCase() ?? 'T'}
                </span>
              </div>
              <div className="min-w-0">
                <p className="text-[10px] font-bold uppercase tracking-widest text-slate-400">Técnico</p>
                <p className="text-sm font-semibold text-slate-700 truncate">{maintenance.technicianName}</p>
                {maintenance.technicianEmail && (
                  <p className="text-xs text-slate-400 truncate">{maintenance.technicianEmail}</p>
                )}
              </div>
            </div>

            {/* Description */}
            {maintenance.description && (
              <div className="rounded-lg bg-white/60 border border-black/5 p-3">
                <p className="text-[10px] font-bold uppercase tracking-widest text-slate-400 mb-1.5">Descrição</p>
                <p className="text-sm leading-6 text-slate-700 whitespace-pre-wrap">{maintenance.description}</p>
              </div>
            )}
          </div>
        );
      })}
    </div>
  );
}
