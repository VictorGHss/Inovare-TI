import { BarChart, Bar, XAxis, YAxis, CartesianGrid, Tooltip, Legend, ResponsiveContainer } from 'recharts';
import type { Ticket } from '../services/api';

interface ChartsBarProps {
  tickets: Ticket[];
  title: string;
}

interface MonthlyData {
  month: string;
  tickets: number;
}

export default function ChartsBar({ tickets, title }: ChartsBarProps) {
  // Process tickets to get monthly volume
  const monthlyData: { [key: string]: number } = {};

  tickets.forEach((ticket) => {
    if (ticket.createdAt) {
      const date = new Date(ticket.createdAt);
      const monthKey = `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}`;
      monthlyData[monthKey] = (monthlyData[monthKey] || 0) + 1;
    }
  });

  // Convert to array and sort by date
  const data: MonthlyData[] = Object.entries(monthlyData)
    .sort(([a], [b]) => a.localeCompare(b))
    .slice(-12) // Last 12 months
    .map(([month, count]) => ({
      month: new Date(`${month}-01`).toLocaleDateString('pt-BR', { month: 'short', year: '2-digit' }),
      tickets: count,
    }));

  if (!data || data.length === 0) {
    return (
      <div className="bg-white rounded-lg shadow p-6 flex items-center justify-center h-80">
        <p className="text-slate-500">Sem dados disponíveis</p>
      </div>
    );
  }

  return (
    <div className="bg-white rounded-lg shadow p-6">
      <h3 className="text-lg font-semibold text-slate-800 mb-4">{title}</h3>
      <ResponsiveContainer width="100%" height={300}>
        <BarChart data={data} margin={{ top: 20, right: 30, left: 0, bottom: 5 }}>
          <CartesianGrid strokeDasharray="3 3" />
          <XAxis dataKey="month" />
          <YAxis />
          <Tooltip formatter={(value) => `${value}`} />
          <Legend />
          <Bar dataKey="tickets" fill="#3b82f6" name="Chamados" />
        </BarChart>
      </ResponsiveContainer>
    </div>
  );
}
