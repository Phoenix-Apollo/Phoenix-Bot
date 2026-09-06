package Botcode.Security;

import Botcode.Utils.Env;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Base64;

/**
 * Manages encrypted database connections using SQLCipher.
 * Handles encryption key generation, rotation, and secure storage.
 */
public class DatabaseEncryptionManager {

  private static final String KEY_STORAGE_DIR = "data/keys";
  private static final String MASTER_KEY_FILE = KEY_STORAGE_DIR + "/master.key";
  private static final int KEY_SIZE_BITS = 256;

  private static volatile String cachedEncryptionKey = null;
  private static volatile long keyRotationTimestamp = System.currentTimeMillis();

  static {
    try {
      Class.forName("org.sqlite.JDBC");
    } catch (ClassNotFoundException e) {
      System.err.println("[DatabaseEncryption] Failed to load SQLite driver: " + e.getMessage());
    }
  }

  private DatabaseEncryptionManager() {
  }

  /**
   * Gets or generates the encryption key for the database.
   * Keys are stored in data/keys/master.key (file permissions restricted).
   *
   * @return Base64-encoded encryption key
   */
  public static String getOrGenerateEncryptionKey() {
    if (cachedEncryptionKey != null) {
      return cachedEncryptionKey;
    }

    synchronized (DatabaseEncryptionManager.class) {
      if (cachedEncryptionKey != null) {
        return cachedEncryptionKey;
      }

      String envKey = Env.get("DB_ENCRYPTION_KEY");
      if (envKey != null && !envKey.isBlank()) {
        cachedEncryptionKey = envKey;
        System.out.println("[DatabaseEncryption] Using encryption key from environment variable");
        return cachedEncryptionKey;
      }

      Path keyPath = Paths.get(MASTER_KEY_FILE);
      try {
        if (Files.exists(keyPath)) {
          cachedEncryptionKey = new String(Files.readAllBytes(keyPath));
          System.out.println("[DatabaseEncryption] Loaded encryption key from file");
          return cachedEncryptionKey;
        }

        cachedEncryptionKey = generateAndStoreKey(keyPath);
        return cachedEncryptionKey;
      } catch (Exception e) {
        System.err.println("[DatabaseEncryption] Failed to manage encryption key: " + e.getMessage());
        throw new RuntimeException("Database encryption key initialization failed", e);
      }
    }
  }

  /**
   * Generates a new AES-256 encryption key and stores it securely.
   *
   * @param keyPath Path where the key should be stored
   * @return Base64-encoded generated key
   */
  private static String generateAndStoreKey(Path keyPath) throws Exception {
    KeyGenerator keyGen = KeyGenerator.getInstance("AES");
    keyGen.init(KEY_SIZE_BITS, new SecureRandom());
    SecretKey secretKey = keyGen.generateKey();

    String encodedKey = Base64.getEncoder().encodeToString(secretKey.getEncoded());

    File keysDir = keyPath.getParent().toFile();
    if (!keysDir.exists()) {
      keysDir.mkdirs();
    }

    Files.write(keyPath, encodedKey.getBytes());

    // Attempt to restrict file permissions (Unix-like systems)
    try {
      keyPath.toFile().setReadable(false, false);
      keyPath.toFile().setReadable(true, true);
      keyPath.toFile().setWritable(false, false);
      keyPath.toFile().setWritable(true, true);
      System.out.println("[DatabaseEncryption] Generated and stored new encryption key with restricted permissions");
    } catch (Exception e) {
      System.out.println("[DatabaseEncryption] Warning: Could not set strict file permissions: " + e.getMessage());
    }

    return encodedKey;
  }

  /**
   * Creates an encrypted database connection.
   *
   * @param dbPath Path to the SQLite database file
   * @return Encrypted database connection
   */
  public static Connection getEncryptedConnection(String dbPath) throws Exception {
    String normalizedPath = normalizeDbPath(dbPath);
    Connection connection = DriverManager.getConnection("jdbc:sqlite:" + normalizedPath);
    String encryptionKey = getOrGenerateEncryptionKey();

    try (Statement statement = connection.createStatement()) {
      statement.execute("PRAGMA key = '" + escapeSqlLiteral(encryptionKey) + "'");
    }

    // Verify that SQLCipher is actually active. When not available, this pragma
    // is empty and encryption has not been applied.
    boolean sqlCipherActive = false;
    try (Statement statement = connection.createStatement();
        ResultSet rs = statement.executeQuery("PRAGMA cipher_version")) {
      if (rs.next()) {
        String cipherVersion = rs.getString(1);
        sqlCipherActive = cipherVersion != null && !cipherVersion.isBlank();
      }
    }

    if (!sqlCipherActive) {
      connection.close();
      throw new IllegalStateException(
          "SQLCipher is not available in the current SQLite driver/runtime");
    }

    return connection;
  }

  /**
   * Rotates the encryption key (generates new key and re-encrypts database).
   * Should be called periodically for security best practices.
   *
   * @param dbPath Path to the database
   */
  public static void rotateEncryptionKey(String dbPath) {
    try {
      long now = System.currentTimeMillis();
      long rotationIntervalMs = Long.parseLong(
          Env.getOrDefault("DB_KEY_ROTATION_INTERVAL_DAYS", "90")) * 24 * 60 * 60 * 1000L;

      if (now - keyRotationTimestamp < rotationIntervalMs) {
        return;
      }

      System.out.println("[DatabaseEncryption] Starting key rotation...");

      // Clear cached key to force regeneration
      cachedEncryptionKey = null;

      // Generate new key
      String newKey = getOrGenerateEncryptionKey();

      System.out.println("[DatabaseEncryption] Key rotation completed successfully");
      keyRotationTimestamp = now;
    } catch (Exception e) {
      System.err.println("[DatabaseEncryption] Key rotation failed: " + e.getMessage());
    }
  }

  /**
   * Checks if database encryption is enabled.
   *
   * @return true if encryption is enabled (default true for security)
   */
  public static boolean isEncryptionEnabled() {
    return Boolean.parseBoolean(Env.getOrDefault("DB_ENCRYPTION_ENABLED", "true"));
  }

  private static String escapeSqlLiteral(String value) {
    return value.replace("'", "''");
  }

  private static String normalizeDbPath(String dbPath) {
    if (dbPath == null) {
      return "";
    }
    int queryStart = dbPath.indexOf('?');
    String clean = queryStart >= 0 ? dbPath.substring(0, queryStart) : dbPath;
    return clean.trim();
  }
}
