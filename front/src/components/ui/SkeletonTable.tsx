// Skeleton animado que simula a tabela de chamados carregando
export default function SkeletonTable() {
  const rows = Array.from({ length: 5 });

  return (
    <div className="animate-pulse w-full">
      {/* Cabeçalho falso */}
      <div className="grid grid-cols-4 gap-4 px-4 py-3 bg-slate-100 rounded-t-lg mb-1">
        {['w-16', 'w-32', 'w-24', 'w-20'].map((w, i) => (
          <div key={i} className={`h-3 ${w} bg-slate-300 rounded`} />
        ))}
      </div>

      {/* Linhas falsas */}
      {rows.map((_, i) => (
        <div
          key={i}
          className="grid grid-cols-4 gap-4 px-4 py-4 border-b border-slate-100"
        >
          <div className="h-3 w-10 bg-slate-200 rounded" />
          <div className="h-3 w-40 bg-slate-200 rounded" />
          <div className="h-3 w-24 bg-slate-200 rounded" />
          <div className="h-5 w-20 bg-slate-200 rounded-full" />
        </div>
      ))}
    </div>
  );
}
