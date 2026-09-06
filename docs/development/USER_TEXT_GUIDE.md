# Phoenix Bot — Full Developer & User Guide

Last updated: May 12, 2026

---

## Quick Reference

### How the Bot Is Structured

Project root module path: `src/main/java/Botcode/`.

- `CommsBot.java` — App entrypoint/bootstrap. Starts JDA, registers commands/listeners, triggers
  startup data refresh.
- `listeners/` — Discord event handlers (`Eventlistener`, `OnJoin`, `TempVoiceDelete`).
- `Commands/` — Slash command orchestration (`CommandManager`).
- `Personality/` — Intent/emotion/persona/phrase learning systems.
- `AI/` — AI response facade and optional web lookup.
- `StarCitizen/` — Fetch/normalize/update/store/search/chat data services.
- `Panel/` — Interactive operations panel UI and handlers.
- `Utils/` — Shared helpers (config, safety, memory, help, channel config).

---

### Learning and Adaptation

**Admin (requires MANAGE_SERVER or ADMINISTRATOR):**

```
@bot learn <intent>: <phrase>
@bot learn <intent> = <phrase>
@bot forget <intent>: <phrase>
@bot forget <intent> = <phrase>
@bot phrases <intent>
```

**User/personal:**

```
@bot learnme <intent>: <phrase>
@bot learnme <intent> = <phrase>
@bot forgetme <intent>: <phrase>
@bot forgetme <intent> = <phrase>
@bot myphrases <intent>
```

**Automatic learning:**

- Reactions on bot replies: upvote 👍/✅, downvote 👎/❌
- Implicit feedback phrases also tune reply scoring.
- Passive adaptation records user style/profile from normal guild messages when
  `BOT_PASSIVE_LEARNING_ALL_GUILD_MESSAGES=true`.
- Startup backfill can ingest recent channel history for memory context when
  `BOT_PASSIVE_HISTORY_BACKFILL_ENABLED=true`.

**Valid intents:** `greeting` `casual_chat` `question` `statement` `command` `complaint` `praise`
`request_action` `confusion` `neutral`

---

### Star Citizen Data Pipeline (Quick Overview)

**`StarCitizenFetcher`** — Pulls raw data from local overrides, env URLs, approved sources, then
defaults. Weapons/ships use optional stats enrichment and HTML fallback extraction.

**`StarCitizenNormalizer`** — Converts raw upstream payloads into stable normalized schemas used by
commands/panel/chat.

**`StarCitizenUpdateManager`** — Orchestrates fetch → normalize → merge → save → reload. Saves to:

- `data/<dataset>.json`
- SQLite dataset store (`data/starcitizen.db`) via `StarCitizenDatasetStore`

**`StarCitizenDataService`** — In-memory dataset access, fuzzy name resolution, and suggestion
helpers.

**Recent updates included in this guide (May 2026):**

- Source override workflow is now documented as `@bot source <dataset>: <https-url>`,
  `@bot approvesource <dataset>`, `@bot sources`.
- Missing-data workflow reflects `@bot missing <dataset>: <item> [| notes]`, natural-language
  detection, live refresh attempt, and manual queue output.
- Fetch priority now explicitly includes approved override URLs between env overrides and default
  upstream URLs.

---

### Weapon Stats

Weapon stats (dps, alpha_damage, rpm, range) are sourced from `https://api.erkul.games/live/weapon`.
Stats manually added via the update pipeline are persisted to both `data/weapons.json` and SQLite.
Missing stats are backfilled during the `weapons` update merge cycle.

- `/weapon`, `/ship`, and panel weapon views all read from the normalized `weapons` dataset.
- During update: missing normalized fields are filled from existing snapshots and any guide-derived
  patches.
- `/ship` and the panel weapon loadout view fall back to the `weapons` dataset stats (
  dps/alpha_damage/rpm) when per-hardpoint fields are sparse on the ship record.

---

### Command and GUI Quick Reference

| Command                                                   | What it does                                    |
|-----------------------------------------------------------|-------------------------------------------------|
| `/help`                                                   | List all commands                               |
| `/panel`                                                  | Post the interactive operations console         |
| `/commodity name:`                                        | Buy/sell prices and locations                   |
| `/mine rock: ship: laser: consumable: operators: format:` | Mining analysis                                 |
| `/ship name:`                                             | Ship specs, stats, loadout                      |
| `/weapon name:`                                           | Weapon DPS, alpha, RPM, range, ammo, fire modes |
| `/trade mode: cargo:`                                     | Trade route finder                              |
| `/reportmissing dataset: item:`                           | Report missing data and trigger live check      |

**Panel buttons:** Mining · Commodity · Ship · Ship Weapons · FPS Weapons · Trade · Refinery ·
Salvage · Components · Armor · Locations · Missions · Refresh Data · Data Status · Help

---

### Key Environment Flags

**Learning:**

```
BOT_AUTONOMOUS_LEARNING_ENABLED=true
BOT_PASSIVE_LEARNING_ALL_GUILD_MESSAGES=true
BOT_AUTO_PERSONAL_PHRASE_LEARNING_ENABLED=true
BOT_PASSIVE_HISTORY_BACKFILL_ENABLED=true
```

**Star Citizen sources:**

```
SC_WEAPONS_STATS_URL=https://api.erkul.games/live/weapon
SC_WEAPONS_STATS_HTML_URL=https://www.erkul.games/live/weapons
SC_SHIPS_STATS_HTML_URL=https://www.erkul.games/live/ships
```

**Channels:**

```
BOT_FREE_CHANNEL_IDS=<comma-separated channel IDs>
BOT_GUI_CHANNEL_IDS=<comma-separated channel IDs>
AI_CATEGORY_ID=<category ID for social/AI chat>
```

---

### Troubleshooting Quick Checks

**Learning seems weak:**

1. Run `@bot learningstats`.
2. Confirm reactions/feedback are targeting bot replies.
3. Check write access for `data/*.json` and SQLite file.

**Weapon stats missing:**

1. Force a weapons refresh (panel → Refresh Data).
2. Confirm `SC_WEAPONS_STATS_URL=https://api.erkul.games/live/weapon` is set.
3. Check update logs for merge counts and unresolved count.
4. Verify `data/weapons.json` and SQLite `datasets` row for `weapons`.

---

## Detailed Module Reference

## Table of Contents

1. [Project Structure Overview](#1-project-structure-overview)
2. [Startup & Bootstrap — CommsBot.java](#2-startup--bootstrap--commsbotjava)
3. [Event Listeners](#3-event-listeners)
4. [Commands Module — CommandManager.java](#4-commands-module--commandmanagerjava)
5. [Personality Module](#5-personality-module)
6. [AI Module](#6-ai-module)
7. [Star Citizen Data Pipeline](#7-star-citizen-data-pipeline)
8. [Panel Module](#8-panel-module)
9. [Utils Module](#9-utils-module)
10. [Learning System — How It Works](#10-learning-system--how-it-works)
11. [Docker Files](#11-docker-files)
12. [Security & Vulnerability Management](#12-security--vulnerability-management-may-2026)
13. [Environment Variables Reference](#13-environment-variables-reference)
14. [Troubleshooting](#14-troubleshooting)

---

## 1. Project Structure Overview

```
src/main/java/Botcode/
├── CommsBot.java                  ← App entry point / bootstrap
├── AI/
│   ├── AIResponder.java           ← Unified AI response facade
│   └── WebLookupService.java      ← Wikipedia / DuckDuckGo fallback lookup
├── Commands/
│   └── CommandManager.java        ← All slash command handlers
├── listeners/
│   ├── Eventlistener.java         ← Main message + reaction + voice handler
│   ├── OnJoin.java                ← Member join welcome flow
│   ├── TempVoiceDelete.java       ← Temp voice channel cleanup
│   └── Interests/
│       ├── InterestDetector.java  ← Detects interest keywords in messages
│       └── RoleAssigner.java      ← Auto-assigns matching Discord roles
├── Panel/
│   ├── PanelModule.java           ← Registers panel listeners on startup
│   ├── PanelCommand.java          ← /panel slash command handler
│   ├── PanelInteractionHandler.java ← All button/modal/select interactions
│   ├── PanelEmbeds.java           ← Discord embed builders for panel results
│   ├── PanelButtons.java          ← Component ID constants + menu builders
│   ├── PanelStateStore.java       ← Persists panel channel/message state
│   └── PanelAutoPublisher.java    ← Auto-posts panel on boot / bumps on messages
├── Personality/
│   ├── TriggerEngine.java         ← Intent detection from message text
│   ├── EmotionEngine.java         ← Emotion detection
│   ├── PersonalityEngine.java     ← Tone + energy profile selection
│   ├── SentenceGenerator.java     ← Template-based response composer
│   ├── PhraseLearner.java         ← Phrase learning, scoring, persistence
│   ├── PersonaVoice.java          ← Deadpool voice wrapper / GIF captions
│   └── SelfFactsRouter.java       ← "What are you?" self-description routing
├── StarCitizen/
│   ├── StarCitizenFetcher.java         ← Fetches raw data from APIs/local
│   ├── StarCitizenNormalizer.java      ← Converts payloads to stable schemas
│   ├── StarCitizenUpdateManager.java   ← Fetch → normalize → merge → save → reload
│   ├── StarCitizenDataService.java     ← In-memory dataset access + fuzzy lookup
│   ├── StarCitizenDatasetStore.java    ← SQLite persistence layer
│   ├── StarCitizenChatService.java     ← Answers SC natural-language questions
│   ├── StarCitizenSearch.java          ← Cross-dataset text search
│   ├── TradeService.java               ← Trade route calculations
│   ├── MiningService.java              ← Mining analysis
│   ├── MiningSpotRecommender.java      ← Best mining location suggestions
│   ├── RefineryService.java            ← Refinery method comparison
│   ├── SalvageService.java             ← Salvage hotspots and tips
│   ├── DPSService.java                 ← DPS calculation helper
│   ├── MissingDataReportService.java   ← Tracks missing-data reports
│   ├── QueryAliasService.java          ← User query shortcuts
│   └── SourceOverrideService.java      ← Admin data source overrides
└── Utils/
    ├── BotConfig.java                  ← All feature flags (reads from env)
    ├── Env.java                        ← Env-var reader with dotenv support
    ├── ChannelConfig.java              ← Free/GUI/AI channel allow-lists
    ├── ConversationMemoryService.java  ← Per-user memory, sessions, preferences
    ├── SafetyGuard.java                ← Content filter for learning/lookup
    └── HelpBuilder.java                ← Builds the /help embed
```

Runtime data written to `data/`:

```
data/
├── starcitizen.db              ← SQLite: all dataset snapshots, reports, overrides
├── ships.json                  ← Normalized snapshot: ships
├── weapons.json                ← Normalized snapshot: weapons (includes guide stats)
├── commodities.json            ← Normalized snapshot: commodities
├── components.json             ← Normalized snapshot: components
├── learned_phrases.json        ← Global phrase scores keyed by intent
├── user_phrase_profiles.json   ← Per-user phrase scores keyed by userId + intent
├── user_memory.json            ← Per-user conversation memory and preferences
├── conversation_sessions.json  ← Active/recent session timestamps
├── panel_state.json            ← Panel channel → message ID for auto-bump
├── source_overrides.json       ← Pending/approved admin-reviewed source URL overrides
├── manual_update_queue.json    ← Queued manual data corrections
├── manual_update_queue.md      ← Human-readable manual review queue output
└── missing_reports.json        ← Submitted /reportmissing entries
```

---

## 2. Startup & Bootstrap — `CommsBot.java`

**File:** `src/main/java/Botcode/CommsBot.java`
This is the main entry point. Everything starts here.

### `main(String[] args)`

Called by the JVM on process start. Creates a `new CommsBot()` instance. Catches `LoginException` (
bad token) and general startup failures with readable messages.

### `CommsBot()` constructor — step by step

**Step 1 — Load environment**

```java
config =Dotenv.

configure().

ignoreIfMalformed().

ignoreIfMissing().

load();
```

Reads `.env` from the working directory. All env vars (token, feature flags, URLs) must be in this
file. If `TOKEN` is missing, startup aborts with a clear error message.
**Step 2 — Build the shard manager**

```java
DefaultShardManagerBuilder builder = DefaultShardManagerBuilder
    .createDefault(token)
    .enableIntents(GatewayIntent.ALL_INTENTS);
```

Creates a JDA shard manager. Enables ALL gateway intents so message content, member events, voice
events, and reactions are all available. Sets bot presence to `Playing Squadron 42`.
**Step 3 — Refresh Star Citizen snapshots (blocking)**

```java
refreshStarCitizenSnapshots();
```

Calls `StarCitizenUpdateManager.update(dataset)` for each of: commodities, ships, weapons,
components, mining, refinery, refinery_stations, locations, salvage. Runs synchronously before any
commands are accepted so data is fresh on every boot.
**Step 4 — Register slash commands**
For each JDA shard, `jda.upsertCommand(...)` registers:

- `/help` — no options
- `/panel` — interactive operations console
- `/commodity name:` — commodity buy/sell info
- `/mine rock: ship: laser: consumable: operators: format: [field:]` — mining analysis
- `/ship name:` — ship specifications
- `/weapon name:` — weapon stats
- `/trade mode: cargo: [commodity:] [count:]` — trade route finder
- `/reportmissing dataset: item: [notes:]` — missing data report
  **Step 5 — Attach event listeners**

```java
shardManager.addEventListener(new Eventlistener());   // main message handler
    shardManager.

addEventListener(new OnJoin());           // welcome messages
    shardManager.

addEventListener(new TempVoiceDelete());  // temp channel cleanup
    shardManager.

addEventListener(new CommandManager());   // slash commands
    PanelModule.

register(shardManager);                    // panel UI
```

### `refreshStarCitizenSnapshots()`

Iterates dataset names and calls `StarCitizenUpdateManager.update()`. Logs "X/Y succeeded". Failures
are non-fatal — the bot uses existing stored data.

### `getStartEpochMs()` / `getVersion()`

Static helpers. `getStartEpochMs()` records process start time for uptime reporting.
`getVersion()` reads `Implementation-Version` from the JAR manifest (set by Maven).
---

## 3. Event Listeners

### 3.1 `Eventlistener.java`

**File:** `src/main/java/Botcode/listeners/Eventlistener.java`
~2000 lines. Handles all message-based bot behaviour.

#### Key class-level state

| Field                            | Type                                 | Purpose                                                                          |
|----------------------------------|--------------------------------------|----------------------------------------------------------------------------------|
| `lastInterjectionEpochByChannel` | `Map<Long, Long>`                    | Per-channel cooldown for SC topic interjections                                  |
| `lastScDomainByUser`             | `Map<Long, String>`                  | Remembers user's last SC topic (ship/trade/mining etc.) for follow-up resolution |
| `lastSocialReplyEpochByChannel`  | `Map<Long, Long>`                    | Throttles spontaneous social replies                                             |
| `lastGifReplyEpochByChannel`     | `Map<Long, Long>`                    | Throttles GIF replies per channel                                                |
| `lastTrackedReplyByUserChannel`  | `Map<String, PendingFeedbackTarget>` | Maps user+channel key to last bot reply for implicit feedback                    |
| `historyBackfilledGuilds`        | `Set<Long>`                          | Prevents double-backfill on the same guild                                       |

#### `onReady(ReadyEvent event)` — line 159

1. Sets bot presence.
2. Triggers `PanelAutoPublisher` to post/refresh the operations panel in all known GUI channels.
3. If `BOT_PASSIVE_HISTORY_BACKFILL_ENABLED=true`, iterates recent channel messages and calls
   `ConversationMemoryService.recordHistoricalSignal()` to build user memory from history.

#### `onGuildMemberJoin(GuildMemberJoinEvent event)` — line 267

Sends a welcome DM and optionally posts to a configured welcome channel.

#### `onGuildVoiceUpdate(GuildVoiceUpdateEvent event)` — line 223

When a user joins the designated "create voice" trigger channel, calls `createTempVoiceChannel()` to
provision a private voice room named after that user.

#### `createTempVoiceChannel(Member, Category)` — line 247

1. Creates a voice channel inside the configured category.
2. Sets user limit and locks it to the creator via Discord permission overrides.
3. Stores the channel ID for `TempVoiceDelete` to clean up when empty.

#### `onMessageReceived(MessageReceivedEvent event)` — line 316

The main dispatch method. Processes every accessible guild message. Full flow:

1. **Ignore bots** — returns immediately if `isBot()`.
2. **Safety check** — `SafetyGuard.isDisallowed()` on raw text; exits silently if blocked.
3. **Passive learning** — if enabled, calls `PhraseLearner.observeUserStyleMessage()` and
   `ConversationMemoryService.recordUserMessage()`.
4. **Implicit feedback** — calls `applyImplicitQualityFeedback()` to detect praise/complaint phrases
   and score the last tracked reply.
5. **Alias learning** — calls `tryLearnQueryAlias()` to detect `term = meaning` patterns.
6. **Channel routing** — decides whether to respond based on: free channel, bot mention, active
   session, or AI-scoped channel.
7. **Admin commands** — processes `@bot channel allow/deny/list`, `@bot guichannel`,
   `@bot learn/forget`, `@bot learnme/forgetme`, `@bot learningstats`, `@bot source`,
   `@bot reportmissing`.
8. **SC domain check** — if message contains SC keywords or is a follow-up in SC context, routes to
   `StarCitizenChatService`.
9. **Politics redirect** — if message hits politics keywords, posts a canned redirect and stops.
10. **Topic interjection** — random chance to interject with an SC fact.
11. **GIF reaction** — random chance to attach a GIF.
12. **Social chat** — in AI-scoped channels, generates a conversational reply.
13. **General AI response** — for direct mentions or active session channels.

#### `handleLearnCommand(event, args, add)` — line 968

- Validates `MANAGE_SERVER` or `ADMINISTRATOR` permission.
- Parses `<intent>: <phrase>` or `<intent> = <phrase>` (both accepted).
- Calls `PhraseLearner.addPhrase()` or `removePhrase()`.

#### `handleUserPhraseCommand(event, args, add)` — line 1034

Same as above but no admin check — any user may teach their own phrases. Calls `addUserPhrase()` or
`removeUserPhrase()`.

#### `hasLearnSeparator(args)` / `findLearnSeparatorIndex(args)` — lines 1081, 1085

Detects `:` or `=` as the intent/phrase separator. Enables natural-language syntax like
`@bot learn greeting = Hey what's up`.

#### `handleMissingReportCommand(event, args)` — line 1098

Parses `@bot missing <dataset>: <item> [| notes]` and
`@bot reportmissing <dataset>: <item> [| notes]`.
Calls `MissingDataReportService.reportAndAttemptFix()` to persist the report, attempt a dataset
refresh when supported, return closest matches, and queue unresolved items for manual review.

#### `tryHandleNaturalMissingReport(event, text)` — line 1182

Detects natural-language "X is missing" statements and auto-generates a report without requiring the
explicit command.

#### `tryPoliticsRedirect(event, text)` — line 1343

Checks `POLITICS_KEYWORDS`. If matched, replies with a `POLITICS_REDIRECT_LINES` entry and returns
`true` (stops processing).

#### `tryTopicInterjection(event, text)` — line 1352

Random SC-topic interjection controlled by `INTERJECTION_CHANCE_PERCENT` and
`INTERJECTION_COOLDOWN_SECONDS`. Posts from `SC_INTERJECTION_LINES`.

#### `trySocialChannelReply(event, text)` — line 1391

In AI-scoped channels with social mode on:

1. Builds prompt with `ConversationMemoryService.buildMemoryPrefix()`.
2. Calls `AIResponder.resolve()`.
3. Wraps result in `PersonaVoice.enforceDeadpoolVoice()`.
4. Optionally appends a GIF via `withExpressiveFlair()`.
5. Posts reply and registers it for feedback tracking.

#### `tryGifReactionReply(event, ...)` — line 1475

If GIF signals detected in message and cooldown is clear, appends a GIF URL from `BOT_GIF_LINKS`
with a `PersonaVoice.gifCaption()`.

#### `sanitizeOutgoingReply(reply)` — line 1546

Strips AI self-reference phrases ("As an AI", "As a language model", etc.) and trims to Discord's
2000-character limit.

#### `applyImplicitQualityFeedback(event, text)` — line 1825

Checks positive signals (`thanks`, `perfect`, `that worked`) and negative signals (`wrong`,
`bad answer`, `fix that`). Resolves the target bot reply from `lastTrackedReplyByUserChannel`. Calls
`PhraseLearner.upvote()` or `downvote()`.

#### `tryLearnQueryAlias(event, text, directBotContext)` — line 1784

Pattern `ALIAS_CORRECTION_PATTERN` matches `term = meaning`. If key and value pass safety checks,
calls `QueryAliasService.learnAlias()`.

#### `isAdmin(member)` — line 1578

Returns `true` if the member has `ADMINISTRATOR` or `MANAGE_SERVER` permission.

#### `onMessageReactionAdd(MessageReactionAddEvent event)` — line 1973

If the emoji is 👍/✅ calls `PhraseLearner.upvoteForUser()`. If 👎/❌ calls
`downvoteForUser()`. Only fires if the message was tracked via `PhraseLearner.trackReply()`.
---

### 3.2 `OnJoin.java`

Handles `GuildMemberJoinEvent`. Sends a configurable welcome message (DM or channel). Channel
controlled by `WELCOME_CHANNEL_ID` env var.

### 3.3 `TempVoiceDelete.java`

Handles `GuildVoiceUpdateEvent`. When a user leaves an auto-created temp voice channel and it is now
empty, deletes it automatically.

### 3.4 `InterestDetector.java` / `RoleAssigner.java`

`InterestDetector` scans messages for topic keywords (mining, trading, pvp, etc.).
`RoleAssigner` matches detected interests to configured Discord roles and assigns them to the user automatically.
---

## 4. Commands Module — `CommandManager.java`

**File:** `src/main/java/Botcode/Commands/CommandManager.java`

### `onSlashCommandInteraction(SlashCommandInteractionEvent event)`

Dispatches by `event.getName()`:

#### `/help`

Calls `HelpBuilder.build(isAdmin)`. Returns a rich embed listing all commands. Admin-only commands
are hidden for non-admin users.

#### `/commodity name:`

1. Resolves name via `StarCitizenDataService.resolveDatasetKey("commodities", input)`.
2. Gets commodity node from the `commodities` dataset.
3. Formats buy/sell locations, price per SCU, route notes.

#### `/mine rock: ship: laser: consumable: operators: format: [field:]`

1. Resolves each parameter using `MiningService.resolve*()` alias/fuzzy methods.
2. Calls the mining analyzer to compute power requirements and yield.
3. Formats as `brief`, `full`, or `specific` output.

#### `/ship name:`

1. Resolves ship name via `StarCitizenDataService.resolveShipName()`.
2. Formats info section: manufacturer, role, size class, crew, cargo, MSRP.
3. Formats stats section: SCM speed, max speed, agility, shields, hull HP.
4. Formats weapons: reads `ship.weapons` array. If per-weapon stats are missing (dps/alpha/rpm = 0),
   falls back to the `weapons` dataset by name lookup.

#### `/weapon name:`

1. Resolves via `resolveDatasetKey("weapons", input)`.
2. Formats info: manufacturer, type, domain (VEHICLE/FPS), size, hardpoint, damage type, store URL.
3. Formats stats: DPS, alpha damage, RPM, range, projectile speed, buy/sell price. Only non-zero
   fields shown.
4. Shows ammo and fire modes if present.
5. If all stats are zero: `*Combat stats not yet available for this weapon.*`

#### `/trade mode: cargo: [commodity:] [count:]`

- `mode=top` — calls `TradeService.topRoutes(cargoScu, null, count)`.
- `mode=commodity` — filters routes for a specific commodity.
- Formats with buy/sell locations, profit per SCU, and total profit per run.

#### `/reportmissing dataset: item: [notes:]`

Calls `MissingDataReportService.reportAndAttemptFix()`. Reports whether item already existed, was
found after refresh, or was queued to `data/manual_update_queue.md` for manual review.

### `onCommandAutoCompleteInteraction(event)`

Provides up to 25 autocomplete suggestions for `/mine`, `/ship`, `/weapon`,
`/commodity` options using `StarCitizenDataService.suggestDatasetKeys()` and
`MiningService.resolve*()` aliases.
---

## 5. Personality Module

### 5.1 `TriggerEngine.java`

Detects the **intent** of a message.
**`enum Intent`** — valid values: `greeting`, `casual_chat`, `question`, `statement`, `command`,
`complaint`, `praise`, `request_action`, `confusion`, `neutral`
**`detectIntent(String message)`** — line 145

1. Lowercase the input.
2. Check `howAreYouPatterns`, `casualChatPatterns`, `correctionPatterns`, `praisePatterns`,
   `botIdentityPatterns`.
3. Check `intentKeywords` map for each intent.
4. Check for question marks / query words.
5. Default to `neutral`.
   **`isFollowUpSignal(String message)`** — line 233
   Checks `followUpPatterns` ("what about", "and that one", "tell me more"). Returns `true` — used
   to keep SC context alive between messages.
   **`isCorrectionSignal(String message)`** — line 255
   Checks correction patterns ("you're wrong", "that's not right"). Returns `true` to trigger
   re-phrasing.

---

### 5.2 `EmotionEngine.java`

**`detectEmotion(String message)`** — line 56
Maps emotion keywords to enum values: `excited`, `frustrated`, `happy`, `sad`, `confused`, `angry`,
`neutral`. Returns `neutral` if no match.
---

### 5.3 `PersonalityEngine.java`

**`decideProfile(Intent intent, Emotion emotion)`** — line 40
Returns a `PersonalityProfile` with:

- `tone` — `PLAYFUL`, `HELPFUL`, `DIRECT`, or `EMPATHETIC`
- `energy` — 1 (low), 2 (medium), 3 (high)
  Used by `SentenceGenerator` and social reply logic to pick the right response style.

---

### 5.4 `SentenceGenerator.java`

Builds template-based responses from personality profile + intent + remembered phrases. Used as the baseline reply before
`AIResponder` enrichment or when AI is disabled.
---

### 5.5 `PhraseLearner.java`

**File:** `src/main/java/Botcode/Personality/PhraseLearner.java`
The learning + scoring system. Persists:

- `data/learned_phrases.json` — global phrases by intent
- `data/user_phrase_profiles.json` — per-user phrases

#### Internal data structures

| Field              | Type                                           | Description                   |
|--------------------|------------------------------------------------|-------------------------------|
| `phraseScores`     | `Map<Intent, Map<String, Integer>>`            | Score per phrase per intent   |
| `userPhraseScores` | `Map<Long, Map<Intent, Map<String, Integer>>>` | Per-user phrase scores        |
| `pendingReplies`   | `Map<Long messageId, PendingReply>`            | Bot replies awaiting feedback |
| `votersByMessage`  | `Map<Long messageId, Set<Long userId>>`        | Prevents double-voting        |

#### `addPhrase(Intent intent, String phrase)` — line 101

1. Normalizes the phrase (trim + dedup via `normalizePhrase()`).
2. Checks `SafetyGuard.isSafeLearningPhrase()`.
3. Rejects command-payload-looking strings.
4. Adds to `phraseScores` at score 1, or increments existing.
5. Calls `saveMaybeAuto()` — writes to disk if 45+ seconds since last save.

#### `removePhrase(Intent intent, String phrase)` — line 118

Finds and removes from `phraseScores`. Triggers auto-save.

#### `addUserPhrase(long userId, Intent intent, String phrase)` — line 131

Same as `addPhrase` but scoped to `userPhraseScores`. Applies `AUTO_USER_PHRASE_MAX_PER_INTENT` (80)
cap via `trimLowestScoreIfNeeded()`.

#### `observeUserStyleMessage(long userId, Intent intent, String message)` — line 164

**Passive auto-learning from ordinary messages:**

1. Checks `BotConfig.AUTO_PERSONAL_PHRASE_LEARNING_ENABLED`.
2. Skips short messages, pure symbols, command-like text.
3. Passes safety check.
4. Calls `addUserPhrase()` at score 1.
5. Enforces bounded growth: trims lowest-score phrase if at capacity.

#### `trackReply(long messageId, Intent intent, String phrase)` — line 191

Registers a bot reply for feedback tracking. Stored in `pendingReplies` with creation timestamp for
TTL (`PENDING_REPLY_TTL_SECONDS` = 24h).

#### `upvote(long messageId)` / `upvoteForUser(long messageId, long userId)` — lines 200, 211

1. Looks up the reply in `pendingReplies`.
2. Increments phrase score in global or per-user scores.
3. Caps at `MAX_PHRASE_LENGTH` (240). Triggers auto-save.

#### `downvote(long messageId)` / `downvoteForUser(...)` — lines 229, 245

Decrements score. If score drops below `MIN_SCORE` (-2), removes the phrase entirely.

#### `getLearnedPhrases(Intent intent)` — line 274

Returns phrases for an intent sorted by score descending (best phrases first).

#### `formatLearningStats()` — line 335

Shows: total global phrases by intent, total user-phrase mappings, pending reply count, and active
learning mode.

#### `load()` / `save()` — lines 425, 479

`loadGlobalPhrases()` reads `data/learned_phrases.json`. `loadUserPhrases()` reads
`data/user_phrase_profiles.json`. `saveMaybeAuto()` throttles writes to at most once per 45 seconds.
---

### 5.6 `PersonaVoice.java`

Forces all outgoing text through the Deadpool persona.
**`enforceDeadpoolVoice(String text)`** — line 32

1. Checks `alreadyInPersona(text)` — returns as-is if already Deadpool-flavored.
2. Randomly prepends one of `DEADPOOL_PREFIXES` (e.g. "Maximum chimichanga advisory —").
   **`gifCaption()`** — line 46
   Returns a random caption from `DEADPOOL_GIF_CAPTIONS` for GIF replies.
   **`alreadyInPersona(String value)`** — line 50
   Checks if the text already starts with a known prefix. Prevents double-wrapping.

---

### 5.7 `SelfFactsRouter.java`

Routes "what are you?" / "who made you?" questions to pre-written self-description responses, bypassing the AI and keeping the Deadpool persona consistent.
---

## 6. AI Module

### 6.1 `AIResponder.java`

Unified facade for all AI-generated text with intelligent context-aware response routing. Returns an `AIResponse` value object.

**`AIResponse` inner class:** holds `text` (String) and `source` (enum: `STAR_CITIZEN`, `SELF_FACTS`, `WEB_LOOKUP`, `PREMIUM`, `LEARNED_PERSONALITY`, `NONE`).

**`resolve(String prompt)`** — Enhanced routing (May 2026+)

The resolve method now intelligently prioritizes response sources based on query intent:

1. **Self-Identity Queries (PRIORITY 1)** — Highest priority for consistent persona
   - Detects: "who are you", "what are you", "your name", "who made you", etc.
   - Routes to `SelfFactsRouter` for consistent Deadpool persona
   - Skips all other sources to maintain character consistency

2. **Explicit Knowledge Queries (PRIORITY 2)** — Web lookup route for factual accuracy
   - Detects: "look up", "define", "how to", "who is", "what is", "search for", etc.
   - Excludes Star Citizen contextual queries (checks for SC keywords)
   - Routes to `WebLookupService` for Wikipedia/DuckDuckGo lookups
   - Useful for facts, definitions, procedures, historical info

3. **Star Citizen Domain Expertise (PRIORITY 3)** — Topic-specific knowledge
   - Routes to `StarCitizenChatService` for ship, weapons, mining, trading, etc.
   - Leverages real game data integration

4. **General Web Lookup (PRIORITY 4)** — Fallback for non-SC questions
   - For topics outside Star Citizen domain
   - Ensures answers are factual and sourced

5. **Premium Provider (PRIORITY 5)** — Optional expansion point
   - Future external AI provider integration
   - Currently placeholder-only

6. **Learned Personality Fallback** — Conversational continuity
   - Uses learned phrases for natural conversation flow
   - Ensures bot always has a response

**Helper Methods:**
- `isSelfIdentityPrompt(String lower)` — Expanded to detect: "created by", "your role", "your purpose", "tell me about yourself"
- `isExplicitNonScLookupPrompt(String lower)` — Detects lookup intent while excluding Star Citizen topics
- `isSCContextualQuery(String lower)` — Prevents routing SC questions to web lookup; checks for: "mining", "cargo", "commodity", "weapon", etc.

---

### 6.2 `WebLookupService.java`

Performs real-time web lookups for knowledge questions.
**`tryLookup(String prompt)`** — line 35

1. `extractLookupQuery()` strips filler words and extracts a clean search query.
2. `lookupWithFallback()` tries Wikipedia first, then DuckDuckGo.
3. Returns trimmed summary text or empty string on failure.
   **`wikipediaLookup(String query)`** — line 106
1. Hits `https://en.wikipedia.org/w/api.php?action=opensearch&search={query}` to resolve the article
   title.
2. Fetches the extract (intro paragraph) from the `query&prop=extracts` endpoint.
3. Returns first paragraph trimmed to `WEB_LOOKUP_MAX_SUMMARY_CHARS`.
   **`duckDuckGoLookup(String query)`** — line 155
   Hits `https://api.duckduckgo.com/?q={query}&format=json&no_html=1`. Extracts `AbstractText` or
   `Answer`.
   **`sendGetWithRetry(String url)`** — line 209
   HTTP GET with up to `WEB_LOOKUP_RETRY_COUNT` retries using Java `HttpClient` with
   `WEB_LOOKUP_TIMEOUT_MS` timeout.

---

## 7. Star Citizen Data Pipeline

The pipeline runs on bot startup and on demand (panel Refresh button):

```
StarCitizenFetcher     →  raw JSON from APIs/local
         ↓
StarCitizenNormalizer  →  stable typed schemas
         ↓
StarCitizenUpdateManager  →  merges with existing + applies guide stats
         ↓  saves to:
data/<dataset>.json  AND  data/starcitizen.db  (SQLite)
         ↓
StarCitizenDataService.reload()  →  refreshes in-memory cache
```

---

### 7.1 `StarCitizenFetcher.java`

Sole responsibility: fetch raw, untransformed data.
**Source priority per dataset (highest to lowest):**

1. Local override file (`SC_<DATASET>_LOCAL_PATH` env)
2. Additional stats file (`SC_<DATASET>_STATS_PATH` env)
3. Environment URL override (`SC_<DATASET>_URL` env)
4. Approved source override (`@bot source` + admin `@bot approvesource`)
5. Hardcoded fallback UEX / Erkul endpoint
6. HTML scraping fallback (`SC_<DATASET>_HTML_URL` env)
   **`fetchWeapons()`** — line 90
   Hits `https://api.erkul.games/live/weapon`. Falls back to UEX category IDs. If both fail,
   attempts `__NEXT_DATA__` extraction from the Erkul HTML page.
   **`fetchShips()`** — line 60
   Hits UEX ships endpoint. Enriches with purchase location data from UEX prices.
   **`fetchEmbeddedStatsArray(urlString, preferredKeys)`** — line 320
   Fetches an HTML page, extracts the `window.__NEXT_DATA__` JSON blob embedded in a `<script>` tag,
   and traverses it to find arrays matching "stat row" heuristics. Used as last-resort fallback.
   **`extractNextDataNode(String html)`** — line 372
   Regex-extracts the `<script id="__NEXT_DATA__">` tag, parses its content as `JsonNode`.
   **`mergeByName(primaryRaw, secondaryRaw, nameKeys)`** — line 671
   Merges two raw arrays by matching on name fields. Combines base data with supplemental stats from
   a secondary source.

---

### 7.2 `StarCitizenNormalizer.java`

Converts raw payloads (which vary by source and version) into stable, typed schemas.
**`normalizeShips(JsonNode raw)`** — line 187
Output:
`{shipName: {info: {...}, stats: {...}, weapons: [...], turrets: [...], missiles: [...], components: {...}}}`

- **info**: name, manufacturer, role, size_class, crew_min/max, cargo_capacity, msrp
- **stats**: speed_scm, speed_max, agility_pitch, shield_hp, hull_hp, fuel_capacity
- **weapons/turrets/missiles**: name, size, count (from hardpoint listings)
  Uses `firstNonBlank()`, `firstPositiveDouble()`, `firstArray()` helpers to probe multiple raw
  field name candidates (UEX field names change between API versions).
  **`normalizeWeapons(JsonNode raw)`** — line 322
  Output:
  `{weaponName: {info: {...}, stats: {...}, ammo: {...}, fire_modes: [...], attachments: [...]}}`
- **info**: manufacturer, type, class, domain, damage_type, hardpoint, buy_location, store_url
- **stats**: dps, burst_dps, alpha_damage, alpha_min/max, rpm, range, projectile_speed, cost_auec
  Uses `classifyWeaponDomain()` to assign `fps` vs `vehicle` based on weapon characteristics.
  **`normalizeCommodities(JsonNode raw)`** — line 25
  Output: `{commodityName: {info: {...}, trade: {...}}}` with buy/sell price ranges and location
  lists.
  **`normalizeComponents(JsonNode raw)`** — line 507
  Output: `{componentName: {info: {...}, stats: {...}, attributes: {...}}}`. Normalizes grade (
  A/B/C) and class via `normalizeGrade()` and `normalizeClass()`.
  **`normalizeMining(JsonNode raw)`** — line 651
  Extracts rock types, mineable materials, mass ranges, and power requirements.
  **`normalizeRefinery(JsonNode raw)`** / **`normalizeRefineryStations(JsonNode raw)`** — lines 681,
  125
  Extracts refinery method names, yield percentages, time estimates, and station location strings.
  **`normalizeSalvage(JsonNode raw)`** — line 743
  Extracts hotspot data: system, location, risk level, material types.
  **`normalizeLocations(JsonNode raw)`** — line 785
  Extracts star system, body type, faction, available services, buy/sell info.

---

### 7.3 `StarCitizenUpdateManager.java`

Orchestrates the full update cycle for a dataset.
**`update(String dataset)` / `updateDetailed(String dataset, boolean force)`** — lines 47, 62
Full step-by-step:

1. **Skip check** — if `shouldSkipLiveFetch(dataset)`, use existing data only.
2. **Age check** — if not `force` and data is recent (within `DEFAULT_REFRESH_INTERVAL_MINUTES` = 30
   days), returns `SKIPPED`.
3. **Fetch** — calls `fetch(dataset)` → `StarCitizenFetcher.fetch<Dataset>()`.
4. **Null guard** — if fetch returns nothing, returns `FAILED`.
5. **Normalize** — calls `normalize(dataset, raw)` → `StarCitizenNormalizer.normalize<Dataset>()`.
6. **Merge with existing** — `mergeShipsWithExisting()` / `mergeWeaponsWithExisting()` to preserve
   previously-known data not available in the current fetch.
7. **Apply guide stats** — for weapons, calls `mergeWeaponsWithGuideStats()`.
8. **Save** — writes to `data/<dataset>.json` AND `StarCitizenDatasetStore.saveDataset()` (SQLite).
9. **Reload** — calls `StarCitizenDataService.reload(dataset)` to push into the live cache.
   **`mergeShipsWithExisting(existing, incoming)`** — line 315
   For each ship in `incoming`: if also in `existing`, copies any non-zero/non-blank fields from
   `existing` into the new record via `mergeInfo()` and `mergeStats()`. Preserves
   weapons/turrets/missile arrays.
   **`mergeWeaponsWithExisting(existing, incoming)`** — line 394
   For each weapon in `incoming`: merges info and stats from `existing` via `mergeWeaponInfo()` and
   `mergeWeaponStats()`. `mergeWeaponStats()` only fills fields where the current value is 0 — never
   overwrites non-zero stats.
   **`mergeWeaponsWithGuideStats(incoming)`** — line 509
   Reads `USER_TEXT_GUIDE.md` line by line. Each tab-delimited row (8+ columns) is parsed as:
   weaponName, dps, burst_dps, alpha_damage, rpm, range, ammo_energy, ammo_physical. Matches weapon
   names by canonical key (lowercase, punctuation-stripped). Only patches fields where the current
   normalized value is 0. Logs count applied.
   **`save(String dataset, JsonNode json)`** — line 297
1. Writes to `data/<dataset>.json` (Jackson pretty-print).
2. Calls `StarCitizenDatasetStore.saveDataset(dataset, json, source, "ok")`.

---

### 7.4 `StarCitizenDataService.java`

In-memory dataset accessor. Full reload order per dataset:

1. **File snapshot** — `data/<dataset>.json`
2. **SQLite** — `StarCitizenDatasetStore.loadDataset(name)`
3. **Packaged resource** — `/starcitizen/<dataset>.json` (bundled in JAR)
   For `ships` and `weapons`, `preferRicherShipData()` / `preferRicherWeaponData()` merge all three
   sources, preserving highest-quality values. For weapons, `applyGuideWeaponStats()` applies
   remaining guide-parsed stats after merge.
   **`reload(String name)`** — line 69
   Refreshes the in-memory cache entry and syncs the `ships`/`weapons`/`components` direct
   references. Called by `UpdateManager` after every save.
   **`resolveDatasetKey(String dataset, String input)`** — line 187
   Fuzzy key resolver:
1. Exact key match.
2. Case-insensitive exact match.
3. Score via `scoreMatch()`: exact (100) → prefix (90) → contains (75) → Levenshtein similarity (
   0-70).
4. Returns `null` if best score < 45.
   **`suggestDatasetKeys(String dataset, String input, int limit)`** — line 220
   Returns top-N fuzzy matches. Used for slash command autocomplete (up to 25 choices).
   **`resolveShipName(String input)`**
   Checks `SHIP_ALIASES` map first ("cutty" → "Cutlass Black", "glad" → "Gladius", etc.), then
   scored fuzzy match. Ambiguity check: returns `null` if top two scores differ by less than 10
   points.

---

### 7.5 `StarCitizenDatasetStore.java`

SQLite persistence. Database: `data/starcitizen.db`.
**Schema:**

```sql
CREATE TABLE IF NOT EXISTS datasets
(
    name
    TEXT
    PRIMARY
    KEY,
    payload
    TEXT,
    source
    TEXT,
    status
    TEXT,
    last_refresh_ms
    INTEGER
)
```

**`saveDataset(dataset, payload, source, status)`** — line 76
`INSERT OR REPLACE` with the full normalized JSON payload and current timestamp.
**`loadDataset(dataset)`** — line 56
`SELECT payload` for the dataset name, deserializes JSON string to `JsonNode`.
**`getLastRefreshMs(dataset)`** — line 94
Returns `last_refresh_ms`. Used by `UpdateManager` to skip refreshes if data is recent.
---

### 7.6 `StarCitizenChatService.java`

Answers natural-language SC questions from chat. Matches topic keywords to the relevant dataset, formats a contextual reply, and returns it to
`Eventlistener` for delivery. Supports: ship lookups, trade queries, mining questions, refinery help, salvage info.
---

### 7.7 `TradeService.java`

**`topRoutes(int cargoScu, String commodityFilter, int limit)`** — line 51
Reads `trade_routes` dataset. Filters by commodity if supplied. Sorts by
`profit_per_scu × cargoScu`. Returns a list of `TradeRoute` records.
**`TradeRoute.format(routes, cargoScu)`** — line 115
Formats as multi-line: commodity, buy location, sell location, profit/SCU, total profit per run.
---

### 7.8 `MiningService.java`

Contains hardcoded maps for all mining data:

- `CONSUMABLES` — name, power modifier, instability modifier
- `LASERS` — name, power range, effective range
- `ROCK_TYPES` — name, power requirement, instability
- `ROCK_MASS` — average mass per rock type
- `SHIP_MINING` — name, max power, number of heads
- `*_ALIASES` maps for fuzzy resolution of user input
  **`isRockViable(requiredPower, ...)`** — line 476
  Core calc: given laser power, consumable modifiers, and rock power requirement, determines if the
  rock can be fractured without detonation.
  **`MiningResult.brief/full/specific(r, ...)`** — lines 657-703
  Format results at different verbosity levels.

---

### 7.9 `MiningSpotRecommender.java`

Reads the
`mining` dataset locations. Scores by ore yield, proximity to trade hubs, and rock quality. Returns sorted location recommendations.
---

### 7.10 `RefineryService.java`

**`analyze(String ore, int rawScu)`** — line 79
Reads `refinery` dataset for all known methods. For each: computes yield SCU, processing time (
minutes), and cost estimate. Returns a `RefineJob` with a list of `MethodResult` entries sorted by
yield.
**`listStations()`** — line 133
Returns formatted station name + services from the `refinery_stations` dataset.
---

### 7.11 `SalvageService.java`

**`listHotspots()` / `listHotspots(String systemFilter)`** — lines 27, 34
Returns salvage location strings from the dataset, optionally filtered by star system.
**`compareShips()`** — line 72
Comparison table of salvage-capable ships: tractor beam power, cargo/hold, SCM speed, features.
**`listMaterials()`** — line 95
Lists all salvageable material types and approximate market values.
**`getTips()`** — line 125
Curated list of salvaging best-practice tips.
---

### 7.12 `MissingDataReportService.java`

Manages missing-data workflows with persistent reporting + recheck:

- Appends every report to `data/missing_reports.json`.
- Attempts immediate resolution through `reportAndAttemptFix(...)`.
- For refresh-supported datasets, tries `StarCitizenUpdateManager.update(dataset)` and rechecks the
  item.
- If still unresolved, appends/upserts manual follow-up entries in `data/manual_update_queue.json`
  and regenerates `data/manual_update_queue.md`.
- Supports explicit command flow and natural-language detector flow in `Eventlistener`.
- Dataset aliases include command-friendly terms like `commodity`, `trade`, `ship`, `weapon`,
  `component`, `refinery`, `salvage`, `location`, `mission`, `item`, and `armor`.

---

### 7.13 `QueryAliasService.java`

Persists user-defined query shortcuts (e.g. "my fave ship" = "Gladius") under a
`query_alias` namespace. Future queries matching the alias key are expanded to the full value before SC lookup.
---

### 7.14 `SourceOverrideService.java`

Handles moderated source overrides through message commands:

- Submit: `@bot source <dataset>: <https-url>` (admin or trusted operator)
- Approve: `@bot approvesource <dataset>` (admin)
- View status: `@bot sources` (admin)

Persistence:

- Stores pending and approved entries in `data/source_overrides.json`.
- Validates URL safety (HTTPS + public/non-local targets) and preflight payload normalization before
  acceptance/approval.
- Approved URL is applied by `StarCitizenFetcher` before hardcoded defaults.

---

## 8. Panel Module

An interactive Discord message with buttons and dropdowns. Users click instead of typing slash
commands for SC lookups.

### 8.1 `PanelModule.java`

`register(ShardManager)` — adds `PanelCommand`, `PanelInteractionHandler`, `PanelAutoPublisher` as
listeners. Also registers the `/panel` slash command.

### 8.2 `PanelCommand.java`

Handles `/panel`. Calls `PanelAutoPublisher.refreshChannelPanel(channel)` to post or edit the panel
in the current channel.

### 8.3 `PanelAutoPublisher.java`

**`onReady(ReadyEvent event)`** — line 28
On every startup: loads all saved panel states from `PanelStateStore`, edits existing panel messages
with fresh embeds. Reposts if message was deleted.
**`onMessageReceived(MessageReceivedEvent event)`** — line 55
When a message arrives in a GUI channel and the panel bump cooldown (
`BOT_PANEL_BUMP_COOLDOWN_SECONDS`, default 30s) has cleared: reposts the panel to keep it at the
bottom.
**`refreshChannelPanel(TextChannel channel)`** — line 67
Public entry point. Posts the main panel embed with action rows.
**`postPanelToChannel(TextChannel, PanelState)`** — line 76
If a message ID is known, edits that message. Otherwise sends a new one. Saves the new message ID
via `PanelStateStore.save()`.

### 8.4 `PanelStateStore.java`

Persists `data/panel_state.json` as `{channelId → messageId}`.
**`loadForChannel(long channelId)`** — Opens the JSON file, returns a `PanelState` with the saved
message ID (or null).
**`save(long channelId, long messageId)`** — Overwrites the entry and writes the map back to disk.

### 8.5 `PanelButtons.java`

Constants-only file. All button/select/modal component IDs defined as `public static final String`.
Also provides factory methods for building menus:
| Method | Menu built |
|---|---|
| `mainPanelRows()` | 5 rows of action buttons (main console) |
| `miningRockMenu()` | Rock list grouped by tier |
| `miningLaserMenu()` | Laser selection with power info |
| `miningConsumableMenu()` | Consumable selection |
| `tradeCargoMenu()` | SCU size selection |
| `tradeModeMenu()` | top vs commodity mode |
| `refineryProfileMenu()` | Ore type selection |
| `weaponDomainMenu()` | Ship vs FPS weapon routing |
| `shipWeaponClassMenu()` | Cannon / Repeater / Gatling / Missile / Other |
| `fpsSystemMenu()` | Ballistic / Energy / Laser / Shotgun |
| `fpsClassMenu()` | Pistol / SMG / Rifle / Sniper / Shotgun |
| `componentCategoryMenu()` | Shield / Cooler / Power Plant / Quantum Drive / etc. |

### 8.6 `PanelInteractionHandler.java`

~2290 lines. Routes every button/dropdown/modal interaction to a specific handler.
**Key handlers:**
| Handler | Trigger | What it does |
|---|---|---|
| `handleMiningResult` | Mining wizard complete | Calls `MiningService.analyze()`, posts embed |
| `handleCommodity(modal)` | Commodity modal submit | Resolves commodity, posts buy/sell embed |
| `handleShip(modal)` | Ship modal submit | Resolves ship, builds info/stats/loadout embed |
| `handleWeapon(modal)` | Weapon name modal | Resolves weapon, posts stats embed |
| `handleWeaponSelect` | Weapon dropdown | Same as modal via dropdown choice |
| `handleFpsWeaponSelect` | FPS weapon dropdown | Shows FPS stats with ammo/fire modes |
| `handleShipWeaponClassSelect` | Ship weapon class dropdown | Filters weapon list to selected
type |
| `handleTrade` | Trade modal | Calls `TradeService.topRoutes()`, posts embed |
| `handleRefineryanalyze` | Refinery modal | Calls `RefineryService.analyze()`, posts results |
| `handleSalvageHotspots` | Button click | Calls `SalvageService.listHotspots()` |
| `handleRefreshData` | Refresh Data button | Calls `StarCitizenUpdateManager.update()` for all
datasets |
| `handleDataStatus` | Data Status button | Shows `last_refresh_ms` per dataset |
**`buildShipLoadout(JsonNode ship)`** — line 1794
Renders the weapons/turrets/missiles section for a ship:

1. Reads `ship.weapons` array.
2. For each weapon: shows name, size, stats.
3. If `dps`, `alpha_damage`, or `rpm` are zero, resolves the weapon from the `weapons` dataset by
   name and fills missing stats.
4. Same fallback for turrets and missiles.
   **`buildWeaponStatsDisplay(JsonNode stats)`** — line 2098
   Iterates all stat fields. Only appends a line if the value is > 0. Weapons with manually-added
   stats show all filled fields; all-zero weapons show nothing.
   **`buildWeaponInfoDisplay(JsonNode info)`** — line 2032
   Shows: manufacturer, type, domain, damage type, hardpoint, size, buy location, store URL.

### 8.7 `PanelEmbeds.java`

Factory for all
`MessageEmbed` objects. Each method takes pre-formatted string args and wraps them in a consistently-styled embed. Key embeds:
`mainPanel()`, `weaponResult()`, `shipResult()`, `miningResult()`, `commodityResult()`,
`tradeResult()`, `dataRefreshResult()`, `notFound()`, `dataError()`.
`addChunkedField()` automatically splits content that exceeds the Discord 1024-char embed field limit into multiple fields.
---

## 9. Utils Module

### 9.1 `BotConfig.java`

All feature flags in one place. Read from env at class load time.
| Flag | Env Key | Default | Purpose |
|---|---|---|---|
| `AI_ENABLED` | `BOT_AI_ENABLED` | `true` | Enable AI response generation |
| `WEB_LOOKUP_ENABLED` | `BOT_WEB_LOOKUP_ENABLED` | `true` | Enable Wikipedia/DDG lookups |
| `AUTONOMOUS_LEARNING_ENABLED` | `BOT_AUTONOMOUS_LEARNING_ENABLED` | `true` | Implicit feedback
learning |
| `PASSIVE_LEARNING_ALL_GUILD_MESSAGES` | `BOT_PASSIVE_LEARNING_ALL_GUILD_MESSAGES` | `false` |
Learn from all guild messages |
| `AUTO_PERSONAL_PHRASE_LEARNING_ENABLED` | `BOT_AUTO_PERSONAL_PHRASE_LEARNING_ENABLED` | `true` |
Auto-learn personal style phrases |
| `PASSIVE_HISTORY_BACKFILL_ENABLED` | `BOT_PASSIVE_HISTORY_BACKFILL_ENABLED` | `false` | Backfill
memory from history |
| `GIF_REPLIES_ENABLED` | `BOT_GIF_REPLIES_ENABLED` | `true` | Allow GIF replies |
| `SHIP_EMBED_REPLIES_ENABLED` | `BOT_SHIP_EMBED_REPLIES_ENABLED` | `true` | Auto-embed ship info on
mentions |

### 9.2 `Env.java`

Thin wrapper: `get(key)` reads from `System.getenv()`, falls back to `System.getProperty()`.
`getOrDefault(key, fallback)` same with a fallback value. Used by `BotConfig` and other classes that
need env values without a Dotenv dependency.

### 9.3 `ChannelConfig.java`

Manages three sets of channel IDs, all persisted to `data/`:
| Set | File | Controls |
|---|---|---|
| `freeChannels` | `data/bot_channels.json` | Channels where the bot responds without being
@mentioned |
| `guiChannels` | `data/gui_channels.json` | Channels where the panel auto-publishes |
| `aiChannels` | in-memory from env | Channels under the AI category for social replies |
**`isFreeChannel(channelId)`** — returns `true` if in the free set.
**`allowChannel(channelId)` / `denyChannel(channelId)`** — add/remove and save.
**`addGuiChannel(channelId)` / `removeGuiChannel(channelId)`** — add/remove and save.
**`bootstrapFromEnvironment()`** — on startup, reads `BOT_FREE_CHANNEL_IDS` and
`BOT_GUI_CHANNEL_IDS` (comma-delimited) and populates the sets.
**`isAiScopedChannel(parentCategoryId, channelId)`** — returns `true` if the channel is under
`AI_CATEGORY_ID` or explicitly in `aiChannels`.

### 9.4 `ConversationMemoryService.java`

Per-user memory system. Persisted to `data/user_memory.json` and `data/conversation_sessions.json`.
**`UserMemory` fields:**
| Field | Description |
|---|---|
| `userId` | Discord user ID |
| `lastIntent` / `lastEmotion` | Most recent detected intent/emotion |
| `lastSeenEpoch` | Unix timestamp of last message |
| `messageCount` | Total messages observed |
| `recentTopics` | Up to 8 recent conversation topics |
| `moduleAffinity` | `{module: count}` — which SC modules the user uses most |
| `preferredDetailLevel` | `brief` / `balanced` / `detailed` |
| `preferredChatStyle` | `friendly` / `direct` / `technical` |
| `lastBotDomain` / `lastBotSubject` | Last SC domain/subject the bot answered |
| `conversationDepthScore` | Increases with long conversations |
**`hasActiveSession(channelId, userId)`** — returns `true` if session hasn't expired (
`SESSION_TTL_SECONDS`).
**`touchSession(channelId, userId)`** — resets expiry timer.
**`recordUserMessage(userId, ...)`** — updates intent, emotion, topics, message count, infers
preferences.
**`inferPreferences(memory, text)`** — detects "brief"/"detailed"/"just tell me" signals and updates
`preferredDetailLevel`/`preferredChatStyle`.
**`buildMemoryPrefix(userId)`** — builds a short context string prepended to AI prompts, e.g.
`[User prefers: brief. Topics: trading, ships. Favorite: trade]`.
**`resolveFollowUpPrompt(userId, text)`** — expands vague follow-ups ("what about that?" → "what
about Gladius stats?") using `lastBotSubject`.
**`recordBotReplyContext(userId, ...)`** — records what the bot just answered (domain, subject,
whether it asked a question).

### 9.5 `SafetyGuard.java`

Content filter for learning and lookup.
**`isDisallowed(String text)`** — returns `true` for instruction injection cues, harmful topic
keywords, and direct block phrases.
**`isSafeLearningPhrase(String text)`** — returns `true` only if phrase is under 240 chars, not
harmful, and not an injection attempt.
**`isDisallowedLookupQuery(String text)`** — returns `true` for risky lookup queries ("how to
hack", "bypass security", etc.).

### 9.6 `HelpBuilder.java`

**`build(boolean isAdmin)`** — constructs the `/help` embed. Admin mode includes:
`channel allow/deny/list`, `guichannel`, `learningstats`, `source approve`,
`reportmissing`. Non-admin mode shows only user commands.
---

## 10. Learning System — How It Works

### Command syntax

**Admin (MANAGE_SERVER or ADMINISTRATOR required):**

```
@bot learn <intent>: <phrase>
@bot learn <intent> = <phrase>
@bot forget <intent>: <phrase>
@bot phrases <intent>
```

**User/personal:**

```
@bot learnme <intent>: <phrase>
@bot learnme <intent> = <phrase>
@bot forgetme <intent>: <phrase>
@bot myphrases <intent>
```

**Natural mention (both work):**

```
@Phoenix Bot learn greeting = Hey, what's up!
@bot learn casual_chat: That hits different, bro.
```

Valid intents: `greeting` `casual_chat` `question` `statement` `command` `complaint` `praise`
`request_action` `confusion` `neutral` `knowledge_query` `hype` `farewell` `how_are_you` `bot_identity`

### Automatic learning pipeline

```
Every message in guild
         ↓
Eventlistener.onMessageReceived()
         ↓
   ┌──────────────────────────────────────────────────────────┐
   │ Passive style observe (if BOT_PASSIVE_LEARNING_* = true) │
   │   PhraseLearner.observeUserStyleMessage()                │
   │   ConversationMemoryService.recordUserMessage()          │
   └──────────────────────────────────────────────────────────┘
         ↓
   ┌──────────────────────────────────────────────────────────┐
   │ Implicit quality feedback                                │
   │   Detects "thanks" / "perfect" / "wrong" etc.           │
   │   Calls PhraseLearner.upvote() / downvote()              │
   └──────────────────────────────────────────────────────────┘
         ↓
   ┌──────────────────────────────────────────────────────────┐
   │ Alias learning                                           │
   │   Detects "X = Y" pattern                               │
   │   Calls QueryAliasService.learnAlias()                   │
   └──────────────────────────────────────────────────────────┘
         ↓
Bot sends reply
         ↓
PhraseLearner.trackReply(messageId, intent, phrase)
         ↓
User reacts 👍 / ✅ / 👎 / ❌
         ↓
onMessageReactionAdd()
   → upvoteForUser() / downvoteForUser()
```

Phrase scores accumulate over time. Higher-scored phrases are returned first by
`getLearnedPhrases()`. Phrases below MIN_SCORE (-2) are auto-removed.

### Enhanced Conversation Awareness (May 2026+)

The bot now maintains sophisticated conversation context through multiple channels:

**Multi-turn conversation tracking:**
- `ConversationMemoryService` tracks per-user conversation history, preferences, and context
- Detects conversation depth and emotional tone to adapt response style
- Remembers user's favorite topics (`moduleAffinity`) for contextual suggestions
- Tracks last bot domain/subject to resolve follow-up references ("what about that?")

**Intelligent response routing:**
- `AIResponder.resolve()` now prioritizes sources based on query intent:
  * **Identity queries** → Consistent Deadpool persona (SelfFactsRouter)
  * **Knowledge queries** → Web lookup for facts (Wikipedia/DuckDuckGo)
  * **Game-specific queries** → Star Citizen expertise (StarCitizenChatService)
  * **General questions** → Web fallback
  * **Conversational continuity** → Learned personality phrases

**Context-aware preference learning:**
- Detects and remembers user's preferred detail level: "brief" / "balanced" / "detailed"
- Learns chat style preference: "friendly" / "direct" / "technical"
- Infers preference from signals like "just tell me", "keep it simple", "detailed analysis"
- Session tracking prevents response overlap and maintains conversation flow

**Reaction-based phrase refinement:**
- When user reacts 👍/✅ to bot replies, that exact phrase gets scored higher for that intent
- When user reacts 👎/❌, the phrase gets scored lower (poor matches auto-removed)
- Over time, bot learns which phrase variations resonate best with that user
- Global phrase scoring also tracks community-wide preferences

---
---

## 11. Docker Files

### `Dockerfile`

Multi-stage build.
**Stage 1 — Maven build:**

```dockerfile
FROM maven:3.9-amazoncorretto-21 AS builder
COPY . /build
WORKDIR /build
RUN mvn package -DskipTests
```

Downloads all dependencies and compiles the JAR. Produces `target/CommsBot-1.0-shaded.jar` — a fat
JAR with all dependencies bundled by the Maven Shade plugin (JDA, Jackson, SQLite JDBC, etc.).
**Stage 2 — Runtime image:**

```dockerfile
FROM amazoncorretto:21-alpine
COPY --from=builder /build/target/CommsBot-1.0-shaded.jar /home/app/bot.jar
CMD ["java", "-jar", "/home/app/bot.jar"]
```

Uses a minimal JRE-only Alpine image. Copies only the final JAR — all build tools are discarded,
making the runtime image ~200 MB instead of ~600 MB.

### `docker-compose.yml`

```yaml
services:
  bot:
    build: .
    env_file: .env
    volumes:
      - ./data:/home/app/data
    restart: unless-stopped
```

**Key points:**

- `env_file: .env` — `.env` is injected as environment variables. The bot reads `TOKEN` and all
  `BOT_*` flags from here.
- `volumes: ./data:/home/app/data` — **critical**. Mounts the host `data/` directory into the
  container so that `learned_phrases.json`, `weapons.json`, `starcitizen.db`, etc. survive container
  restarts and image upgrades. Without this, all runtime data is lost every time the container is
  recreated.
- `restart: unless-stopped` — auto-restarts on failure but respects manual `docker stop`.
  **Common commands:**

```bash
# Start
docker compose up -d
# View logs
docker compose logs -f
# Rebuild after code changes
docker compose up -d --build
# Stop
docker compose down
```

---

## 12. Security & Vulnerability Management (May 2026)

### Dependency Security

**Current status:** All dependencies verified free from known CVEs as of May 12, 2026.

**Recent security updates:**
- Upgraded `org.apache.opennlp:opennlp-tools` from 2.3.1 to 2.5.9
  - **CVE-2026-40682** (CRITICAL) — XML External Entity (XXE) injection fixed
  - **CVE-2026-42027** (CRITICAL) — Arbitrary class instantiation in model loading fixed
  - **CVE-2026-42440** (HIGH) — Out-of-Memory Denial of Service fixed

**Recommended practices for operators:**
1. Run `mvn clean verify` or use external vulnerability scanners periodically
2. Use `maven-dependency-check` plugin to scan new dependencies before merging
3. Monitor GitHub security advisories for Discord JDA library updates
4. Keep Java 21 runtime updated with latest security patches
5. Use `docker scan` or similar tools before deploying container images

**Content filtering for AI & Learning:**
- `SafetyGuard.isDisallowed()` blocks harmful instruction injection and blocked topics
- `SafetyGuard.isSafeLearningPhrase()` validates all user-taught phrases before persistence
- `SafetyGuard.isDisallowedLookupQuery()` prevents risky web lookups
- Web lookups only use HTTPS and public APIs (Wikipedia, DuckDuckGo)

**Learning data integrity:**
- Learned phrases stored in `data/learned_phrases.json` and `data/user_phrase_profiles.json`
- SQLite database (`data/starcitizen.db`) uses parameterized queries
- File permissions: ensure `data/` directory has restricted access in production
- Backup critical files (`learned_phrases.json`, `starcitizen.db`) regularly

---

## 13. Environment Variables Reference

### Required

| Variable | Description       |
|----------|-------------------|
| `TOKEN`  | Discord bot token |

### AI & Web Lookup

| Variable                           | Default | Description                          |
|------------------------------------|---------|--------------------------------------|
| `BOT_AI_ENABLED`                   | `true`  | Enable AI responses                  |
| `BOT_WEB_LOOKUP_ENABLED`           | `true`  | Enable Wikipedia/DuckDuckGo fallback |
| `BOT_WEB_LOOKUP_TIMEOUT_MS`        | `4000`  | Per-request HTTP timeout (ms)        |
| `BOT_WEB_LOOKUP_MAX_SUMMARY_CHARS` | `400`   | Max characters from web summary      |
| `BOT_WEB_LOOKUP_RETRY_COUNT`       | `2`     | HTTP retry attempts                  |

### Learning

| Variable                                              | Default | Description                                |
|-------------------------------------------------------|---------|--------------------------------------------|
| `BOT_AUTONOMOUS_LEARNING_ENABLED`                     | `true`  | Implicit upvote/downvote learning          |
| `BOT_PASSIVE_LEARNING_ALL_GUILD_MESSAGES`             | `false` | Learn phrase style from all guild messages |
| `BOT_AUTO_PERSONAL_PHRASE_LEARNING_ENABLED`           | `true`  | Auto-save signals as personal phrases      |
| `BOT_PASSIVE_HISTORY_BACKFILL_ENABLED`                | `false` | Backfill chat history on startup           |
| `BOT_PASSIVE_HISTORY_BACKFILL_MESSAGES_PER_CHANNEL`   | `100`   | Messages to backfill per channel           |
| `BOT_PASSIVE_HISTORY_BACKFILL_MAX_CHANNELS_PER_GUILD` | `5`     | Max channels to backfill per guild         |
| `BOT_PASSIVE_HISTORY_BACKFILL_MAX_AGE_DAYS`           | `7`     | Max age of messages to backfill            |

### Channels & Behaviour

| Variable                             | Default | Description                                        |
|--------------------------------------|---------|----------------------------------------------------|
| `BOT_FREE_CHANNEL_IDS`               | empty   | Comma-separated channel IDs for free-response mode |
| `BOT_GUI_CHANNEL_IDS`                | empty   | Comma-separated channel IDs for panel auto-publish |
| `AI_CATEGORY_ID`                     | empty   | Discord category ID for AI-scoped social chat      |
| `BOT_REQUIRE_REPLY_CONTEXT`          | `false` | Only respond to explicit replies in free channels  |
| `BOT_AI_SOCIAL_MODE`                 | `true`  | Social conversational replies in AI channels       |
| `BOT_AI_SOCIAL_REPLY_CHANCE_PERCENT` | `68`    | Chance to reply socially in AI channels            |
| `BOT_INTERJECTION_CHANCE_PERCENT`    | `12`    | Chance to interject SC facts unprompted            |
| `BOT_INTERJECTION_COOLDOWN_SECONDS`  | `180`   | Cooldown between interjections per channel         |
| `BOT_GIF_REPLIES_ENABLED`            | `true`  | Allow GIF attachment replies                       |
| `BOT_PANEL_BUMP_COOLDOWN_SECONDS`    | `30`    | Min seconds between panel bumps                    |

### Star Citizen Data Sources

| Variable                    | Description                                                          |
|-----------------------------|----------------------------------------------------------------------|
| `SC_WEAPONS_STATS_URL`      | Primary weapons API (default: `https://api.erkul.games/live/weapon`) |
| `SC_SHIPS_URL`              | Ships API URL override                                               |
| `SC_COMMODITIES_URL`        | Commodities API URL override                                         |
| `SC_WEAPONS_STATS_HTML_URL` | HTML fallback for weapon stats scraping                              |
| `SC_<DATASET>_LOCAL_PATH`   | Path to a local JSON file override for any dataset                   |

---

## 14. Troubleshooting

### Bot not responding in a channel

1. Run `@bot channel list` to see allowed channels.
2. Add it: `@bot channel allow` (admin required).
3. Check `BOT_REQUIRE_REPLY_CONTEXT=false` or @mention the bot directly.

### Learning not working

1. Run `@bot learningstats` to see phrase counts and mode.
2. Confirm `BOT_AUTONOMOUS_LEARNING_ENABLED=true` and/or
   `BOT_PASSIVE_LEARNING_ALL_GUILD_MESSAGES=true`.
3. Check write access to `data/learned_phrases.json` and `data/user_phrase_profiles.json`.

### Weapon stats missing in `/weapon` or panel

1. Confirm `data/weapons.json` has non-zero `dps/alpha_damage/rpm` for the weapon.
2. Force a refresh: click **Refresh Data** in the panel, or restart the bot.
3. Check startup logs for `[UpdateManager] Weapons missing core stats after merge:` count.
4. Confirm `SC_WEAPONS_STATS_URL=https://api.erkul.games/live/weapon` is set.

### Ship info showing as empty

1. `data/ships.json` may have been overwritten by an empty fetch. Check file size.
2. Force refresh from panel or use a local override: `SC_SHIPS_LOCAL_PATH=path/to/ships.json`.

### Panel not appearing

1. Run `/panel` in the target channel.
2. Confirm the channel is in `data/gui_channels.json` or run `@bot guichannel allow`.
3. If the panel message was deleted, remove the stale entry from `data/panel_state.json` and re-run
   `/panel`.

### Docker container exits immediately

1. Check logs: `docker compose logs bot`.
2. Most common: `TOKEN` missing or invalid in `.env`.
3. Confirm `.env` is in the same directory as `docker-compose.yml`.

### Data lost after container restart

Missing the `data/` volume mount. Add to `docker-compose.yml`:

```yaml
volumes:
  - ./data:/home/app/data
```

Then: `docker compose up -d --build`
