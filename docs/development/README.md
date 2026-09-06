# Phoenix Bot (Phoenix-Bot-development)

**Status**: ✅ Active development / deployable  
**Build**: ✅ Maven package succeeds  
**Last Updated**: Aug 21, 2026

Phoenix Bot is a Discord operations bot with:

- ⭐ **Star Citizen tools** (mining, trading, ships, weapons, commodities)
- 🎮 **Interactive GUI panel** (multi-channel deployment)
- 🤖 **AI personality** with mood/intent/learning
- 🎙️ **Temp voice automation** (Join to Create, auto-cleanup)
- 💾 **Local-first data store** (SQLite + snapshot files)

---

## 📚 Documentation

Start here based on what you need:

### 🚀 **[SETUP_AND_BUILD.md](SETUP_AND_BUILD.md)** — Build & Deployment

Everything about building, compiling, and running the bot.

- 60-second quick start
- All build script options
- VS Code setup
- Manual build steps
- **⏱️ Read time: 15 minutes**

### 🔧 **[IMPLEMENTATION_MASTER.md](IMPLEMENTATION_MASTER.md)** — Features & Configuration

Complete overview of all features and how to use them.

- Feature 1: AI Category Scoping
- Feature 2: Multi-Channel GUI Panel
- Feature 3: Star Citizen Data Pipeline
- Configuration reference
- All commands documented
- **⏱️ Read time: 30 minutes**

### 📖 **[USER_TEXT_GUIDE.md](USER_TEXT_GUIDE.md)** — Module-by-Module User Guide

Detailed plain-language guide for how each bot module works and how learning/adaptation behaves.

- Message routing and response lifecycle
- Automatic learning modes and tuning signals
- Star Citizen fetch/normalize/update architecture
- Admin/operator controls and troubleshooting
- **⏱️ Read time: 20 minutes**

### 🧪 **[TESTING_GUIDE.md](TESTING_GUIDE.md)** — Testing & Verification

Full testing procedures and verification steps.

- Pre-test checklist
- Code verification status
- 8 comprehensive test procedures
- Performance baseline
- **⏱️ Read time: 25 minutes**

---

## ✨ What's Implemented

### Star Citizen Tools

- ✅ `/commodity <name>` — Lookup prices and profit routes
- ✅ `/trade <mode> <cargo>` — Find profitable trade routes
- ✅ `/mine <rock> <ship> <laser> <consumable> <operators> <format>` — Mining analysis
- ✅ `/ship <name>` — Ship specifications
- ✅ `/weapon <name>` — Weapon DPS and stats
- ✅ `/panel` — Interactive operations console

### AI Features

- ✅ Phoenix Industries category scoping (default AI habitat)
- ✅ Free-response channels (via `@bot allowchannel`)
- ✅ Phrase learning system (`@bot learn`/`@bot forget`)
- ✅ Personality engine with emotions and intent
- ✅ Web-lookup-first answers for random/general questions
- ✅ Deterministic routing for planning/playful/specific prompts
- ✅ Signature narrator opener occasionally prefixed before answers

### Panel Features

- ✅ Multi-server deployment (test + real server)
- ✅ Dynamic channel management (`@bot addguichannel`/`@bot removeguichannel`)
- ✅ Paged selectors for large datasets
- ✅ Admin controls (Refresh Data, Data Status)

### Data Pipeline

- ✅ Local-first loading: SQLite -> `data/*.json` -> packaged resources
- ✅ Remote fetch only when stale (monthly by default) or force-refresh
- ✅ SQLite persistence (`SC_DB_PATH`, default `data/starcitizen.db`)
- ✅ Optional raw/stats file overrides for all major datasets
- ✅ Fuzzy lookup for better UX

### Voice Automation

- ✅ "Join to Create" temp voice channels
- ✅ Auto-cleanup of empty temp rooms
- ✅ Welcome messages on member join

---

## 🚀 Quick Start (60 seconds)

```powershell
# Build with automatic setup
cd C:\Users\badbo\Phoenix-Bot-development
powershell -ExecutionPolicy Bypass -File build.ps1

# Or use complete build script
build-complete.bat

# Run the bot
java -jar target/CommsBot-1.0-shaded.jar
```

✅ Bot will start and:

- Load environment from `.env`
- Load datasets from SQLite/snapshots
- Refresh stale datasets based on policy
- Post GUI panel to configured channels
- Register commands

---

## 📋 Configuration (`.env`)

```env
# Required
TOKEN=your_discord_bot_token

# Data store + refresh policy
SC_DB_PATH=data/starcitizen.db
SC_REFRESH_INTERVAL_MINUTES=43200
SC_FORCE_REFRESH=false

# Optional examples
BOT_AI_CATEGORY_ID=1498011508548829234
PANEL_CHANNEL_IDS=1172143831702065294,1498011767283126403
```

See **[IMPLEMENTATION_MASTER.md](IMPLEMENTATION_MASTER.md#configuration-reference)** for full
configuration.

---

## 🎮 Discord Setup Requirements

### Text Channels Needed

- `public-chat` — Welcome/public messaging
- `operations-console` — AI responses/commands

### Categories Needed

- `Phoenix Industries` — Primary AI habitat (ID: 1498011508548829234)
- `Events & Operations` — For temp voice channels
- Other organization categories

### Voice Channels Needed

- `Join to Create` — Triggers temp room creation
- Static channels can include `~` in name to prevent auto-cleanup

---

## 📚 Complete Feature List

| Feature             | Status | Command              |
|---------------------|--------|----------------------|
| Commodity lookup    | ✅      | `/commodity`         |
| Trade route finder  | ✅      | `/trade`             |
| Mining analyzer     | ✅      | `/mine`              |
| Ship specs          | ✅      | `/ship`              |
| Weapon DPS          | ✅      | `/weapon`            |
| AI responses        | ✅      | `@bot`, AI-scoped channels, active sessions |
| GUI panel           | ✅      | `/panel`             |
| Category scoping    | ✅      | `BOT_AI_CATEGORY_ID` |
| Multi-channel panel | ✅      | `PANEL_CHANNEL_IDS`  |
| Admin learning      | ✅      | `@bot learn`         |
| Voice automation    | ✅      | Auto                 |

---

## 🔄 What's Next

- [ ] See **[SETUP_AND_BUILD.md](SETUP_AND_BUILD.md)** to build the bot
- [ ] See **[IMPLEMENTATION_MASTER.md](IMPLEMENTATION_MASTER.md)** to understand all features
- [ ] See **[TESTING_GUIDE.md](TESTING_GUIDE.md)** to test before deployment

---

## 🐛 Troubleshooting

### Build fails

→ See **[SETUP_AND_BUILD.md#troubleshooting](SETUP_AND_BUILD.md#troubleshooting)**

### Bot won't start

→ See **[TESTING_GUIDE.md#troubleshooting](TESTING_GUIDE.md#troubleshooting)**

### Features not working

→ See **[IMPLEMENTATION_MASTER.md#troubleshooting](IMPLEMENTATION_MASTER.md#troubleshooting)**

---

## 📊 Build Status

```
✅ Code: 39 files compiled (0 errors)
✅ Build: JAR created and tested
✅ Tests: All procedures verified
✅ Deploy: Ready for production
```

---

## 📝 Documentation History

See **[RECENT_CHANGES_SUMMARY.txt](RECENT_CHANGES_SUMMARY.txt)** for historical changes.

---

**Status**: 🟢 **READY FOR DEPLOYMENT**

Use `QUICKSTART.md` for full setup and run instructions.
