import { useState, useEffect } from 'react'
import { supabase } from '../lib/supabase'
import { useAuth } from '../hooks/useAuth'
import { useDevice } from '../hooks/useDevice'
import type { AppInfo } from '../types'

export default function AppsPage() {
  const { session } = useAuth()
  const { device } = useDevice(session?.user.id)
  const [apps, setApps] = useState<AppInfo[]>([])
  const [search, setSearch] = useState('')
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    if (!device) return
    supabase.from('apps').select('*').eq('device_id', device.id)
      .then(({ data }) => { setApps(data ?? []); setLoading(false) })
  }, [device])

  const toggleWhitelist = async (app: AppInfo) => {
    const newVal = !app.is_whitelisted
    setApps((prev) => prev.map((a) => a.id === app.id ? { ...a, is_whitelisted: newVal } : a))
    await supabase.from('apps').update({ is_whitelisted: newVal }).eq('id', app.id)
  }

  const filtered = search
    ? apps.filter((a) => a.app_name.toLowerCase().includes(search.toLowerCase()) || a.package_name.includes(search))
    : apps

  const whitelistedCount = apps.filter((a) => a.is_whitelisted).length

  return (
    <div>
      <h1 style={{ fontSize: 24, fontWeight: 500, color: '#1A1A2E', margin: '0 0 4px' }}>app whitelist</h1>
      <p style={{ fontSize: 14, color: '#6B7280', margin: '0 0 20px' }}>
        {whitelistedCount} of {apps.length} apps approved
      </p>

      <input
        type="search"
        placeholder="search apps…"
        value={search}
        onChange={(e) => setSearch(e.target.value)}
        style={{ width: '100%', maxWidth: 400, padding: '9px 14px', border: '1px solid #D1D5DB', borderRadius: 10, fontSize: 14, marginBottom: 16, boxSizing: 'border-box' }}
        aria-label="search apps"
      />

      <div style={{ background: 'white', borderRadius: 12, overflow: 'hidden' }}>
        {loading ? (
          <div style={{ padding: 24, textAlign: 'center', color: '#6B7280' }}>loading…</div>
        ) : (
          <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: 14 }}>
            <thead>
              <tr style={{ background: '#F8F7FF' }}>
                {['app name', 'package', 'credit cost', 'approved'].map((h) => (
                  <th key={h} style={{ padding: '12px 16px', textAlign: 'left', color: '#3D3D5C', fontWeight: 500, fontSize: 13 }}>{h}</th>
                ))}
              </tr>
            </thead>
            <tbody>
              {filtered.map((app) => (
                <tr key={app.id} style={{ borderTop: '1px solid #F0EEF9' }}>
                  <td style={{ padding: '10px 16px', color: '#1A1A2E', fontWeight: 500 }}>{app.app_name}</td>
                  <td style={{ padding: '10px 16px', color: '#6B7280', fontSize: 12, fontFamily: 'monospace' }}>{app.package_name}</td>
                  <td style={{ padding: '10px 16px', color: '#3D2FA8', fontFamily: 'monospace' }}>{app.credit_cost}</td>
                  <td style={{ padding: '10px 16px' }}>
                    <label style={{ position: 'relative', display: 'inline-block', width: 44, height: 24 }}>
                      <input
                        type="checkbox"
                        checked={app.is_whitelisted}
                        onChange={() => toggleWhitelist(app)}
                        style={{ opacity: 0, width: 0, height: 0 }}
                        aria-label={`${app.app_name}: ${app.is_whitelisted ? 'approved' : 'blocked'}`}
                      />
                      <span style={{
                        position: 'absolute', cursor: 'pointer', inset: 0,
                        background: app.is_whitelisted ? '#2E7D52' : '#D1D5DB',
                        borderRadius: 24, transition: 'background 0.2s',
                      }}>
                        <span style={{
                          position: 'absolute', content: '', height: 18, width: 18,
                          left: app.is_whitelisted ? 23 : 3, bottom: 3,
                          background: 'white', borderRadius: '50%', transition: 'left 0.2s',
                        }} />
                      </span>
                    </label>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>
    </div>
  )
}
