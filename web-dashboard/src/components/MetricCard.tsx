import type { LucideIcon } from 'lucide-react'

interface MetricCardProps {
  label: string
  value: string | number
  subtitle?: string
  icon?: LucideIcon
  valueColor?: string
}

export default function MetricCard({ label, value, subtitle, icon: Icon, valueColor = '#3D2FA8' }: MetricCardProps) {
  return (
    <div
      style={{
        background: 'white',
        borderRadius: 12,
        padding: '16px 20px',
        minWidth: 140,
        flex: 1,
      }}
      aria-label={`${label}: ${value}`}
    >
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
        <span style={{ fontSize: 14, color: '#2E7D52', fontWeight: 500 }}>{label}</span>
        {Icon && <Icon size={16} color="#6C5CE7" />}
      </div>
      <div style={{ fontSize: 26, fontWeight: 500, color: valueColor, marginTop: 8, fontFamily: 'monospace' }}>
        {value}
      </div>
      {subtitle && (
        <div style={{ fontSize: 12, color: '#6B7280', marginTop: 4 }}>{subtitle}</div>
      )}
    </div>
  )
}
