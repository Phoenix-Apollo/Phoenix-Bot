package Botcode.Monitoring;

import Botcode.Utils.Env;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Centralized monitoring and metrics collection for the bot.
 * Tracks performance metrics, error rates, and operational health.
 * Provides alerting when metrics exceed configured thresholds.
 */
public class MetricsCollector {

  public enum MetricType {
    COMMAND_EXECUTED("commands.executed"),
    COMMAND_ERROR("commands.error"),
    AI_RESPONSE_TIME("ai.response_time_ms"),
    DB_QUERY_TIME("db.query_time_ms"),
    API_REQUEST_TIME("api.request_time_ms"),
    MESSAGE_PROCESSED("messages.processed"),
    MESSAGE_ERROR("messages.error"),
    VOICE_MEMBER_JOIN("voice.member_join"),
    VOICE_MEMBER_LEAVE("voice.member_leave"),
    RATE_LIMIT_HIT("ratelimit.hit"),
    SECURITY_VIOLATION("security.violation");

    final String name;

    MetricType(String name) {
      this.name = name;
    }
  }

  private static final ConcurrentHashMap<String, AtomicLong> counters = new ConcurrentHashMap<>();
  private static final ConcurrentHashMap<String, Long> lastAlertTime = new ConcurrentHashMap<>();
  private static final long ALERT_COOLDOWN_MS = 300_000; // 5 minutes between duplicate alerts

  private static final int ERROR_RATE_THRESHOLD = Integer.parseInt(
      Env.getOrDefault("BOT_METRICS_ERROR_THRESHOLD_PERCENT", "5"));
  private static final long RESPONSE_TIME_THRESHOLD_MS = Long.parseLong(
      Env.getOrDefault("BOT_METRICS_RESPONSE_TIME_THRESHOLD_MS", "5000"));
  private static final int MEMORY_USAGE_THRESHOLD_PERCENT = Integer.parseInt(
      Env.getOrDefault("BOT_METRICS_MEMORY_THRESHOLD_PERCENT", "80"));

  /**
   * Records a metric event.
   *
   * @param metricType Type of metric
   * @param value Value to record
   */
  public static void recordMetric(MetricType metricType, long value) {
    String key = metricType.name;
    counters.computeIfAbsent(key, k -> new AtomicLong(0)).addAndGet(value);

    // Check for threshold violations
    if (metricType == MetricType.AI_RESPONSE_TIME
        || metricType == MetricType.DB_QUERY_TIME
        || metricType == MetricType.API_REQUEST_TIME) {
      if (value > RESPONSE_TIME_THRESHOLD_MS) {
        alertSlowResponse(metricType, value);
      }
    }

    if (metricType == MetricType.COMMAND_ERROR
        || metricType == MetricType.MESSAGE_ERROR) {
      checkErrorRateThreshold();
    }
  }

  /**
   * Increments a counter metric.
   *
   * @param metricType Type of metric to increment
   */
  public static void incrementMetric(MetricType metricType) {
    recordMetric(metricType, 1);
  }

  /**
   * Gets the current value of a counter metric.
   *
   * @param metricType Type of metric to retrieve
   * @return Current counter value
   */
  public static long getMetricValue(MetricType metricType) {
    AtomicLong counter = counters.get(metricType.name);
    return counter != null ? counter.get() : 0;
  }

  /**
   * Gets the error rate as a percentage.
   *
   * @return Error rate (0-100)
   */
  public static double getErrorRate() {
    long totalCommands = getMetricValue(MetricType.COMMAND_EXECUTED)
        + getMetricValue(MetricType.COMMAND_ERROR);
    if (totalCommands == 0) {
      return 0;
    }

    long errors = getMetricValue(MetricType.COMMAND_ERROR);
    return (errors * 100.0) / totalCommands;
  }

  /**
   * Gets memory usage as a percentage.
   *
   * @return Memory usage percentage (0-100)
   */
  public static double getMemoryUsagePercent() {
    Runtime runtime = Runtime.getRuntime();
    long maxMemory = runtime.maxMemory();
    long usedMemory = runtime.totalMemory() - runtime.freeMemory();
    return (usedMemory * 100.0) / maxMemory;
  }

  /**
   * Gets uptime in milliseconds since bot start.
   *
   * @return Uptime in milliseconds
   */
  public static long getUptimeMs() {
    // This should be set at bot startup
    Long startTime = startTimestampMs;
    return startTime != null ? System.currentTimeMillis() - startTime : 0;
  }

  /**
   * Formats uptime for display.
   *
   * @return Human-readable uptime string
   */
  public static String getFormattedUptime() {
    long ms = getUptimeMs();
    long days = ms / (24 * 60 * 60 * 1000);
    long hours = (ms / (60 * 60 * 1000)) % 24;
    long minutes = (ms / (60 * 1000)) % 60;
    long seconds = (ms / 1000) % 60;

    return String.format("%d days, %02d:%02d:%02d", days, hours, minutes, seconds);
  }

  /**
   * Checks if system health is critical.
   *
   * @return true if any critical thresholds exceeded
   */
  public static boolean isCritical() {
    return getErrorRate() > ERROR_RATE_THRESHOLD
        || getMemoryUsagePercent() > MEMORY_USAGE_THRESHOLD_PERCENT;
  }

  /**
   * Resets all metrics (typically done daily for trending).
   */
  public static void resetMetrics() {
    counters.clear();
    System.out.println("[Metrics] All metrics reset");
  }

  /**
   * Generates a health check report.
   *
   * @return Human-readable health report
   */
  public static String getHealthReport() {
    StringBuilder report = new StringBuilder();
    report.append("=== BOT HEALTH REPORT ===\n");
    report.append("Uptime: ").append(getFormattedUptime()).append("\n");
    report.append("Commands Executed: ").append(getMetricValue(MetricType.COMMAND_EXECUTED))
        .append("\n");
    report.append("Command Errors: ").append(getMetricValue(MetricType.COMMAND_ERROR))
        .append(" (").append(String.format("%.2f%%", getErrorRate())).append(")\n");
    report.append("Messages Processed: ").append(getMetricValue(MetricType.MESSAGE_PROCESSED))
        .append("\n");
    report.append("Memory Usage: ").append(String.format("%.2f%%", getMemoryUsagePercent()))
        .append("\n");
    report.append("Security Violations: ").append(getMetricValue(MetricType.SECURITY_VIOLATION))
        .append("\n");
    report.append("Rate Limits Hit: ").append(getMetricValue(MetricType.RATE_LIMIT_HIT))
        .append("\n");
    report.append("Status: ").append(isCritical() ? "⚠️ CRITICAL" : "✅ HEALTHY").append("\n");
    return report.toString();
  }

  /**
   * Sets the bot start timestamp (called during initialization).
   *
   * @param timestampMs Start timestamp in milliseconds
   */
  public static void setStartTimestamp(long timestampMs) {
    startTimestampMs = timestampMs;
  }

  /**
   * Alerts on slow response times.
   *
   * @param metricType Type of slow operation
   * @param responseTimeMs Response time in milliseconds
   */
  private static void alertSlowResponse(MetricType metricType, long responseTimeMs) {
    String alertKey = "slow_" + metricType.name;
    long now = System.currentTimeMillis();
    Long lastAlert = lastAlertTime.get(alertKey);

    if (lastAlert == null || (now - lastAlert) > ALERT_COOLDOWN_MS) {
      System.out.println("[ALERT] Slow response: " + metricType.name
          + " took " + responseTimeMs + "ms (threshold: " + RESPONSE_TIME_THRESHOLD_MS + "ms)");
      lastAlertTime.put(alertKey, now);
    }
  }

  /**
   * Checks and alerts on high error rates.
   */
  private static void checkErrorRateThreshold() {
    double errorRate = getErrorRate();
    if (errorRate > ERROR_RATE_THRESHOLD) {
      String alertKey = "high_error_rate";
      long now = System.currentTimeMillis();
      Long lastAlert = lastAlertTime.get(alertKey);

      if (lastAlert == null || (now - lastAlert) > ALERT_COOLDOWN_MS) {
        System.out.println("[ALERT] High error rate: " + String.format("%.2f%%", errorRate)
            + " (threshold: " + ERROR_RATE_THRESHOLD + "%)");
        lastAlertTime.put(alertKey, now);
      }
    }
  }

  /**
   * Initialize metrics collection (called at bot startup).
   */
  public static void initialize() {
    if (startTimestampMs == null) {
      startTimestampMs = System.currentTimeMillis();
      System.out.println("[Metrics] Monitoring system initialized");
    }
  }

  /**
   * Get human-readable uptime string.
   */
  public static String getUptimeString() {
    if (startTimestampMs == null) {
      return "Unknown";
    }
    long uptimeMs = System.currentTimeMillis() - startTimestampMs;
    long days = uptimeMs / (1000 * 60 * 60 * 24);
    long hours = (uptimeMs / (1000 * 60 * 60)) % 24;
    long minutes = (uptimeMs / (1000 * 60)) % 60;
    
    if (days > 0) {
      return days + "d " + hours + "h " + minutes + "m";
    } else if (hours > 0) {
      return hours + "h " + minutes + "m";
    } else {
      return minutes + "m";
    }
  }

  /**
   * Record a generic event metric.
   */
  public static void recordEvent(String eventName, long value) {
    counters.computeIfAbsent(eventName, k -> new AtomicLong(0)).addAndGet(value);
  }

  // Timestamp when bot started (milliseconds)
  private static Long startTimestampMs = null;
}
