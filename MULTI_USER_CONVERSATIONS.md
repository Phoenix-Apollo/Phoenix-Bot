# Multi-User Conversation System

**Version:** Phoenix Bot 1.0  
**Date:** May 12, 2026  
**Status:** Active

## Overview

The Phoenix Bot now intelligently detects and participates in multi-user channel conversations. Instead of only responding when directly mentioned or when in active session with a single user, the bot can now "listen in" on group discussions and contribute contextually relevant feedback, suggestions, and insights.

## Features

### 1. **Multi-User Conversation Detection**

The bot tracks conversations happening in channels and identifies when multiple users are actively discussing topics. It maintains awareness of:

- **Active participants**: Users engaging in real-time conversation
- **Conversation topics**: What's being discussed (mining, trading, ships, weapons, etc.)
- **Sentiment analysis**: Detecting questions, problems, positive energy, or neutral banter
- **Message history**: Recent messages for context understanding
- **Conversation windows**: 5-minute rolling windows to identify active discussions

### 2. **Contextual Interjection**

When appropriate conditions are met, the bot naturally injects feedback or suggestions:

**Triggers for engagement:**
- 2+ unique users actively participating
- Recent message activity (within 5 minutes)
- Presence of natural engagement signals (questions, problems, opinions)
- Sufficient time since last interjection (respects channel cooldown)

**Probability modulation:**
- Base interjection chance: ~25% for casual conversation
- Higher probability (~35%+) when:
  - Questions are asked
  - Someone describes a problem
  - Opinion prompts are present
  - Longer, substantive messages are shared

### 3. **Sentiment-Based Response Generation**

Responses are dynamically generated based on conversation sentiment analysis:

#### Question Responses
- **Star Citizen specific**: Tailored help for ships, weapons, mining, trading questions
- **General questions**: Encouraging further discussion or exploring alternative angles
- Example: *"Good question — I can help narrow this down. What's your main priority here?"*

#### Problem Responses
- Empathetic acknowledgment + practical troubleshooting steps
- Suggests checking common issues before escalation
- Example: *"Yikes. Have you tried the standard troubleshooting path yet, or shall we brainstorm?"*

#### Positive Responses
- Enthusiastic reinforcement to build momentum
- Motivational framing to encourage continued engagement
- Example: *"Yes! That energy. Don't forget to capitalize on it while momentum's high."*

#### Neutral/Banter Responses
- Constructive additions to ongoing discussion
- Introduces complementary perspectives
- Example: *"Interesting angle. Has anyone considered alternatives?"*

### 4. **Learning Integration**

Multi-user conversational exchanges are tracked and enhance the bot's learning:

- Phrases from group interactions are learned via the existing `PhraseLearner` system
- Group conversation context feeds into personality development
- Bot learns which types of interjections resonate with the community
- Upvotes/downvotes on group replies refine future responses

### 5. **Channel Configuration**

The system is fully aware of existing channel configuration:

- Respects "free-response" channel settings
- Works seamlessly in AI-scoped channels
- Honors channel-specific conversation TTL settings (if configured)
- Maintains independent conversation tracking per channel

## Implementation Details

### Core Service: `ChannelConversationService`

Located at: `src/main/java/Botcode/Utils/ChannelConversationService.java`

**Key Methods:**
- `recordChannelMessage()` — Logs user messages to channel conversation tracker
- `shouldBotInterjact()` — Determines if conditions warrant bot participation
- `analyzeChannelConversation()` — Returns full conversation context
- `pruneOldConversations()` — Cleans up stale data (prevents memory leaks)
- `getRecentConversationSnippet()` — Extracts recent messages for context

**Data Structure: `ConversationContext`**
```java
public transient context = {
  - channelId: long
  - participantUserIds: Set<Long>
  - topicsDiscussed: Set<String>
  - messageCount: int
  - lastMessageTime: long
  - dominantTopic: String
  - recentMessages: List<String>
  - sentiment: String ("question", "problem", "positive", "neutral")
}
```

### Integration Points: `Eventlistener.java`

1. **Message Recording** (line ~670):
   - Every user message is recorded in the channel conversation service
   - Topic detection via existing ConversationMemoryService
   - Non-intrusive logging (doesn't affect message routing)

2. **Multi-User Response Logic** (line ~752):
   - Called after social channel reply logic
   - Only executes in AI-scoped channels
   - Returns `true` if interjection was attempted

3. **Response Generation** (lines ~1780-2000):
   - `tryMultiUserConversationInterjection()` — Main entry point
   - `generateContextualGroupResponse()` — Routes based on sentiment
   - `generateQuestionResponse()`, `generateProblemResponse()`, etc. — Sentiment-specific helpers

## Configuration

### Environment Variables

```bash
# (No new env vars required — uses existing channel config)
# Respects existing:
# - BOT_AI_CATEGORY_ID — scope for free-response mode
# - BOT_CHANNEL_SESSION_TTLS — per-channel conversation TTLs
# - BOT_AI_SOCIAL_MODE — enables/disables all social engagement
```

### Runtime Behavior

The system automatically:
1. Detects multi-user conversations
2. Analyzes sentiment and context
3. Generates appropriate responses
4. Respects channel permissions and configuration
5. Learns from user feedback (reactions)
6. Prunes old data to manage memory

## Example Interactions

### Scenario 1: Mining Advice Question
```
User A: anyone know the best mining location for low risk?
User B: depends on your ship right?
User A: running a MOLE
[BOT INTERJECTION]: Good question — I can help narrow this down. What's your main priority here?
```

### Scenario 2: Troubleshooting
```
User A: mining keeps freezing
User B: same issue here
[BOT INTERJECTION]: Yikes. Have you tried the standard troubleshooting path yet, or shall we brainstorm?
```

### Scenario 3: Trade Route Discussion
```
User A: trying to optimize my route
User B: I've been running lucrative recently
[BOT INTERJECTION]: Interesting angle. Has anyone considered alternatives?
```

## Benefits

1. **Natural Integration**: Bot feels like a community member, not just a command bot
2. **Contextual Help**: Assistance appears when actually needed
3. **Learning Reinforcement**: Observes peer interactions to improve responses
4. **Community Engagement**: Encourages continued conversation and knowledge sharing
5. **Efficient Participation**: Avoids spam through intelligent cooldowns and probability gating

## Performance Impact

- **Memory**: ~50 KB per active channel (50 message ring buffer + metadata)
- **CPU**: Minimal — sentiment analysis is simple heuristics (no ML inference)
- **Latency**: No impact on existing message routing

Conversation data is automatically pruned after 10 minutes of inactivity per channel.

## Troubleshooting

### Bot not interjecting in channels
1. Verify AI social mode is enabled: `BOT_AI_SOCIAL_MODE=true`
2. Check that channel is AI-scoped or in free-response list
3. Ensure 2+ unique users are actively conversing
4. Confirm recent message activity (within 5 minutes)

### Bot interjecting too frequently
1. Reduce interjection cooldown (default: 180 seconds between interjections per channel)
2. Adjust `INTERJECTION_CHANCE_PERCENT` environment variable
3. Ensure channel is configured as intended

### Memory usage concerns
1. Pruning runs automatically — no manual intervention needed
2. Max 50 messages per channel in memory (configurable in `ChannelConversationService`)
3. Old channels naturally decay as conversation window expires

## Future Enhancements

Potential improvements planned:
- Topic clustering to group related discussions
- User reputation weighting (prioritize responses to experienced users)
- Temporal response throttling (avoid back-to-back interjections)
- Custom per-guild interjection policies
- Integration with thread/thread-comment sentiment tracking

## Related Features

- [Learning Pipeline](./RECENT_CHANGES_SUMMARY.txt) — Autonomous phrase learning
- [Personality Engine](./RECENT_CHANGES_SUMMARY.txt) — Tone/emotion matching
- [Channel Configuration](./SETUP_AND_BUILD.md) — Free-response/AI channel setup

