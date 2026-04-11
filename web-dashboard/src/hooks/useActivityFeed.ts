import { useState, useEffect } from 'react'
import { supabase } from '../lib/supabase'
import type { ActivityLogEntry } from '../types'

export function useActivityFeed(deviceId: string | undefined, limit = 20) {
  const [feed, setFeed] = useState<ActivityLogEntry[]>([])
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    if (!deviceId) return

    const fetchFeed = async () => {
      const { data } = await supabase
        .from('activity_log')
        .select('*')
        .eq('device_id', deviceId)
        .order('created_at', { ascending: false })
        .limit(limit)

      if (data) setFeed(data)
      setLoading(false)
    }

    fetchFeed()

    // Realtime subscription — prepend new entries
    const channel = supabase
      .channel(`activity-${deviceId}`)
      .on(
        'postgres_changes',
        {
          event: 'INSERT',
          schema: 'public',
          table: 'activity_log',
          filter: `device_id=eq.${deviceId}`,
        },
        (payload) => {
          setFeed((prev) => [payload.new as ActivityLogEntry, ...prev].slice(0, limit))
        }
      )
      .subscribe()

    return () => {
      supabase.removeChannel(channel)
    }
  }, [deviceId, limit])

  return { feed, loading }
}
