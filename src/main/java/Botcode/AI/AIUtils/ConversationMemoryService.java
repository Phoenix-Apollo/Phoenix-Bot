package Botcode.AI.AIUtils;

import Botcode.AI.Personality.EmotionEngine;
import Botcode.AI.Personality.TriggerEngine;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Stores lightweight per-user memory and short-lived channel conversations.
 *
 * <p>Data is persisted in the local data folder so memory survives restarts.
 */
public class ConversationMemoryService {

  private static final String MEMORY_FILE = "data/user_memory.json";
  private static final String SESSIONS_FILE = "data/conversation_sessions.json";

  // Keep conversation continuity short to avoid channel spam.
  // Override globally with BOT_SESSION_TTL_SECONDS (e.g. 300),
  // or per channel with BOT_CHANNEL_SESSION_TTLS="1234567890:600,987654321:180".
  private static final long SESSION_TTL_SECONDS = readLongEnv("BOT_SESSION_TTL_SECONDS", 5 * 60L);
  private static final Map<Long, Long> CHANNEL_SESSION_TTLS =
      parseChannelSessionTtls(System.getenv("BOT_CHANNEL_SESSION_TTLS"));
  private static final int MAX_TOPICS_PER_USER = 8;
  private static final int MAX_RECENT_ENTITIES_PER_USER = 6;

  /**
   * Maximum number of user memory records kept in-process before oldest are evicted.
   */
  private static final int MAX_USER_MEMORIES = 10_000;

  private static final ObjectMapper MAPPER =
      new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

  // key: channelId:userId -> epoch seconds
  private static final Map<String, Long> activeSessions = new ConcurrentHashMap<>();

  // key: userId -> memory
  private static final Map<Long, UserMemory> userMemories = new ConcurrentHashMap<>();

  private static final Set<String> TOPIC_STOPWORDS =
      Set.of(
          "the", "and", "for", "with", "that", "this", "have", "you", "your", "from", "about",
          "what", "when", "where", "which", "there", "their", "they", "then", "were", "will",
          "would", "could", "should", "into", "just", "like", "really", "okay", "yeah", "hello",
          "thanks", "please", "help", "bot");

  private static final Map<String, List<String>> MODULE_KEYWORDS =
      Map.of(
          "mining", List.of("mining", "laser", "rock", "fracture", "quant"),
          "trade", List.of("trade", "cargo", "route", "commodity", "profit"),
          "ships", List.of("ship", "fleet", "hull", "crew"),
          "weapons", List.of("weapon", "dps", "gun", "cannon", "laser repeaters"),
          "refinery", List.of("refinery", "refine", "yield", "method"),
          "salvage", List.of("salvage", "rmc", "scrape", "reclaimer", "vulture"));

  static {
    load();
  }

  public static boolean hasActiveSession(long channelId, long userId) {
    pruneSessions();
    String key = sessionKey(channelId, userId);
    Long last = activeSessions.get(key);
    if (last == null) {
      return false;
    }
    return now() - last <= ttlForChannel(channelId);
  }

  public static void touchSession(long channelId, long userId) {
    activeSessions.put(sessionKey(channelId, userId), now());
    saveSessions();
  }

  public static void recordUserMessage(
      long userId,
      String username,
      String text,
      TriggerEngine.Intent intent,
      EmotionEngine.Emotion emotion) {
    UserMemory memory = userMemories.computeIfAbsent(userId, k -> new UserMemory());
    evictOldestMemoriesIfNeeded();
    memory.userId = userId;
    memory.username = username;
    memory.lastIntent = intent != null ? intent.name() : "NEUTRAL";
    memory.lastEmotion = emotion != null ? emotion.name() : "NEUTRAL";
    memory.lastSeenEpoch = now();
    memory.messageCount += 1;
    memory.lastUserMessage = text;

    inferPreferences(memory, text);

    String topic = extractTopic(text);
    if (!topic.isEmpty()) {
      if (memory.recentTopics == null) {
        memory.recentTopics = new ArrayList<>();
      }
      Deque<String> queue = new ArrayDeque<>(memory.recentTopics);

      // De-duplicate while preserving recent order.
      queue.remove(topic);
      queue.addFirst(topic);
      while (queue.size() > MAX_TOPICS_PER_USER) {
        queue.removeLast();
      }
      memory.recentTopics = new ArrayList<>(queue);
    }

    saveMemory();
  }

  /**
   * Stores light context about the bot's last answer so short follow-ups can inherit
   * domain/subject.
   */
  public static void recordBotReplyContext(
      long userId, String domain, String subject, boolean askedFollowUpQuestion) {
    UserMemory memory = userMemories.computeIfAbsent(userId, k -> new UserMemory());
    memory.userId = userId;
    memory.lastSeenEpoch = now();

    if (domain != null && !domain.isBlank()) {
      memory.lastBotDomain = domain.toLowerCase(Locale.ROOT).trim();
    }
    if (subject != null && !subject.isBlank()) {
      String normalized = subject.trim();
      memory.lastBotSubject = normalized;
      if (memory.recentEntityMentions == null) {
        memory.recentEntityMentions = new ArrayList<>();
      }
      Deque<String> queue = new ArrayDeque<>(memory.recentEntityMentions);
      queue.remove(normalized);
      queue.addFirst(normalized);
      while (queue.size() > MAX_RECENT_ENTITIES_PER_USER) {
        queue.removeLast();
      }
      memory.recentEntityMentions = new ArrayList<>(queue);
    }
    if (askedFollowUpQuestion) {
      memory.lastBotQuestionEpoch = now();
    }

    saveMemory();
  }

  /**
   * Expands short follow-up prompts using previous reply context.
   */
  public static String resolveFollowUpPrompt(long userId, String text) {
    if (text == null || text.isBlank()) {
      return text;
    }
    UserMemory memory = userMemories.get(userId);
    if (memory == null) {
      return text;
    }

    String normalized = text.trim();
    String lower = normalized.toLowerCase(Locale.ROOT);
    if (isGreetingOrAckTurn(lower)) {
      return normalized;
    }

    boolean followUp =
        TriggerEngine.isFollowUpSignal(lower)
            || (memory.lastBotQuestionEpoch > 0
            && (now() - memory.lastBotQuestionEpoch) <= 8 * 60L);
    if (!followUp) {
      return normalized;
    }

    String domain = memory.lastBotDomain == null ? "" : memory.lastBotDomain.trim();
    String subject = memory.lastBotSubject == null ? "" : memory.lastBotSubject.trim();

    boolean hasDomain =
        hasAny(
            lower,
            "trade",
            "commodity",
            "mine",
            "mining",
            "ship",
            "weapon",
            "component",
            "refinery",
            "salvage",
            "location",
            "mission",
            "armor",
            "armour");
    boolean referencesPriorThing =
        hasAny(
            lower,
            "that",
            "that one",
            "this one",
            "it",
            "those",
            "same one",
            "same ship",
            "same weapon");

    StringBuilder expanded = new StringBuilder();
    if (!hasDomain && !domain.isBlank()) {
      expanded.append(domain).append(' ');
    }
    if (referencesPriorThing
        && !subject.isBlank()
        && !lower.contains(subject.toLowerCase(Locale.ROOT))) {
      expanded.append(subject).append(' ');
    }
    expanded.append(normalized);
    return expanded.toString().trim();
  }

  /**
   * Returns inferred preferences for a user, or defaults when unknown.
   */
  public static UserPreferences getUserPreferences(long userId) {
    UserMemory memory = userMemories.get(userId);
    if (memory == null) {
      return new UserPreferences("balanced", "friendly", "none");
    }
    return new UserPreferences(
        safeValue(memory.preferredDetailLevel),
        safeValue(memory.preferredChatStyle),
        topModule(memory));
  }

  /**
   * Returns a brief personalized prefix when we have meaningful memory context AND the user hasn't
   * been seen for at least 2 hours.
   *
   * <p>Returning an empty string means no prefix should be added.
   */
  public static String buildMemoryPrefix(long userId) {
    UserMemory memory = userMemories.get(userId);
    if (memory == null) {
      return "";
    }

    // Only greet returning users if:
    //   (a) they have had at least 3 prior messages (established user), and
    //   (b) they haven't been seen in the last 2 hours (meaningful absence).
    if (memory.messageCount < 3) {
      return "";
    }
    long twoHoursAgo = now() - (2 * 60 * 60);
    if (memory.lastSeenEpoch > twoHoursAgo) {
      return "";
    }

    String name = safeName(memory.username);

    // Vary the return message so it doesn't sound robotic on every visit.
    int bucket = (int) (memory.messageCount % 5);
    switch (bucket) {
      case 0:
        return "Welcome back, " + name + "!";
      case 1:
        return "Good to see you again, " + name + ".";
      case 2:
        return "Hey " + name + ", welcome back!";
      case 3:
        return "Good to have you back, " + name + ".";
      default:
        return "Hey " + name + "!";
    }
  }

  public static List<String> getRecentTopics(long userId) {
    UserMemory memory = userMemories.get(userId);
    if (memory == null || memory.recentTopics == null) {
      return Collections.emptyList();
    }
    return Collections.unmodifiableList(memory.recentTopics);
  }

  /**
   * Returns recently-mentioned entities from bot replies for contextual follow-ups.
   */
  public static List<String> getRecentEntityMentions(long userId) {
    UserMemory memory = userMemories.get(userId);
    if (memory == null || memory.recentEntityMentions == null) {
      return Collections.emptyList();
    }
    return Collections.unmodifiableList(memory.recentEntityMentions);
  }

  /**
   * Returns the user's latest message text (best-effort) for continuity hints.
   */
  public static String getLastUserMessage(long userId) {
    UserMemory memory = userMemories.get(userId);
    if (memory == null || memory.lastUserMessage == null) {
      return "";
    }
    return memory.lastUserMessage;
  }

  /**
   * Returns a bounded depth score inferred from message complexity/preferences. 0 means no signal;
   * higher values indicate user prefers richer back-and-forth.
   */
  public static int getConversationDepthScore(long userId) {
    UserMemory memory = userMemories.get(userId);
    if (memory == null) {
      return 0;
    }
    return Math.max(0, Math.min(20, memory.conversationDepthScore));
  }

  /**
   * Returns a compact memory summary for a given user.
   */
  public static String memorySummary(long userId) {
    UserMemory memory = userMemories.get(userId);
    if (memory == null) {
      return "I don't have memory for you yet. Chat with me a bit first.";
    }

    String topics =
        (memory.recentTopics == null || memory.recentTopics.isEmpty())
            ? "none yet"
            : String.join(", ", memory.recentTopics);

    return "**Your memory profile**\n"
        + "--¢ Name: "
        + safeName(memory.username)
        + "\n"
        + "--¢ Messages seen: "
        + memory.messageCount
        + "\n"
        + "--¢ Last intent: `"
        + safeValue(memory.lastIntent)
        + "`\n"
        + "--¢ Last emotion: `"
        + safeValue(memory.lastEmotion)
        + "`\n"
        + "--¢ Last bot domain: `"
        + safeValue(memory.lastBotDomain)
        + "`\n"
        + "--¢ Last bot subject: `"
        + safeValue(memory.lastBotSubject)
        + "`\n"
        + "--¢ Preferred detail: `"
        + safeValue(memory.preferredDetailLevel)
        + "`\n"
        + "--¢ Preferred style: `"
        + safeValue(memory.preferredChatStyle)
        + "`\n"
        + "--¢ Favorite module: `"
        + topModule(memory)
        + "`\n"
        + "--¢ Recent topics: "
        + topics;
  }

  /**
   * Removes a user's long-term memory and any active sessions for that user.
   */
  public static boolean forgetUser(long userId) {
    boolean removedMemory = userMemories.remove(userId) != null;

    Set<String> remove = new HashSet<>();
    String suffix = ":" + userId;
    for (String key : activeSessions.keySet()) {
      if (key.endsWith(suffix)) {
        remove.add(key);
      }
    }
    for (String key : remove) {
      activeSessions.remove(key);
    }

    if (removedMemory || !remove.isEmpty()) {
      saveMemory();
      saveSessions();
    }
    return removedMemory || !remove.isEmpty();
  }

  /**
   * Lightweight learning path for peer-to-peer chat where the bot intentionally stays silent.
   *
   * <p>Updates preference/topic memory without forcing a bot reply or session touch. Saves every
   * few passive messages to reduce disk churn in busy channels.
   */
  public static void recordPassiveSignal(long userId, String username, String text) {
    recordHistoricalSignal(userId, username, text, now());
  }

  /**
   * Learning path for historical backfill. Ignores stale duplicates using message epoch.
   */
  public static void recordHistoricalSignal(
      long userId, String username, String text, long messageEpochSeconds) {
    if (text == null || text.isBlank()) {
      return;
    }

    UserMemory memory = userMemories.computeIfAbsent(userId, k -> new UserMemory());
    evictOldestMemoriesIfNeeded();
    // Skip old/stale history already covered by prior backfills.
    if (memory.lastSeenEpoch > 0
        && messageEpochSeconds > 0
        && messageEpochSeconds < memory.lastSeenEpoch) {
      return;
    }

    memory.userId = userId;
    memory.username = username;
    memory.lastSeenEpoch =
        messageEpochSeconds > 0 ? Math.max(memory.lastSeenEpoch, messageEpochSeconds) : now();
    memory.messageCount += 1;
    memory.passiveSignalCount += 1;

    inferPreferences(memory, text);

    String topic = extractTopic(text);
    if (!topic.isEmpty()) {
      if (memory.recentTopics == null) {
        memory.recentTopics = new ArrayList<>();
      }
      Deque<String> queue = new ArrayDeque<>(memory.recentTopics);
      queue.remove(topic);
      queue.addFirst(topic);
      while (queue.size() > MAX_TOPICS_PER_USER) {
        queue.removeLast();
      }
      memory.recentTopics = new ArrayList<>(queue);
    }

    // Persist periodically for high-traffic channels.
    if (memory.passiveSignalCount % 5 == 0) {
      saveMemory();
    }
  }

  private static String safeValue(String value) {
    return value == null || value.isBlank() ? "unknown" : value;
  }

  private static String safeName(String name) {
    return name == null || name.isBlank() ? "pilot" : name;
  }

  private static String topModule(UserMemory memory) {
    if (memory.moduleAffinity == null || memory.moduleAffinity.isEmpty()) {
      return "none";
    }
    String best = "none";
    int bestScore = 0;
    for (Map.Entry<String, Integer> entry : memory.moduleAffinity.entrySet()) {
      if (entry.getValue() > bestScore) {
        best = entry.getKey();
        bestScore = entry.getValue();
      }
    }
    return best;
  }

  private static void inferPreferences(UserMemory memory, String text) {
    if (text == null || text.isBlank()) {
      return;
    }
    String lower = text.toLowerCase(Locale.ROOT);

    // Detail level inference
    if (containsAny(lower, "detailed", "full", "deep", "step by step", "breakdown", "explain")) {
      memory.preferredDetailLevel = "detailed";
    } else if (containsAny(lower, "quick", "brief", "short", "tldr", "summary")) {
      memory.preferredDetailLevel = "concise";
    }

    // Style inference --” require 3+ humor signals across separate messages before
    // committing to humorous mode, so a single "lol" doesn't permanently change the profile.
    if (containsAny(
        lower,
        "joke",
        "funny",
        "lol",
        "lmao",
        "meme",
        "roast",
        "haha",
        "lmfao",
        "deadpool",
        "chaotic",
        "chaos",
        "snark",
        "sarcastic",
        "banter")) {
      memory.humorSignalCount = memory.humorSignalCount + 1;
      if (memory.humorSignalCount >= 3) {
        memory.preferredChatStyle = "humorous";
      }
    } else if (containsAny(lower, "serious", "formal", "professional", "straight to the point")) {
      memory.preferredChatStyle = "serious";
    } else if (containsAny(lower, "friendly", "chill", "casual")) {
      memory.preferredChatStyle = "friendly";
    }

    // Module affinity inference
    if (memory.moduleAffinity == null) {
      memory.moduleAffinity = new HashMap<>();
    }
    for (Map.Entry<String, List<String>> module : MODULE_KEYWORDS.entrySet()) {
      boolean hit = false;
      for (String kw : module.getValue()) {
        if (lower.contains(kw)) {
          hit = true;
          break;
        }
      }
      if (hit) {
        memory.moduleAffinity.merge(module.getKey(), 1, Integer::sum);
      }
    }

    // Conversation depth inference: rewards multi-clause prompts and explicit
    // requests for deeper discussion, then decays very gradually over time.
    if (memory.conversationDepthScore > 0 && memory.messageCount % 7 == 0) {
      memory.conversationDepthScore -= 1;
    }
    int depthDelta = 0;
    if (lower.length() >= 70) {
      depthDelta += 1;
    }
    if (containsAny(
        lower,
        "why",
        "because",
        "compare",
        "tradeoff",
        "pros",
        "cons",
        "deeper",
        "in-depth",
        "walk me through",
        "breakdown",
        "step by step",
        "long version",
        "continue",
        "keep going")) {
      depthDelta += 2;
    }
    if (containsAny(lower, "?")) {
      depthDelta += 1;
    }
    memory.conversationDepthScore =
        Math.max(0, Math.min(20, memory.conversationDepthScore + depthDelta));
  }

  private static boolean containsAny(String text, String... needles) {
    for (String n : needles) {
      if (text.contains(n)) {
        return true;
      }
    }
    return false;
  }

  private static boolean hasAny(String text, String... needles) {
    return containsAny(text, needles);
  }

  private static boolean isGreetingOrAckTurn(String lower) {
    if (lower == null || lower.isBlank()) {
      return true;
    }
    String value = lower.trim();
    return value.equals("hi")
        || value.equals("hey")
        || value.equals("hello")
        || value.equals("yo")
        || value.equals("sup")
        || value.equals("ye")
        || value.equals("yeah")
        || value.equals("y")
        || value.equals("ya")
        || value.equals("yep")
        || value.equals("ok")
        || value.equals("okay")
        || value.equals("k")
        || value.equals("um")
        || value.equals("uh")
        || value.equals("um what")
        || value.equals("what?");
  }

  private static String extractTopic(String text) {
    if (text == null || text.isBlank()) {
      return "";
    }
    String[] raw =
        text.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9\\s]", " ").trim().split("\\s+");

    Map<String, Integer> score = new HashMap<>();
    for (String token : raw) {
      if (token.length() < 4) {
        continue;
      }
      if (TOPIC_STOPWORDS.contains(token)) {
        continue;
      }
      score.merge(token, 1, Integer::sum);
    }

    String best = "";
    int bestScore = 0;
    for (Map.Entry<String, Integer> entry : score.entrySet()) {
      if (entry.getValue() > bestScore) {
        best = entry.getKey();
        bestScore = entry.getValue();
      }
    }
    return best;
  }

  private static String sessionKey(long channelId, long userId) {
    return channelId + ":" + userId;
  }

  private static long now() {
    return Instant.now().getEpochSecond();
  }

  /**
   * Evicts the oldest 10 % of user memory records when the map exceeds {@link #MAX_USER_MEMORIES}.
   * "Oldest" is determined by {@link UserMemory#lastSeenEpoch}.
   */
  private static void evictOldestMemoriesIfNeeded() {
    if (userMemories.size() <= MAX_USER_MEMORIES) {
      return;
    }
    int evictCount = Math.max(1, userMemories.size() / 10);
    userMemories.values().stream()
        .sorted(Comparator.comparingLong(m -> m.lastSeenEpoch))
        .limit(evictCount)
        .map(m -> m.userId)
        .collect(Collectors.toList())
        .forEach(userMemories::remove);
  }

  private static void pruneSessions() {
    long now = now();
    Set<String> remove = new HashSet<>();
    for (Map.Entry<String, Long> entry : activeSessions.entrySet()) {
      long channelId = parseChannelIdFromSessionKey(entry.getKey());
      long ttl = ttlForChannel(channelId);
      if (now - entry.getValue() > ttl) {
        remove.add(entry.getKey());
      }
    }
    for (String key : remove) {
      activeSessions.remove(key);
    }
    if (!remove.isEmpty()) {
      saveSessions();
    }
  }

  private static void load() {
    loadMemory();
    loadSessions();
  }

  private static void loadMemory() {
    File file = new File(MEMORY_FILE);
    if (!file.exists()) {
      return;
    }
    try {
      MemoryState state = MAPPER.readValue(file, MemoryState.class);
      userMemories.clear();
      if (state != null && state.users != null) {
        for (UserMemory memory : state.users) {
          normalizeMemory(memory);
          userMemories.put(memory.userId, memory);
        }
      }
    } catch (IOException e) {
      System.out.println("[Memory] Failed to load user memory: " + e.getMessage());
    }
  }

  private static void loadSessions() {
    File file = new File(SESSIONS_FILE);
    if (!file.exists()) {
      return;
    }
    try {
      SessionState state = MAPPER.readValue(file, SessionState.class);
      activeSessions.clear();
      if (state != null && state.sessions != null) {
        activeSessions.putAll(state.sessions);
      }
      pruneSessions();
    } catch (IOException e) {
      System.out.println("[Memory] Failed to load sessions: " + e.getMessage());
    }
  }

  private static void saveMemory() {
    try {
      File file = new File(MEMORY_FILE);
      if (file.getParentFile() != null) {
        file.getParentFile().mkdirs();
      }
      MemoryState state = new MemoryState();
      state.users = new ArrayList<>(userMemories.values());
      MAPPER.writeValue(file, state);
    } catch (IOException e) {
      System.out.println("[Memory] Failed to save user memory: " + e.getMessage());
    }
  }

  private static void saveSessions() {
    try {
      File file = new File(SESSIONS_FILE);
      if (file.getParentFile() != null) {
        file.getParentFile().mkdirs();
      }
      SessionState state = new SessionState();
      state.sessions = new HashMap<>(activeSessions);
      MAPPER.writeValue(file, state);
    } catch (IOException e) {
      System.out.println("[Memory] Failed to save sessions: " + e.getMessage());
    }
  }

  public static class UserMemory {

    public long userId;
    public String username;
    public String lastIntent;
    public String lastEmotion;
    public long lastSeenEpoch;
    public int messageCount;
    public List<String> recentTopics = new ArrayList<>();
    public String preferredDetailLevel = "balanced";
    public String preferredChatStyle = "friendly";
    public Map<String, Integer> moduleAffinity = new HashMap<>();

    /**
     * Number of messages containing humor signals. Style flips to "humorous" after 3+.
     */
    public int humorSignalCount = 0;

    /**
     * Counter for passive-learning messages; used to batch persistence.
     */
    public int passiveSignalCount = 0;

    /**
     * Last user message text for short continuity hooks.
     */
    public String lastUserMessage = "";

    /**
     * Most recent bot response domain context (trade/mining/ship/etc.).
     */
    public String lastBotDomain = "";

    /**
     * Most recent domain subject/entity (e.g., ship/component name).
     */
    public String lastBotSubject = "";

    /**
     * Epoch when the bot ended with a follow-up question.
     */
    public long lastBotQuestionEpoch = 0;

    /**
     * Small recency queue of entities referenced in bot replies.
     */
    public List<String> recentEntityMentions = new ArrayList<>();

    /**
     * Preference signal for richer, multi-turn conversational depth.
     */
    public int conversationDepthScore = 0;
  }

  public static class UserPreferences {

    public final String detailLevel;
    public final String chatStyle;
    public final String favoriteModule;

    public UserPreferences(String detailLevel, String chatStyle, String favoriteModule) {
      this.detailLevel = detailLevel;
      this.chatStyle = chatStyle;
      this.favoriteModule = favoriteModule;
    }
  }

  public static class MemoryState {

    public List<UserMemory> users = new ArrayList<>();
  }

  public static class SessionState {

    public Map<String, Long> sessions = new HashMap<>();
  }

  private static long ttlForChannel(long channelId) {
    return CHANNEL_SESSION_TTLS.getOrDefault(channelId, SESSION_TTL_SECONDS);
  }

  private static long parseChannelIdFromSessionKey(String key) {
    int idx = key.indexOf(':');
    if (idx < 0) {
      return -1;
    }
    try {
      return Long.parseLong(key.substring(0, idx));
    } catch (NumberFormatException e) {
      return -1;
    }
  }

  private static long readLongEnv(String key, long defaultValue) {
    String raw = System.getenv(key);
    if (raw == null || raw.isBlank()) {
      return defaultValue;
    }
    try {
      long v = Long.parseLong(raw.trim());
      return v > 0 ? v : defaultValue;
    } catch (NumberFormatException e) {
      return defaultValue;
    }
  }

  private static Map<Long, Long> parseChannelSessionTtls(String raw) {
    Map<Long, Long> result = new HashMap<>();
    if (raw == null || raw.isBlank()) {
      return result;
    }

    String[] pairs = raw.split(",");
    for (String pair : pairs) {
      String[] parts = pair.trim().split(":");
      if (parts.length != 2) {
        continue;
      }
      try {
        long channelId = Long.parseLong(parts[0].trim());
        long ttl = Long.parseLong(parts[1].trim());
        if (channelId > 0 && ttl > 0) {
          result.put(channelId, ttl);
        }
      } catch (NumberFormatException ignored) {
        // Ignore malformed entries and keep parsing the rest.
      }
    }
    return result;
  }

  private static void normalizeMemory(UserMemory memory) {
    if (memory.recentTopics == null) {
      memory.recentTopics = new ArrayList<>();
    }
    if (memory.preferredDetailLevel == null || memory.preferredDetailLevel.isBlank()) {
      memory.preferredDetailLevel = "balanced";
    }
    if (memory.preferredChatStyle == null || memory.preferredChatStyle.isBlank()) {
      memory.preferredChatStyle = "friendly";
    }
    if (memory.moduleAffinity == null) {
      memory.moduleAffinity = new HashMap<>();
    }
    if (memory.lastUserMessage == null) {
      memory.lastUserMessage = "";
    }
    if (memory.lastBotDomain == null) {
      memory.lastBotDomain = "";
    }
    if (memory.lastBotSubject == null) {
      memory.lastBotSubject = "";
    }
    if (memory.recentEntityMentions == null) {
      memory.recentEntityMentions = new ArrayList<>();
    }
    if (memory.recentEntityMentions.size() > MAX_RECENT_ENTITIES_PER_USER) {
      memory.recentEntityMentions =
          new ArrayList<>(memory.recentEntityMentions.subList(0, MAX_RECENT_ENTITIES_PER_USER));
    }
    if (memory.conversationDepthScore < 0) {
      memory.conversationDepthScore = 0;
    }
    // Missing fields from old JSON snapshots default to 0 automatically.
  }
}


