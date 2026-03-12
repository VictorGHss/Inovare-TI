// Sidebar direita com resumo dos chamados
import { motion } from 'framer-motion';
import type { LucideIcon } from 'lucide-react';
import { AlertCircle, CheckCircle2, Clock3, PackageMinus, Tickets } from 'lucide-react';

interface SummaryAsideProps {
  openTickets: number;
  inProgressTickets: number;
  resolvedTickets: number;
  lowStockItems: number;
  totalTickets?: number;
  closedTickets?: number;
  isAdmin?: boolean;
}

interface SummaryItem {
  label: string;
  value: number;
  colorClass: string;
  iconBgClass: string;
  icon: LucideIcon;
}

export default function SummaryAside({
  openTickets,
  inProgressTickets,
  resolvedTickets,
  lowStockItems,
  totalTickets,
  closedTickets = 0,
  isAdmin = true,
}: SummaryAsideProps) {
  const summaryItems: SummaryItem[] = [
    {
      label: 'Abertos',
      value: openTickets,
      colorClass: 'text-red-600',
      iconBgClass: 'bg-red-100',
      icon: AlertCircle,
    },
    {
      label: 'Em Andamento',
      value: inProgressTickets,
      colorClass: 'text-amber-600',
      iconBgClass: 'bg-amber-100',
      icon: Clock3,
    },
    {
      label: 'Resolvidos',
      value: resolvedTickets,
      colorClass: 'text-emerald-600',
      iconBgClass: 'bg-emerald-100',
      icon: CheckCircle2,
    },
    ...(isAdmin
      ? [{
        label: 'Itens Baixo Estoque',
        value: lowStockItems,
        colorClass: 'text-brand-primary-dark',
        iconBgClass: 'bg-brand-secondary',
        icon: PackageMinus,
      }]
      : []),
  ];

  const total = totalTickets ?? (openTickets + inProgressTickets + resolvedTickets + closedTickets);

  const containerVariants = {
    hidden: { opacity: 0 },
    show: {
      opacity: 1,
      transition: { staggerChildren: 0.05 },
    },
  };

  const itemVariants = {
    hidden: { opacity: 0, y: 10 },
    show: {
      opacity: 1,
      y: 0,
      transition: { duration: 0.24 },
    },
  };

  return (
    <aside className="w-full">
      <motion.div
        className="grid grid-cols-1 sm:grid-cols-2 xl:grid-cols-5 gap-4"
        variants={containerVariants}
        initial="hidden"
        animate="show"
      >
        {summaryItems.map((item) => {
          const Icon = item.icon;
          return (
            <motion.article
              key={item.label}
              variants={itemVariants}
              className="group rounded-2xl border border-slate-200 bg-white p-4 shadow-sm transition-all hover:-translate-y-0.5 hover:shadow-xl"
            >
              <div className="mb-3 flex items-center justify-between">
                <span className={`inline-flex h-10 w-10 items-center justify-center rounded-xl ${item.iconBgClass}`}>
                  <Icon size={18} className={item.colorClass} />
                </span>
                <span className={`text-2xl font-bold ${item.colorClass}`}>{item.value}</span>
              </div>
              <p className="text-xs font-semibold uppercase tracking-wide text-slate-500">{item.label}</p>
            </motion.article>
          );
        })}

        <motion.article
          variants={itemVariants}
          className="rounded-2xl border border-slate-200 bg-white p-4 shadow-sm transition-all hover:-translate-y-0.5 hover:shadow-xl"
        >
          <div className="mb-3 flex items-center justify-between">
            <span className="inline-flex h-10 w-10 items-center justify-center rounded-xl bg-slate-100">
              <Tickets size={18} className="text-slate-700" />
            </span>
            <span className="text-2xl font-bold text-brand-primary">{total}</span>
          </div>
          <p className="text-xs font-semibold uppercase tracking-wide text-slate-500">Total Tickets</p>
        </motion.article>
      </motion.div>
      <div className="hidden lg:block" aria-hidden="true">
        <div className="sticky top-6" />
      </div>
    </aside>
  );
}

