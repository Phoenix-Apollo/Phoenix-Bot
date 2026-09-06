package Botcode.Security;

import Botcode.Utils.Env;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.time.Instant;

/**
 * Audits and logs all admin actions for security compliance.
 * Tracks sensitive operations like data deletion, configuration changes, and token rotation.
 * Implements role-based access control and requires confirmation for destructive operations.
 */
public class AdminAuditLogger {

  public enum ActionType {
    DATA_EXPORT("DATA_EXPORT", "GDPR data subject access request"),
    DATA_DELETION("DATA_DELETION", "User data deletion (right to be forgotten)"),
    BACKUP_RESTORE("BACKUP_RESTORE", "Database backup restoration"),
    TOKEN_ROTATION("TOKEN_ROTATION", "Discord bot token rotation"),
    CACHE_CLEAR("CACHE_CLEAR", "Dataset cache cleared"),
    CONFIG_CHANGE("CONFIG_CHANGE", "Configuration modified"),
    PERMISSION_GRANT("PERMISSION_GRANT", "Admin permission granted"),
    PERMISSION_REVOKE("PERMISSION_REVOKE", "Admin permission revoked"),
    SECURITY_EVENT("SECURITY_EVENT", "Security-related event"),
    FAILED_ADMIN_ATTEMPT("FAILED_ADMIN_ATTEMPT", "Unauthorized admin command attempt");

    final String code;
    final String description;

    ActionType(String code, String description) {
      this.code = code;
      this.description = description;
    }
  }

  public enum AccessLevel {
    OWNER(3, "Guild owner"),
    ADMIN(2, "Guild administrator"),
    MODERATOR(1, "Guild moderator"),
    USER(0, "Regular user");

    final int level;
    final String label;

    AccessLevel(int level, String label) {
      this.level = level;
      this.label = label;
    }
  }

  private static final String AUDIT_TABLE = "admin_audit_log";

  /**
   * Logs an admin action to the audit trail.
   *
   * @param dbConnection Database connection
   * @param userId Discord user ID performing the action
   * @param guildId Discord guild ID where action occurred
   * @param actionType Type of admin action
   * @param targetId ID of resource being acted upon (optional)
   * @param details JSON details of the action
   * @param success Whether the action succeeded
   */
  public static void logAction(
      Connection dbConnection,
      String userId,
      String guildId,
      ActionType actionType,
      String targetId,
      String details,
      boolean success) {

    try {
      ensureAuditTable(dbConnection);

      String insertAudit = "INSERT INTO " + AUDIT_TABLE
          + " (user_id, guild_id, action_type, target_id, details, success, timestamp) "
          + "VALUES (?, ?, ?, ?, ?, ?, ?)";

      try (PreparedStatement ps = dbConnection.prepareStatement(insertAudit)) {
        ps.setString(1, userId);
        ps.setString(2, guildId);
        ps.setString(3, actionType.code);
        ps.setString(4, targetId);
        ps.setString(5, details);
        ps.setBoolean(6, success);
        ps.setLong(7, System.currentTimeMillis());
        ps.executeUpdate();

        String status = success ? "SUCCESS" : "FAILED";
        System.out.println("[AdminAudit] " + status + ": " + actionType.code
            + " by user=" + userId + " in guild=" + guildId);
      }
    } catch (Exception e) {
      System.err.println("[AdminAudit] Failed to log action: " + e.getMessage());
    }
  }

  /**
   * Checks if a user has admin permissions in a guild.
   * Hierarchy: Owner > Admin > Moderator > User
   *
   * @param member JDA Member object (null returns USER level)
   * @return AccessLevel of the user
   */
  public static AccessLevel getUserAccessLevel(net.dv8tion.jda.api.entities.Member member) {
    if (member == null) {
      return AccessLevel.USER;
    }

    if (member.isOwner()) {
      return AccessLevel.OWNER;
    }

    if (member.hasPermission(net.dv8tion.jda.api.Permission.ADMINISTRATOR)) {
      return AccessLevel.ADMIN;
    }

    if (member.hasPermission(net.dv8tion.jda.api.Permission.MANAGE_SERVER,
        net.dv8tion.jda.api.Permission.MODERATE_MEMBERS)) {
      return AccessLevel.MODERATOR;
    }

    return AccessLevel.USER;
  }

  /**
   * Checks if a user has sufficient permissions for an admin action.
   *
   * @param userLevel User's access level
   * @param requiredLevel Minimum required access level
   * @return true if user has sufficient permissions
   */
  public static boolean hasPermission(AccessLevel userLevel, AccessLevel requiredLevel) {
    return userLevel.level >= requiredLevel.level;
  }

  /**
   * Checks if an action requires multi-step confirmation.
   * Destructive operations require explicit admin confirmation.
   *
   * @param actionType The action to check
   * @return true if action requires confirmation
   */
  public static boolean requiresConfirmation(ActionType actionType) {
    return actionType == ActionType.DATA_DELETION
        || actionType == ActionType.BACKUP_RESTORE
        || actionType == ActionType.TOKEN_ROTATION
        || actionType == ActionType.CACHE_CLEAR;
  }

  /**
   * Creates the audit log table if it doesn't exist.
   *
   * @param dbConnection Database connection
   */
  private static void ensureAuditTable(Connection dbConnection) throws Exception {
    String createTable = "CREATE TABLE IF NOT EXISTS " + AUDIT_TABLE + " (" +
        "id INTEGER PRIMARY KEY AUTOINCREMENT," +
        "user_id TEXT NOT NULL," +
        "guild_id TEXT NOT NULL," +
        "action_type TEXT NOT NULL," +
        "target_id TEXT," +
        "details TEXT," +
        "success BOOLEAN DEFAULT 1," +
        "timestamp INTEGER NOT NULL" +
        ")";

    try (java.sql.Statement st = dbConnection.createStatement()) {
      st.execute(createTable);
    }

    // Create indexes for performance
    try (java.sql.Statement st = dbConnection.createStatement()) {
      st.execute("CREATE INDEX IF NOT EXISTS idx_audit_user ON " + AUDIT_TABLE + " (user_id)");
      st.execute("CREATE INDEX IF NOT EXISTS idx_audit_guild ON " + AUDIT_TABLE + " (guild_id)");
      st.execute("CREATE INDEX IF NOT EXISTS idx_audit_action ON " + AUDIT_TABLE + " (action_type)");
      st.execute("CREATE INDEX IF NOT EXISTS idx_audit_time ON " + AUDIT_TABLE + " (timestamp)");
    } catch (Exception ignored) {
      // Indexes may already exist
    }
  }

  /**
   * Logs a failed authorization attempt.
   *
   * @param dbConnection Database connection
   * @param userId User attempting unauthorized action
   * @param guildId Guild where attempt occurred
   * @param attemptedAction What the user tried to do
   */
  public static void logUnauthorizedAttempt(
      Connection dbConnection,
      String userId,
      String guildId,
      String attemptedAction) {

    logAction(dbConnection, userId, guildId, ActionType.FAILED_ADMIN_ATTEMPT,
        null, "Attempted: " + attemptedAction, false);
  }

  /**
   * Retrieves audit log entries for a specific guild.
   *
   * @param dbConnection Database connection
   * @param guildId Guild ID to retrieve logs for
   * @param limit Maximum number of records to return
   * @return Array of audit log entries
   */
  public static String[] getGuildAuditLog(Connection dbConnection, String guildId, int limit) {
    try {
      ensureAuditTable(dbConnection);

      String query = "SELECT * FROM " + AUDIT_TABLE
          + " WHERE guild_id = ? ORDER BY timestamp DESC LIMIT ?";

      java.sql.PreparedStatement ps = dbConnection.prepareStatement(query);
      ps.setString(1, guildId);
      ps.setInt(2, limit);

      java.sql.ResultSet rs = ps.executeQuery();
      java.util.List<String> logs = new java.util.ArrayList<>();

      while (rs.next()) {
        String entry = String.format(
            "[%s] %s by %s: %s",
            new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
                .format(new java.util.Date(rs.getLong("timestamp"))),
            rs.getString("action_type"),
            rs.getString("user_id"),
            rs.getString("details"));
        logs.add(entry);
      }

      rs.close();
      ps.close();

      return logs.toArray(new String[0]);
    } catch (Exception e) {
      System.err.println("[AdminAudit] Failed to retrieve logs: " + e.getMessage());
      return new String[0];
    }
  }
}
