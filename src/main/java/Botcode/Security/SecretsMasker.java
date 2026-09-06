package Botcode.Security;

import java.util.regex.Pattern;

/**
 * Redacts sensitive information (tokens, API keys, passwords) from log output.
 * Prevents accidental exposure in error messages and console logs.
 */
public class SecretsMasker {

  private static final Pattern DISCORD_TOKEN_PATTERN = Pattern.compile(
      "\\b[MN][A-Za-z0-9_-]{23,25}\\.[A-Za-z0-9_-]{6,7}\\.[A-Za-z0-9_-]{27}\\b");

  private static final Pattern API_KEY_PATTERN = Pattern.compile(
      "(['\"])?(?:api[_-]?key|apikey|secret|password)(['\"])?\s*[:=]\s*(['\"]?)([^'\"\\s]+)\\3",
      Pattern.CASE_INSENSITIVE);

  private static final Pattern BEARER_TOKEN_PATTERN = Pattern.compile(
      "(?:Bearer|Authorization)\\s+([A-Za-z0-9._-]+)", Pattern.CASE_INSENSITIVE);

  private static final Pattern DATABASE_PASSWORD_PATTERN = Pattern.compile(
      "password\\s*=\\s*([^;&\\s]+)", Pattern.CASE_INSENSITIVE);

  private static final Pattern ENV_VAR_PATTERN = Pattern.compile(
      "^([A-Z_]+KEY|[A-Z_]+TOKEN|[A-Z_]+PASSWORD|[A-Z_]+SECRET)\\s*=\\s*(.+)$",
      Pattern.MULTILINE | Pattern.CASE_INSENSITIVE);

  private SecretsMasker() {
  }

  /**
   * Redacts all known sensitive patterns from the given text.
   *
   * @param text The text to redact
   * @return The text with sensitive information masked
   */
  public static String mask(String text) {
    if (text == null) {
      return null;
    }

    String result = text;

    // Mask Discord bot tokens
    result = DISCORD_TOKEN_PATTERN.matcher(result)
        .replaceAll("[REDACTED_TOKEN]");

    // Mask API keys and secrets
    result = API_KEY_PATTERN.matcher(result)
        .replaceAll("$1$2 = $3[REDACTED_SECRET]$3");

    // Mask Bearer tokens
    result = BEARER_TOKEN_PATTERN.matcher(result)
        .replaceAll("Bearer [REDACTED_TOKEN]");

    // Mask database passwords
    result = DATABASE_PASSWORD_PATTERN.matcher(result)
        .replaceAll("password=[REDACTED_PASSWORD]");

    // Mask environment variable values
    result = ENV_VAR_PATTERN.matcher(result)
        .replaceAll("$1=[REDACTED]");

    return result;
  }

  /**
   * Masks a sensitive value to show only first 4 and last 4 characters.
   * Useful for logging that a value was present without exposing it.
   *
   * @param value The secret value
   * @return Partially masked value (e.g., "sk-a...9xyz" for API keys)
   */
  public static String partialMask(String value) {
    if (value == null || value.length() < 8) {
      return "[REDACTED]";
    }
    String start = value.substring(0, 4);
    String end = value.substring(value.length() - 4);
    return start + "..." + end;
  }
}
