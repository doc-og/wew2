export interface Device {
  id: string
  parent_user_id: string
  device_name: string
  fcm_token: string | null
  is_locked: boolean
  current_credits: number
  daily_credit_budget: number
  credits_reset_time: string
  last_seen_at: string | null
  created_at: string
}

export interface ActivityLogEntry {
  id: string
  device_id: string
  action_type: string
  app_package: string | null
  app_name: string | null
  credits_deducted: number
  metadata: Record<string, string>
  created_at: string
}

export interface CreditChange {
  id: string
  device_id: string
  change_amount: number
  balance_after: number
  reason: string
  action_type: string | null
  parent_note: string | null
  created_at: string
}

export interface LocationPoint {
  id: string
  device_id: string
  latitude: number
  longitude: number
  accuracy_meters: number | null
  created_at: string
}

export interface AppInfo {
  id: string
  device_id: string
  package_name: string
  app_name: string
  is_whitelisted: boolean
  is_system_app: boolean
  credit_cost: number
}

export interface Schedule {
  id: string | null
  device_id: string
  schedule_type: 'bedtime' | 'school'
  start_time: string
  end_time: string
  days_of_week: number[]
  is_enabled: boolean
}

export interface NotificationConfig {
  id: string | null
  parent_user_id: string
  low_credit_threshold_pct: number
  daily_summary_enabled: boolean
  daily_summary_time: string
  notify_blocked_apps: boolean
  notify_tamper_attempts: boolean
  notify_location_updates: boolean
}

export interface UsageStats {
  date: string
  app_name: string
  total_credits: number
  action_count: number
}
