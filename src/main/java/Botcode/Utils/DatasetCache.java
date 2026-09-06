package Botcode.Utils;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Caches Star Citizen game data in SQLite to minimize API hits. Refreshes data
 * once every 10 days.
 */
public class DatasetCache {

  private static final String DB_PATH = "data/starcitizen.db";
  private static final long REFRESH_INTERVAL_SECONDS = 10 * 24 * 60 * 60L; // 10 days
  private static final ObjectMapper MAPPER = new ObjectMapper();

  static {
    try {
      Class.forName("org.sqlite.JDBC");
    } catch (ClassNotFoundException e) {
      System.err.println("[DatasetCache] SQLite JDBC driver not found: " + e.getMessage());
    }
  }

  /** Initialize database schema and load initial data from JSON files. */
  public static void initializeAtStartup() {
    try {
      ensureSchema();
      loadInitialData();
      System.out.println("[DatasetCache] Database initialized successfully.");
    } catch (Exception e) {
      System.err.println("[DatasetCache] Initialization failed: " + e.getMessage());
      e.printStackTrace();
    }
  }

  /**
   * Get cached dataset if fresh (within 10 days), otherwise return null so caller can
   * refresh.
   */
  public static String getCachedDataset(String datasetName) {
    if (isCacheFresh(datasetName)) {
      return loadPayload(datasetName);
    }
    return null;
  }

  /** Store or update a dataset in the cache. */
  public static void saveCachedDataset(String datasetName, String payload, String source) {
    long now = Instant.now().getEpochSecond();
    try (Connection conn = getConnection()) {
      String sql =
          "INSERT OR REPLACE INTO datasets (dataset, payload, refreshed_at, source, status) "
              + "VALUES (?, ?, ?, ?, 'ok')";
      try (PreparedStatement ps = conn.prepareStatement(sql)) {
        ps.setString(1, datasetName);
        ps.setString(2, payload);
        ps.setLong(3, now);
        ps.setString(4, source);
        ps.executeUpdate();
      }
    } catch (SQLException e) {
      System.err.println("[DatasetCache] Failed to save dataset " + datasetName + ": " + e);
    }
  }

  /** Check if a dataset exists in cache and is fresh (within 10 days). */
  private static boolean isCacheFresh(String datasetName) {
    try (Connection conn = getConnection()) {
      String sql = "SELECT refreshed_at FROM datasets WHERE dataset = ?";
      try (PreparedStatement ps = conn.prepareStatement(sql)) {
        ps.setString(1, datasetName);
        try (ResultSet rs = ps.executeQuery()) {
          if (rs.next()) {
            long refreshedAt = rs.getLong("refreshed_at");
            long age = Instant.now().getEpochSecond() - refreshedAt;
            return age < REFRESH_INTERVAL_SECONDS;
          }
        }
      }
    } catch (SQLException e) {
      System.err.println(
          "[DatasetCache] Failed to check cache freshness for " + datasetName + ": " + e);
    }
    return false;
  }

  /** Load payload for a dataset. */
  private static String loadPayload(String datasetName) {
    try (Connection conn = getConnection()) {
      String sql = "SELECT payload FROM datasets WHERE dataset = ?";
      try (PreparedStatement ps = conn.prepareStatement(sql)) {
        ps.setString(1, datasetName);
        try (ResultSet rs = ps.executeQuery()) {
          if (rs.next()) {
            return rs.getString("payload");
          }
        }
      }
    } catch (SQLException e) {
      System.err.println("[DatasetCache] Failed to load dataset " + datasetName + ": " + e);
    }
    return null;
  }

  /** Create database schema if it doesn't exist. */
  private static void ensureSchema() throws SQLException {
    try (Connection conn = getConnection();
        Statement st = conn.createStatement()) {
      st.executeUpdate(
          "CREATE TABLE IF NOT EXISTS datasets ("
              + "  dataset TEXT PRIMARY KEY,"
              + "  payload TEXT NOT NULL,"
              + "  refreshed_at INTEGER NOT NULL,"
              + "  source TEXT,"
              + "  status TEXT DEFAULT 'ok'"
              + ")");
    }
  }

  /** Load initial data from JSON files into database (first-time setup). */
  private static void loadInitialData() {
    Map<String, String> jsonFiles =
        Map.of(
            "ships", "data/ships.json",
            "weapons", "data/weapons.json",
            "components", "data/components.json",
            "commodities", "data/commodities.json",
            "refinery", "data/refinery.json",
            "locations", "data/locations.json",
            "mining", "data/mining.json");

    for (Map.Entry<String, String> entry : jsonFiles.entrySet()) {
      String datasetName = entry.getKey();
      String filePath = entry.getValue();
      File file = new File(filePath);

      // Skip if file doesn't exist
      if (!file.exists()) {
        System.out.println("[DatasetCache] Skipping " + filePath + " (file not found)");
        continue;
      }

      // Skip if already in database and fresh
      if (isCacheFresh(datasetName)) {
        System.out.println("[DatasetCache] Dataset '" + datasetName + "' already cached (fresh)");
        continue;
      }

      try {
        String payload = new String(java.nio.file.Files.readAllBytes(file.toPath()));
        saveCachedDataset(datasetName, payload, "local");
        System.out.println(
            "[DatasetCache] Loaded '"
                + datasetName
                + "' from "
                + filePath
                + " ("
                + payload.length()
                + " bytes)");
      } catch (IOException e) {
        System.err.println(
            "[DatasetCache] Failed to load initial data from " + filePath + ": " + e);
      }
    }
  }

  /** Get a database connection. */
  private static Connection getConnection() throws SQLException {
    File dbFile = new File(DB_PATH);
    if (dbFile.getParentFile() != null) {
      dbFile.getParentFile().mkdirs();
    }
    return DriverManager.getConnection("jdbc:sqlite:" + DB_PATH);
  }
}

