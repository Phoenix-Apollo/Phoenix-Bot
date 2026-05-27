# Phoenix Bot - Implementation Guide (Current State)

Last Updated: May 12, 2026

## What It Is

Phoenix Bot is a Discord operations bot with:

- Star Citizen command + panel tools
- AI chat/personality features
- Voice automation (join/create + cleanup)
- Local-first data pipeline with persistent SQLite storage

## Core Runtime Components

- `CommsBot`: startup, command registration, listener wiring
- `CommandManager`: slash commands (`/ship`, `/weapon`, `/mine`, `/trade`, etc.)
- `PanelModule` + `PanelInteractionHandler`: interactive panel buttons/select menus
- `StarCitizenDataService`: in-memory dataset cache and lookup APIs
- `StarCitizenUpdateManager`: fetch -> normalize -> save -> reload
- `StarCitizenFetcher`: local-file first, remote fallback data retrieval
- `StarCitizenDatasetStore`: SQLite persistence for normalized datasets

## AI Conversational Framework

- `AIResponder` (`Botcode.AI.AIHook`): Main router for AI response generation
- **Personality Engine** (`Botcode.AI.Personality`): 8 distinct personas (CasualChat, Excited, Helpful, Shy, Sarcastic, Academic, Empathetic, Deadpool)
- **AI Utilities** (`Botcode.AI.AIUtils`):
  - `ConversationMemoryService`: tracks conversation history and context per user
  - `ChannelConversationService`: monitors multi-user conversations for contextual group interjection
  - `BotConfig`: centralized configuration and .env management
  - `SafetyGuard`: input validation and safety filtering
- **Learning System**: PhraseLearner tracks user reactions (upvotes/downvotes) and reinforces successful personality patterns
- **Sentence Generation**: SentenceGenerator creates contextual responses with personality variation (40% unique phrasing) and Deadpool marker injection

## Data Architecture (Now)

Load priority at runtime:

1. SQLite dataset store (`SC_DB_PATH`, default `data/starcitizen.db`)
2. JSON snapshots in `data/<dataset>.json`
3. Packaged resources in `src/main/resources/starcitizen/*.json`

Update behavior:

- Monthly refresh by default (`SC_REFRESH_INTERVAL_MINUTES=43200`)
- Skip remote fetch if local data is still fresh
- Force refresh supported for admin/panel actions
- Keep existing snapshot/DB data if remote fetch fails
- Dual-write on update: JSON snapshot + SQLite row

## Supported Datasets

- commodities
- trade_routes
- ships
- weapons
- components
- mining
- refinery
- refinery_stations
- salvage
- locations

## Local-First Input Overrides

Optional env vars can point to local raw/game files.

Primary datasets:

- `SC_SHIPS_FILE`
- `SC_WEAPONS_FILE`
- `SC_COMPONENTS_FILE`
- `SC_COMMODITIES_FILE`
- `SC_TRADE_ROUTES_FILE`
- `SC_MINING_FILE`
- `SC_REFINERY_FILE`
- `SC_REFINERY_STATIONS_FILE`
- `SC_SALVAGE_FILE`
- `SC_LOCATIONS_FILE`

Stats enrich files:

- `SC_SHIPS_STATS_FILE`
- `SC_WEAPONS_STATS_FILE`
- `SC_COMPONENTS_STATS_FILE`

## Database Schema

Table: `datasets`

- `dataset` TEXT PRIMARY KEY
- `payload` TEXT (normalized JSON)
- `refreshed_at` INTEGER (epoch ms)
- `source` TEXT
- `status` TEXT

## Current Ship/Weapon UX Notes

- Ship weapons panel now uses practical class matching by name patterns
- Weapon stats rendering includes expanded fields when data exists
- If upstream enrichment is unavailable, existing local values are preserved

## Multi-User Conversation Features

- `ChannelConversationService` tracks active group conversations in real-time
- Bot detects when 2+ users are discussing topics in a channel
- Intelligently interjects with contextual feedback/suggestions based on sentiment:
  - **Questions**: Helpful perspective and follow-ups
  - **Problems**: Empathetic troubleshooting and practical suggestions
  - **Positive energy**: Reinforcement and momentum-building
  - **Neutral banter**: Constructive additions and alternative angles
- Respects channel permissions and maintains conversation context windows
- Multi-user interactions feed back into the PhraseLearner system for personality refinement

## Operations

Standard build:

```powershell
cd C:\Users\badbo\Phoenix-Bot-development
.\mvnw.cmd package -DskipTests
```

Force data refresh on next run (optional):

```powershell
$env:SC_FORCE_REFRESH = "true"
```

## AI Configuration

See `.env.example` and `BotConfig.java` for AI-specific variables:
- `DEADPOOL_PERSONALITY_ENABLED`: Enable/disable Deadpool persona injection (default: true)
- `AI_LEARNING_ENABLED`: Track user reactions for personality refinement (default: true)
- `MULTI_USER_INTERJECTION_ENABLED`: Enable contextual group conversation participation (default: true)

## Configuration Reference

See `.env.example` for the complete active variable set.

## Scope of This Document

This file intentionally describes current behavior only. It is not a historical changelog.
