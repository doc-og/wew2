import { format } from 'date-fns'
import type { ActivityLogEntry } from '../types'

const ACTION_LABELS: Record<string, string> = {
  app_open: 'opened',
  message_sent: 'sent a message',
  call_made: 'made a call',
  call_received: 'received a call',
  photo_taken: 'took a photo',
  photo_shared: 'shared a photo',
  web_link_opened: 'opened a link',
  app_blocked: 'tried to open blocked app',
  device_admin_revoked: 'tried to disable wew',
  lock_activated: 'phone locked by schedule',
  lock_deactivated: 'phone unlocked',
  credit_exhausted: 'credits ran out',
}

interface ActivityFeedProps {
  entries: ActivityLogEntry[]
}

export default function ActivityFeed({ entries }: ActivityFeedProps) {
  if (entries.length === 0) {
    return <p style={{ color: '#6B7280', fontSize: 14 }}>no activity yet</p>
  }

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 0 }}>
      {entries.map((entry) => {
        const label = ACTION_LABELS[entry.action_type] ?? entry.action_type.replace(/_/g, ' ')
        const appLabel = entry.app_name || entry.app_package || ''
        const isTamper = entry.action_type === 'device_admin_revoked' || entry.action_type === 'app_blocked'

        return (
          <div
            key={entry.id}
            style={{
              display: 'flex',
              justifyContent: 'space-between',
              alignItems: 'center',
              padding: '10px 0',
              borderBottom: '1px solid #F0EEF9',
            }}
          >
            <div>
              <span style={{ fontSize: 14, color: isTamper ? '#C0392B' : '#1A1A2E' }}>
                {label}
                {appLabel ? ` — ${appLabel}` : ''}
              </span>
              <div style={{ fontSize: 12, color: '#6B7280', marginTop: 2 }}>
                {format(new Date(entry.created_at), 'h:mm a')}
              </div>
            </div>
            {entry.credits_deducted > 0 && (
              <span style={{ fontSize: 13, fontFamily: 'monospace', color: '#3D2FA8', fontWeight: 600 }}>
                −{entry.credits_deducted}
              </span>
            )}
          </div>
        )
      })}
    </div>
  )
}
