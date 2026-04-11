import { useState, useEffect } from 'react'
import { supabase } from '../lib/supabase'
import type { Device } from '../types'

export function useDevice(userId: string | undefined) {
  const [device, setDevice] = useState<Device | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    if (!userId) return

    const fetchDevice = async () => {
      const { data, error } = await supabase
        .from('devices')
        .select('*')
        .eq('parent_user_id', userId)
        .limit(1)
        .single()

      if (error) {
        setError(error.message)
      } else {
        setDevice(data)
      }
      setLoading(false)
    }

    fetchDevice()

    // Realtime subscription for live credit balance and lock status
    const channel = supabase
      .channel(`device-${userId}`)
      .on(
        'postgres_changes',
        {
          event: 'UPDATE',
          schema: 'public',
          table: 'devices',
          filter: `parent_user_id=eq.${userId}`,
        },
        (payload) => {
          setDevice(payload.new as Device)
        }
      )
      .subscribe()

    return () => {
      supabase.removeChannel(channel)
    }
  }, [userId])

  return { device, loading, error, setDevice }
}
