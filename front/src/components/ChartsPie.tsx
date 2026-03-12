import { PieChart, Pie, Cell, ResponsiveContainer, Legend, Tooltip } from 'recharts';
import type { MetricDTO } from '../services/api';
import { VIBRANT_CHART_COLORS } from '../lib/chartPalette';

interface ChartsPieProps {
  data: MetricDTO[];
  title: string;
}

export default function ChartsPie({ data, title }: ChartsPieProps) {
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
      <ResponsiveContainer width="100%" height={320}>
        <PieChart>
          <Pie
            data={data}
            cx="50%"
            cy="45%"
            innerRadius={60}
            outerRadius={100}
            fill="#8884d8"
            dataKey="value"
            paddingAngle={2}
          >
            {data.map((_, index) => (
              <Cell key={`cell-${index}`} fill={VIBRANT_CHART_COLORS[index % VIBRANT_CHART_COLORS.length]} />
            ))}
          </Pie>
          <Tooltip formatter={(value) => `${value}`} contentStyle={{ borderRadius: '8px', border: 'none', boxShadow: '0 2px 8px rgba(0,0,0,0.1)' }} />
          <Legend verticalAlign="bottom" height={36} />
        </PieChart>
      </ResponsiveContainer>
    </div>
  );
}
