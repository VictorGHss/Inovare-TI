import { BarChart, Bar, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer } from 'recharts';
import type { MetricDTO } from '@/types/models';

interface SlaBreachesBarProps {
  data: MetricDTO[];
  title: string;
}

const ALERT_BAR_COLOR = '#f97316';

export default function SlaBreachesBar({ data, title }: SlaBreachesBarProps) {
  if (!data.length) {
    return (
      <div className="bg-white rounded-xl border border-slate-200 shadow-sm p-6 flex items-center justify-center h-80">
        <p className="text-slate-500">Sem dados disponíveis</p>
      </div>
    );
  }

  return (
    <div className="bg-white rounded-xl border border-slate-200 shadow-sm p-6">
      <h3 className="text-lg font-semibold text-slate-800 mb-4">{title}</h3>
      <ResponsiveContainer width="100%" height={330}>
        <BarChart data={data} layout="vertical" margin={{ top: 16, right: 24, left: 8, bottom: 8 }}>
          <CartesianGrid strokeDasharray="3 3" stroke="#e2e8f0" />
          <XAxis type="number" stroke="#64748b" />
          <YAxis type="category" dataKey="name" width={160} stroke="#64748b" />
          <Tooltip />
          <Bar dataKey="value" name="Estouro de SLA" fill={ALERT_BAR_COLOR} radius={[0, 8, 8, 0]} />
        </BarChart>
      </ResponsiveContainer>
    </div>
  );
}