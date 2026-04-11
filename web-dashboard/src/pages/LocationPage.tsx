import { useState, useEffect } from 'react'
import { format } from 'date-fns'
import { supabase } from '../lib/supabase'
import { useAuth } from '../hooks/useAuth'
import { useDevice } from '../hooks/useDevice'
import LocationMap from '../components/LocationMap'
import type { LocationPoint } from '../types'

export default function LocationPage() {
  const { session } = useAuth()
  const { device } = useDevice(session?.user.id)
  const [locations, setLocations] = useState<LocationPoint[]>([])
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    if (!device) return
    supabase
      .from('location_log')
      .select('*')
      .eq('device_id', device.id)
      .order('created_at', { ascending: false })
      .limit(48)
      .then(({ data }) => {
        setLocations(data ?? [])
        setLoading(false)
      })
  }, [device])

  const latest = locations[0]

  return (
    <div>
      <h1 style={{ fontSize: 24, fontWeight: 500, color: '#1A1A2E', margin: '0 0 4px' }}>location</h1>
      {latest && (
        <p style={{ fontSize: 14, color: '#6B7280', margin: '0 0 20px' }}>
          last update: {format(new Date(latest.created_at), 'h:mm a, MMM d')}
          {latest.accuracy_meters ? ` · ±${Math.round(latest.accuracy_meters)}m` : ''}
        </p>
      )}

      {loading ? (
        <div style={{ height: 500, display: 'flex', alignItems: 'center', justifyContent: 'center', color: '#6B7280' }}>
          loading map…
        </div>
      ) : (
        <LocationMap locations={locations} height={500} />
      )}
    </div>
  )
}
