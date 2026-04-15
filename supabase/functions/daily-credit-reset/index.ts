import { serve } from 'https://deno.land/std@0.168.0/http/server.ts'
import { createClient } from 'https://esm.sh/@supabase/supabase-js@2'

const SUPABASE_URL = Deno.env.get('SUPABASE_URL')!
const SUPABASE_SERVICE_ROLE_KEY = Deno.env.get('SUPABASE_SERVICE_ROLE_KEY')!

serve(async (_req: Request) => {
  const supabase = createClient(SUPABASE_URL, SUPABASE_SERVICE_ROLE_KEY)

  // Token-based daily reset (see migration 007 — replaces legacy credit reset)
  const { error: tokenErr } = await supabase.rpc('reset_daily_tokens')
  if (tokenErr) {
    console.error('Token reset failed:', tokenErr.message)
    return new Response(JSON.stringify({ error: tokenErr.message }), { status: 500 })
  }

  const { data: summaryCount, error: sumErr } = await supabase.rpc('generate_daily_usage_summaries')
  if (sumErr) {
    console.error('Daily usage summaries:', sumErr.message)
  } else {
    console.log(`Inserted ${summaryCount} daily usage summaries`)
  }

  return new Response(
    JSON.stringify({ success: true, reset: 'tokens', summariesInserted: summaryCount ?? 0 }),
    { status: 200 }
  )
})
