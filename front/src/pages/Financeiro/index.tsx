const mockConferenceRows = [
  {
    id: 'R-001',
    medico: 'Dra. Ana Martins',
    parcela: 'PARC-2026-0001',
    status: 'Conferido',
    envio: 'OK',
  },
  {
    id: 'R-002',
    medico: 'Dr. Carlos Nunes',
    parcela: 'PARC-2026-0002',
    status: 'Pendente',
    envio: 'Aguardando',
  },
];

export default function Financeiro() {
  return (
    <main className="w-full max-w-full px-4 sm:px-6 lg:px-8 py-8">
      <section className="mb-6">
        <h1 className="text-2xl font-bold text-slate-800">Financeiro • Conferência de Recibos</h1>
        <p className="text-sm text-slate-500 mt-1">
          Integração com ContaAzul e trilha de conferência protegida por 2FA.
        </p>
      </section>

      <section className="bg-white rounded-xl border border-slate-200 shadow-sm overflow-hidden">
        <div className="px-5 py-4 bg-primary/10 border-b border-primary/20">
          <h2 className="text-sm font-semibold text-primary">Tabela de Conferência</h2>
        </div>

        <div className="overflow-x-auto">
          <table className="min-w-full text-sm">
            <thead className="bg-brand-primary text-white">
              <tr>
                <th className="text-left px-4 py-3 font-semibold">Recibo</th>
                <th className="text-left px-4 py-3 font-semibold">Médico</th>
                <th className="text-left px-4 py-3 font-semibold">Parcela</th>
                <th className="text-left px-4 py-3 font-semibold">Status</th>
                <th className="text-left px-4 py-3 font-semibold">Envio Brevo</th>
              </tr>
            </thead>
            <tbody>
              {mockConferenceRows.map((row) => (
                <tr key={row.id} className="border-b border-slate-100 last:border-b-0 hover:bg-primary/5 transition-colors">
                  <td className="px-4 py-3 text-slate-700 font-medium">{row.id}</td>
                  <td className="px-4 py-3 text-slate-700">{row.medico}</td>
                  <td className="px-4 py-3 text-slate-700">{row.parcela}</td>
                  <td className="px-4 py-3 text-slate-700">{row.status}</td>
                  <td className="px-4 py-3 text-slate-700">{row.envio}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </section>
    </main>
  );
}