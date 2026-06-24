import { BarChart, Bar, XAxis, YAxis, CartesianGrid, Tooltip, Legend, ResponsiveContainer, Cell } from 'recharts';
import type { MetricDTO } from '@/types/models';
import { VIBRANT_CHART_COLORS } from '@/lib/chartPalette';

interface ChartsBarProps {
  data: MetricDTO[];
  title: string;
}

export default function ChartsBar({ data, title }: ChartsBarProps) {
  if (!data || data.length === 0) {
    return (
      <div className="bg-white rounded-xl border border-slate-200 shadow-sm p-6 flex items-center justify-center h-80">
        <p className="text-slate-500">Sem dados disponíveis</p>
      </div>
    );
  }

  return (
    <div className="bg-white rounded-xl border border-slate-200 shadow-sm p-6">
      <h3 className="text-lg font-semibold text-slate-800 mb-4">{title}</h3>
      <ResponsiveContainer width="100%" height={300}>
        <BarChart data={data} margin={{ top: 20, right: 30, left: 0, bottom: 5 }}>
          <CartesianGrid strokeDasharray="3 3" stroke="#e2e8f0" />
          <XAxis dataKey="name" stroke="#64748b" />
          <YAxis stroke="#64748b" />
          <Tooltip formatter={(value) => `${value}`} />
          <Legend />
          <Bar dataKey="value" name="Chamados" radius={[8, 8, 0, 0]}>
            {data.map((entry, index) => (
              <Cell key={`monthly-bar-${entry.name}`} fill={VIBRANT_CHART_COLORS[index % VIBRANT_CHART_COLORS.length]} />
            ))}
          </Bar>
        </BarChart>
      </ResponsiveContainer>
    </div>
  );
}

