# Phoenix Bot - Improvements & Roadmap

**Document Date**: July 21, 2026  
**Status**: Pre-Production (Phase 2 Planning)  
**Purpose**: Identify security vulnerabilities, compliance needs, and feature enhancements before live deployment

---

## 🔒 SECURITY HARDENING (CRITICAL)

### 1. Data Encryption at Rest

**Current State**: SQLite database stored unencrypted on disk

**Requirements**:
- ✅ Encrypt all sensitive data in SQLite database (conversation history, user preferences)
- ✅ Use AES-256 encryption for data payloads
- ✅ Implement key rotation mechanism
- ✅ Secure key storage (NOT in .env or code)

**Implementation**:
- Add SQLCipher library (SQLite encryption): `net.zetetic:sqlcipher:4.5.7`
- Replace standard SQLite connection with encrypted variant
- Generate/rotate encryption keys via secure key management service
- Document key backup/recovery procedures

**Timeline**: CRITICAL - Must complete before production

**Files to Modify**:
- `StarCitizenDatasetStore.java` - Implement encrypted connection
- `BotConfig.java` - Add DB_ENCRYPTION_KEY configuration
- pom.xml - Add SQLCipher dependency

---

### 2. Data Retention & Automatic Purging

**Current State**: No data retention policy; conversation history and logs persist indefinitely

**Requirements**:
- ✅ Auto-delete conversation history after 60 days
- ✅ Archive historical data if compliance requires longer retention
- ✅ Allow admin override for important conversations
- ✅ Provide audit trail of what was deleted

**Implementation**:
- Create `DataRetentionService` to run daily cleanup
- Add `retention_policy` column to conversation history table
- Implement scheduled task (Quartz or Java Timer) for deletion
- Log all deletions to secure audit table

**Configuration** (add to .env):
```env
BOT_DATA_RETENTION_DAYS=60
BOT_ARCHIVE_ENABLED=true
BOT_ARCHIVE_PATH=data/archive/
BOT_RETENTION_CLEANUP_HOUR=2
```

**Timeline**: HIGH - Deploy with encryption

---

### 3. Conversation History Privacy

**Current State**: All user messages stored without anonymization or redaction

**Requirements**:
- ✅ PII detection and redaction (emails, phone numbers, Discord IDs)
- ✅ Opt-in consent model for message storage
- ✅ User right to request deletion of their conversation data
- ✅ GDPR compliance (Right to be Forgotten)

**Implementation**:
- Create `PIIRedactor` utility to mask sensitive data before storage
- Add per-guild/per-user privacy consent flag
- Implement `DELETE /user/:id/conversations` admin endpoint
- Audit logging for all data access/deletion

**Timeline**: HIGH - Legal requirement before production

---

### 4. API Key & Token Security

**Current State**: Tokens stored in `.env`; potential exposure in logs

**Requirements**:
- ✅ Never log token/API key values
- ✅ Rotate Discord bot token on compromise detection
- ✅ Implement rate limiting on failed token attempts
- ✅ Use secure secrets manager (not .env in production)

**Implementation**:
- Add `SecretsMasker` to redact tokens from all log output
- Implement HashiCorp Vault integration for production secrets
- Add bot token rotation mechanism in admin commands
- Monitor for token leakage in error messages

**Files to Modify**:
- `Env.java` - Add secret masking
- `CommsBot.java` - Implement token rotation
- `BotConfig.java` - Support Vault provider

**Timeline**: CRITICAL - Before first deployment

---

### 5. Input Validation & Injection Prevention

**Current State**: SafetyGuard filters basic harmful content; no SQL injection/command injection protection

**Requirements**:
- ✅ Parameterized queries for all database operations
- ✅ Command injection prevention in shell operations
- ✅ XSS prevention in web lookup responses
- ✅ Rate limiting on user commands (prevent spam attacks)

**Implementation**:
- Audit all SQL queries in `StarCitizenDatasetStore` - ensure parameterization
- Implement `RateLimiter` per user/guild
- Sanitize web lookup HTML responses before parsing
- Add input length limits

**Timeline**: HIGH - Implement before beta

---

### 6. Admin Access Control Hardening

**Current State**: Admin commands checked against role; no audit trail

**Requirements**:
- ✅ Multi-factor approval for sensitive operations (refresh data, delete cache)
- ✅ Audit log all admin actions with timestamp + actor
- ✅ Role-based permission model (owner, admin, moderator)
- ✅ Require confirmation for destructive operations

**Implementation**:
- Create `AdminAuditLog` table tracking all sensitive operations
- Implement `AdminActionRequester` for multi-step approval workflow
- Add role hierarchy verification in `CommandManager`
- Store audit logs separately from main database

**Timeline**: MEDIUM - Pre-production

---

## 🛡️ COMPLIANCE & LEGAL

### GDPR Compliance

**Required Implementations**:
1. ✅ Data Subject Access Request (DSAR) handling
2. ✅ Right to be Forgotten (data deletion on demand)
3. ✅ Data Processing Agreement (DPA) documentation
4. ✅ Privacy Policy updated with bot data collection disclosure
5. ✅ Consent mechanism for EU users

**Action Items**:
- Create `/request-data-export` command for users
- Create `GDPRDataExporter` service
- Document bot's data collection in privacy policy
- Implement consent banners for EU guilds

**Timeline**: CRITICAL if deployed in EU

---

## 🔐 DEPENDENCY SECURITY

### Vulnerable Dependency Review

**Current Issues** (from pom.xml):
- ✅ Jackson 2.18.6 (pinned to fix vulnerability from 2.18.3)
- ⚠️ OpenNLP 2.5.9 - Check for CVEs
- ⚠️ JDA 5.6.1 - Monitor for security updates
- ⚠️ dotenv-java 3.0.0 - No secrets management; upgrade to Vault

**Required Actions**:
- Run `mvn dependency:check` monthly for vulnerabilities
- Set up Dependabot alerts on GitHub
- Create security update policy (auto-patch non-breaking)
- Regular CVE scanning in CI/CD pipeline

**Timeline**: ONGOING

---

## 📊 OPERATIONAL IMPROVEMENTS

### 1. Monitoring & Observability

**Current State**: No centralized logging or monitoring

**Needed**:
- ✅ Centralized log aggregation (ELK Stack, Datadog, or CloudWatch)
- ✅ Performance metrics (response time, command latency)
- ✅ Error tracking with Sentry integration
- ✅ Health check endpoint for uptime monitoring
- ✅ Discord bot status page

**Implementation**:
- Add Micrometer metrics library
- Implement `/health` command for monitoring
- Integrate Sentry for error tracking
- Add structured logging (JSON format for parsing)

**Timeline**: MEDIUM - Deploy in first 30 days

---

### 2. Database Performance & Optimization

**Current Issues**:
- Single SQLite database may bottleneck under load
- No query optimization or indexing strategy documented
- No connection pooling

**Improvements**:
- ✅ Add indexes on frequently queried columns (dataset, refreshed_at)
- ✅ Implement connection pooling (HikariCP)
- ✅ Optimize Star Citizen dataset queries with cache TTL
- ✅ Consider read replicas for high-traffic guilds
- ✅ Add query performance logging

**Timeline**: MEDIUM - After initial deployment

---

### 3. Backup & Disaster Recovery

**Current State**: No documented backup strategy

**Requirements**:
- ✅ Daily automated SQLite backups to secure storage (S3, GCS)
- ✅ Point-in-time recovery capability
- ✅ Backup encryption with separate key
- ✅ Backup integrity validation
- ✅ Disaster recovery runbook

**Implementation**:
- Create `BackupService` with scheduled daily exports
- Integrate with AWS S3 or GCS for offsite storage
- Document recovery procedures
- Test recovery quarterly

**Timeline**: HIGH - Before production

---

## 🚀 FEATURE ENHANCEMENTS

### 1. Premium AI Integration

**Current State**: Claude/OpenAI hooks exist but not implemented

**Implementation Plan**:
- ✅ Implement Claude API integration (Anthropic)
- ✅ Add fallback chain: Claude → Web Lookup → Learned Personality
- ✅ Cost tracking per guild (optional billing model)
- ✅ Rate limiting per guild for API costs
- ✅ Configuration per guild (enable/disable premium)

**Configuration** (add to .env):
```env
BOT_AI_PREMIUM_ENABLED=true
BOT_AI_PREMIUM_PROVIDER=CLAUDE
BOT_CLAUDE_API_KEY=sk-ant-xxx
BOT_CLAUDE_MODEL=claude-3-sonnet
BOT_PREMIUM_RATE_LIMIT_PER_GUILD=100
```

**Timeline**: HIGH - After security hardening

---

### 2. Multi-Language Support

**Current State**: English-only responses and dataset names

**Improvements**:
- ✅ Detect user language preference from Discord settings
- ✅ Translate AI responses to user's language
- ✅ Support international Star Citizen data (price feeds per region)
- ✅ Localize command help text

**Implementation**:
- Add message translation via Claude/external API
- Create `LanguagePreferenceStore` per user
- Implement translation caching

**Timeline**: LOW - Post-launch enhancement

---

### 3. Voice Channel Enhancements

**Current State**: Basic join-to-create and auto-cleanup

**Improvements**:
- ✅ Persistent voice category preferences per guild
- ✅ Voice activity logging (who joined/left, duration)
- ✅ AFK member auto-disconnect after 30 minutes
- ✅ Voice channel naming templates (e.g., "Mining Squad {#}")
- ✅ Automated voice statistics/leaderboards

**Timeline**: MEDIUM - Phase 2

---

### 4. Panel UI Enhancements

**Current State**: Basic button/selector interactions

**Improvements**:
- ✅ Add pagination for large datasets (commodities, ships)
- ✅ Search/filter functionality in panel
- ✅ Custom embed coloring by category
- ✅ Mobile-optimized layouts
- ✅ Dark mode support

**Timeline**: MEDIUM - Q3 2026

---

### 5. Learning System Enhancements

**Current State**: Basic phrase learning with upvote/downvote

**Improvements**:
- ✅ A/B testing framework for personality variants
- ✅ Conversation outcome tracking (user satisfaction)
- ✅ Adaptive learning that adjusts tone per user preference
- ✅ Context-aware response selection (avoid repeating same phrase)
- ✅ Export learned patterns for analysis

**Timeline**: LOW - Advanced feature

---

## ⚡ PERFORMANCE OPTIMIZATIONS

### 1. Caching Strategy

**Current State**: In-memory dataset cache; no TTL management

**Improvements**:
- ✅ Implement Redis cache layer for distributed deployments
- ✅ Add cache invalidation on data refresh
- ✅ LRU eviction policy for memory efficiency
- ✅ Cache statistics and hit-rate monitoring

**Timeline**: MEDIUM - Scale phase

---

### 2. Response Time Optimization

**Current Targets**:
- ✅ AI response: <2 seconds (currently 1-3s)
- ✅ Command response: <1 second (panel actions)
- ✅ Dataset lookup: <500ms

**Improvements**:
- Profile current response times
- Identify bottlenecks (web lookup vs. local)
- Implement lazy-loading for panel datasets
- Add concurrent request batching

**Timeline**: MEDIUM - After launch

---

### 3. Memory Management

**Current State**: Single JVM heap allocation; no memory monitoring

**Improvements**:
- ✅ Implement memory usage tracking
- ✅ Alert on heap utilization >80%
- ✅ GC tuning for consistent pause times
- ✅ Memory leak detection tools
- ✅ Document recommended heap sizes

**Timeline**: MEDIUM - Operational tuning

---

## 📋 SCALABILITY ROADMAP

### Phase 1: Single Instance (Current)
- ✅ SQLite local database
- ✅ Single bot instance
- ✅ Local file storage

**Status**: Deployable now

### Phase 2: Multi-Instance (Q3 2026)
- ✅ Shared SQLite with connection pooling
- ✅ Redis for distributed caching
- ✅ Shared data directory (NFS or S3)
- ✅ Load balancing

**Effort**: 2-3 weeks

### Phase 3: Enterprise Scale (Q4 2026)
- ✅ PostgreSQL instead of SQLite
- ✅ Kafka for event streaming
- ✅ Kubernetes deployment
- ✅ Geo-distributed instances

**Effort**: 6-8 weeks

---

## 🧪 TESTING IMPROVEMENTS

### Missing Coverage

**Currently Lacking**:
- ✅ Unit tests for AI response chains
- ✅ Integration tests for panel interactions
- ✅ Load tests (500+ concurrent commands)
- ✅ Security tests (SQL injection, XSS)
- ✅ Data retention cleanup verification

**Timeline**: HIGH - Add before beta

---

## 📝 DOCUMENTATION GAPS

**Needed**:
- ✅ API documentation for custom integrations
- ✅ Troubleshooting guide for common issues
- ✅ Data schema documentation
- ✅ Deployment runbook (staging → production)
- ✅ Emergency incident response procedures

**Timeline**: MEDIUM - During beta

---

## ⚖️ LEGAL & COMPLIANCE NFRs

**Current State**: Compliance items are present, but legal obligations are not tracked as a distinct non-functional requirement.

**Requirements**:
- ✅ Publish and maintain a privacy policy covering bot data collection and storage
- ✅ Publish terms of service / acceptable use rules for Discord interaction
- ✅ Record consent and deletion requests where user data is stored
- ✅ Review third-party API and model usage terms before production
- ✅ Clarify content ownership, data retention, and jurisdiction handling

**Implementation**:
- Add a compliance review checkpoint before launch approval
- Keep legal policy links surfaced in admin docs and onboarding
- Log consent, export, and deletion actions for auditability
- Review source licenses and API terms for all external data/model providers

**Timeline**: CRITICAL - Must be resolved before public deployment

---

## 🗺️ DEPLOYMENT ROADMAP

### Pre-Launch Checklist (CRITICAL)

- [ ] Implement data encryption (SQLCipher)
- [ ] Add data retention cleanup (60-day purge)
- [ ] Security audit of all admin commands
- [ ] API key/token protection (secret masking)
- [ ] Legal/compliance review
- [ ] Backup & disaster recovery system
- [ ] Monitoring & alerting setup
- [ ] Load testing (100+ concurrent users)
- [ ] Security penetration testing
- [ ] Privacy policy updated
- [ ] Database indexes optimized
- [ ] Rate limiting implemented

### Launch Window: August 2026

### Post-Launch (30 Days)

- [ ] Monitor error rates and performance
- [ ] Gather user feedback
- [ ] Implement quick fixes from user reports
- [ ] Add phase 2 monitoring/observability

### Q3 2026 (60-90 Days)

- [ ] Premium AI (Claude) integration
- [ ] Performance optimizations
- [ ] Multi-language support research

---

## 💰 COST CONSIDERATIONS

### Current (Free/Low-Cost)
- SQLite (free)
- Local file storage (free)
- JDA (free)
- Web lookup (free, but limited)

### With Premium AI (Claude)
- **Estimated**: $50-200/month depending on usage
- Usage per guild configurable
- Optional per-guild billing model

### With Enterprise Scale
- PostgreSQL hosting: $50-300/month
- Redis cache: $20-100/month
- CloudWatch/Datadog: $100-500/month
- Backup storage: $10-50/month

**Total Estimated**: $200-1000/month at scale

---

## 📅 PRIORITY MATRIX

| Feature | Impact | Effort | Priority |
|---------|--------|--------|----------|
| Data Encryption | Critical | High | P0 |
| Data Retention Policy | Critical | Medium | P0 |
| Backup & Recovery | Critical | Medium | P0 |
| API Key Protection | Critical | Low | P0 |
| GDPR Compliance | Critical | Medium | P0 |
| Legal/Compliance Review | Critical | Medium | P0 |
| Monitoring & Alerts | High | Medium | P1 |
| Premium AI Integration | High | High | P1 |
| Input Validation Audit | High | High | P1 |
| Admin Audit Logging | High | Medium | P1 |
| Database Optimization | Medium | High | P2 |
| Multi-Language | Medium | High | P2 |
| Voice Enhancements | Medium | Medium | P2 |
| Panel UI Updates | Medium | Medium | P2 |
| Advanced Learning | Low | High | P3 |

---

## 🎯 SUCCESS METRICS

**Before Production**:
- ✅ 100% encryption of data at rest
- ✅ Automated data deletion working
- ✅ Zero unmasked secrets in logs
- ✅ All admin actions audited

**After Launch (30 Days)**:
- ✅ <2 second avg response time
- ✅ <0.1% error rate
- ✅ 99% uptime
- ✅ Zero security incidents
- ✅ ≥90% GDPR compliance score

**Q3 (60-90 Days)**:
- ✅ Premium AI active with <10% API cost overage
- ✅ Monitoring dashboard showing all metrics
- ✅ Multi-instance deployment tested

---

**Document Status**: 🟡 DRAFT - Ready for review before implementation  
**Next Step**: Prioritize and create implementation tickets
