# Wew Token System

## Overview

The token system is the core usage-control mechanism in the Wew launcher. Each child device is
allocated a daily token budget. Actions on the device consume tokens according to a cost table.
When the budget is exhausted the child can request additional tokens from the parent.

The system is modeled on agentic usage limits — each action type carries a cost that reflects its
relative demand on the child's attention, not just its data or network cost.

---

## Daily Budget

| Parameter | Default | Configurable |
|---|---|---|
| Daily token budget | 10,000 | Yes, per device (parent dashboard) |
| Reset time | 00:00 local | Yes, per device |
| Rollover | Disabled | No (see below) |

### No rollover — intentional design

Tokens **do not carry over** between days. Each day the budget resets to the full daily limit
regardless of how many tokens were left unused.

This is intentional: the goal is not to punish under-use or reward hoarding. Leftover tokens at
the end of the day are expected and fine — they simply reflect a light day. A future feature will
surface leftover-token patterns to parents as a usage insight, but leftover tokens themselves
have no mechanical effect on the next day's budget.

---

## Budget Calibration Target

The system is calibrated so that **~75 discrete actions exhaust the 10,000-token daily budget**
under current engine defaults, giving an effective average cost of roughly **130 tokens per
action** for that mix.

Costs were doubled from the prior table so each billed action consumes more aggressively; passive
consumption still costs materially more than active creation per [Cost Hierarchy](#cost-hierarchy).
The daily ceiling stays at 10,000 unless the parent raises it per device.

---

## Cost Hierarchy

Actions are grouped into tiers based on how much attention they capture. Passive consumption
(watching video, social scrolling) sits at the top because it is the most attention-hijacking
behaviour. Active creation (messaging, photos) sits in the middle. Navigation sits at the bottom.

```
Tier 0  — Free (0)                Safety events, SOS, check-ins, monitoring signals
Tier 1  — Minimal (80–250)       Short web lookups, audio streaming, MMS
Tier 2  — Standard (130–300)      App opens, SMS, web session entry
Tier 3  — Significant (500–1,500) Game sessions, photos, calls, social scrolling
Tier 4  — High drain (1,500+ base + high/min)  Video watching, video calls
Tier 5  — Privilege (5,000 flat)  Temporary access grants (bypass of an app block)
```

---

## Action Cost Table

### Flat actions

| Action | Tokens | Notes |
|---|---|---|
| App open | 130 | Most frequent action; scales with broader daily pacing |
| SMS sent | 100 | Active communication — lower cost than passive consumption |
| MMS sent | 250 | Slightly above SMS; usually a photo attached to a message |
| Photo taken | 500 | Creative/productive action; not penalised as heavily as passive media |
| Temporary access granted | 5,000 | Intentionally high — this is a privilege bypass |

### Launcher chrome (threads, nav, contacts)

Default **130 tokens** each unless the parent overrides in `token_action_costs`. Implemented in
`launcher/.../token/TokenEngine.kt` as separate `action_type` strings for the ledger.

| Child gesture | `action_type` |
|---|---|
| Mark thread read / unread (swipe or menu) | `sms_thread_mark_read`, `sms_thread_mark_unread` |
| Mute/unmute or delete thread | `conversation_meta_changed`, `sms_thread_delete` |
| Open a thread or tap compose | `chat_surface_open` |
| Contacts or Check In overlay | `launcher_overlay_open` |
| Open Map from menu | `map_session` |
| Launch calendar / weather / approved app from nav | `app_open` |
| Add contact or request authorization | `contact_attention_action` |
| Open parent app after passcode | `app_open` |

Opening a thread pays **`chat_surface_open`** once; syncing read state in the background does not add a second `sms_thread_mark_read` charge (swipe/menu still bill those keys).

### Time-based actions (base + per-minute rate)

| Action | Base | Per Minute | Example: 10 min | Example: 30 min |
|---|---|---|---|---|
| Web session | 300 | +200 | 2,300 | 6,300 |
| Call received | 0 | +500 | 5,000 | 15,000 |
| Call made | 1,000 | +500 | 6,000 | 16,000 |
| Video call | 750 | +750 | 8,250 | 23,250 |
| Audio streamed | 200 | +80 | 1,000 | 2,600 |
| Game session | 750 | +400 | 4,750 | 12,750 |
| Social scroll | 500 | +250 | 3,000 | 8,000 |
| Video watched | 1,500 | +1,000 | 11,500 | 31,500 |

### Free actions (always 0 tokens)

The following actions never cost tokens regardless of budget level. Safety and monitoring signals
must not be disincentivised by the token system.

| Action | Reason |
|---|---|
| SOS / emergency signal | Safety-critical — must never be gated by token balance |
| Check-in (location ping) | Parent-initiated safety feature |
| URL blocked (event log) | Monitoring signal, not a child action |
| App blocked (event log) | Monitoring signal |
| Token request | Requesting more tokens must always be possible |
| Token exhausted (event) | System event |
| Contact requested | Awaiting parent approval; child should not be penalised for asking |
| Contact quarantined | System event |
| Settings tamper detected | Security event |
| Device admin revoked | Security event |
| Lock activated / deactivated | Parent-controlled events |

---

## Media action types (implemented)

| Enum value | String key | Description |
|---|---|---|
| `VIDEO_WATCHED` | `video_watched` | Video / heavy streaming foreground time |
| `GAME_SESSION` | `game_session` | Game foreground session |
| `SOCIAL_SCROLL` | `social_scroll` | Passive scroll / social-style session |
| `AUDIO_STREAMED` | `audio_streamed` | Music / podcast foreground session |

**Detection:** `LauncherForegroundService` samples the foreground package each minute. Parents can set `apps.media_action_type` per whitelisted app in Supabase; otherwise a small built-in fallback map
(e.g. YouTube, Spotify, TikTok) applies. Sessions under 30 seconds are not billed. Billable minutes
use ceiling of elapsed time; each session is charged when the child leaves the app or returns to wew.

**Daily summaries:** At each parent’s `notifications_config.daily_summary_time` (local device
timezone), `generate_daily_usage_summaries()` aggregates `token_ledger` for the prior “phone day” and
inserts a `messages` row (`sender_type = system`, `message_type = daily_summary`) into the parent SMS
thread (`devices.parent_sms_thread_id`). The child and parent UIs merge/show that text.

---

## Parent Override

All costs in the table above are engine defaults. Parents can override any action cost per device
via the `token_action_costs` table in Supabase. The `TokenEngine.calculateCost()` method checks
for device-specific overrides before falling back to defaults.

This allows a parent to, for example, make `VIDEO_WATCHED` cheaper for a child who is using
educational video content, or make `GAME_SESSION` more expensive for a child who over-games.

---

## Key Design Principles

1. **Passive consumption costs more than active creation.** Watching a video should drain the
   budget faster than sending a message. The child should feel the difference.

2. **Safety is never gated.** SOS, check-ins, and monitoring events are always free. The token
   system must never create a perverse incentive to avoid safety features.

3. **Leftover tokens are fine.** The reset is daily and unconditional. Under-spending on a quiet
   day does not roll over. The budget is a ceiling on heavy days, not a target to hit every day.

4. **Time is the true cost unit for attention-capturing activities.** A short YouTube clip costs
   much less than a 30-minute session. The per-minute rates on media actions are the mechanism
   that makes duration visible to the child.

5. **Parents tune, the engine defaults.** The defaults are calibrated for a typical 8–12-year-old
   and should work without any parent configuration. Overrides exist for edge cases.

---

## Implementation Files

| File | Role |
|---|---|
| `launcher/app/src/main/java/com/wew/launcher/token/TokenEngine.kt` | Cost table and consume/canAfford logic |
| `launcher/app/src/main/java/com/wew/launcher/data/TokenBalanceCache.kt` | Last-known `devices` token snapshot for cold-start chip accuracy |
| `launcher/app/src/main/java/com/wew/launcher/data/model/Models.kt` | `ActionType` enum, `TokenLedger`, `TokenBudget`, `TokenRequest` models |
| `launcher/app/src/main/java/com/wew/launcher/service/LauncherForegroundService.kt` | Schedule locks; foreground media sessions → token deductions |
| `supabase/migrations/007_tokens_and_chat.sql` | DB schema: `token_ledger`, `token_budgets`, `token_action_costs`, `token_requests` |
| `supabase/migrations/008_device_parent_contact.sql` | Parent phone, `parent_sms_thread_id`, timezone, `apps.media_action_type` |
| `supabase/migrations/009_daily_summary_messages.sql` | `messages.body`, `generate_daily_usage_summaries()`, optional pg_cron |
| `supabase/migrations/005_cron.sql` | Legacy cron hook; prefer `reset_daily_tokens` + Edge schedule (see CLAUDE.md) |
