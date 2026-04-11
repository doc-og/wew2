# Wew — CLAUDE.md

## Project overview
Wew is a parental control Android launcher for children aged 8–12, with a companion parent app and web dashboard. The system uses a credit-based usage model governed in real time via Supabase.

## Directory layout
```
wew 2/
├── launcher/          Android child launcher app   (com.wew.launcher)
├── parent-app/        Android parent companion app (com.wew.parent)
├── web-dashboard/     React + TypeScript dashboard
├── supabase/          SQL migrations + Edge Functions
│   ├── migrations/    001–005 (all applied)
│   └── functions/     send-fcm-notification, daily-credit-reset (deployed)
└── CLAUDE.md
```

## Build instructions

### Prerequisites
- Java: `/Applications/Android Studio.app/Contents/jbr/Contents/Home` (JDK 21)
- Android SDK: `~/Library/Android/sdk` (API 34 + 35 available)
- Supabase CLI: `/opt/homebrew/bin/supabase` (v2.75)
- Firebase CLI: `/opt/homebrew/bin/firebase` (v15.11)
- Node: v24 / npm v11

### Android builds
Always set JAVA_HOME and ANDROID_HOME:
```bash
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" \
ANDROID_HOME="/Users/drseanogrady/Library/Android/sdk" \
bash -c 'cd launcher && ./gradlew assembleDebug'
```
APK outputs:
- `launcher/app/build/outputs/apk/debug/app-debug.apk`
- `parent-app/app/build/outputs/apk/debug/app-debug.apk`

### Web dashboard
```bash
cd web-dashboard && npm run dev    # dev server
cd web-dashboard && npm run build  # production build → dist/
```

### Supabase
```bash
# From wew 2/ root:
SUPABASE_ACCESS_TOKEN="..." supabase db push         # run new migrations
SUPABASE_ACCESS_TOKEN="..." supabase functions deploy send-fcm-notification
SUPABASE_ACCESS_TOKEN="..." supabase functions deploy daily-credit-reset
```
Project ref: `sqrmupfpdchiecfthqtj`

## Key technical decisions

### Supabase Kotlin SDK version
Both Android apps use **supabase-kt 2.1.4** with Kotlin 1.9.22. Do NOT upgrade to 2.5.x without also upgrading Kotlin to 2.0+ and switching to the Kotlin Compose plugin.

Correct Maven coordinates:
```
io.github.jan-tennert.supabase:postgrest-kt:2.1.4
io.github.jan-tennert.supabase:gotrue-kt:2.1.4
io.github.jan-tennert.supabase:realtime-kt:2.1.4
io.ktor:ktor-client-android:2.3.7
```

### No `rpc()` calls in Kotlin
`supabase.postgrest.rpc()` does not resolve cleanly in supabase-kt 2.1.4 + Kotlin 1.9. All credit operations are done via direct table inserts/updates instead of the `deduct_credits` / `add_credits` DB functions. The DB functions remain in place for future use.

### `onConflict` syntax
Use the parameter form, not the builder block:
```kotlin
supabase.postgrest["table"].upsert(data, onConflict = "col1,col2")
```

### Android themes
Both apps use `Theme.AppCompat.DayNight.NoActionBar` (not Material3 XML theme). Compose Material3 theming is applied via `MaterialTheme {}` in Kotlin. Both apps require `androidx.appcompat:appcompat:1.6.1` as a dependency.

### `google-services.json`
Placeholder files are in place — push notifications will not work until you:
1. Create a Firebase project at console.firebase.google.com
2. Add Android apps: `com.wew.launcher` and `com.wew.parent`
3. Download real `google-services.json` files and replace `launcher/app/google-services.json` and `parent-app/app/google-services.json`
4. Set `FCM_SERVER_KEY` as a Supabase Edge Function secret

### pg_cron (migration 005)
The cron job to reset daily credits is NOT yet active. To enable:
1. Supabase Dashboard → Database → Extensions → enable `pg_cron`
2. Re-run: `supabase db push` (005_cron.sql will register the job)

## Supabase credentials
Stored in `/Users/drseanogrady/wew/wew 2/.claude/settings.local.json` (gitignored).
Do not commit credentials. Local properties files are also gitignored.

## Next steps
1. Replace placeholder `google-services.json` with real Firebase config
2. Enable pg_cron extension in Supabase dashboard
3. Activate Device Admin on child device, sideload launcher APK
4. Deploy web dashboard (Vercel recommended — add `VITE_SUPABASE_URL` and `VITE_SUPABASE_ANON_KEY` as environment variables)
5. Test real-time credit deduction flow end-to-end
