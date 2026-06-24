import { BarChart, Bar, XAxis, YAxis, CartesianGrid, Tooltip, Legend, ResponsiveContainer } from 'recharts';
import type { SectorPriorityMetricDTO } from '@/types/models';
import { VIBRANT_CHART_COLORS } from '@/lib/chartPalette';

interface ChartsBarStackedProps {
  data: SectorPriorityMetricDTO[];
  title: string;
}

type PriorityLabel = 'Baixa' | 'Média' | 'Alta';

const PRIORITY_ORDER: PriorityLabel[] = ['Baixa', 'Média', 'Alta'];

const PRIORITY_COLORS: Record<PriorityLabel, string> = {
  Baixa: VIBRANT_CHART_COLORS[2],
  Média: VIBRANT_CHART_COLORS[1],
  Alta: VIBRANT_CHART_COLORS[0],
};

interface ChartRow {
  sector: string;
  Baixa: number;
  Média: number;
  Alta: number;
}

function buildChartRows(data: SectorPriorityMetricDTO[]): ChartRow[] {
  const rowsBySector = new Map<string, ChartRow>();

  data.forEach((entry) => {
    const priority = normalizePriority(entry.priority);
    const existingRow = rowsBySector.get(entry.sector) ?? {
      sector: entry.sector,
      Baixa: 0,
      Média: 0,
      Alta: 0,
    };

    existingRow[priority] += entry.value;
    rowsBySector.set(entry.sector, existingRow);
  });

  return Array.from(rowsBySector.values());
}

function normalizePriority(priority: string): PriorityLabel {
  const normalized = priority.trim().toLowerCase();

  if (normalized === 'baixa' || normalized === 'low') {
    return 'Baixa';
  }

  if (normalized === 'média' || normalized === 'media' || normalized === 'normal') {
    return 'Média';
  }

  return 'Alta';
}

export default function ChartsBarStacked({ data, title }: ChartsBarStackedProps) {
  const chartData = buildChartRows(data);

  if (!chartData.length) {
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
        <BarChart data={chartData} margin={{ top: 16, right: 24, left: 0, bottom: 24 }}>
          <CartesianGrid strokeDasharray="3 3" stroke="#e2e8f0" />
          <XAxis dataKey="sector" stroke="#64748b" angle={-12} textAnchor="end" height={54} interval={0} />
          <YAxis stroke="#64748b" />
          <Tooltip />
          <Legend />
          {PRIORITY_ORDER.map((priority) => (
            <Bar key={priority} dataKey={priority} stackId="priority" name={priority} fill={PRIORITY_COLORS[priority]} radius={[6, 6, 0, 0]} />
          ))}
        </BarChart>
      </ResponsiveContainer>
    </div>
  );
}