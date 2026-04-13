# wew — safe by design

A parental control launcher for Android (ages 8–12). wew replaces the default home screen on a child’s phone with a curated app grid and a credit-based usage system. Parents monitor and control everything via a companion Android app and web dashboard.

---

## Project structure

```
/launcher        — Android (Kotlin) child launcher app
/parent-app      — Android (Kotlin) parent companion app
/web-dashboard   — React + TypeScript web dashboard
/supabase        — SQL migrations and edge functions
```

---

## Architecture overview

```
┌─────────────────────────────────────────────────────────────┐
│                        Supabase                             │
│  Postgres DB · Auth · Realtime · Edge Functions             │
└───────────────┬──────────────────┬──────────────────────────┘
                │  REST + Realtime │  REST + Realtime
     ┌──────────▼──────┐   ┌───────▼──────────┐
     │  Child Launcher  │   │   Parent App     │
     │  Android/Kotlin  │   │  Android/Kotlin  │
     │  (home screen)   │   │  (companion app) │
     └─────────────────┘   └──────────────────┘
                                    │
                            ┌───────▼──────────┐
                            │  Web Dashboard   │
                            │  React + TS      │
                            └──────────────────┘
                                    │ FCM
                            ┌───────▼──────────┐
                            │  Firebase (FCM)  │
                            │  Push to parent  │
                            └──────────────────┘
```

## Tech stack

| Layer | Technology |
|-------|-----------|
| Child launcher | Android (Kotlin), Jetpack Compose, min SDK 29 |
| Parent app | Android (Kotlin), Jetpack Compose, min SDK 29 |
| Web dashboard | React 18, TypeScript, Vite |
| Backend / database | Supabase (Postgres, Auth, Realtime, Edge Functions) |
| Push notifications | Firebase Cloud Messaging (FCM) |
| Maps | OpenStreetMap via Leaflet (web), Google Maps (Android) |
| Charts | Recharts (web), Vico (Android) |

---

## Prerequisites

- **Android Studio** Hedgehog or newer
- **Node.js** 18+ and npm
- **Supabase CLI** — `npm install -g supabase`
- **Firebase project** with Cloud Messaging enabled
- A Supabase account (free tier works for dev)

---

## 1. Supabase setup

### 1.1 Create a project

1. Go to [supabase.com](https://supabase.com) and create a new project.
2. Note your **Project URL** and **anon/public key** from Settings → API.

### 1.2 Run migrations

Run the SQL migrations in order from `supabase/migrations/`:

```bash
# Option A: Supabase CLI
supabase login
supabase link --project-ref YOUR_PROJECT_REF
supabase db push

# Option B: Paste into Supabase SQL Editor — run files 001 → 005 in order
```

### 1.3 Enable realtime

In Supabase Dashboard → Database → Replication, ensure `supabase_realtime` publication is active. Migration `004_realtime.sql` adds the required tables automatically.

### 1.4 Enable pg_cron (daily credit reset)

In Supabase Dashboard → Database → Extensions, enable **pg_cron**. Then re-run `005_cron.sql` to register the cron job.

### 1.5 Enable auth

Dashboard → Authentication → Providers → ensure **Email** is enabled.

### 1.6 Key values

From Dashboard → Settings → API:
- `SUPABASE_URL` — e.g. `https://abcdefgh.supabase.co`
- `SUPABASE_ANON_KEY` — the `anon` public key
- `SUPABASE_SERVICE_ROLE_KEY` — used only in edge functions (keep secret)

---

## 2. Firebase (FCM) setup

1. [Firebase Console](https://console.firebase.google.com) → create project.
2. Add two Android apps:
   - Child launcher: `com.wew.launcher`
   - Parent app: `com.wew.parent`
3. Download `google-services.json` for each:
   - → `launcher/app/google-services.json`
   - → `parent-app/app/google-services.json`
4. Project Settings → Cloud Messaging → copy **Server Key**.
5. Add to Supabase secrets:
   ```bash
   supabase secrets set FCM_SERVER_KEY=your_server_key
   supabase secrets set SUPABASE_SERVICE_ROLE_KEY=your_service_role_key
   ```
6. Deploy edge functions:
   ```bash
   supabase functions deploy send-fcm-notification
   supabase functions deploy daily-credit-reset
   ```

---

## 3. Child launcher app setup

**Create** `launcher/local.properties` (gitignored):

```properties
SUPABASE_URL=https://your-project.supabase.co
SUPABASE_ANON_KEY=your_anon_key_here
```

Place `google-services.json` in `launcher/app/`, then build:

```bash
cd launcher
./gradlew assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
```

**First-run Device Admin activation on child device:**

1. Open wew → tap **"activate and continue"**.
2. System dialog appears → tap **Activate**.
3. On first Home press, select wew → **Always**.

In Supabase, insert a row into `devices` with `parent_user_id` = your user UUID. Store the resulting device `id` in the child phone’s SharedPreferences under key `device_id`.

---

## 4. Parent app setup

**Create** `parent-app/local.properties`:

```properties
SUPABASE_URL=https://your-project.supabase.co
SUPABASE_ANON_KEY=your_anon_key_here
```

Place `google-services.json` in `parent-app/app/`, then:

```bash
cd parent-app
./gradlew assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
```

Sign in with the email/password from your Supabase Auth account.

---

## 5. Web dashboard setup

```bash
cd web-dashboard
npm install
cp .env.example .env
# Edit .env — set VITE_SUPABASE_URL and VITE_SUPABASE_ANON_KEY
npm run dev
# → http://localhost:3000
```

**Deploy to Vercel:**
```bash
npm install -g vercel && vercel
# Set env vars in Vercel dashboard
```

**Deploy to Netlify:** build command `npm run build`, publish dir `dist`.

---

## 6. Signing APKs for production

```bash
keytool -genkeypair -v -keystore wew-release.keystore -alias wew \
  -keyalg RSA -keysize 2048 -validity 10000
```

Add to `local.properties`:
```properties
KEYSTORE_PATH=/path/to/wew-release.keystore
KEYSTORE_PASSWORD=your_keystore_password
KEY_ALIAS=wew
KEY_PASSWORD=your_key_password
```

Add signing config to `app/build.gradle` `android {}` block, then:

```bash
cd launcher && ./gradlew assembleRelease
cd ../parent-app && ./gradlew assembleRelease
```

---

## 7. Environment variables reference

| Variable | Where | Purpose |
|----------|-------|---------|
| `SUPABASE_URL` | `launcher/local.properties`, `parent-app/local.properties` | Supabase project URL |
| `SUPABASE_ANON_KEY` | `launcher/local.properties`, `parent-app/local.properties` | Supabase public anon key |
| `VITE_SUPABASE_URL` | `web-dashboard/.env` | Supabase URL for web dashboard |
| `VITE_SUPABASE_ANON_KEY` | `web-dashboard/.env` | Supabase anon key for web dashboard |
| `FCM_SERVER_KEY` | Supabase secrets | Firebase server key for push notifications |
| `SUPABASE_SERVICE_ROLE_KEY` | Supabase secrets | Service role key for edge functions |

---

## 8. Architecture notes

### Credit formula

Base costs: app open=1, message=1, call=2, photo=2, share photo=5, web link=2.

After 50% of daily budget consumed, cost scales per 10%:
- 60% used → +1 · 70% used → +2 · 80% used → +3 …

### Scheduling

`LauncherForegroundService` checks enabled schedules every minute. If current time/day matches a bedtime or school window, it sets `is_locked=true` in Supabase and activates emergency-only mode locally.

### Realtime sync

Supabase realtime keeps dashboards live: `devices` → credits/lock state; `activity_log` → live feed; `location_log` → live map.

### Security model

Device Admin prevents uninstall/launcher replacement. Revocation triggers FCM alert to parent + emergency-only mode. All tables use RLS; the child device uses SECURITY DEFINER RPCs to prevent direct table writes.

---

## Original structure
- `agent/permissions.yml` — human-readable matrix of what our agent is allowed to do.
- `.github/workflows/agent-ci.yml` — lint/test/build CI (safe defaults).
- `.github/ISSUE_TEMPLATE/*.md` — templates for work items.
- `docs/roadmap.md` — running product/tech roadmap.

## Working Agreement
- All changes via PR to `main`.
- CI must pass. “High-risk” ops require explicit approval.
- Secrets live in 1Password; never commit credentials.

---

## Coding standards

### General
- **Small deliverables.** Every PR ships one user-visible behavior. If you can't describe what a user will see differently, break it down further.
- **No speculative code.** Don't add abstractions or fields “for later.” Build exactly what is needed now.
- **Plain English comments.** Explain *why*, not *what*. If the code is obvious, skip the comment.
- **No jargon in user-facing strings.** Write the way a child's parent would speak.

### Android / Kotlin
- **Jetpack Compose only** for all new UI. No XML layouts.
- **MVVM.** ViewModels own state. Composables receive data and emit events — no logic in composables.
- **StateFlow for UI state.** One `UiState` data class per ViewModel. Never expose `MutableStateFlow` directly.
- **supabase-kt 2.1.4 + Kotlin 1.9.22.** Do not upgrade without upgrading Kotlin to 2.0+ simultaneously.
- **No `rpc()` calls in Kotlin.** All DB ops via direct table inserts/updates/selects.
- **`onConflict` parameter form only:** `upsert(data, onConflict = “col1,col2”)`.
- **Batch upserts use `buildJsonObject`.** Typed data classes in batch upserts produce PostgREST “All object keys must match” errors. Build JSON manually for all bulk operations.
- **AppCompat theme in XML, Material3 in Compose.** Both apps use `Theme.AppCompat.DayNight.NoActionBar` as the XML theme; `MaterialTheme {}` wraps Compose content.
- **Errors are logged, never swallowed.** All `runCatching` failures call `Log.e()` with the full exception.

### Database
- New features = new migration file (`00N_description.sql`). Never edit existing migrations.
- All tables require `created_at timestamptz NOT NULL DEFAULT now()`.
- Add an index for every foreign key used as a filter.
- Every new table needs RLS policies (parent sees only their own device's data).

### Secrets
- Never commit credentials. Android: `local.properties`. Scripts: `.env`.
- Supabase credentials: `/Users/drseanogrady/wew/wew 2/.env` (gitignored).

---

## Credit / token system

Every meaningful action the child takes costs credits from their daily budget.

### Base costs

| Action | Credits |
|--------|---------|
| Open an app | 1 |
| Send a message | 1 |
| Make a call | 2 |
| Receive a call | 2 |
| Take a photo | 2 |
| Share a photo | 5 |
| Open a web link | 2 |
| Access an unauthorized app (temp grant) | 2 |
| Check In (send location to parent) | 0 — always free |
| Add or request a contact | 0 — always free |
| SOS call | 0 — always free |

### Scaling rule
Once the child has used more than 50% of their daily budget, each action costs progressively more:

- 50–60% used → base cost  
- 60–70% used → base + 1  
- 70–80% used → base + 2  
- 80–90% used → base + 3  
- 90%+ used → base + 4  

The last 20% of credits is effectively double cost — designed to slow down usage naturally rather than cutting the child off abruptly.

### Daily reset
Credits reset to `daily_credit_budget` at `credits_reset_time` (default 7:00 AM) via a Supabase cron job (`pg_cron` extension required). Parents can adjust the budget and add/remove credits manually from the dashboard at any time.

### Emergency access
SOS, Check In, and contact requests never cost credits and are available even when the credit balance is zero.

---

## Development workflow

### 1. Branch from main
```bash
git checkout main && git pull
git checkout -b feature/<short-description>
# or: fix/<short-description>
```

### 2. Implement
Make the smallest change that ships the feature. Build locally and run on a device or emulator before opening a PR.

### 3. Test locally
- Rebase from `main` before testing: `git rebase main`
- Build the relevant APK(s)
- Verify every acceptance criterion manually
- A notification is sent when local testing is ready, with a summary of what to test and how

### 4. Open a pull request
```bash
gh pr create --title “feat: <description>” --body “...”
```

Every PR must include:
- Plain-English summary of what changed and why
- List of files changed with a one-line description of each
- Step-by-step QA instructions (what to tap, what to expect)

### 5. Code review (CodeRabbit)
CodeRabbit reviews every PR automatically. Address all blocking comments before merging. To re-trigger after fixes, reply `@coderabbitai review` in the PR thread.

### 6. QA on device
A notification is sent with the PR link and QA steps. Sideload the APK, run each QA step, then approve.

### 7. Merge
Squash merge to `main`. Deploy edge functions and web dashboard as needed.

---

## Ticket format (GitHub Issues)

Every PR has a matching GitHub Issue:

```
## Summary
One sentence: what this does and why.

## Files changed
- `path/to/file.kt` — what changed and why

## QA steps
1. [Setup] Install the debug APK
2. [Action] Tap X
3. [Expected] Y appears / Z happens
...

## Notes
Known limitations, follow-up tickets, gotchas.
```

---

## Notification workflow

| Event | Who gets notified | What's in the notification |
|-------|------------------|---------------------------|
| Local build ready to test | Sean | Brief summary + how to test each feature |
| PR opened and ready for QA | Sean | PR link + QA steps |
| Child checks in (location share) | Parent (FCM) | “Child checked in” + map link |
| Child requests contact authorization | Parent (FCM) | Contact name + approve/deny link |
| Credits running low | Parent (FCM) | Current balance + threshold |
| Unauthorized app access attempt | Parent (FCM) | App name + attempt count |

