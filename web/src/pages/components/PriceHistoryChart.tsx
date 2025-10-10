import { Area, AreaChart, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts';

interface PricePoint {
  checkedAt: string;
  price: number | null;
}

interface Props {
  data: PricePoint[];
}

export default function PriceHistoryChart({ data }: Props) {
  const chartData = (data || []).filter((point) => typeof point.price === 'number');

  if (!chartData.length) {
    return <p className="text-sm text-slate-400">No price history yet.</p>;
  }

  return (
    <div className="h-64">
      <ResponsiveContainer width="100%" height="100%">
        <AreaChart data={chartData}>
          <defs>
            <linearGradient id="colorPrice" x1="0" y1="0" x2="0" y2="1">
              <stop offset="5%" stopColor="#38bdf8" stopOpacity={0.8} />
              <stop offset="95%" stopColor="#38bdf8" stopOpacity={0} />
            </linearGradient>
          </defs>
          <XAxis dataKey="checkedAt" stroke="#94a3b8" tickLine={false} axisLine={false} hide />
          <YAxis stroke="#94a3b8" tickFormatter={(value) => `₩${Math.round(value / 10000)}만`} width={80} />
          <Tooltip
            contentStyle={{ backgroundColor: '#0f172a', borderColor: '#1e293b', color: '#e2e8f0' }}
            formatter={(value: number) => `₩${value.toLocaleString()}`}
          />
          <Area
            type="monotone"
            dataKey="price"
            stroke="#38bdf8"
            fillOpacity={1}
            fill="url(#colorPrice)"
          />
        </AreaChart>
      </ResponsiveContainer>
    </div>
  );
}
