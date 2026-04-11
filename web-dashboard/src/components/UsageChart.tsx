import { BarChart, Bar, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer } from 'recharts'

interface UsageDataPoint {
  label: string
  credits: number
}

interface UsageChartProps {
  data: UsageDataPoint[]
  title?: string
}

export default function UsageChart({ data, title = 'credit usage' }: UsageChartProps) {
  return (
    <div>
      {title && <h3 style={{ fontSize: 16, fontWeight: 500, color: '#1A1A2E', margin: '0 0 12px' }}>{title}</h3>}
      <ResponsiveContainer width="100%" height={220}>
        <BarChart data={data} margin={{ top: 4, right: 8, left: -16, bottom: 0 }}>
          <defs>
            <linearGradient id="creditGradient" x1="0" y1="0" x2="0" y2="1">
              <stop offset="0%" stopColor="#3D2FA8" />
              <stop offset="100%" stopColor="#6C5CE7" />
            </linearGradient>
          </defs>
          <CartesianGrid strokeDasharray="3 3" stroke="#F0EEF9" />
          <XAxis dataKey="label" tick={{ fontSize: 12, fill: '#6B7280' }} />
          <YAxis tick={{ fontSize: 12, fill: '#6B7280' }} />
          <Tooltip
            contentStyle={{ background: 'white', border: '1px solid #E5E4FF', borderRadius: 8, fontSize: 13 }}
            labelStyle={{ color: '#1A1A2E', fontWeight: 500 }}
          />
          <Bar dataKey="credits" fill="url(#creditGradient)" radius={[4, 4, 0, 0]} />
        </BarChart>
      </ResponsiveContainer>
    </div>
  )
}
