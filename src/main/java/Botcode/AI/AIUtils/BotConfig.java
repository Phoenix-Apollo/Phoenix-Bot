package Botcode.AI.AIUtils;

import Botcode.Utils.Env;

import java.util.Arrays;
import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Global configuration settings for the bot.
 *
 * <p>Values are env-driven so features can be tuned without recompiling.
 */
public class BotConfig {

  // Global AI facade switch. Defaults true to preserve current chat behavior.
  public static final boolean AI_ENABLED = readBoolean("BOT_AI_ENABLED", true);

  // Local deterministic provider identifier (non-premium path).
  public static final String AI_PROVIDER = readString("BOT_AI_PROVIDER", "LOCAL_RULES");
  public static final String AI_API_KEY = readString("BOT_AI_API_KEY", "");

  // Premium remains a placeholder until paid integration is approved.
  // Keep hard-disabled for now so no premium provider can activate by env.
  public static final boolean AI_PREMIUM_ENABLED = false;
  public static final String AI_PREMIUM_PROVIDER = "NONE";

  // Safe web lookup support for prompts like "look up X on google".
  public static final boolean WEB_LOOKUP_ENABLED = readBoolean("BOT_WEB_LOOKUP_ENABLED", true);
  public static final int WEB_LOOKUP_TIMEOUT_MS = readInt("BOT_WEB_LOOKUP_TIMEOUT_MS", 4500);
  public static final int WEB_LOOKUP_MAX_SUMMARY_CHARS =
      readInt("BOT_WEB_LOOKUP_MAX_SUMMARY_CHARS", 520);
  public static final int WEB_LOOKUP_RETRY_COUNT = readInt("BOT_WEB_LOOKUP_RETRY_COUNT", 2);
  public static final long WEB_LOOKUP_ISSUE_COOLDOWN_SECONDS =
      readLong("BOT_WEB_LOOKUP_ISSUE_COOLDOWN_SECONDS", 1800L);
  public static final int WEB_LOOKUP_MAX_QUERY_CHARS =
      readInt("BOT_WEB_LOOKUP_MAX_QUERY_CHARS", 140);
  public static final int WEB_LOOKUP_MAX_RESPONSE_BYTES =
      readInt("BOT_WEB_LOOKUP_MAX_RESPONSE_BYTES", 1_200_000);
  public static final long WEB_LOOKUP_CACHE_SECONDS =
      readLong("BOT_WEB_LOOKUP_CACHE_SECONDS", 600L);

  // Autonomous quality-learning from conversational feedback.
  public static final boolean AUTONOMOUS_LEARNING_ENABLED =
      readBoolean("BOT_AUTONOMOUS_LEARNING_ENABLED", true);
  public static final long AUTONOMOUS_FEEDBACK_TTL_SECONDS =
      readLong("BOT_AUTONOMOUS_FEEDBACK_TTL_SECONDS", 180L);

  // Passive adaptation from normal guild conversation even when the bot is not replying.
  public static final boolean PASSIVE_LEARNING_ALL_GUILD_MESSAGES =
      readBoolean("BOT_PASSIVE_LEARNING_ALL_GUILD_MESSAGES", true);

  // Auto-learn personal style phrases from user messages (bounded per intent).
  public static final boolean AUTO_PERSONAL_PHRASE_LEARNING_ENABLED =
      readBoolean("BOT_AUTO_PERSONAL_PHRASE_LEARNING_ENABLED", true);

  // Startup backfill learning from recent channel history.
  public static final boolean PASSIVE_HISTORY_BACKFILL_ENABLED =
      readBoolean("BOT_PASSIVE_HISTORY_BACKFILL_ENABLED", true);
  public static final int PASSIVE_HISTORY_BACKFILL_MESSAGES_PER_CHANNEL =
      readInt("BOT_PASSIVE_HISTORY_BACKFILL_MESSAGES_PER_CHANNEL", 60);
  public static final int PASSIVE_HISTORY_BACKFILL_MAX_CHANNELS_PER_GUILD =
      readInt("BOT_PASSIVE_HISTORY_BACKFILL_MAX_CHANNELS_PER_GUILD", 10);
  public static final int PASSIVE_HISTORY_BACKFILL_MAX_AGE_DAYS =
      readInt("BOT_PASSIVE_HISTORY_BACKFILL_MAX_AGE_DAYS", 14);
  public static final boolean PASSIVE_HISTORY_BACKFILL_AI_SCOPE_ONLY =
      readBoolean("BOT_PASSIVE_HISTORY_BACKFILL_AI_SCOPE_ONLY", true);

  // Rich ship replies in chat with image + structured stats embed.
  public static final boolean SHIP_EMBED_REPLIES_ENABLED =
      readBoolean("BOT_SHIP_EMBED_REPLIES_ENABLED", true);

  // Expressive chat features: occasional emojis and GIF replies.
  public static final boolean GIF_REPLIES_ENABLED = readBoolean("BOT_GIF_REPLIES_ENABLED", true);
  public static final int GIF_REPLY_CHANCE_PERCENT = readInt("BOT_GIF_REPLY_CHANCE_PERCENT", 45);
  public static final long GIF_REPLY_COOLDOWN_SECONDS =
      readLong("BOT_GIF_REPLY_COOLDOWN_SECONDS", 20L);
  public static final int EMOJI_REPLY_CHANCE_PERCENT =
      readInt("BOT_EMOJI_REPLY_CHANCE_PERCENT", 18);
  public static final int GIF_REPLY_DECORATION_CHANCE_PERCENT =
      readInt("BOT_GIF_REPLY_DECORATION_CHANCE_PERCENT", 6);

  // Online-source override hardening controls.
  public static final boolean SOURCE_OVERRIDE_ADMIN_ONLY =
      readBoolean("BOT_SOURCE_OVERRIDE_ADMIN_ONLY", true);
  public static final boolean SOURCE_OVERRIDE_REQUIRE_STANDARD_HTTPS_PORT =
      readBoolean("BOT_SOURCE_OVERRIDE_REQUIRE_STANDARD_HTTPS_PORT", true);
  public static final int SOURCE_OVERRIDE_MAX_DOWNLOAD_BYTES =
      readInt("BOT_SOURCE_OVERRIDE_MAX_DOWNLOAD_BYTES", 1_500_000);
  public static final long SOURCE_OVERRIDE_SUBMIT_COOLDOWN_SECONDS =
      readLong("BOT_SOURCE_OVERRIDE_SUBMIT_COOLDOWN_SECONDS", 60L);
  public static final long SOURCE_OVERRIDE_APPROVE_COOLDOWN_SECONDS =
      readLong("BOT_SOURCE_OVERRIDE_APPROVE_COOLDOWN_SECONDS", 10L);
  public static final Set<String> SOURCE_OVERRIDE_TRUSTED_USER_IDS =
      readCsvSet("BOT_SOURCE_OVERRIDE_TRUSTED_USER_IDS");
  public static final Set<String> SOURCE_OVERRIDE_ALLOWED_HOSTS =
      readCsvSet("BOT_SOURCE_OVERRIDE_ALLOWED_HOSTS");

  private BotConfig() {
  }

  private static boolean readBoolean(String key, boolean defaultValue) {
    String raw = Env.get(key);
    if (raw == null || raw.isBlank()) {
      return defaultValue;
    }
    return Boolean.parseBoolean(raw.trim());
  }

  private static String readString(String key, String defaultValue) {
    String raw = Env.get(key);
    if (raw == null || raw.isBlank()) {
      return defaultValue;
    }
    return raw.trim();
  }

  private static int readInt(String key, int defaultValue) {
    String raw = Env.get(key);
    if (raw == null || raw.isBlank()) {
      return defaultValue;
    }
    try {
      return Integer.parseInt(raw.trim());
    } catch (NumberFormatException e) {
      return defaultValue;
    }
  }

  private static long readLong(String key, long defaultValue) {
    String raw = Env.get(key);
    if (raw == null || raw.isBlank()) {
      return defaultValue;
    }
    try {
      return Long.parseLong(raw.trim());
    } catch (NumberFormatException e) {
      return defaultValue;
    }
  }

  private static Set<String> readCsvSet(String key) {
    String raw = Env.get(key);
    if (raw == null || raw.isBlank()) {
      return Collections.emptySet();
    }
    return Arrays.stream(raw.split(","))
        .map(String::trim)
        .filter(v -> !v.isBlank())
        .map(String::toLowerCase)
        .collect(Collectors.toUnmodifiableSet());
  }
}

