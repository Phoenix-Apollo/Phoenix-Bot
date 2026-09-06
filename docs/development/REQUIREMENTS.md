# Phoenix Bot - Requirements Analysis

**Document Date**: July 21, 2026  
**Project**: Phoenix Bot Discord Operations Bot  
**Status**: Active Development / Deployable

---

## 📋 Project Overview

Phoenix Bot is a **Discord operations bot** built in Java (JDA 5.6.1) designed for the Star Citizen gaming community. It provides gaming analytics, interactive UI panels, AI-driven conversational capabilities, and voice channel automation within Discord servers.

**Core Stack**: Java 21 | Maven 3.9 | SQLite | Docker | JDA Framework

---

## ✅ FUNCTIONAL REQUIREMENTS

### 1. SLASH COMMANDS - Star Citizen Tools

| Command | Input Parameters | Output | Purpose |
|---------|-----------------|--------|---------|
| `/commodity <name>` | Commodity name | Pricing, profit routes | Lookup current commodity prices and trading opportunities |
| `/trade <mode> <cargo>` | Route mode, cargo type | Trade routes with profit | Find profitable trade routes for cargo delivery |
| `/mine <rock> <ship> <laser> <consumable> <operators> <format>` | Rock type, ship, laser, consumable, operator count, format | Mining yield analysis | Calculate optimal mining yields based on inputs |
| `/ship <name>` | Ship name | Ship specs, hardpoints, cargo | Display detailed ship specifications and loadouts |
| `/weapon <name>` | Weapon name | DPS, stats, damage type | Show weapon damage-per-second and combat stats |
| `/panel` | None | Interactive GUI | Deploy multi-channel interactive operations console |

### 2. AI CONVERSATIONAL SYSTEM

**Trigger Points:**
- `@bot` mention in any channel
- Message in designated AI category (default: Phoenix Industries)
- Free-response channels (enabled via `@bot allowchannel`)

**Conversation Features:**
- Multi-message conversation history per user (ConversationMemoryService)
- Group context awareness for multi-user conversations (ChannelConversationService)
- Response generation with 8 distinct personalities
- Emotional state tracking (mood, intent recognition)
- Passive message history backfill from Discord (60 messages default, 14-day window)

**Personality System (7 Base Tones + Deadpool Soul):**

Deadpool is the CORE VOICE/SOUL that permeates all responses. Each interaction blends a base tone with Deadpool's irreverent, fourth-wall-breaking energy:

1. **Helpful Deadpool** - Informative + chimichanga chaos
2. **Excited Deadpool** - Enthusiastic + snarky chaos
3. **Casual Chat Deadpool** - Relaxed + irreverent humor
4. **Sarcastic Deadpool** - Witty + meta-commentary
5. **Academic Deadpool** - Technical detail + merc-with-a-mouth flavor
6. **Empathetic Deadpool** - Compassionate + chaos-clerk energy
7. **Comforting Deadpool** - Supportive + fourth-wall breaking

The Deadpool overlay is ALWAYS present, injecting personality prefixes ("Maximum chimichanga advisory", "Fourth-wall report", "Merc-with-a-mouth update") into responses based on message intent and tone selection.

**Learning System:**
- User upvote/downvote reactions reinforce personality patterns
- Phrase learning via `@bot learn <phrase>` / `@bot forget <phrase>`
- Autonomous learning enabled/disabled via configuration
- Passive learning of successful conversational patterns

### 3. INTERACTIVE GUI PANEL

**Deployment:**
- Single or multiple channels (via `PANEL_CHANNEL_IDS` or `PANEL_DEFAULT_CHANNEL_ID`)
- Multi-server deployment supported (test + production)

**Interactive Elements:**
- Button controls (Refresh Data, Data Status, Admin functions)
- Paged selectors for large datasets (commodity lists, ship databases, etc.)
- Dynamic channel management (`@bot addguichannel` / `@bot removeguichannel`)
- Real-time data synchronization from cache

**Panel Features:**
- Commodity price browser
- Ship specifications viewer
- Weapon comparison tools
- Trade route calculator
- Mining analyzer
- Admin refresh/status controls

### 4. VOICE CHANNEL AUTOMATION

**Join-to-Create Feature:**
- Voice channel named "Join to Create" generates temporary rooms on user join
- Automatic cleanup of empty temporary channels
- Temporary channels auto-deleted when all members leave

**Voice Management:**
- Welcome messages on member join
- Static channel preservation (channels with `~` in name)
- Configurable category for temporary rooms (Events & Operations)

### 5. ADMIN & MANAGEMENT COMMANDS

| Command | Access | Function |
|---------|--------|----------|
| `@bot allowchannel` | Admin | Enable AI responses in specific channel |
| `@bot learn <phrase>` | Admin | Add phrase to learning system |
| `@bot forget <phrase>` | Admin | Remove phrase from learning system |
| `@bot addguichannel <id>` | Admin | Add channel to panel deployment |
| `@bot removeguichannel <id>` | Admin | Remove channel from panel deployment |
| Panel: Refresh Data | Admin | Force-update all datasets |
| Panel: Data Status | Admin | Display cache state and last refresh time |

### 6. DATA MANAGEMENT - Star Citizen Content Pipeline

**Supported Datasets:**
- Commodities (prices, trading info)
- Trade routes (source → destination → profit)
- Ships (specs, hardpoints, weapons)
- Weapons (DPS, damage type, handling)
- Components (module specifications)
- Mining (rock types, yields)
- Refinery (refinement data)
- Refinery stations (location data)
- Salvage (loot tables)
- Locations (game world locations)

**Data Load Priority:**
1. SQLite database (`SC_DB_PATH`, default: `data/starcitizen.db`)
2. JSON snapshots in `data/<dataset>.json`
3. Packaged resources in `src/main/resources/starcitizen/*.json`

**Data Update Behavior:**
- Monthly refresh cycle (configurable: `SC_REFRESH_INTERVAL_MINUTES`)
- Skip remote fetch if local data still fresh
- Force refresh available via admin panel action
- Fallback to existing data if remote fetch fails
- Dual-write persistence: JSON snapshot + SQLite row
- Support for local game file overrides via environment variables

**Local-First File Overrides:**
- `SC_SHIPS_FILE` / `SC_SHIPS_STATS_FILE`
- `SC_WEAPONS_FILE` / `SC_WEAPONS_STATS_FILE`
- `SC_COMPONENTS_FILE` / `SC_COMPONENTS_STATS_FILE`
- `SC_COMMODITIES_FILE`
- `SC_TRADE_ROUTES_FILE`
- `SC_MINING_FILE`
- `SC_REFINERY_FILE`
- `SC_REFINERY_STATIONS_FILE`
- `SC_SALVAGE_FILE`
- `SC_LOCATIONS_FILE`

**Fuzzy Lookup:**
- Approximate string matching for better UX
- Handles typos and partial name searches

### 7. MESSAGE ROUTING & RESPONSE LIFECYCLE

**Incoming Message Processing:**
1. Capture all guild messages
2. Route based on channel category or mention
3. Apply safety filtering (SafetyGuard)
4. Build conversation context (history + group context)
5. Route to appropriate AI personality
6. Generate response with sentiment/emotion tracking
7. Post to Discord with reaction collectors for learning

**Learning Integration:**
- Reaction-based reinforcement (👍 upvote / 👎 downvote)
- Phrase learning system updates weights
- Personality pattern reinforcement over time

---

## ⚙️ NON-FUNCTIONAL REQUIREMENTS

### 1. PERFORMANCE

- **Response Latency**: Sub-5 second response time for slash commands
- **AI Response Time**: 1-3 seconds for conversational AI responses
- **Panel Load Time**: Instant (pre-cached data)
- **Concurrent Users**: Support 100+ concurrent panel interactions per server
- **Throughput**: Handle 500+ messages per minute per guild
- **Memory Footprint**: Run on systems with ≥512MB heap allocation

### 2. SCALABILITY

- **Multi-Server Deployment**: Support panel deployment across multiple Discord servers simultaneously
- **Dataset Size**: Handle Star Citizen datasets with 1000+ items per category
- **Message History**: Backfill 60 messages × 10 channels × 14-day window per guild without performance degradation
- **Horizontal Scaling**: Architecture supports multiple bot instances (via shared SQLite)

### 3. RELIABILITY & AVAILABILITY

- **Graceful Degradation**: Continue operating with stale data if remote fetch fails
- **Data Persistence**: Maintain SQLite backup of all normalized datasets
- **Bot Recovery**: Auto-restart capability via process manager/Docker
- **Uptime Target**: 99% availability during Discord service availability
- **Data Integrity**: Transactional SQLite writes for consistency

### 4. SECURITY

- **Token Management**: Discord bot token stored in `.env` (never in code)
- **Input Validation**: SafetyGuard filters user input before processing
- **Access Control**: Admin-only commands require role/permission verification
- **Data Privacy**: Conversation history stored locally (not sent externally)
- **Dependency Security**: Pinned vulnerable dependency versions in pom.xml (jackson-core override)

### 5. LEGAL & COMPLIANCE

- **Privacy Policy**: Publish and maintain a clear privacy policy for message storage, learning, and retention
- **Terms of Service**: Define acceptable use, abuse handling, and moderation boundaries for Discord usage
- **Consent Tracking**: Record consent where user data is stored or learned from
- **Deletion Rights**: Support deletion/export requests for stored conversation history where applicable
- **Third-Party Terms**: Review and comply with API, model, and data source licenses before deployment
- **Jurisdiction Handling**: Document data retention, hosting location, and applicable legal jurisdiction

### 6. MAINTAINABILITY

- **Code Organization**: Modular package structure (Commands, StarCitizen, AI, Panel, Utils, Listeners)
- **Configuration**: Centralized BotConfig with environment variable support
- **Logging**: SLF4J-based logging with configurable levels
- **Documentation**: Comprehensive README, implementation guides, testing procedures
- **Extensibility**: Plugin-style command registration system

### 7. TECHNOLOGY STACK & COMPATIBILITY

- **Language**: Java 21 (OpenJDK Temurin)
- **Build Tool**: Maven 3.9+
- **Database**: SQLite 3.x
- **Discord API**: JDA 5.6.1
- **JSON Processing**: Jackson 2.18.3+ (with security patches)
- **NLP**: Apache OpenNLP 2.5.9
- **Environment**: dotenv-java 3.0.0
- **Docker**: Multi-stage build with `maven:3.9-eclipse-temurin-21` + `openjdk:21-jdk-slim`
- **OS**: Windows, Linux, macOS (via Docker)

### 8. DEPLOYABILITY

- **Build Artifact**: Shaded JAR (all dependencies bundled: `CommsBot-1.0-shaded.jar`)
- **Containerization**: Docker support with persistent volume mounting for data
- **Startup Time**: <10 seconds from JAR execution to ready state
- **Configuration**: Single `.env` file for all environment-specific settings
- **Data Migration**: Automatic dataset loading from SQLite/JSON/resources on startup

### 9. TESTING & VERIFICATION

- **Pre-Test Checklist**: Environment verification, dependency confirmation
- **Procedures**: 8 comprehensive test procedures documented
- **Smoke Tests**: Quick startup and command registration verification
- **Load Tests**: Message throughput and panel interaction capacity verification
- **Integration Tests**: End-to-end command → response → panel update workflows
- **Performance Baseline**: Documented performance metrics for regression detection

### 10. DATA CONSISTENCY & INTEGRITY

**Database Schema:**
```
Table: datasets
  - dataset (TEXT PRIMARY KEY) — dataset identifier
  - payload (TEXT) — normalized JSON content
  - refreshed_at (INTEGER) — epoch milliseconds
  - source (TEXT) — data origin (local/remote/packaged)
  - status (TEXT) — processing state
```

- Atomic transactions for dataset updates
- Rollback on partial failure
- Snapshot versioning for data recovery

### 11. OPERATIONAL REQUIREMENTS

- **Startup Dependencies**: Discord bot token (required), internet connectivity (for remote fetches)
- **Runtime Dependencies**: Active Discord connection, SQLite database access, local data directory
- **External Dependencies**: Star Citizen game data sources (erkul.games API)
- **Monitoring**: Startup logs, command execution logs, error logs
- **Debugging**: Environment variable configuration for log level override

---

## 📊 REQUIREMENTS BY PRIORITY

### CRITICAL (Must Have)
- ✅ Slash command framework (commodity, trade, mine, ship, weapon, panel)
- ✅ AI conversational system with personality
- ✅ SQLite data persistence
- ✅ Discord integration via JDA
- ✅ Admin command access control

### HIGH (Should Have)
- ✅ Interactive GUI panel with buttons/selectors
- ✅ Voice channel automation (join-to-create)
- ✅ Multi-server deployment
- ✅ Fuzzy lookup for datasets
- ✅ Reaction-based learning system

### MEDIUM (Nice to Have)
- ✅ 8-persona personality system
- ✅ Conversation memory service
- ✅ Passive message history backfill
- ✅ Local file override support
- ✅ Docker containerization

### LOW (Future Enhancement)
- ⏳ External AI provider integration (infrastructure exists)
- ⏳ Advanced NLP sentiment analysis
- ⏳ Database clustering (multi-instance)

---

## 🔗 DEPENDENCIES & INTEGRATIONS

### Internal Components
- **CommandManager**: Routes slash commands to handlers
- **StarCitizenDataService**: In-memory dataset cache
- **AIResponder**: Main AI conversation router
- **PersonalityEngine**: Manages 8 distinct personas
- **PanelModule**: Interactive UI panel deployment
- **TempVoiceDelete**: Voice channel automation listener

### External Services
- Discord API (JDA client)
- Star Citizen game data (via erkul.games)
- System environment variables
- Local filesystem (data storage)

### Build & Deployment
- Maven Central Repository (dependencies)
- Docker Hub (base images)
- Local Docker daemon (containerization)

---

## 📝 ASSUMPTIONS & CONSTRAINTS

1. **Discord Server Setup**: Assumes specific channel/category structure (Phoenix Industries, Events & Operations, Join to Create)
2. **Data Freshness**: Monthly refresh assumes game patches align with refresh cycle
3. **Local-First Philosophy**: Assumes network availability not guaranteed; offline operation preferred
4. **Single JVM Process**: Current architecture assumes single bot instance (SQLite concurrency limits)
5. **Memory Availability**: Assumes 512MB+ heap for dataset caching in memory
6. **Timezone Independence**: All timestamps stored as epoch milliseconds (UTC)
7. **English Language**: AI responses and dataset naming assume English language context
8. **Star Citizen Focus**: Dataset pipeline and commands hardcoded for Star Citizen game data

---

## 🎯 SUCCESS CRITERIA

- ✅ All documented commands execute without error
- ✅ AI responses generated within 3 seconds
- ✅ Panel renders in <1 second with pre-cached data
- ✅ Data refreshes complete within 30 seconds
- ✅ No SQL errors on concurrent operations
- ✅ Learning system correctly reinforces positive responses
- ✅ Voice automation creates/deletes temporary channels reliably
- ✅ Multi-server panel deployment maintains consistency
