package Botcode.Utils;

import io.github.cdimascio.dotenv.Dotenv;

/**
 * Unified environment reader: OS env first, then .env fallback.
 */
public final class Env {

  private static final Dotenv DOTENV =
      Dotenv.configure().ignoreIfMalformed().ignoreIfMissing().load();

  private Env() {
  }

  public static String get(String key) {
    String sys = System.getenv(key);
    if (sys != null && !sys.isBlank()) {
      return sys.trim();
    }
    String dot = DOTENV.get(key);
    return (dot == null || dot.isBlank()) ? null : dot.trim();
  }

  public static String getOrDefault(String key, String defaultValue) {
    String value = get(key);
    return value == null ? defaultValue : value;
  }
}
