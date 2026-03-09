// Sidebar direita com resumo dos chamados
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
  color: string;
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
    { label: 'Abertos', value: openTickets, color: 'text-red-600' },
    { label: 'Em Andamento', value: inProgressTickets, color: 'text-yellow-600' },
    { label: 'Resolvidos', value: resolvedTickets, color: 'text-green-600' },
    // SECURITY: Only show low stock items for ADMIN and TECHNICIAN
    ...(isAdmin ? [{ label: 'Itens Baixo Estoque', value: lowStockItems, color: 'text-orange-600' }] : []),
  ];

  // Use provided totalTickets or calculate from components
  const total = totalTickets ?? (openTickets + inProgressTickets + resolvedTickets + closedTickets);

  return (
    <aside className="w-full lg:w-64 shrink-0">
      <div className="sticky top-6 bg-white rounded-2xl border border-slate-200 shadow-sm p-6">
        <h2 className="text-sm font-semibold text-slate-500 uppercase tracking-wider mb-4">
          Resumo
        </h2>

        <ul className="flex flex-col gap-3">
          {summaryItems.map((item) => (
            <li key={item.label} className="flex items-center justify-between">
              <span className="text-sm text-slate-600">{item.label}</span>
              <span className={`text-sm font-bold ${item.color}`}>
                {item.value}
              </span>
            </li>
          ))}
        </ul>

        <div className="mt-4 pt-4 border-t border-slate-100 flex items-center justify-between">
          <span className="text-sm font-semibold text-slate-700">Total Tickets</span>
          <span className="text-sm font-bold text-primary">{total}</span>
        </div>
      </div>
    </aside>
  );
}

