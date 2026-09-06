package Botcode.Security;

import Botcode.Utils.Env;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Timer;
import java.util.TimerTask;

/**
 * Handles automatic data retention and purging of old conversation history.
 * Deletes conversation records older than the configured retention period.
 * Implements GDPR right-to-be-forgotten compliance.
 */
public class DataRetentionService {

  private static final String AUDIT_TABLE = "data_retention_audit";
  private static final int DEFAULT_RETENTION_DAYS = 60;
  private static final int DEFAULT_CLEANUP_HOUR = 2; // 2 AM

  private volatile Timer cleanupTimer;
  private volatile boolean running = false;

  /**
   * Starts the data retention cleanup scheduler.
   * Runs daily at the configured hour to purge old conversation data.
   *
   * @param dbConnection Connection to the database for cleanup operations
   */
  public void start(Connection dbConnection) {
    if (running) {
      return;
    }

    synchronized (this) {
      if (running) {
        return;
      }

      try {
        ensureAuditTable(dbConnection);

        int retentionDays = Integer.parseInt(
            Env.getOrDefault("BOT_DATA_RETENTION_DAYS", String.valueOf(DEFAULT_RETENTION_DAYS)));

        int cleanupHour = Integer.parseInt(
            Env.getOrDefault("BOT_RETENTION_CLEANUP_HOUR", String.valueOf(DEFAULT_CLEANUP_HOUR)));

        cleanupTimer = new Timer("DataRetentionService", true);

        // Schedule daily cleanup task
        long delayMs = calculateInitialDelay(cleanupHour);
        long periodMs = 24 * 60 * 60 * 1000; // 24 hours

        cleanupTimer.scheduleAtFixedRate(
            new RetentionCleanupTask(dbConnection, retentionDays),
            delayMs,
            periodMs);

        running = true;
        System.out.println("[DataRetention] Started cleanup scheduler (retention: " + retentionDays
            + " days, cleanup at " + cleanupHour + ":00 UTC)");
      } catch (Exception e) {
        System.err.println("[DataRetention] Failed to start scheduler: " + e.getMessage());
      }
    }
  }

  /**
   * Stops the data retention cleanup scheduler.
   */
  public void stop() {
    if (cleanupTimer != null) {
      cleanupTimer.cancel();
      running = false;
      System.out.println("[DataRetention] Stopped cleanup scheduler");
    }
  }

  /**
   * Manually triggers a retention cleanup run.
   *
   * @param dbConnection Database connection
   * @param retentionDays Number of days to retain data
   */
  public void purgeExpiredData(Connection dbConnection, int retentionDays) {
    try {
      long cutoffTime = System.currentTimeMillis() - (retentionDays * 24 * 60 * 60 * 1000L);

      // Purge conversation history
      String deleteConversations = "DELETE FROM conversation_history WHERE created_at < ?";
      try (PreparedStatement ps = dbConnection.prepareStatement(deleteConversations)) {
        ps.setLong(1, cutoffTime);
        int deletedRows = ps.executeUpdate();

        if (deletedRows > 0) {
          logAuditEntry(dbConnection, "CONVERSATION_PURGE", deletedRows, retentionDays);
          System.out.println("[DataRetention] Purged " + deletedRows + " conversation records older than "
              + retentionDays + " days");
        }
      }

      // Purge learning phrases if older than retention period
      String deletePhrases = "DELETE FROM learned_phrases WHERE created_at < ?";
      try (PreparedStatement ps = dbConnection.prepareStatement(deletePhrases)) {
        ps.setLong(1, cutoffTime);
        int deletedPhrases = ps.executeUpdate();

        if (deletedPhrases > 0) {
          logAuditEntry(dbConnection, "PHRASE_PURGE", deletedPhrases, retentionDays);
          System.out.println("[DataRetention] Purged " + deletedPhrases + " learned phrase records");
        }
      }

      // Purge activity logs
      String deleteLogs = "DELETE FROM activity_logs WHERE created_at < ?";
      try (PreparedStatement ps = dbConnection.prepareStatement(deleteLogs)) {
        ps.setLong(1, cutoffTime);
        int deletedLogs = ps.executeUpdate();

        if (deletedLogs > 0) {
          logAuditEntry(dbConnection, "LOG_PURGE", deletedLogs, retentionDays);
        }
      }

      dbConnection.commit();
    } catch (Exception e) {
      System.err.println("[DataRetention] Purge failed: " + e.getMessage());
    }
  }

  /**
   * Deletes all data associated with a specific user (GDPR right to be forgotten).
   *
   * @param dbConnection Database connection
   * @param userId The Discord user ID
   */
  public void deleteUserData(Connection dbConnection, String userId) {
    try {
      String deleteConversations = "DELETE FROM conversation_history WHERE user_id = ?";
      try (PreparedStatement ps = dbConnection.prepareStatement(deleteConversations)) {
        ps.setString(1, userId);
        int deleted = ps.executeUpdate();
        logAuditEntry(dbConnection, "USER_DELETION_REQUEST", deleted, 0);
        System.out.println("[DataRetention] Deleted " + deleted + " records for user " + userId);
      }

      dbConnection.commit();
    } catch (Exception e) {
      System.err.println("[DataRetention] User deletion failed: " + e.getMessage());
    }
  }

  /**
   * Creates an audit table if it doesn't exist.
   *
   * @param dbConnection Database connection
   */
  private void ensureAuditTable(Connection dbConnection) throws Exception {
    String createTable = "CREATE TABLE IF NOT EXISTS " + AUDIT_TABLE + " (" +
        "id INTEGER PRIMARY KEY AUTOINCREMENT," +
        "event_type TEXT NOT NULL," +
        "record_count INTEGER DEFAULT 0," +
        "retention_days INTEGER DEFAULT 0," +
        "timestamp INTEGER NOT NULL," +
        "details TEXT" +
        ")";

    try (java.sql.Statement st = dbConnection.createStatement()) {
      st.execute(createTable);
    }
  }

  /**
   * Logs a data retention/purge event to the audit table.
   *
   * @param dbConnection Database connection
   * @param eventType Type of retention event (CONVERSATION_PURGE, USER_DELETION_REQUEST, etc.)
   * @param recordCount Number of records affected
   * @param retentionDays Retention policy applied
   */
  private void logAuditEntry(Connection dbConnection, String eventType, int recordCount,
      int retentionDays) {
    try {
      String insertAudit = "INSERT INTO " + AUDIT_TABLE
          + " (event_type, record_count, retention_days, timestamp) VALUES (?, ?, ?, ?)";
      try (PreparedStatement ps = dbConnection.prepareStatement(insertAudit)) {
        ps.setString(1, eventType);
        ps.setInt(2, recordCount);
        ps.setInt(3, retentionDays);
        ps.setLong(4, System.currentTimeMillis());
        ps.executeUpdate();
      }
    } catch (Exception e) {
      System.err.println("[DataRetention] Failed to log audit entry: " + e.getMessage());
    }
  }

  /**
   * Calculates milliseconds until the next scheduled cleanup time.
   *
   * @param cleanupHour Hour (0-23) when cleanup should occur
   * @return Milliseconds until next cleanup
   */
  private long calculateInitialDelay(int cleanupHour) {
    Instant now = Instant.now();
    Instant nextCleanup = now.truncatedTo(ChronoUnit.DAYS)
        .plusSeconds(cleanupHour * 3600L);

    if (nextCleanup.isBefore(now)) {
      nextCleanup = nextCleanup.plusSeconds(24 * 3600);
    }

    return nextCleanup.toEpochMilli() - now.toEpochMilli();
  }

  /**
   * Inner class for the scheduled cleanup task.
   */
  private static class RetentionCleanupTask extends TimerTask {

    private final Connection dbConnection;
    private final int retentionDays;

    RetentionCleanupTask(Connection dbConnection, int retentionDays) {
      this.dbConnection = dbConnection;
      this.retentionDays = retentionDays;
    }

    @Override
    public void run() {
      System.out.println("[DataRetention] Starting scheduled cleanup task...");
      try {
        DataRetentionService service = new DataRetentionService();
        service.purgeExpiredData(dbConnection, retentionDays);
      } catch (Exception e) {
        System.err.println("[DataRetention] Scheduled cleanup task failed: " + e.getMessage());
      }
    }
  }
}
