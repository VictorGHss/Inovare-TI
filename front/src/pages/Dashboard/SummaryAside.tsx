// Sidebar direita com resumo dos chamados
interface SummaryItem {
  label: string;
  value: number;
  color: string;
}

const summaryItems: SummaryItem[] = [
  { label: 'Abertos', value: 4, color: 'text-red-600' },
  { label: 'Em Andamento', value: 2, color: 'text-yellow-600' },
  { label: 'Resolvidos', value: 7, color: 'text-green-600' },
  { label: 'Fechados', value: 3, color: 'text-gray-500' },
];

export default function SummaryAside() {
  const total = summaryItems.reduce((acc, i) => acc + i.value, 0);

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
          <span className="text-sm font-semibold text-slate-700">Total</span>
          <span className="text-sm font-bold text-primary">{total}</span>
        </div>
      </div>
    </aside>
  );
}
