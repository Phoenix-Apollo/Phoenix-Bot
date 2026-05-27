package Botcode.AI.Personality;

import Botcode.AI.AIUtils.SafetyGuard;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Handles runtime learning of new phrases and reaction-based phrase scoring.
 *
 * <p>Learned phrases are stored in {@code data/learned_phrases.json} as a map of intent-name ->
 * (phrase-text -> score). Phrases scoring below {@code MIN_SCORE} are pruned on the next save.
 * Scores above {@code PROMOTE_THRESHOLD} cause the phrase to be persisted with a positive bias so
 * it keeps appearing after restarts.
 *
 * <p><b>Teach command (admin):</b> {@code @bot learn <intent>: <phrase text>} or {@code @bot learn
 * <intent> = <phrase text>}<br>
 * <b>Forget command (admin):</b> {@code @bot forget <intent>: <phrase text>} or {@code @bot forget
 * <intent> = <phrase text>}<br>
 * <b>List command:</b> {@code @bot phrases <intent>}<br>
 * <b>Reaction rating:</b> React 👍 to upvote a bot reply, 👎 to downvote it.
 */
public class PhraseLearner {

  // ---------------------------------------------------------------------------
  // Configuration
  // ---------------------------------------------------------------------------

  /**
   * Relative path (from working directory) for the JSON persistence file.
   */
  private static final String DATA_FILE = "data/learned_phrases.json";

  /**
   * Per-user phrase profile persistence file.
   */
  private static final String USER_DATA_FILE = "data/user_phrase_profiles.json";

  /**
   * Phrases whose score drops to or below this value are removed.
   */
  private static final int MIN_SCORE = -2;

  private static final int MAX_PHRASE_LENGTH = 240;
  private static final long PENDING_REPLY_TTL_SECONDS = 24 * 60 * 60L;
  private static final int AUTO_USER_PHRASE_MAX_PER_INTENT = 80;
  private static final int AUTO_USER_PHRASE_MAX_SCORE = 10;
  private static final long AUTO_SAVE_INTERVAL_SECONDS = 45L;

  /**
   * Maximum number of per-user phrase profiles held in memory before LRU eviction.
   */
  private static final int MAX_USER_PROFILES = 5_000;

  /**
   * Unicode strings for the supported reaction emojis.
   */
  public static final Set<String> UPVOTE_EMOJIS = Set.of("👍");

  public static final Set<String> DOWNVOTE_EMOJIS = Set.of("👎");

  // ---------------------------------------------------------------------------
  // In-process state
  // ---------------------------------------------------------------------------

  private static final ObjectMapper mapper =
      new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

  /**
   * intent -> (phrase-text -> score)
   */
  private static final Map<TriggerEngine.Intent, Map<String, Integer>> phraseScores =
      new ConcurrentHashMap<>();

  /**
   * userId -> intent -> (phrase -> score)
   */
  private static final Map<Long, Map<TriggerEngine.Intent, Map<String, Integer>>> userPhraseScores =
      new ConcurrentHashMap<>();

  /**
   * Tracks the last time each userId's phrase profile was touched, for LRU eviction.
   */
  private static final Map<Long, Long> userPhraseLastTouchMs = new ConcurrentHashMap<>();

  /**
   * Tracks the most recent bot reply per channel so reactions can be attributed. key = bot
   * message-id, value = the intent + phrase that produced that reply.
   */
  private static final Map<Long, PendingReply> pendingReplies = new ConcurrentHashMap<>();

  /**
   * Tracks who has already voted on a tracked bot reply to avoid duplicate learning noise.
   */
  private static final Map<Long, Set<Long>> votersByMessage = new ConcurrentHashMap<>();

  private static volatile long lastAutoSaveEpoch = 0L;

  // Static initializer - load persisted phrases when the class is first referenced.
  static {
    load();
  }

  // ---------------------------------------------------------------------------
  // Data classes
  // ---------------------------------------------------------------------------

  /**
   * Associates a sent bot-reply message with the intent / phrase it came from.
   */
  public static class PendingReply {

    public final TriggerEngine.Intent intent;
    public final String phrase;
    public final long createdEpoch;

    public PendingReply(TriggerEngine.Intent intent, String phrase, long createdEpoch) {
      this.intent = intent;
      this.phrase = phrase;
      this.createdEpoch = createdEpoch;
    }
  }

  // ---------------------------------------------------------------------------
  // Public API
  // ---------------------------------------------------------------------------

  /**
   * Adds a new phrase for the given intent (score starts at 0). Ignores the call if an identical
   * phrase already exists.
   *
   * @return {@code true} if the phrase was new and added, {@code false} if it already existed.
   */
  public static boolean addPhrase(TriggerEngine.Intent intent, String phrase) {
    String clean = normalizePhrase(phrase);
    if (clean == null || !SafetyGuard.isSafeLearningPhrase(clean)) {
      return false;
    }
    Map<String, Integer> scores =
        phraseScores.computeIfAbsent(intent, k -> new ConcurrentHashMap<>());
    String existing = findEquivalentKey(scores, clean);
    if (existing != null) {
      return false;
    }
    scores.put(clean, 0);
    save();
    return true;
  }

  /**
   * Explicitly removes a phrase for the given intent.
   *
   * @return {@code true} if removed, {@code false} if it was not found.
   */
  public static boolean removePhrase(TriggerEngine.Intent intent, String phrase) {
    String clean = normalizePhrase(phrase);
    if (clean == null) {
      return false;
    }
    Map<String, Integer> scores = phraseScores.get(intent);
    if (scores == null) {
      return false;
    }
    String existing = findEquivalentKey(scores, clean);
    if (existing == null) {
      return false;
    }
    scores.remove(existing);
    save();
    return true;
  }

  /**
   * Adds a personal phrase for one user only.
   */
  public static boolean addUserPhrase(long userId, TriggerEngine.Intent intent, String phrase) {
    String clean = normalizePhrase(phrase);
    if (clean == null || !SafetyGuard.isSafeLearningPhrase(clean)) {
      return false;
    }
    Map<TriggerEngine.Intent, Map<String, Integer>> intents =
        userPhraseScores.computeIfAbsent(userId, k -> new ConcurrentHashMap<>());
    Map<String, Integer> scores = intents.computeIfAbsent(intent, k -> new ConcurrentHashMap<>());
    String existing = findEquivalentKey(scores, clean);
    if (existing != null) {
      return false;
    }
    scores.put(clean, 0);
    touchUserProfile(userId);
    save();
    return true;
  }

  /**
   * Removes a personal phrase for one user only.
   */
  public static boolean removeUserPhrase(long userId, TriggerEngine.Intent intent, String phrase) {
    String clean = normalizePhrase(phrase);
    if (clean == null) {
      return false;
    }
    Map<TriggerEngine.Intent, Map<String, Integer>> intents = userPhraseScores.get(userId);
    if (intents == null) {
      return false;
    }
    Map<String, Integer> scores = intents.get(intent);
    if (scores == null) {
      return false;
    }
    String existing = findEquivalentKey(scores, clean);
    if (existing == null) {
      return false;
    }
    scores.remove(existing);
    save();
    return true;
  }

  /**
   * Passive personal-style adaptation from natural user messages.
   *
   * <p>This is intentionally bounded per intent so memory stays predictable.
   */
  public static void observeUserStyleMessage(
      long userId, TriggerEngine.Intent intent, String message) {
    if (intent == null || message == null) {
      return;
    }
    String clean = normalizePhrase(message);
    if (clean == null || !SafetyGuard.isSafeLearningPhrase(clean)) {
      return;
    }
    if (looksLikeCommandPayload(clean)) {
      return;
    }

    Map<TriggerEngine.Intent, Map<String, Integer>> intents =
        userPhraseScores.computeIfAbsent(userId, k -> new ConcurrentHashMap<>());
    Map<String, Integer> scores = intents.computeIfAbsent(intent, k -> new ConcurrentHashMap<>());

    String existing = findEquivalentKey(scores, clean);
    if (existing != null) {
      int next = Math.min(AUTO_USER_PHRASE_MAX_SCORE, scores.getOrDefault(existing, 0) + 1);
      scores.put(existing, next);
      touchUserProfile(userId);
      saveMaybeAuto();
      return;
    }

    trimLowestScoreIfNeeded(scores, AUTO_USER_PHRASE_MAX_PER_INTENT - 1);
    scores.put(clean, 1);
    touchUserProfile(userId);
    saveMaybeAuto();
  }

  /**
   * Records that the bot sent {@code messageId} using {@code phrase} for {@code intent}. This
   * allows the reaction handler to know what to score.
   */
  public static void trackReply(long messageId, TriggerEngine.Intent intent, String phrase) {
    pruneTrackedReplies();
    pendingReplies.put(messageId, new PendingReply(intent, phrase, now()));
  }

  /**
   * Upvotes the phrase associated with {@code messageId} by +1. No-op if the message is not tracked
   * (e.g., bot was restarted).
   */
  public static void upvote(long messageId) {
    PendingReply pending = pendingReplies.get(messageId);
    if (pending == null) {
      return;
    }
    phraseScores
        .computeIfAbsent(pending.intent, k -> new ConcurrentHashMap<>())
        .merge(pending.phrase, 1, Integer::sum);

    // Reinforce successful personality patterns to improve future generations
    SentenceGenerator.reinforceSuccessfulPattern(pending.phrase, pending.intent);

    save();
    System.out.println("[PhraseLearner] +1 -> \"" + pending.phrase + "\" [" + pending.intent + "]");
  }

  /**
   * Upvotes globally and also trains the reacting user's personal phrase profile.
   */
  public static void upvoteForUser(long messageId, long userId) {
    PendingReply pending = pendingReplies.get(messageId);
    if (pending == null) {
      return;
    }
    if (!registerVote(messageId, userId)) {
      return;
    }

    upvote(messageId); // global tuning + pattern reinforcement

    Map<TriggerEngine.Intent, Map<String, Integer>> intents =
        userPhraseScores.computeIfAbsent(userId, k -> new ConcurrentHashMap<>());
    Map<String, Integer> scores =
        intents.computeIfAbsent(pending.intent, k -> new ConcurrentHashMap<>());
    scores.merge(pending.phrase, 1, Integer::sum);
    touchUserProfile(userId);
    save();
  }

  /**
   * Downvotes the phrase associated with {@code messageId} by -1. If the resulting score drops to
   * or below {@link #MIN_SCORE} the phrase is pruned.
   */
  public static void downvote(long messageId) {
    PendingReply pending = pendingReplies.get(messageId);
    if (pending == null) {
      return;
    }
    Map<String, Integer> scores = phraseScores.get(pending.intent);
    if (scores == null) {
      return;
    }
    int newScore = scores.merge(pending.phrase, -1, Integer::sum);
    System.out.println(
        "[PhraseLearner] -1 -> \""
            + pending.phrase
            + "\" ["
            + pending.intent
            + "] score="
            + newScore);
    if (newScore <= MIN_SCORE) {
      scores.remove(pending.phrase);
      System.out.println("[PhraseLearner] Pruned phrase (below threshold).");
    }
    save();
  }

  /**
   * Downvotes globally and also tunes the reacting user's personal profile.
   */
  public static void downvoteForUser(long messageId, long userId) {
    PendingReply pending = pendingReplies.get(messageId);
    if (pending == null) {
      return;
    }
    if (!registerVote(messageId, userId)) {
      return;
    }

    downvote(messageId); // global tuning

    Map<TriggerEngine.Intent, Map<String, Integer>> intents = userPhraseScores.get(userId);
    if (intents == null) {
      return;
    }
    Map<String, Integer> scores = intents.get(pending.intent);
    if (scores == null) {
      return;
    }

    int newScore = scores.merge(pending.phrase, -1, Integer::sum);
    if (newScore <= MIN_SCORE) {
      scores.remove(pending.phrase);
    }
    save();
  }

  /**
   * Returns {@code true} if the bot reply for {@code messageId} is still being tracked.
   */
  public static boolean isTracked(long messageId) {
    pruneTrackedReplies();
    return pendingReplies.containsKey(messageId);
  }

  /**
   * Returns all learned phrases for {@code intent} whose score is at or above 0. Returns an empty
   * list when none exist.
   */
  public static List<String> getLearnedPhrases(TriggerEngine.Intent intent) {
    Map<String, Integer> scores = phraseScores.get(intent);
    if (scores == null || scores.isEmpty()) {
      return Collections.emptyList();
    }
    List<String> result = new ArrayList<>();
    for (Map.Entry<String, Integer> e : scores.entrySet()) {
      if (e.getValue() >= 0) {
        result.add(e.getKey());
      }
    }
    return Collections.unmodifiableList(result);
  }

  /**
   * Returns positive-score personal phrases for a specific user and intent.
   */
  public static List<String> getUserPhrases(long userId, TriggerEngine.Intent intent) {
    Map<TriggerEngine.Intent, Map<String, Integer>> intents = userPhraseScores.get(userId);
    if (intents == null) {
      return Collections.emptyList();
    }
    Map<String, Integer> scores = intents.get(intent);
    if (scores == null || scores.isEmpty()) {
      return Collections.emptyList();
    }

    List<String> result = new ArrayList<>();
    for (Map.Entry<String, Integer> e : scores.entrySet()) {
      if (e.getValue() >= 0) {
        result.add(e.getKey());
      }
    }
    return Collections.unmodifiableList(result);
  }

  /**
   * Returns a formatted summary of all learned phrases for {@code intent}, including their scores
   * (useful for the {@code @bot phrases} command).
   */
  public static String formatPhraseList(TriggerEngine.Intent intent) {
    Map<String, Integer> scores = phraseScores.get(intent);
    if (scores == null || scores.isEmpty()) {
      return "No learned phrases for `" + intent.name().toLowerCase() + "` yet.";
    }
    StringBuilder sb =
        new StringBuilder("**Learned phrases for `")
            .append(intent.name().toLowerCase())
            .append("`:**\n");
    scores.entrySet().stream()
        .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
        .forEach(
            e ->
                sb.append("- ")
                    .append(e.getKey())
                    .append(" *(score: ")
                    .append(e.getValue())
                    .append(")*\n"));
    return sb.toString().trim();
  }

  /**
   * List personal phrase scores for one user and intent.
   */
  public static String formatUserPhraseList(long userId, TriggerEngine.Intent intent) {
    Map<TriggerEngine.Intent, Map<String, Integer>> intents = userPhraseScores.get(userId);
    if (intents == null || !intents.containsKey(intent) || intents.get(intent).isEmpty()) {
      return "You don't have personal phrases for `" + intent.name().toLowerCase() + "` yet.";
    }
    Map<String, Integer> scores = intents.get(intent);
    StringBuilder sb =
        new StringBuilder("**Your personal phrases for `")
            .append(intent.name().toLowerCase())
            .append("`:**\n");
    scores.entrySet().stream()
        .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
        .forEach(
            e ->
                sb.append("- ")
                    .append(e.getKey())
                    .append(" *(score: ")
                    .append(e.getValue())
                    .append(")*\n"));
    return sb.toString().trim();
  }

  /**
   * Admin diagnostics summary for autonomous/community learning health.
   */
  public static String formatLearningStats() {
    pruneTrackedReplies();

    int globalTotal = 0;
    int globalActive = 0;
    List<String> byIntent = new ArrayList<>();

    for (TriggerEngine.Intent intent : TriggerEngine.Intent.values()) {
      Map<String, Integer> scores = phraseScores.get(intent);
      if (scores == null || scores.isEmpty()) {
        continue;
      }

      int total = scores.size();
      int active = 0;
      for (Integer score : scores.values()) {
        if (score != null && score >= 0) {
          active++;
        }
      }

      globalTotal += total;
      globalActive += active;
      byIntent.add(intent.name().toLowerCase(Locale.ROOT) + ": " + active + "/" + total);
    }

    int personalProfiles = userPhraseScores.size();
    int personalPhrases = 0;
    for (Map<TriggerEngine.Intent, Map<String, Integer>> intents : userPhraseScores.values()) {
      for (Map<String, Integer> scores : intents.values()) {
        personalPhrases += scores.size();
      }
    }

    byIntent.sort(String.CASE_INSENSITIVE_ORDER);

    StringBuilder sb = new StringBuilder();
    sb.append("**Learning Diagnostics**\n");
    sb.append("- Global phrases (active/total): ")
        .append(globalActive)
        .append("/")
        .append(globalTotal)
        .append("\n");
    sb.append("- Personal profiles: ")
        .append(personalProfiles)
        .append(" users, ")
        .append(personalPhrases)
        .append(" phrases\n");
    sb.append("- Pending feedback targets: ")
        .append(pendingReplies.size())
        .append(" tracked replies\n");
    sb.append("- Vote records in cache: ").append(votersByMessage.size()).append("\n");

    if (!byIntent.isEmpty()) {
      sb.append("\n**By Intent (active/total)**\n");
      for (String line : byIntent) {
        sb.append("- ").append(line).append("\n");
      }
    }

    sb.append("\nUse 👍 and 👎 (or conversational corrections/praise) to keep tuning me.");
    return sb.toString().trim();
  }

  /**
   * Deletes all personalized phrase learning for one user.
   */
  public static boolean forgetUser(long userId) {
    boolean removed = userPhraseScores.remove(userId) != null;
    userPhraseLastTouchMs.remove(userId);
    if (removed) {
      save();
    }
    return removed;
  }

  /**
   * Parses an intent name from a user-supplied string (case-insensitive, underscores optional).
   *
   * @return the matching {@link TriggerEngine.Intent}, or {@code null} if unrecognised.
   */
  public static TriggerEngine.Intent parseIntent(String name) {
    if (name == null) {
      return null;
    }
    try {
      return TriggerEngine.Intent.valueOf(name.toUpperCase().replace(" ", "_").replace("-", "_"));
    } catch (IllegalArgumentException e) {
      return null;
    }
  }

  /**
   * Returns a comma-separated list of valid intent names for command help text.
   */
  public static String validIntentNames() {
    StringBuilder sb = new StringBuilder();
    for (TriggerEngine.Intent i : TriggerEngine.Intent.values()) {
      if (sb.length() > 0) {
        sb.append(", ");
      }
      sb.append("`").append(i.name().toLowerCase()).append("`");
    }
    return sb.toString();
  }

  // ---------------------------------------------------------------------------
  // Persistence
  // ---------------------------------------------------------------------------

  @SuppressWarnings("unchecked")
  private static void load() {
    loadGlobalPhrases();
    loadUserPhrases();
  }

  @SuppressWarnings("unchecked")
  private static void loadGlobalPhrases() {
    File file = new File(DATA_FILE);
    if (!file.exists()) {
      return;
    }
    try {
      Map<String, Map<String, Integer>> raw = mapper.readValue(file, Map.class);
      for (Map.Entry<String, Map<String, Integer>> entry : raw.entrySet()) {
        TriggerEngine.Intent intent = parseIntent(entry.getKey());
        if (intent == null) {
          continue;
        }
        Map<String, Integer> scores = new ConcurrentHashMap<>(entry.getValue());
        scores.entrySet().removeIf(e -> e.getValue() <= MIN_SCORE);
        phraseScores.put(intent, scores);
      }
      System.out.println("[PhraseLearner] Loaded learned phrases from " + DATA_FILE);
    } catch (IOException e) {
      System.err.println("[PhraseLearner] Failed to load learned phrases: " + e.getMessage());
    }
  }

  @SuppressWarnings("unchecked")
  private static void loadUserPhrases() {
    File file = new File(USER_DATA_FILE);
    if (!file.exists()) {
      return;
    }
    try {
      Map<String, Map<String, Map<String, Integer>>> raw = mapper.readValue(file, Map.class);
      for (Map.Entry<String, Map<String, Map<String, Integer>>> userEntry : raw.entrySet()) {
        long userId;
        try {
          userId = Long.parseLong(userEntry.getKey());
        } catch (NumberFormatException e) {
          continue;
        }

        Map<TriggerEngine.Intent, Map<String, Integer>> intentMap = new ConcurrentHashMap<>();
        for (Map.Entry<String, Map<String, Integer>> intentEntry :
            userEntry.getValue().entrySet()) {
          TriggerEngine.Intent intent = parseIntent(intentEntry.getKey());
          if (intent == null) {
            continue;
          }
          Map<String, Integer> scores = new ConcurrentHashMap<>(intentEntry.getValue());
          scores.entrySet().removeIf(e -> e.getValue() <= MIN_SCORE);
          if (!scores.isEmpty()) {
            intentMap.put(intent, scores);
          }
        }
        if (!intentMap.isEmpty()) {
          userPhraseScores.put(userId, intentMap);
          userPhraseLastTouchMs.put(userId, System.currentTimeMillis());
        }
      }
      System.out.println("[PhraseLearner] Loaded personalized phrases from " + USER_DATA_FILE);
    } catch (IOException e) {
      System.err.println("[PhraseLearner] Failed to load personalized phrases: " + e.getMessage());
    }
  }

  private static void save() {
    saveGlobalPhrases();
    saveUserPhrases();
  }

  private static void saveGlobalPhrases() {
    try {
      File file = new File(DATA_FILE);
      if (file.getParentFile() != null) {
        file.getParentFile().mkdirs();
      }
      Map<String, Map<String, Integer>> raw = new LinkedHashMap<>();
      for (Map.Entry<TriggerEngine.Intent, Map<String, Integer>> e : phraseScores.entrySet()) {
        if (!e.getValue().isEmpty()) {
          raw.put(e.getKey().name(), new LinkedHashMap<>(e.getValue()));
        }
      }
      mapper.writeValue(file, raw);
    } catch (IOException e) {
      System.err.println("[PhraseLearner] Failed to save learned phrases: " + e.getMessage());
    }
  }

  private static void saveUserPhrases() {
    try {
      File file = new File(USER_DATA_FILE);
      if (file.getParentFile() != null) {
        file.getParentFile().mkdirs();
      }

      Map<String, Map<String, Map<String, Integer>>> raw = new LinkedHashMap<>();
      for (Map.Entry<Long, Map<TriggerEngine.Intent, Map<String, Integer>>> userEntry :
          userPhraseScores.entrySet()) {
        Map<String, Map<String, Integer>> intents = new LinkedHashMap<>();
        for (Map.Entry<TriggerEngine.Intent, Map<String, Integer>> intentEntry :
            userEntry.getValue().entrySet()) {
          if (!intentEntry.getValue().isEmpty()) {
            intents.put(intentEntry.getKey().name(), new LinkedHashMap<>(intentEntry.getValue()));
          }
        }
        if (!intents.isEmpty()) {
          raw.put(String.valueOf(userEntry.getKey()), intents);
        }
      }

      mapper.writeValue(file, raw);
    } catch (IOException e) {
      System.err.println("[PhraseLearner] Failed to save personalized phrases: " + e.getMessage());
    }
  }

  private static String normalizePhrase(String phrase) {
    if (phrase == null) {
      return null;
    }
    String clean = phrase.replaceAll("\\s+", " ").trim();
    if (clean.length() < 3) {
      return null;
    }
    if (clean.length() > MAX_PHRASE_LENGTH) {
      clean = clean.substring(0, MAX_PHRASE_LENGTH).trim();
    }
    return clean;
  }

  private static String findEquivalentKey(Map<String, Integer> scores, String phrase) {
    String target = phrase.toLowerCase(Locale.ROOT);
    for (String key : scores.keySet()) {
      if (key != null && key.toLowerCase(Locale.ROOT).equals(target)) {
        return key;
      }
    }
    return null;
  }

  private static boolean looksLikeCommandPayload(String clean) {
    String lower = clean.toLowerCase(Locale.ROOT);
    if (lower.startsWith("@") || lower.startsWith("/") || lower.startsWith("!")) {
      return true;
    }
    return lower.startsWith("learn ")
        || lower.startsWith("learnme ")
        || lower.startsWith("forget ")
        || lower.startsWith("forgetme ");
  }

  private static void trimLowestScoreIfNeeded(Map<String, Integer> scores, int maxSize) {
    if (scores.size() <= maxSize) {
      return;
    }
    String lowestKey = null;
    int lowestScore = Integer.MAX_VALUE;
    for (Map.Entry<String, Integer> entry : scores.entrySet()) {
      int value = entry.getValue() == null ? 0 : entry.getValue();
      if (value < lowestScore) {
        lowestScore = value;
        lowestKey = entry.getKey();
      }
    }
    if (lowestKey != null) {
      scores.remove(lowestKey);
    }
  }

  private static void saveMaybeAuto() {
    long now = now();
    if (now - lastAutoSaveEpoch >= AUTO_SAVE_INTERVAL_SECONDS) {
      save();
      lastAutoSaveEpoch = now;
    }
  }

  private static boolean registerVote(long messageId, long userId) {
    Set<Long> voters =
        votersByMessage.computeIfAbsent(messageId, k -> ConcurrentHashMap.newKeySet());
    return voters.add(userId);
  }

  private static void pruneTrackedReplies() {
    long now = now();
    List<Long> remove = new ArrayList<>();
    for (Map.Entry<Long, PendingReply> e : pendingReplies.entrySet()) {
      if (now - e.getValue().createdEpoch > PENDING_REPLY_TTL_SECONDS) {
        remove.add(e.getKey());
      }
    }
    for (Long id : remove) {
      pendingReplies.remove(id);
      votersByMessage.remove(id);
    }
  }

  private static long now() {
    return System.currentTimeMillis() / 1000L;
  }

  /**
   * Records a touch for the user's phrase profile and, when the in-memory map exceeds
   * {@link #MAX_USER_PROFILES}, evicts the least-recently-used profiles.
   */
  private static void touchUserProfile(long userId) {
    userPhraseLastTouchMs.put(userId, System.currentTimeMillis());
    if (userPhraseScores.size() > MAX_USER_PROFILES) {
      evictLruUserProfiles();
    }
  }

  /**
   * Removes the oldest 10 % of user profiles to bring the map back under the cap.
   */
  private static void evictLruUserProfiles() {
    int evictCount = Math.max(1, userPhraseScores.size() / 10);
    userPhraseLastTouchMs.entrySet().stream()
        .sorted(Map.Entry.comparingByValue())
        .limit(evictCount)
        .map(Map.Entry::getKey)
        .collect(Collectors.toList())
        .forEach(
            uid -> {
              userPhraseScores.remove(uid);
              userPhraseLastTouchMs.remove(uid);
            });
  }
}


