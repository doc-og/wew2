import { useState, useEffect } from 'react'
import { supabase } from '../lib/supabase'
import { useAuth } from '../hooks/useAuth'
import { useDevice } from '../hooks/useDevice'
import type { Schedule, NotificationConfig } from '../types'

const DAYS = ['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat']

export default function SettingsPage() {
  const { session } = useAuth()
  const { device } = useDevice(session?.user.id)
  const [schedules, setSchedules] = useState<Record<string, Schedule>>({})
  const [config, setConfig] = useState<NotificationConfig | null>(null)
  const [saved, setSaved] = useState(false)

  useEffect(() => {
    if (!device || !session) return
    supabase.from('schedules').select('*').eq('device_id', device.id)
      .then(({ data }) => {
        const map: Record<string, Schedule> = {}
        data?.forEach((s) => { map[s.schedule_type] = s })
        if (!map.bedtime) map.bedtime = { id: null, device_id: device.id, schedule_type: 'bedtime', start_time: '21:00', end_time: '07:00', days_of_week: [0, 1, 2, 3, 4, 5, 6], is_enabled: false }
        if (!map.school) map.school = { id: null, device_id: device.id, schedule_type: 'school', start_time: '08:00', end_time: '15:00', days_of_week: [1, 2, 3, 4, 5], is_enabled: false }
        setSchedules(map)
      })
    supabase.from('notifications_config').select('*').eq('parent_user_id', session.user.id).single()
      .then(({ data }) => {
        setConfig(data ?? {
          id: null, parent_user_id: session.user.id, low_credit_threshold_pct: 20,
          daily_summary_enabled: true, daily_summary_time: '20:00:00',
          notify_blocked_apps: true, notify_tamper_attempts: true, notify_location_updates: false,
        })
      })
  }, [device, session])

  const saveSchedule = async (type: string) => {
    const s = schedules[type]
    if (!s) return
    await supabase.from('schedules').upsert(s, { onConflict: 'device_id,schedule_type' })
    setSaved(true)
    setTimeout(() => setSaved(false), 2000)
  }

  const saveConfig = async () => {
    if (!config) return
    await supabase.from('notifications_config').upsert(config, { onConflict: 'parent_user_id' })
    setSaved(true)
    setTimeout(() => setSaved(false), 2000)
  }

  const updateSchedule = (type: string, patch: Partial<Schedule>) => {
    setSchedules((prev) => ({ ...prev, [type]: { ...prev[type], ...patch } }))
  }

  const toggleDay = (type: string, day: number) => {
    const s = schedules[type]
    if (!s) return
    const days = s.days_of_week.includes(day)
      ? s.days_of_week.filter((d) => d !== day)
      : [...s.days_of_week, day].sort()
    updateSchedule(type, { days_of_week: days })
  }

  return (
    <div>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 20 }}>
        <h1 style={{ fontSize: 24, fontWeight: 500, color: '#1A1A2E', margin: 0 }}>settings</h1>
        {saved && <span style={{ color: '#2E7D52', fontSize: 14 }}>saved ✓</span>}
      </div>

      {['bedtime', 'school'].map((type) => {
        const s = schedules[type]
        if (!s) return null
        return (
          <div key={type} style={{ background: 'white', borderRadius: 12, padding: 20, marginBottom: 16 }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 14 }}>
              <h2 style={{ fontSize: 16, fontWeight: 500, color: '#1A1A2E', margin: 0 }}>
                {type === 'bedtime' ? 'bedtime lock' : 'school lock'}
              </h2>
              <label style={{ display: 'flex', alignItems: 'center', gap: 8, cursor: 'pointer' }}>
                <input
                  type="checkbox"
                  checked={s.is_enabled}
                  onChange={(e) => updateSchedule(type, { is_enabled: e.target.checked })}
                  aria-label={`enable ${type} lock`}
                />
                <span style={{ fontSize: 14, color: '#3D3D5C' }}>enabled</span>
              </label>
            </div>
            <div style={{ display: 'flex', gap: 16, flexWrap: 'wrap', marginBottom: 12 }}>
              <label style={{ fontSize: 14, color: '#3D3D5C' }}>
                start
                <input
                  type="time"
                  value={s.start_time.slice(0, 5)}
                  onChange={(e) => updateSchedule(type, { start_time: e.target.value })}
                  style={{ marginLeft: 8, padding: '6px 10px', border: '1px solid #D1D5DB', borderRadius: 8, fontSize: 14 }}
                />
              </label>
              <label style={{ fontSize: 14, color: '#3D3D5C' }}>
                end
                <input
                  type="time"
                  value={s.end_time.slice(0, 5)}
                  onChange={(e) => updateSchedule(type, { end_time: e.target.value })}
                  style={{ marginLeft: 8, padding: '6px 10px', border: '1px solid #D1D5DB', borderRadius: 8, fontSize: 14 }}
                />
              </label>
            </div>
            <div style={{ display: 'flex', gap: 6, marginBottom: 14 }}>
              {DAYS.map((day, i) => (
                <button
                  key={day}
                  onClick={() => toggleDay(type, i)}
                  aria-pressed={s.days_of_week.includes(i)}
                  style={{
                    width: 36, height: 36, borderRadius: '50%', border: 'none', cursor: 'pointer', fontSize: 12,
                    background: s.days_of_week.includes(i) ? '#3D2FA8' : '#F0EEF9',
                    color: s.days_of_week.includes(i) ? 'white' : '#3D3D5C',
                  }}
                >
                  {day[0]}
                </button>
              ))}
            </div>
            <button
              onClick={() => saveSchedule(type)}
              style={{ background: '#3D2FA8', color: 'white', border: 'none', borderRadius: 8, padding: '8px 20px', fontSize: 14, fontWeight: 500, cursor: 'pointer' }}
            >
              save schedule
            </button>
          </div>
        )
      })}

      {/* Notification preferences */}
      {config && (
        <div style={{ background: 'white', borderRadius: 12, padding: 20 }}>
          <h2 style={{ fontSize: 16, fontWeight: 500, color: '#1A1A2E', margin: '0 0 14px' }}>notification preferences</h2>
          <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
            <label style={{ fontSize: 14, color: '#3D3D5C' }}>
              low credit alert at
              <input
                type="number"
                min={5} max={50}
                value={config.low_credit_threshold_pct}
                onChange={(e) => setConfig({ ...config, low_credit_threshold_pct: parseInt(e.target.value) || 20 })}
                style={{ width: 60, marginLeft: 8, padding: '4px 8px', border: '1px solid #D1D5DB', borderRadius: 6, fontSize: 14 }}
                aria-label="low credit threshold percentage"
              />
              %
            </label>
            {([
              ['daily_summary_enabled', 'daily summary at 8 PM'],
              ['notify_blocked_apps', 'blocked app attempts'],
              ['notify_tamper_attempts', 'tamper / security alerts'],
              ['notify_location_updates', 'location updates'],
            ] as [keyof NotificationConfig, string][]).map(([key, label]) => (
              <label key={key} style={{ display: 'flex', alignItems: 'center', gap: 8, fontSize: 14, color: '#1A1A2E', cursor: 'pointer' }}>
                <input
                  type="checkbox"
                  checked={config[key] as boolean}
                  onChange={(e) => setConfig({ ...config, [key]: e.target.checked })}
                  aria-label={label}
                />
                {label}
              </label>
            ))}
            <button
              onClick={saveConfig}
              style={{ background: '#3D2FA8', color: 'white', border: 'none', borderRadius: 8, padding: '8px 20px', fontSize: 14, fontWeight: 500, cursor: 'pointer', alignSelf: 'flex-start' }}
            >
              save preferences
            </button>
          </div>
        </div>
      )}
    </div>
  )
}
