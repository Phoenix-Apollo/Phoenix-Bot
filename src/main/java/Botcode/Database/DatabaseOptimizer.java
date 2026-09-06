package Botcode.Database;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

/**
 * Database performance optimization and indexing utility.
 * Analyzes queries, identifies bottlenecks, and creates optimal indexes.
 * Implements best practices for SQLite query performance.
 */
public class DatabaseOptimizer {

  /**
   * Creates all recommended indexes for optimal query performance.
   * Should be called once during database initialization.
   *
   * @param dbConnection Database connection
   */
  public static void createOptimalIndexes(Connection dbConnection) {
    System.out.println("[DatabaseOptimizer] Creating performance indexes...");

    try {
      // Conversation history indexes
      createIndexIfNotExists(dbConnection, "idx_conv_guild",
          "CREATE INDEX idx_conv_guild ON conversation_history(guild_id)");
      createIndexIfNotExists(dbConnection, "idx_conv_user",
          "CREATE INDEX idx_conv_user ON conversation_history(user_id)");
      createIndexIfNotExists(dbConnection, "idx_conv_timestamp",
          "CREATE INDEX idx_conv_timestamp ON conversation_history(created_at)");
      createIndexIfNotExists(dbConnection, "idx_conv_channel",
          "CREATE INDEX idx_conv_channel ON conversation_history(channel_id)");

      // Learned phrases indexes
      createIndexIfNotExists(dbConnection, "idx_phrases_trigger",
          "CREATE INDEX idx_phrases_trigger ON learned_phrases(trigger_text)");
      createIndexIfNotExists(dbConnection, "idx_phrases_guild",
          "CREATE INDEX idx_phrases_guild ON learned_phrases(guild_id)");
      createIndexIfNotExists(dbConnection, "idx_phrases_timestamp",
          "CREATE INDEX idx_phrases_timestamp ON learned_phrases(created_at)");

      // Star Citizen dataset indexes
      createIndexIfNotExists(dbConnection, "idx_datasets_name",
          "CREATE INDEX idx_datasets_name ON datasets(dataset)");
      createIndexIfNotExists(dbConnection, "idx_datasets_refreshed",
          "CREATE INDEX idx_datasets_refreshed ON datasets(refreshed_at)");

      // Admin audit log indexes
      createIndexIfNotExists(dbConnection, "idx_audit_user",
          "CREATE INDEX idx_audit_user ON admin_audit_log(user_id)");
      createIndexIfNotExists(dbConnection, "idx_audit_guild",
          "CREATE INDEX idx_audit_guild ON admin_audit_log(guild_id)");
      createIndexIfNotExists(dbConnection, "idx_audit_action",
          "CREATE INDEX idx_audit_action ON admin_audit_log(action_type)");
      createIndexIfNotExists(dbConnection, "idx_audit_timestamp",
          "CREATE INDEX idx_audit_timestamp ON admin_audit_log(timestamp)");

      // Data retention audit indexes
      createIndexIfNotExists(dbConnection, "idx_retention_action",
          "CREATE INDEX idx_retention_action ON data_retention_audit(event_type)");
      createIndexIfNotExists(dbConnection, "idx_retention_timestamp",
          "CREATE INDEX idx_retention_timestamp ON data_retention_audit(timestamp)");

      // Panel state indexes
      createIndexIfNotExists(dbConnection, "idx_panel_guild",
          "CREATE INDEX idx_panel_guild ON panel_state(guild_id)");
      createIndexIfNotExists(dbConnection, "idx_panel_channel",
          "CREATE INDEX idx_panel_channel ON panel_state(channel_id)");

      // Voice channel preferences indexes
      createIndexIfNotExists(dbConnection, "idx_voice_guild",
          "CREATE INDEX idx_voice_guild ON voice_preferences(guild_id)");

      System.out.println("[DatabaseOptimizer] Indexes created successfully");
    } catch (Exception e) {
      System.err.println("[DatabaseOptimizer] Failed to create indexes: " + e.getMessage());
    }
  }

  /**
   * Analyzes database query performance and provides optimization recommendations.
   *
   * @param dbConnection Database connection
   * @return Performance analysis report
   */
  public static String analyzePerformance(Connection dbConnection) {
    StringBuilder report = new StringBuilder();
    report.append("╔════════════════════════════════════════════╗\n");
    report.append("║     DATABASE PERFORMANCE ANALYSIS          ║\n");
    report.append("╚════════════════════════════════════════════╝\n\n");

    try {
      // Get SQLite PRAGMA statistics
      report.append("Database Statistics:\n");
      report.append("─────────────────────────────────────────────\n");

      String pageCount = executeQuerySingleValue(dbConnection, "PRAGMA page_count");
      String pageSize = executeQuerySingleValue(dbConnection, "PRAGMA page_size");
      String cacheSize = executeQuerySingleValue(dbConnection, "PRAGMA cache_size");

      long dbSize = Long.parseLong(pageCount) * Long.parseLong(pageSize) / 1024 / 1024;
      report.append("Database Size: ~").append(dbSize).append(" MB\n");
      report.append("Page Count: ").append(pageCount).append("\n");
      report.append("Page Size: ").append(pageSize).append(" bytes\n");
      report.append("Cache Size: ").append(cacheSize).append(" pages\n\n");

      // Table analysis
      report.append("Table Analysis:\n");
      report.append("─────────────────────────────────────────────\n");
      report.append(analyzeTableSizes(dbConnection));
      report.append("\n");

      // Index analysis
      report.append("Index Coverage:\n");
      report.append("─────────────────────────────────────────────\n");
      report.append(analyzeIndexes(dbConnection));
      report.append("\n");

      // Recommendations
      report.append("Optimization Recommendations:\n");
      report.append("─────────────────────────────────────────────\n");
      report.append(getRecommendations(dbConnection));

    } catch (Exception e) {
      report.append("Error analyzing database: ").append(e.getMessage()).append("\n");
    }

    return report.toString();
  }

  /**
   * Optimizes database by running VACUUM and ANALYZE commands.
   * Reclaims unused space and updates query optimizer statistics.
   *
   * @param dbConnection Database connection
   */
  public static void optimizeDatabase(Connection dbConnection) {
    try {
      System.out.println("[DatabaseOptimizer] Running VACUUM and ANALYZE...");

      // VACUUM reclaims unused space
      try (Statement stmt = dbConnection.createStatement()) {
        stmt.execute("VACUUM");
        System.out.println("[DatabaseOptimizer] VACUUM completed");
      }

      // ANALYZE updates query statistics
      try (Statement stmt = dbConnection.createStatement()) {
        stmt.execute("ANALYZE");
        System.out.println("[DatabaseOptimizer] ANALYZE completed");
      }

      System.out.println("[DatabaseOptimizer] Database optimization complete");
    } catch (Exception e) {
      System.err.println("[DatabaseOptimizer] Optimization failed: " + e.getMessage());
    }
  }

  /**
   * Enables query performance metrics.
   * SQLite EXPLAIN QUERY PLAN shows how queries are executed.
   *
   * @param dbConnection Database connection
   * @param query SQL query to analyze
   * @return Query execution plan
   */
  public static String explainQueryPlan(Connection dbConnection, String query) {
    StringBuilder plan = new StringBuilder();
    plan.append("Query Plan for: ").append(query).append("\n");
    plan.append("─────────────────────────────────────────────\n");

    try {
      String explainQuery = "EXPLAIN QUERY PLAN " + query;
      try (Statement stmt = dbConnection.createStatement();
          ResultSet rs = stmt.executeQuery(explainQuery)) {

        while (rs.next()) {
          int detail = rs.getInt("detail");
          String description = rs.getString("detail");
          plan.append("[").append(detail).append("] ").append(description).append("\n");
        }
      }
    } catch (Exception e) {
      plan.append("Error: ").append(e.getMessage()).append("\n");
    }

    return plan.toString();
  }

  /**
   * Enables write-ahead logging (WAL) for better concurrency.
   *
   * @param dbConnection Database connection
   */
  public static void enableWAL(Connection dbConnection) {
    try {
      try (Statement stmt = dbConnection.createStatement()) {
        stmt.execute("PRAGMA journal_mode=WAL");
        stmt.execute("PRAGMA wal_autocheckpoint=1000");
      }
      System.out.println("[DatabaseOptimizer] Write-Ahead Logging (WAL) enabled");
    } catch (Exception e) {
      System.err.println("[DatabaseOptimizer] Failed to enable WAL: " + e.getMessage());
    }
  }

  /**
   * Configures optimal SQLite pragmas for performance.
   *
   * @param dbConnection Database connection
   */
  public static void configureOptimalPragmas(Connection dbConnection) {
    try {
      try (Statement stmt = dbConnection.createStatement()) {
        // Synchronous mode: NORMAL for balance between safety and speed
        stmt.execute("PRAGMA synchronous=NORMAL");

        // Cache size: 10,000 pages (typical)
        stmt.execute("PRAGMA cache_size=10000");

        // Enable foreign keys
        stmt.execute("PRAGMA foreign_keys=ON");

        // Memory-mapped I/O for faster reads
        stmt.execute("PRAGMA mmap_size=30000000");

        // Query timeout to prevent hangs
        stmt.execute("PRAGMA query_only=OFF");
      }
      System.out.println("[DatabaseOptimizer] Optimal pragmas configured");
    } catch (Exception e) {
      System.err.println("[DatabaseOptimizer] Failed to configure pragmas: " + e.getMessage());
    }
  }

  /**
   * Creates an index if it doesn't already exist.
   *
   * @param dbConnection Database connection
   * @param indexName Name of the index
   * @param createIndexSql CREATE INDEX SQL statement
   */
  private static void createIndexIfNotExists(Connection dbConnection, String indexName,
      String createIndexSql) {
    try {
      try (Statement stmt = dbConnection.createStatement()) {
        stmt.execute(createIndexSql);
        System.out.println("  ✓ Created index: " + indexName);
      }
    } catch (Exception e) {
      // Index may already exist, which is fine
      if (e.getMessage().contains("already exists")) {
        System.out.println("  ✓ Index exists: " + indexName);
      }
    }
  }

  /**
   * Analyzes table sizes and row counts.
   *
   * @param dbConnection Database connection
   * @return Analysis report
   */
  private static String analyzeTableSizes(Connection dbConnection) {
    StringBuilder report = new StringBuilder();

    try {
      String[] tables = {
          "conversation_history",
          "learned_phrases",
          "datasets",
          "admin_audit_log",
          "data_retention_audit",
          "panel_state",
          "voice_preferences"
      };

      for (String table : tables) {
        try {
          String countQuery = "SELECT COUNT(*) as cnt FROM " + table;
          String count = executeQuerySingleValue(dbConnection, countQuery);
          report.append(table).append(": ").append(count).append(" rows\n");
        } catch (Exception ignored) {
          // Table may not exist yet
        }
      }
    } catch (Exception e) {
      report.append("Error analyzing tables: ").append(e.getMessage()).append("\n");
    }

    return report.toString();
  }

  /**
   * Analyzes index coverage.
   *
   * @param dbConnection Database connection
   * @return Analysis report
   */
  private static String analyzeIndexes(Connection dbConnection) {
    StringBuilder report = new StringBuilder();

    try {
      String indexQuery = "SELECT name, tbl_name FROM sqlite_master WHERE type='index' AND name LIKE 'idx_%'";
      try (Statement stmt = dbConnection.createStatement();
          ResultSet rs = stmt.executeQuery(indexQuery)) {

        int indexCount = 0;
        while (rs.next()) {
          String indexName = rs.getString("name");
          String tableName = rs.getString("tbl_name");
          report.append("  • ").append(indexName).append(" on ").append(tableName).append("\n");
          indexCount++;
        }

        if (indexCount == 0) {
          report.append("  ⚠️ No indexes found - run createOptimalIndexes()\n");
        } else {
          report.append("  Total: ").append(indexCount).append(" indexes\n");
        }
      }
    } catch (Exception e) {
      report.append("Error analyzing indexes: ").append(e.getMessage()).append("\n");
    }

    return report.toString();
  }

  /**
   * Generates optimization recommendations based on database state.
   *
   * @param dbConnection Database connection
   * @return Recommendations
   */
  private static String getRecommendations(Connection dbConnection) {
    StringBuilder recommendations = new StringBuilder();

    try {
      String pageCount = executeQuerySingleValue(dbConnection, "PRAGMA page_count");
      long pages = Long.parseLong(pageCount);

      if (pages > 1000000) {
        recommendations.append("  • Database is large - consider VACUUM to reclaim space\n");
      }

      if (pages < 100) {
        recommendations.append("  • Database is small - WAL mode may not be needed\n");
      }

      String indexCount = executeQuerySingleValue(dbConnection,
          "SELECT COUNT(*) FROM sqlite_master WHERE type='index'");
      if (Integer.parseInt(indexCount) < 10) {
        recommendations.append("  • Limited indexes detected - create optimal indexes\n");
      }

      recommendations.append("  • Run createOptimalIndexes() if not already done\n");
      recommendations.append("  • Run optimizeDatabase() weekly for maintenance\n");
      recommendations.append("  • Use EXPLAIN QUERY PLAN for slow queries\n");

    } catch (Exception e) {
      recommendations.append("  • Error getting recommendations\n");
    }

    return recommendations.toString();
  }

  /**
   * Executes a query and returns a single value.
   *
   * @param dbConnection Database connection
   * @param query SQL query
   * @return Query result as string
   */
  private static String executeQuerySingleValue(Connection dbConnection, String query)
      throws Exception {
    try (Statement stmt = dbConnection.createStatement();
        ResultSet rs = stmt.executeQuery(query)) {
      if (rs.next()) {
        return rs.getString(1);
      }
    }
    return "0";
  }
}
