import { useState } from 'react'
import { Lock, LockKeyhole, Plus, Minus, Wifi } from 'lucide-react'
import { format } from 'date-fns'
import { useAuth } from '../hooks/useAuth'
import { useDevice } from '../hooks/useDevice'
import { useActivityFeed } from '../hooks/useActivityFeed'
import MetricCard from '../components/MetricCard'
import ActivityFeed from '../components/ActivityFeed'
import { supabase } from '../lib/supabase'

export default function DashboardPage() {
  const { session } = useAuth()
  const { device, setDevice } = useDevice(session?.user.id)
  const { feed } = useActivityFeed(device?.id)

  const [creditAmount, setCreditAmount] = useState('')
  const [creditNote, setCreditNote] = useState('')
  const [creditError, setCreditError] = useState<string | null>(null)

  const adjustCredits = async (direction: 'add' | 'remove') => {
    if (!device) return
    const amount = parseInt(creditAmount)
    if (isNaN(amount) || amount <= 0) { setCreditError('enter a valid amount'); return }
    setCreditError(null)
    const { error } = await supabase.rpc('add_credits', {
      p_device_id: device.id,
      p_amount: direction === 'add' ? amount : -amount,
      p_reason: direction === 'add' ? 'parent_add' : 'parent_remove',
      p_parent_note: creditNote || null,
    })
    if (error) setCreditError(error.message)
    else { setCreditAmount(''); setCreditNote('') }
  }

  const toggleLock = async () => {
    if (!device) return
    const newLocked = !device.is_locked
    await supabase.from('devices').update({ is_locked: newLocked }).eq('id', device.id)
    setDevice({ ...device, is_locked: newLocked })
  }

  const creditPct = device ? (device.current_credits / device.daily_credit_budget) * 100 : 100
  const creditColor = creditPct <= 0 ? '#C0392B' : creditPct < 20 ? '#F59E0B' : '#3D2FA8'

  return (
    <div>
      <h1 style={{ fontSize: 24, fontWeight: 500, color: '#1A1A2E', margin: '0 0 4px' }}>dashboard</h1>
      {device && (
        <p style={{ fontSize: 14, color: '#6B7280', margin: '0 0 20px' }}>
          {device.device_name} · last seen {device.last_seen_at ? format(new Date(device.last_seen_at), 'h:mm a') : 'unknown'}
        </p>
      )}

      {/* Metric cards */}
      <div style={{ display: 'flex', gap: 12, marginBottom: 20, flexWrap: 'wrap' }}>
        <MetricCard
          label="credits remaining"
          value={device?.current_credits ?? '—'}
          subtitle={`of ${device?.daily_credit_budget ?? 100} daily`}
          icon={Wifi}
          valueColor={creditColor}
        />
        <MetricCard
          label="credits used today"
          value={device ? device.daily_credit_budget - device.current_credits : '—'}
          valueColor="#6C5CE7"
        />
        <MetricCard
          label="phone status"
          value={device?.is_locked ? 'locked' : 'active'}
          valueColor={device?.is_locked ? '#C0392B' : '#2E7D52'}
        />
      </div>

      <div style={{ display: 'grid', gridTemplateColumns: '1fr 340px', gap: 20, alignItems: 'start' }}>
        {/* Left column */}
        <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
          {/* Lock control */}
          <div style={{ background: 'white', borderRadius: 12, padding: 20 }}>
            <h2 style={{ fontSize: 16, fontWeight: 500, color: '#1A1A2E', margin: '0 0 12px' }}>remote control</h2>
            <button
              onClick={toggleLock}
              style={{
                display: 'flex',
                alignItems: 'center',
                gap: 8,
                background: device?.is_locked ? '#2E7D52' : '#C0392B',
                color: 'white',
                border: 'none',
                borderRadius: 10,
                padding: '10px 20px',
                fontSize: 14,
                fontWeight: 500,
                cursor: 'pointer',
              }}
              aria-label={device?.is_locked ? 'unlock device' : 'lock device'}
            >
              {device?.is_locked ? <LockKeyhole size={16} /> : <Lock size={16} />}
              {device?.is_locked ? 'unlock phone' : 'lock phone now'}
            </button>
          </div>

          {/* Credit manager */}
          <div style={{ background: 'white', borderRadius: 12, padding: 20 }}>
            <h2 style={{ fontSize: 16, fontWeight: 500, color: '#1A1A2E', margin: '0 0 12px' }}>adjust credits</h2>
            <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
              <input
                type="number"
                placeholder="amount"
                value={creditAmount}
                onChange={(e) => setCreditAmount(e.target.value)}
                min={1}
                style={{ padding: '9px 12px', border: '1px solid #D1D5DB', borderRadius: 8, fontSize: 14, width: '100%', boxSizing: 'border-box' }}
              />
              <input
                type="text"
                placeholder="note (optional)"
                value={creditNote}
                onChange={(e) => setCreditNote(e.target.value)}
                style={{ padding: '9px 12px', border: '1px solid #D1D5DB', borderRadius: 8, fontSize: 14, width: '100%', boxSizing: 'border-box' }}
              />
              {creditError && <p style={{ color: '#C0392B', fontSize: 13, margin: 0 }}>{creditError}</p>}
              <div style={{ display: 'flex', gap: 10 }}>
                <button
                  onClick={() => adjustCredits('add')}
                  style={{ flex: 1, background: '#3D2FA8', color: 'white', border: 'none', borderRadius: 8, padding: '9px 0', fontSize: 14, fontWeight: 500, cursor: 'pointer', display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 6 }}
                  aria-label="add credits"
                >
                  <Plus size={14} /> add
                </button>
                <button
                  onClick={() => adjustCredits('remove')}
                  style={{ flex: 1, background: 'white', color: '#3D2FA8', border: '1px solid #3D2FA8', borderRadius: 8, padding: '9px 0', fontSize: 14, fontWeight: 500, cursor: 'pointer', display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 6 }}
                  aria-label="remove credits"
                >
                  <Minus size={14} /> remove
                </button>
              </div>
            </div>
          </div>
        </div>

        {/* Activity feed */}
        <div style={{ background: 'white', borderRadius: 12, padding: 20 }}>
          <h2 style={{ fontSize: 16, fontWeight: 500, color: '#1A1A2E', margin: '0 0 12px' }}>live activity</h2>
          <ActivityFeed entries={feed} />
        </div>
      </div>
    </div>
  )
}
