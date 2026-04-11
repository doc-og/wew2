import { useState, useEffect } from 'react'
import { format } from 'date-fns'
import { Download } from 'lucide-react'
import { supabase } from '../lib/supabase'
import { useAuth } from '../hooks/useAuth'
import { useDevice } from '../hooks/useDevice'
import type { ActivityLogEntry } from '../types'

const PAGE_SIZE = 20

export default function ActivityPage() {
  const { session } = useAuth()
  const { device } = useDevice(session?.user.id)
  const [entries, setEntries] = useState<ActivityLogEntry[]>([])
  const [loading, setLoading] = useState(true)
  const [page, setPage] = useState(0)
  const [hasMore, setHasMore] = useState(true)
  const [actionFilter, setActionFilter] = useState('')
  const [dateFilter, setDateFilter] = useState('')

  useEffect(() => {
    if (!device) return
    setLoading(true)
    setPage(0)

    let query = supabase
      .from('activity_log')
      .select('*')
      .eq('device_id', device.id)
      .order('created_at', { ascending: false })
      .range(0, PAGE_SIZE - 1)

    if (actionFilter) query = query.eq('action_type', actionFilter)
    if (dateFilter) {
      query = query
        .gte('created_at', `${dateFilter}T00:00:00`)
        .lte('created_at', `${dateFilter}T23:59:59`)
    }

    query.then(({ data }) => {
      setEntries(data ?? [])
      setHasMore((data?.length ?? 0) === PAGE_SIZE)
      setLoading(false)
    })
  }, [device, actionFilter, dateFilter])

  const loadMore = async () => {
    if (!device) return
    const next = page + 1
    const { data } = await supabase
      .from('activity_log')
      .select('*')
      .eq('device_id', device.id)
      .order('created_at', { ascending: false })
      .range(next * PAGE_SIZE, (next + 1) * PAGE_SIZE - 1)

    if (data) {
      setEntries((prev) => [...prev, ...data])
      setHasMore(data.length === PAGE_SIZE)
      setPage(next)
    }
  }

  const exportCsv = () => {
    const header = 'id,action_type,app_name,app_package,credits_deducted,created_at\n'
    const rows = entries.map((e) =>
      [e.id, e.action_type, e.app_name ?? '', e.app_package ?? '', e.credits_deducted, e.created_at].join(',')
    ).join('\n')
    const blob = new Blob([header + rows], { type: 'text/csv' })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `wew-activity-${dateFilter || 'all'}.csv`
    a.click()
    URL.revokeObjectURL(url)
  }

  return (
    <div>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 20 }}>
        <h1 style={{ fontSize: 24, fontWeight: 500, color: '#1A1A2E', margin: 0 }}>activity log</h1>
        <button
          onClick={exportCsv}
          style={{ display: 'flex', alignItems: 'center', gap: 6, background: '#3D2FA8', color: 'white', border: 'none', borderRadius: 8, padding: '8px 16px', fontSize: 14, cursor: 'pointer' }}
          aria-label="export activity log as CSV"
        >
          <Download size={14} /> export CSV
        </button>
      </div>

      {/* Filters */}
      <div style={{ display: 'flex', gap: 12, marginBottom: 16, flexWrap: 'wrap' }}>
        <input
          type="date"
          value={dateFilter}
          onChange={(e) => setDateFilter(e.target.value)}
          style={{ padding: '8px 12px', border: '1px solid #D1D5DB', borderRadius: 8, fontSize: 14 }}
          aria-label="filter by date"
        />
        <select
          value={actionFilter}
          onChange={(e) => setActionFilter(e.target.value)}
          style={{ padding: '8px 12px', border: '1px solid #D1D5DB', borderRadius: 8, fontSize: 14 }}
          aria-label="filter by action type"
        >
          <option value="">all actions</option>
          <option value="app_open">app opens</option>
          <option value="message_sent">messages sent</option>
          <option value="call_made">calls made</option>
          <option value="photo_taken">photos taken</option>
          <option value="photo_shared">photos shared</option>
          <option value="app_blocked">blocked attempts</option>
          <option value="device_admin_revoked">tamper alerts</option>
        </select>
      </div>

      {/* Table */}
      <div style={{ background: 'white', borderRadius: 12, overflow: 'hidden' }}>
        <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: 14 }}>
          <thead>
            <tr style={{ background: '#F8F7FF' }}>
              {['time', 'action', 'app', 'credits'].map((h) => (
                <th key={h} style={{ padding: '12px 16px', textAlign: 'left', color: '#3D3D5C', fontWeight: 500, fontSize: 13 }}>{h}</th>
              ))}
            </tr>
          </thead>
          <tbody>
            {loading ? (
              <tr><td colSpan={4} style={{ padding: 24, textAlign: 'center', color: '#6B7280' }}>loading…</td></tr>
            ) : entries.length === 0 ? (
              <tr><td colSpan={4} style={{ padding: 24, textAlign: 'center', color: '#6B7280' }}>no entries found</td></tr>
            ) : entries.map((entry) => (
              <tr key={entry.id} style={{ borderTop: '1px solid #F0EEF9' }}>
                <td style={{ padding: '10px 16px', color: '#6B7280', whiteSpace: 'nowrap' }}>
                  {format(new Date(entry.created_at), 'MMM d, h:mm a')}
                </td>
                <td style={{ padding: '10px 16px', color: entry.action_type.includes('blocked') || entry.action_type.includes('revoked') ? '#C0392B' : '#1A1A2E' }}>
                  {entry.action_type.replace(/_/g, ' ')}
                </td>
                <td style={{ padding: '10px 16px', color: '#3D3D5C' }}>
                  {entry.app_name || entry.app_package || '—'}
                </td>
                <td style={{ padding: '10px 16px', fontFamily: 'monospace', color: '#3D2FA8' }}>
                  {entry.credits_deducted > 0 ? `−${entry.credits_deducted}` : '—'}
                </td>
              </tr>
            ))}
          </tbody>
        </table>

        {hasMore && (
          <div style={{ padding: 16, textAlign: 'center' }}>
            <button
              onClick={loadMore}
              style={{ background: 'none', border: '1px solid #3D2FA8', color: '#3D2FA8', borderRadius: 8, padding: '8px 20px', fontSize: 14, cursor: 'pointer' }}
            >
              load more
            </button>
          </div>
        )}
      </div>
    </div>
  )
}
