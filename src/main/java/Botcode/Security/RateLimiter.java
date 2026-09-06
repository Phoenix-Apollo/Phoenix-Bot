package Botcode.Security;

import Botcode.AI.AIUtils.BotConfig;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Implements rate limiting per user and per guild to prevent abuse and spam attacks.
 * Tracks command frequency and enforces configurable limits.
 */
public class RateLimiter {

  private static class RateLimitRecord {
    final long timestamp;

    RateLimitRecord(long timestamp) {
      this.timestamp = timestamp;
    }
  }

  private static final long WINDOW_SIZE_MS = 60_000; // 1 minute

  private final ConcurrentHashMap<String, ConcurrentLinkedQueue<RateLimitRecord>> userLimits =
      new ConcurrentHashMap<>();
  private final ConcurrentHashMap<String, ConcurrentLinkedQueue<RateLimitRecord>> guildLimits =
      new ConcurrentHashMap<>();

  /**
   * Checks if a user has exceeded their rate limit.
   *
   * @param userId Discord user ID
   * @return true if rate limit exceeded, false if request allowed
   */
  public boolean isUserRateLimited(String userId) {
    int limit = BotConfig.RATE_LIMIT_PER_USER;
    if (limit <= 0) {
      return false; // 0 = unlimited
    }

    return checkAndRecordLimit(userLimits, userId, limit);
  }

  /**
   * Checks if a guild has exceeded its rate limit.
   *
   * @param guildId Discord guild ID
   * @return true if rate limit exceeded, false if request allowed
   */
  public boolean isGuildRateLimited(String guildId) {
    int limit = BotConfig.RATE_LIMIT_PER_GUILD;
    if (limit <= 0) {
      return false; // 0 = unlimited
    }

    return checkAndRecordLimit(guildLimits, guildId, limit);
  }

  /**
   * Validates input length against maximum allowed.
   *
   * @param input The user input message
   * @return true if input is valid, false if too long
   */
  public boolean isInputValid(String input) {
    if (!BotConfig.INPUT_VALIDATION_ENABLED) {
      return true;
    }

    int maxLength = BotConfig.MAX_INPUT_LENGTH;
    if (maxLength <= 0) {
      return true; // 0 = unlimited
    }

    return input == null || input.length() <= maxLength;
  }

  /**
   * Gets the current rate limit status for a user.
   *
   * @param userId Discord user ID
   * @return Number of requests in current window
   */
  public int getUserRequestCount(String userId) {
    ConcurrentLinkedQueue<RateLimitRecord> records = userLimits.getOrDefault(userId, null);
    if (records == null) {
      return 0;
    }

    cleanExpiredRecords(records);
    return records.size();
  }

  /**
   * Gets the current rate limit status for a guild.
   *
   * @param guildId Discord guild ID
   * @return Number of requests in current window
   */
  public int getGuildRequestCount(String guildId) {
    ConcurrentLinkedQueue<RateLimitRecord> records = guildLimits.getOrDefault(guildId, null);
    if (records == null) {
      return 0;
    }

    cleanExpiredRecords(records);
    return records.size();
  }

  /**
   * Resets rate limit for a specific user (admin operation).
   *
   * @param userId Discord user ID
   */
  public void resetUserLimit(String userId) {
    userLimits.remove(userId);
  }

  /**
   * Resets rate limit for a specific guild (admin operation).
   *
   * @param guildId Discord guild ID
   */
  public void resetGuildLimit(String guildId) {
    guildLimits.remove(guildId);
  }

  /**
   * Checks and records a request against a rate limit.
   *
   * @param limitsMap Map of rate limit records
   * @param identifier User or guild ID
   * @param limit Maximum requests per minute
   * @return true if limited, false if allowed
   */
  private boolean checkAndRecordLimit(
      ConcurrentHashMap<String, ConcurrentLinkedQueue<RateLimitRecord>> limitsMap,
      String identifier,
      int limit) {

    ConcurrentLinkedQueue<RateLimitRecord> records = limitsMap
        .computeIfAbsent(identifier, k -> new ConcurrentLinkedQueue<>());

    cleanExpiredRecords(records);

    if (records.size() >= limit) {
      return true; // Rate limited
    }

    records.offer(new RateLimitRecord(System.currentTimeMillis()));
    return false; // Request allowed
  }

  /**
   * Removes expired records (older than 1 minute) from the queue.
   *
   * @param records Queue of rate limit records
   */
  private void cleanExpiredRecords(ConcurrentLinkedQueue<RateLimitRecord> records) {
    long cutoff = System.currentTimeMillis() - WINDOW_SIZE_MS;

    while (!records.isEmpty()) {
      RateLimitRecord record = records.peek();
      if (record != null && record.timestamp < cutoff) {
        records.poll();
      } else {
        break;
      }
    }
  }
}
