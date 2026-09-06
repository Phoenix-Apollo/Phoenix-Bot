package Botcode.Security;

import java.util.regex.Pattern;

/**
 * Validates user input to prevent common injection attacks and malicious payloads.
 * Checks for SQL injection, XSS, command injection, and other attack vectors.
 */
public class InputValidator {

  // SQL injection patterns
  private static final Pattern SQL_INJECTION_PATTERN = Pattern.compile(
      "(?i).*('(\\s)*(or|and)(\\s)*'|;(\\s)*(drop|delete|insert|update|create|alter)|--|\\*/|/\\*|xp_|sp_)",
      Pattern.CASE_INSENSITIVE);

  // XSS patterns - common script injections
  private static final Pattern XSS_SCRIPT_PATTERN = Pattern.compile(
      "(?i).*(<script|javascript:|onerror=|onload=|onclick=|<iframe|<object|<embed)",
      Pattern.CASE_INSENSITIVE);

  // Command injection patterns - shell metacharacters
  private static final Pattern COMMAND_INJECTION_PATTERN = Pattern.compile(
      "[;&|`$()\\[\\]{}]");

  // Suspicious patterns that look like encoded exploits
  private static final Pattern ENCODED_INJECTION_PATTERN = Pattern.compile(
      "(?i).*(\\\\x[0-9a-f]{2}|%[0-9a-f]{2}|\\\\u[0-9a-f]{4}|&#)",
      Pattern.CASE_INSENSITIVE);

  private InputValidator() {
  }

  /**
   * Validates input for common injection attacks.
   *
   * @param input The user input to validate
   * @return true if input appears safe, false if suspicious patterns detected
   */
  public static boolean isSafe(String input) {
    if (input == null || input.isBlank()) {
      return true;
    }

    return !hasSqlInjectionPatterns(input)
        && !hasXssPatterns(input)
        && !hasCommandInjectionPatterns(input)
        && !hasEncodedInjectionPatterns(input);
  }

  /**
   * Sanitizes SQL strings to prevent injection (for display purposes only).
   * Use parameterized queries in actual database operations.
   *
   * @param value String to escape for SQL
   * @return SQL-escaped string
   */
  public static String escapeSqlString(String value) {
    if (value == null) {
      return null;
    }
    return value.replace("'", "''");
  }

  /**
   * Sanitizes HTML to prevent XSS (for display in embeds).
   *
   * @param html HTML string to sanitize
   * @return Sanitized HTML with dangerous tags removed
   */
  public static String sanitizeHtml(String html) {
    if (html == null) {
      return null;
    }

    // Remove dangerous tags and attributes
    String sanitized = html
        .replaceAll("(?i)<script[^>]*>.*?</script>", "")
        .replaceAll("(?i)<iframe[^>]*>.*?</iframe>", "")
        .replaceAll("(?i)<object[^>]*>.*?</object>", "")
        .replaceAll("(?i)<embed[^>]*>", "")
        .replaceAll("(?i)on\\w+\\s*=", "")
        .replaceAll("(?i)javascript:", "")
        .replaceAll("(?i)data:", "");

    return sanitized;
  }

  /**
   * Validates database query parameter to ensure it's not attempting injection.
   * Should be used alongside parameterized queries for defense in depth.
   *
   * @param parameter Database query parameter
   * @return true if parameter looks safe
   */
  public static boolean isValidQueryParameter(String parameter) {
    if (parameter == null || parameter.isBlank()) {
      return true;
    }

    // Block anything that looks like SQL keywords or dangerous syntax
    return !hasSqlInjectionPatterns(parameter);
  }

  /**
   * Validates a Discord user ID or guild ID format.
   *
   * @param id The ID to validate
   * @return true if ID matches expected format (numeric snowflake)
   */
  public static boolean isValidDiscordId(String id) {
    if (id == null || id.isBlank()) {
      return false;
    }
    return id.matches("\\d{17,20}");
  }

  /**
   * Checks for SQL injection patterns.
   *
   * @param input Input to check
   * @return true if SQL injection patterns detected
   */
  private static boolean hasSqlInjectionPatterns(String input) {
    return SQL_INJECTION_PATTERN.matcher(input).matches();
  }

  /**
   * Checks for XSS (cross-site scripting) patterns.
   *
   * @param input Input to check
   * @return true if XSS patterns detected
   */
  private static boolean hasXssPatterns(String input) {
    return XSS_SCRIPT_PATTERN.matcher(input).matches();
  }

  /**
   * Checks for shell command injection patterns.
   *
   * @param input Input to check
   * @return true if command injection patterns detected
   */
  private static boolean hasCommandInjectionPatterns(String input) {
    return COMMAND_INJECTION_PATTERN.matcher(input).find();
  }

  /**
   * Checks for encoded injection attempts (hex, unicode, etc.).
   *
   * @param input Input to check
   * @return true if encoded injection patterns detected
   */
  private static boolean hasEncodedInjectionPatterns(String input) {
    return ENCODED_INJECTION_PATTERN.matcher(input).matches();
  }
}
