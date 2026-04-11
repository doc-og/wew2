import { serve } from 'https://deno.land/std@0.168.0/http/server.ts'
import { createClient } from 'https://esm.sh/@supabase/supabase-js@2'

interface NotificationRequest {
  deviceId: string
  type: string
  data?: Record<string, string>
}

const SUPABASE_URL = Deno.env.get('SUPABASE_URL')!
const SUPABASE_SERVICE_ROLE_KEY = Deno.env.get('SUPABASE_SERVICE_ROLE_KEY')!
const FCM_SERVER_KEY = Deno.env.get('FCM_SERVER_KEY')!

serve(async (req: Request) => {
  if (req.method !== 'POST') {
    return new Response('Method not allowed', { status: 405 })
  }

  const { deviceId, type, data = {} }: NotificationRequest = await req.json()

  const supabase = createClient(SUPABASE_URL, SUPABASE_SERVICE_ROLE_KEY)

  // Get the device's parent FCM token
  const { data: device, error: deviceError } = await supabase
    .from('devices')
    .select('parent_user_id, fcm_token')
    .eq('id', deviceId)
    .single()

  if (deviceError || !device) {
    return new Response(JSON.stringify({ error: 'Device not found' }), { status: 404 })
  }

  // The parent app's FCM token is needed; for simplicity we store it on the device record
  // In production, parent FCM tokens are stored separately
  const parentFcmToken = device.fcm_token
  if (!parentFcmToken) {
    return new Response(JSON.stringify({ error: 'No FCM token for parent' }), { status: 400 })
  }

  const notification = buildNotification(type, data)

  const fcmPayload = {
    to: parentFcmToken,
    notification,
    data: { type, deviceId, ...data },
    priority: type === 'device_admin_revoked' ? 'high' : 'normal',
  }

  const fcmResponse = await fetch('https://fcm.googleapis.com/fcm/send', {
    method: 'POST',
    headers: {
      Authorization: `key=${FCM_SERVER_KEY}`,
      'Content-Type': 'application/json',
    },
    body: JSON.stringify(fcmPayload),
  })

  if (!fcmResponse.ok) {
    const err = await fcmResponse.text()
    return new Response(JSON.stringify({ error: err }), { status: 500 })
  }

  return new Response(JSON.stringify({ success: true }), { status: 200 })
})

function buildNotification(type: string, data: Record<string, string>): { title: string; body: string } {
  switch (type) {
    case 'low_credits':
      return {
        title: 'credits running low',
        body: `your child has ${data.remaining ?? '?'} credits remaining`,
      }
    case 'credits_exhausted':
      return {
        title: 'credits used up',
        body: 'your child has run out of credits for today',
      }
    case 'blocked_app':
      return {
        title: 'blocked access',
        body: `your child tried to open ${data.app_name ?? 'a blocked app'}`,
      }
    case 'device_admin_revoked':
      return {
        title: '⚠️ security alert',
        body: 'wew device admin was disabled — phone is now in emergency-only mode',
      }
    case 'daily_summary':
      return {
        title: 'daily summary',
        body: `your child used ${data.credits_used ?? '?'} credits today`,
      }
    case 'location_update':
      return {
        title: 'location updated',
        body: "your child's location has been refreshed",
      }
    default:
      return { title: 'wew alert', body: type }
  }
}
