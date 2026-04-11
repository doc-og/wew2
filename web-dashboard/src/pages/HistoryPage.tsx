import { useState, useEffect } from 'react'
import { subDays, format, startOfDay, endOfDay } from 'date-fns'
import { supabase } from '../lib/supabase'
import { useAuth } from '../hooks/useAuth'
import { useDevice } from '../hooks/useDevice'
import UsageChart from '../components/UsageChart'
import type { ActivityLogEntry } from '../types'

export default function HistoryPage() {
  const { session } = useAuth()
  const { device } = useDevice(session?.user.id)
  const [days, setDays] = useState(7)
  const [entries, setEntries] = useState<ActivityLogEntry[]>([])
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    if (!device) return
    setLoading(true)
    const since = subDays(new Date(), days)
    supabase
      .from('activity_log')
      .select('*')
      .eq('device_id', device.id)
      .gte('created_at', since.toISOString())
      .order('created_at', { ascending: true })
      .then(({ data }) => {
        setEntries(data ?? [])
        setLoading(false)
      })
  }, [device, days])

  // Build daily credit usage chart data
  const dailyCreditData = Array.from({ length: days }, (_, i) => {
    const day = subDays(new Date(), days - 1 - i)
    const dayStart = startOfDay(day).toISOString()
    const dayEnd = endOfDay(day).toISOString()
    const credits = entries
      .filter((e) => e.created_at >= dayStart && e.created_at <= dayEnd)
      .reduce((sum, e) => sum + e.credits_deducted, 0)
    return { label: format(day, 'EEE'), credits }
  })

  // Per-app usage
  const appUsage: Record<string, { credits: number; count: number }> = {}
  entries.forEach((e) => {
    const name = e.app_name || e.app_package || 'unknown'
    if (!appUsage[name]) appUsage[name] = { credits: 0, count: 0 }
    appUsage[name].credits += e.credits_deducted
    appUsage[name].count += 1
  })
  const appChartData = Object.entries(appUsage)
    .sort((a, b) => b[1].credits - a[1].credits)
    .slice(0, 8)
    .map(([label, { credits }]) => ({ label, credits }))

  const totalCredits = entries.reduce((sum, e) => sum + e.credits_deducted, 0)

  return (
    <div>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 20 }}>
        <h1 style={{ fontSize: 24, fontWeight: 500, color: '#1A1A2E', margin: 0 }}>usage history</h1>
        <select
          value={days}
          onChange={(e) => setDays(parseInt(e.target.value))}
          style={{ padding: '8px 12px', border: '1px solid #D1D5DB', borderRadius: 8, fontSize: 14 }}
          aria-label="time range"
        >
          <option value={7}>last 7 days</option>
          <option value={14}>last 14 days</option>
          <option value={30}>last 30 days</option>
        </select>
      </div>

      <div style={{ background: 'white', borderRadius: 12, padding: '8px 16px', marginBottom: 16, display: 'inline-block' }}>
        <span style={{ fontSize: 14, color: '#3D3D5C' }}>total credits used: </span>
        <span style={{ fontSize: 16, fontWeight: 600, fontFamily: 'monospace', color: '#3D2FA8' }}>{totalCredits}</span>
      </div>

      {loading ? (
        <div style={{ textAlign: 'center', padding: 40, color: '#6B7280' }}>loading…</div>
      ) : (
        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 20 }}>
          <div style={{ background: 'white', borderRadius: 12, padding: 20 }}>
            <UsageChart data={dailyCreditData} title="daily credit usage" />
          </div>
          <div style={{ background: 'white', borderRadius: 12, padding: 20 }}>
            <UsageChart data={appChartData} title="credits by app" />
          </div>
        </div>
      )}
    </div>
  )
}
