# Wew — CLAUDE.md (agent handbook)

## Why this project exists

Wew is a **parental-control Android launcher** for children about **8–12**. It replaces the default home experience with a **curated, safer surface** (messages-first navigation, approved apps, check-in, map, etc.). **Parents** manage the child device through a **companion Android app** and a **React web dashboard**. **Supabase** (Postgres, Auth, Realtime, Edge Functions) is the backend; **FCM** notifies parents.

---

## Product principles

1. **Safety is never gated by token balance.** SOS, check-in, and monitoring events are always free. The token system must never create a perverse incentive to avoid safety features.
2. **Passive consumption costs more than active creation.** Watching a video drains the budget faster than sending a message. The child should feel the difference.
3. **Plain English in UI.** No jargon. Lowercase-friendly, parent- and child-appropriate language.
4. **Smallest shippable change.** One user-visible improvement per PR; no speculative abstractions.
5. **Leftover tokens are fine.** Daily reset is unconditional — under-spending is expected and healthy, not punished.
6. **Parents tune, the engine defaults.** Defaults are calibrated for 8–12-year-olds; overrides exist for edge cases.

See [TOKEN_SYSTEM.md](TOKEN_SYSTEM.md) for token economics.

---

## Architecture

- **Supabase** is the hub: Postgres, RLS, Realtime, Auth, Edge Functions.
- **Child launcher** and **parent app** are Kotlin + Jetpack Compose; **web dashboard** is React + Vite (still partly credit-oriented until migrated).
- **Edge Functions:** `send-fcm-notification`, `daily-credit-reset` (token reset + `generate_daily_usage_summaries`).
- **Tokens** are the active usage currency (`devices.current_tokens`, `token_ledger`). Legacy **credit** columns/RPCs may still exist for older dashboard code paths.
- **Child devices** identify themselves with a **device UUID** stored in `SharedPreferences`; RLS uses a **capability-style** model (anon key + broad device policies) — treat device IDs as secrets.
- **Parent phone** for SOS: `devices.parent_phone` + `devices.parent_display_name`, set from **parent app Settings**. The launcher caches to `SharedPreferences` for offline SOS.
- **Daily usage summaries:** `generate_daily_usage_summaries()` (migration 009) runs at each parent's `notifications_config.daily_summary_time` in the device's `devices.timezone`. Inserts into `messages` with `sender_type = 'system'`, `message_type = 'daily_summary'`, `body` text. Child Chat merges these rows for the WeW Parent thread only.

---

## Directory layout

```
wew 2/
├── launcher/          Child app (com.wew.launcher)
├── parent-app/        Parent app (com.wew.parent)
├── web-dashboard/     React + TypeScript
├── supabase/
│   ├── migrations/    001–009 (apply in order; never edit applied files)
│   └── functions/     send-fcm-notification, daily-credit-reset
├── TOKEN_SYSTEM.md    Source of truth for default token costs
├── README.md          Setup, standards, workflow
└── CLAUDE.md          This file
```

---

## Build and config

### Prerequisites

- **JDK 17+** / Android Studio embedded JBR (path: `/Applications/Android Studio.app/Contents/jbr/Contents/Home`)
- **Android SDK** at `~/Library/Android/sdk` with platform 34+
- **Supabase CLI**, **Node** for web dashboard
- **Firebase** project for FCM (`google-services.json` per app)

### Android

Always set `JAVA_HOME` and `ANDROID_HOME`:

```bash
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" \
ANDROID_HOME="/Users/drseanogrady/Library/Android/sdk" \
bash -c 'cd launcher && ./gradlew assembleDebug'
```

APK outputs:
- `launcher/app/build/outputs/apk/debug/app-debug.apk`
- `parent-app/app/build/outputs/apk/debug/app-debug.apk`

Install and restart on connected device:
```bash
adb install -r launcher/app/build/outputs/apk/debug/app-debug.apk
adb shell am force-stop com.wew.launcher
adb shell am start -n com.wew.launcher/.MainActivity
```

Child and parent need `local.properties` with `SUPABASE_URL` and `SUPABASE_ANON_KEY`.

### Web dashboard

```bash
cd web-dashboard && npm install && npm run dev
```

### Supabase

```bash
supabase db push
supabase functions deploy send-fcm-notification
supabase functions deploy daily-credit-reset
```

Project ref: `sqrmupfpdchiecfthqtj`

Enable **`pg_cron`** in the dashboard if you rely on migration 009's optional schedule; otherwise the **`daily-credit-reset`** Edge Function calls **`reset_daily_tokens`** and **`generate_daily_usage_summaries`**.

**Do not commit secrets.** Use `local.properties`, `.env`, or gitignored agent settings only.

---

## Technical decisions (must-follow)

### supabase-kt **2.1.4** + Kotlin **1.9.x**

Do not upgrade supabase-kt to 2.5.x without upgrading Kotlin to 2.0+ and the Compose compiler plugin.

Correct Maven coordinates:
```
io.github.jan-tennert.supabase:postgrest-kt:2.1.4
io.github.jan-tennert.supabase:gotrue-kt:2.1.4
io.github.jan-tennert.supabase:realtime-kt:2.1.4
io.ktor:ktor-client-android:2.3.7
```

### No `rpc()` from Kotlin

Use **direct PostgREST** `select` / `insert` / `update` / `upsert`. The **web dashboard** may still call RPCs (e.g. legacy credits) until unified.

### `onConflict` (upsert)

Use the parameter form: `upsert(data, onConflict = "col1,col2")`.

### Batch upserts

Typed data classes can trigger PostgREST "All object keys must match" errors; use **`buildJsonObject`** for bulk rows.

### Themes

XML: `Theme.AppCompat.DayNight.NoActionBar`. Compose: **Material3** via `MaterialTheme`.

### Accessibility

Critical HUD (e.g. **token chip** on dark background) must meet **WCAG AA contrast** (4.5:1 normal text, 3:1 large). Use **solid light chip + dark text** over low-alpha tints on `Night`.

---

## UI / Design patterns

### Color palette

| Name | Hex | Usage |
|---|---|---|
| `Night` | `#0D0D15` | Primary background for all screens |
| `OnNight` | `#E5E5E7` | Primary text color on dark backgrounds |
| `BrandViolet` / `ElectricViolet` | `#7B61FF` | Accent, buttons, active elements |
| `SurfaceVariant` | `#1E1E2E` | Card/chip backgrounds, input fields |
| `WarningAmber` | amber | Low-token warnings, error states |
| `SafetyGreen` | green | Check-in success, location confirmed |

### Navigation architecture (launcher)

- **`MainActivity`** owns a `WewScreen` sealed class (`ConversationList`, `Chat`, `Web`, `Map`).
- No Jetpack Navigation — screen transitions are manual `when(screen)` branches in `setContent`.
- **Navigation menu** slides DOWN from top via `AnimatedVisibility(slideInVertically { -it })` with a scrim overlay. Not a `ModalBottomSheet`.
- Menu items: Messages, Contacts, Check In, Map, Calendar (if approved), Weather (if approved), Parent App (passcode-gated), SOS.
- **FAB removed** from conversation list; compose icon is in the **top bar** (right-aligned, TokenChip centered).
- **Contacts FAB removed**; add button is an `IconButton(+)` in the header row (right-aligned, same row as X and "contacts" title).

### Screen patterns

- Each screen is a full-screen `@Composable` backed by its own `ViewModel`.
- Back navigation: `BackHandler` composable in each screen; `MainActivity.onBackPressed()` is swallowed.
- Overlay screens (Contacts, CheckIn) use `if (showX) { XScreen(...) }` stacked in the `ConversationList` branch.
- Dialogs: `AlertDialog` for confirmations (SOS, call), `Dialog(usePlatformDefaultWidth = false)` for full-screen overlays (contact detail, image viewer).

### Chat / InputBar keyboard handling

- The outer `Column` in `ChatScreen` has `statusBarsPadding()` only (NOT `navigationBarsPadding()`).
- `InputBar` composable applies `imePadding().navigationBarsPadding()` so the top bar stays pinned above the keyboard.

### Contacts

- **Status pills:** Only `"pending"` (amber) is shown. `"approved"` contacts show no badge. `"denied"` contacts are **hidden entirely** from the list.
- **Phone formatting:** `(NXX) NXX-XXXX` format via `formatPhoneNumber()` helper. Handles 10-digit and 11-digit (leading 1).
- Contacts with `relationship = 'parent'` are seeded by the parent app (`ParentRepository.seedParentContact()`).

### SOS dialog

- Title: "Call [parent name]" (falls back to "your parent" if name unavailable).
- Subtitle: parent phone number (or "parent number not set up yet").
- Buttons: Cancel | Call.
- Call action: sends SMS `"Please call me back I sent this from the SOS"` via `SmsManager`, then opens the dialer via `Intent.ACTION_DIAL`.
- Parent phone comes from `devices.parent_phone` (Supabase) → cached in `SharedPreferences` as `parent_phone`.

### Passcode dialog

- 4-digit PIN numpad (`PasscodeDialog` composable).
- Hash: `SHA-256(deviceId + pin)` compared against `device_passcode.passcode_hash`.
- Used for: Parent App access from child launcher menu.

---

## Coding standards

- **MVVM**, **Compose-only** UI for new work.
- **One `UiState` data class** per screen; expose **`StateFlow`**, not raw mutable flows.
- **Smallest user-visible change** per PR; no speculative abstractions.
- **Comments** explain *why*, not *what*.
- **User-facing strings:** plain English, lowercase-friendly, parent- and child-appropriate; no jargon.
- **Errors:** log with **`Log.e` / `Log.w`**; do not swallow failures silently.
- **Database:** new behavior = **new migration file** (`NNN_description.sql`). **Never** edit applied migrations. New tables: `created_at`, indexes on FK filters, **RLS** for parent/device scopes.
- **Token / ledger writes:** include **`context_metadata`** map when duration or app context matters.
- **Imports:** Always verify imports compile. Common gotchas: `clickable` needs `androidx.compose.foundation.clickable`, not `combinedClickable`. Don't use `MutableInteractionSource` inline in click modifiers (just use `.clickable { ... }`).
- **Expression bodies vs block bodies:** If a function has a `return` statement or `try/catch`, use a block body `{ return ... }`, NOT an expression body `= try { ... }`. The latter causes "Returns are not allowed for expression body" errors.

---

## Key data models

### Supabase tables (migrations 001–009)

| Table | Purpose |
|---|---|
| `devices` | Child device records. Key fields: `parent_phone`, `parent_display_name`, `parent_sms_thread_id`, `timezone`, `current_tokens`, `daily_token_budget` |
| `contacts` | Child's contacts. Fields: `relationship` (`parent`/null), `status` (`requested`/`approved`/`blocked`), `is_authorized`, `phone` |
| `contact_auth_requests` | Pending parent approval requests for contacts |
| `token_ledger` | Token consumption log per action |
| `activity_log` | Activity history (action_type, tokens_consumed, app_package) |
| `messages` | System messages (daily summaries). Key: `sender_type = 'system'`, `message_type = 'daily_summary'`, `body`, `summary_for_date` |
| `apps` | Installed apps per device. `is_whitelisted`, `media_action_type` for billing category |
| `check_ins` | Location check-ins from child |
| `conversations` | Thread metadata (pinned, muted) |
| `device_passcode` | SHA-256 passcode hash for parent-app access |
| `notifications_config` | Per-parent notification preferences, `daily_summary_time` |

### Launcher SharedPreferences (`wew_prefs`)

| Key | Type | Source |
|---|---|---|
| `device_id` | String | Set during SetupActivity |
| `parent_phone` | String? | Cached from `devices.parent_phone` |
| `parent_name` | String? | Cached from `devices.parent_display_name` |
| `fcm_token` | String | Set by `WewFirebaseMessagingService` |

### ActionType enum (launcher `Models.kt`)

`APP_OPEN`, `SMS_SENT`, `SMS_RECEIVED`, `MMS_SENT`, `CALL_MADE`, `CALL_RECEIVED`, `WEB_SESSION`, `PHOTO_TAKEN`, `VIDEO_WATCHED`, `GAME_SESSION`, `SOCIAL_SCROLL`, `AUDIO_STREAMED`, `TEMP_ACCESS_GRANTED`, `CHECK_IN`, `SOS`, `URL_BLOCKED`, `APP_BLOCKED`, `TOKEN_REQUEST`, `TOKENS_EXHAUSTED`, `CONTACT_REQUESTED`, `CONTACT_QUARANTINED`, `TAMPER_DETECTED`, `DEVICE_ADMIN_REVOKED`, `LOCK_ACTIVATED`, `LOCK_DEACTIVATED`

---

## Known drift / follow-ups

- **Web dashboard** may still show **credits** and call **`add_credits` RPC** — tokens are authoritative on device; align dashboard when possible.
- **`005_cron.sql`** references legacy **`reset_daily_credits`**; production should use **`reset_daily_tokens`** (007) and/or Edge **`daily-credit-reset`**.
- **FCM** `send-fcm-notification` still uses a simplified parent-token-on-device model; extend as needed for multi-device parents.
- **Parent phone** (`devices.parent_phone`) must be set by the parent app for SOS to work. If null, the SOS dialog shows "parent number not set up yet."
- **New conversation flow** (compose icon in top bar) currently opens a bottom sheet listing approved contacts. Planned: blank chat view with participant picker.
- **`QuarantineBanner` composable** has been removed from the conversation list (unknown-contact quarantine messages are no longer surfaced to the child).

---

## Operational checklist

1. Apply migrations through **009+** (parent phone, messages body, summaries).
2. Parent sets **phone + display name + timezone** in parent app settings.
3. Child opens **WeW Parent** thread once so **`parent_sms_thread_id`** syncs.
4. Enable **`daily_summary`** time in `notifications_config` (parent app settings).
5. Deploy Edge **`daily-credit-reset`** on a schedule (e.g. hourly) so token reset and summary generation run reliably if pg_cron is unused.

---

## Reference docs

- [TOKEN_SYSTEM.md](TOKEN_SYSTEM.md) — token costs, media actions, daily summaries, cost hierarchy
- [README.md](README.md) — full setup, PR workflow, notification matrix
