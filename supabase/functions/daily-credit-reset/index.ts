import { serve } from 'https://deno.land/std@0.168.0/http/server.ts'
import { createClient } from 'https://esm.sh/@supabase/supabase-js@2'

const SUPABASE_URL = Deno.env.get('SUPABASE_URL')!
const SUPABASE_SERVICE_ROLE_KEY = Deno.env.get('SUPABASE_SERVICE_ROLE_KEY')!

serve(async (_req: Request) => {
  const supabase = createClient(SUPABASE_URL, SUPABASE_SERVICE_ROLE_KEY)

  // Call the SQL function that resets credits for devices whose reset time matches now
  const { data: resetCount, error } = await supabase.rpc('reset_daily_credits')

  if (error) {
    console.error('Credit reset failed:', error.message)
    return new Response(JSON.stringify({ error: error.message }), { status: 500 })
  }

  console.log(`Reset credits for ${resetCount} devices`)

  // Get all devices that were just reset and send FCM notifications to parents
  const { data: resetDevices } = await supabase
    .from('credit_ledger')
    .select('device_id')
    .eq('reason', 'daily_reset')
    .gte('created_at', new Date(Date.now() - 2 * 60 * 1000).toISOString()) // last 2 minutes

  if (resetDevices && resetDevices.length > 0) {
    // Fire-and-forget FCM notifications for each reset device
    const notifyPromises = resetDevices.map(({ device_id }: { device_id: string }) =>
      fetch(`${SUPABASE_URL}/functions/v1/send-fcm-notification`, {
        method: 'POST',
        headers: {
          Authorization: `Bearer ${SUPABASE_SERVICE_ROLE_KEY}`,
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({
          deviceId: device_id,
          type: 'daily_summary',
          data: {},
        }),
      }).catch((err: Error) => console.error('FCM notify failed for', device_id, err.message))
    )
    await Promise.allSettled(notifyPromises)
  }

  return new Response(JSON.stringify({ success: true, devicesReset: resetCount }), { status: 200 })
})
