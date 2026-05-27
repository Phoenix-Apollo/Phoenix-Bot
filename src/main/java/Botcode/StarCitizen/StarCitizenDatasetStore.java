package Botcode.StarCitizen;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;

/**
 * Lightweight SQLite store for normalized dataset payloads.
 */
public class StarCitizenDatasetStore {

  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final String DEFAULT_DB_PATH = "data/starcitizen.db";
  private static volatile boolean initialized = false;

  private StarCitizenDatasetStore() {
  }

  private static String dbPath() {
    String env = System.getenv("SC_DB_PATH");
    return (env == null || env.isBlank()) ? DEFAULT_DB_PATH : env.trim();
  }

  private static String jdbcUrl() {
    return "jdbc:sqlite:" + dbPath();
  }

  private static synchronized void ensureInit() {
      if (initialized) {
          return;
      }
    try {
      File dbFile = new File(dbPath());
      File parent = dbFile.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }

      try (Connection conn = DriverManager.getConnection(jdbcUrl());
          Statement st = conn.createStatement()) {
        st.execute(
            "CREATE TABLE IF NOT EXISTS datasets ("
                + "dataset TEXT PRIMARY KEY,"
                + "payload TEXT NOT NULL,"
                + "refreshed_at INTEGER NOT NULL,"
                + "source TEXT DEFAULT '',"
                + "status TEXT DEFAULT ''"
                + ")");
      }
      initialized = true;
    } catch (Exception e) {
      System.out.println("[DatasetStore] Initialization failed: " + e.getMessage());
    }
  }

  public static JsonNode loadDataset(String dataset) {
      if (dataset == null || dataset.isBlank()) {
          return null;
      }
    ensureInit();
    try (Connection conn = DriverManager.getConnection(jdbcUrl());
        PreparedStatement ps =
            conn.prepareStatement("SELECT payload FROM datasets WHERE dataset = ?")) {
      ps.setString(1, dataset);
      try (ResultSet rs = ps.executeQuery()) {
          if (!rs.next()) {
              return null;
          }
        String payload = rs.getString(1);
          if (payload == null || payload.isBlank()) {
              return null;
          }
        JsonNode json = MAPPER.readTree(payload);
        System.out.println("[DatasetStore] Loaded " + dataset + " from SQLite");
        return json;
      }
    } catch (Exception e) {
      System.out.println("[DatasetStore] Load failed for " + dataset + ": " + e.getMessage());
      return null;
    }
  }

  public static void saveDataset(String dataset, JsonNode payload, String source, String status) {
      if (dataset == null || dataset.isBlank() || payload == null) {
          return;
      }
    ensureInit();
    String sql =
        "INSERT INTO datasets(dataset, payload, refreshed_at, source, status) VALUES (?, ?, ?, ?, ?) "
            + "ON CONFLICT(dataset) DO UPDATE SET payload=excluded.payload, refreshed_at=excluded.refreshed_at, source=excluded.source, status=excluded.status";
    try (Connection conn = DriverManager.getConnection(jdbcUrl());
        PreparedStatement ps = conn.prepareStatement(sql)) {
      ps.setString(1, dataset);
      ps.setString(2, MAPPER.writeValueAsString(payload));
      ps.setLong(3, System.currentTimeMillis());
      ps.setString(4, source == null ? "" : source);
      ps.setString(5, status == null ? "" : status);
      ps.executeUpdate();
    } catch (Exception e) {
      System.out.println("[DatasetStore] Save failed for " + dataset + ": " + e.getMessage());
    }
  }

  public static long getLastRefreshMs(String dataset) {
      if (dataset == null || dataset.isBlank()) {
          return 0;
      }
    ensureInit();
    try (Connection conn = DriverManager.getConnection(jdbcUrl());
        PreparedStatement ps =
            conn.prepareStatement("SELECT refreshed_at FROM datasets WHERE dataset = ?")) {
      ps.setString(1, dataset);
      try (ResultSet rs = ps.executeQuery()) {
          if (rs.next()) {
              return rs.getLong(1);
          }
        return 0;
      }
    } catch (Exception e) {
      return 0;
    }
  }
}
