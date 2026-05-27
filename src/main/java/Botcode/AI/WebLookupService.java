package Botcode.AI;

import Botcode.StarCitizen.MissingDataReportService;
import Botcode.AI.AIUtils.BotConfig;
import Botcode.AI.AIUtils.SafetyGuard;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.InetAddress;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Comparator;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Safe, lightweight web lookup helper for general knowledge prompts.
 */
public class WebLookupService {

  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final HttpClient HTTP = HttpClient.newBuilder()
      .connectTimeout(Duration.ofMillis(BotConfig.WEB_LOOKUP_TIMEOUT_MS))
      .build();
  private static final String USER_AGENT =
      "PhoenixBot/1.0 (+https://github.com; compatible; web-lookup)";
  private static final Map<String, Long> ISSUE_COOLDOWN_BY_KEY = new ConcurrentHashMap<>();
  private static final Map<String, CachedLookup> CACHE_BY_QUERY = new ConcurrentHashMap<>();

  /**
   * Hard cap on how many cooldown entries we retain to prevent unbounded growth.
   */
  private static final int MAX_ISSUE_COOLDOWN_ENTRIES = 2_000;

  /**
   * Active cache/cooldown sweeper — runs every 3 minutes on a daemon thread.
   */
  private static final ScheduledExecutorService CACHE_CLEANER =
      Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "weblookup-cache-cleaner");
        t.setDaemon(true);
        return t;
      });

  static {
    CACHE_CLEANER.scheduleAtFixedRate(
        WebLookupService::evictExpiredCaches, 3, 3, TimeUnit.MINUTES);
  }

  /**
   * Removes expired cache entries and trims the issue-cooldown map. Called on a background daemon
   * thread every 3 minutes so neither map can grow forever.
   */
  private static void evictExpiredCaches() {
    // Remove expired lookup-result cache entries.
    CACHE_BY_QUERY.entrySet().removeIf(e -> e.getValue().isExpired());

    // Remove issue-cooldown entries whose window has passed.
    long now = System.currentTimeMillis();
    long maxCooldownMs = Math.max(60L, BotConfig.WEB_LOOKUP_ISSUE_COOLDOWN_SECONDS) * 1_000L;
    ISSUE_COOLDOWN_BY_KEY.entrySet().removeIf(e -> now - e.getValue() > maxCooldownMs);

    // Hard-cap failsafe: if still oversized, evict the oldest entries first.
    if (ISSUE_COOLDOWN_BY_KEY.size() > MAX_ISSUE_COOLDOWN_ENTRIES) {
      ISSUE_COOLDOWN_BY_KEY.entrySet().stream()
          .sorted(Comparator.comparingLong(Map.Entry::getValue))
          .limit(ISSUE_COOLDOWN_BY_KEY.size() - MAX_ISSUE_COOLDOWN_ENTRIES)
          .forEach(e -> ISSUE_COOLDOWN_BY_KEY.remove(e.getKey()));
    }
  }

  private WebLookupService() {
  }

  public static String tryLookup(String prompt) {
      if (!BotConfig.WEB_LOOKUP_ENABLED) {
          return null;
      }
    String query = extractLookupQuery(prompt);
      if (query == null || query.isBlank()) {
          return null;
      }
    if (SafetyGuard.isDisallowedLookupQuery(query)) {
      return "Sorry, I can't assist with that. My maximum effort days are over.";
    }

    String cacheKey = query.toLowerCase(Locale.ROOT);
    CachedLookup cached = CACHE_BY_QUERY.get(cacheKey);
    if (cached != null && !cached.isExpired()) {
      return formatLookupReply(cached.result, query);
    }

    LookupAttempt attempt = lookupWithFallback(query);
    if (attempt.result == null) {
      String reason = attempt.failureReason == null || attempt.failureReason.isBlank()
          ? "lookup_failed"
          : attempt.failureReason;
      maybeQueueLookupIssue(query, reason);

      return "Quick lookup mode is on, but both search providers missed this one.\n"
          + "Try this search: " + googleSearchUrl(query)
          + "\nNote: I logged this failure into update notes for periodic fixes.";
    }

    CACHE_BY_QUERY.put(cacheKey, new CachedLookup(attempt.result, System.currentTimeMillis()));
    return formatLookupReply(attempt.result, query);
  }

  private static String extractLookupQuery(String prompt) {
      if (prompt == null || prompt.isBlank()) {
          return null;
      }
    String lower = prompt.toLowerCase(Locale.ROOT).trim();
    boolean explicitLookup = lower.contains("look up")
        || lower.contains("lookup")
        || lower.contains("google")
        || lower.contains("search for")
        || lower.startsWith("search ")
        || lower.startsWith("wiki ")
        || lower.startsWith("wikipedia ")
        || lower.startsWith("define ")
        || lower.contains("tell me about")
        || lower.startsWith("who is ")
        || lower.startsWith("what is ")
        || lower.startsWith("who was ")
        || lower.startsWith("what was ");
      if (!explicitLookup) {
          return null;
      }

    String q = prompt.trim();
    q = q.replaceAll(
        "(?i)^\\s*(look\\s*up|lookup|search\\s+for|search|google|wiki|wikipedia|define)\\s+", "");
    q = q.replaceAll(
        "(?i)^\\s*(tell\\s+me\\s+about|who\\s+is|what\\s+is|who\\s+was|what\\s+was)\\s+", "");
    q = q.replaceAll("(?i)\\s+on\\s+google\\s*$", "");
    q = q.replaceAll("(?i)\\s+using\\s+google\\s*$", "");
    q = q.replaceAll("^[\\s\"'`]+|[\\s\"'`?.!,:;]+$", "").trim();

      if (q.length() < 2) {
          return null;
      }
    int maxChars = Math.max(40, BotConfig.WEB_LOOKUP_MAX_QUERY_CHARS);
      if (q.length() > maxChars) {
          q = q.substring(0, maxChars).trim();
      }
      if (looksLikeLocalTarget(q)) {
          return null;
      }
    return q;
  }

  private static LookupAttempt lookupWithFallback(String query) {
    LookupAttempt wiki = wikipediaLookup(query);
      if (wiki.result != null) {
          return wiki;
      }

    LookupAttempt ddg = duckDuckGoLookup(query);
      if (ddg.result != null) {
          return ddg;
      }

    String reason = wiki.failureReason != null ? wiki.failureReason : "wikipedia_failed";
    if (ddg.failureReason != null && !ddg.failureReason.isBlank()) {
      reason += "; " + ddg.failureReason;
    }
    return LookupAttempt.failed(reason);
  }

  private static LookupAttempt wikipediaLookup(String query) {
    try {
      String title = resolveWikipediaTitle(query);
      if (title == null || title.isBlank()) {
        title = query;
      }

      String summaryUrl = "https://en.wikipedia.org/api/rest_v1/page/summary/"
          + URLEncoder.encode(title, StandardCharsets.UTF_8);
      HttpResponse<String> res = sendGetWithRetry(summaryUrl);
        if (res == null) {
            return LookupAttempt.failed("wikipedia_unreachable");
        }
      if (res.statusCode() < 200 || res.statusCode() >= 300) {
        return LookupAttempt.failed("wikipedia_summary_http_" + res.statusCode());
      }

      JsonNode node = MAPPER.readTree(res.body());
      String resolvedTitle = node.path("title").asText(title);
      String extract = node.path("extract").asText("");
      if (extract.isBlank()) {
        // Fallback when summary extract is unavailable for the page.
        extract = node.path("description").asText("");
      }
      String page = node.path("content_urls").path("desktop").path("page").asText("");
        if (extract.isBlank()) {
            return LookupAttempt.failed("wikipedia_empty_extract");
        }
      if (page.isBlank()) {
        page = "https://en.wikipedia.org/wiki/" + URLEncoder.encode(resolvedTitle,
            StandardCharsets.UTF_8);
      }
      return LookupAttempt.success(new LookupResult(resolvedTitle, extract, page));
    } catch (Exception e) {
      return LookupAttempt.failed("wikipedia_exception");
    }
  }

  private static String resolveWikipediaTitle(String query) {
    try {
      String url =
          "https://en.wikipedia.org/w/api.php?action=query&list=search"
              + "&srlimit=1&format=json&srsearch="
              + URLEncoder.encode(query, StandardCharsets.UTF_8);
      HttpResponse<String> res = sendGetWithRetry(url);
        if (res == null || res.statusCode() < 200 || res.statusCode() >= 300) {
            return null;
        }

      JsonNode root = MAPPER.readTree(res.body());
      JsonNode search = root.path("query").path("search");
        if (!search.isArray() || search.isEmpty()) {
            return null;
        }
      return search.get(0).path("title").asText("");
    } catch (Exception e) {
      return null;
    }
  }

  private static LookupAttempt duckDuckGoLookup(String query) {
    try {
      String url = "https://api.duckduckgo.com/?format=json&no_html=1&skip_disambig=1&q="
          + URLEncoder.encode(query, StandardCharsets.UTF_8);
      HttpResponse<String> res = sendGetWithRetry(url);
        if (res == null) {
            return LookupAttempt.failed("duckduckgo_unreachable");
        }
      if (res.statusCode() < 200 || res.statusCode() >= 300) {
        return LookupAttempt.failed("duckduckgo_http_" + res.statusCode());
      }

      JsonNode node = MAPPER.readTree(res.body());
      String title = node.path("Heading").asText("").trim();
      String extract = node.path("AbstractText").asText("").trim();
      String page = node.path("AbstractURL").asText("").trim();

      if (extract.isBlank()) {
        JsonNode topics = node.path("RelatedTopics");
        if (topics.isArray()) {
          for (JsonNode topic : topics) {
            String text = topic.path("Text").asText("").trim();
            String firstUrl = topic.path("FirstURL").asText("").trim();
            if (!text.isBlank()) {
              extract = text;
                if (title.isBlank()) {
                    title = query;
                }
                if (page.isBlank()) {
                    page = firstUrl;
                }
              break;
            }
            JsonNode nestedTopics = topic.path("Topics");
            if (nestedTopics.isArray()) {
              for (JsonNode nested : nestedTopics) {
                String nestedText = nested.path("Text").asText("").trim();
                String nestedUrl = nested.path("FirstURL").asText("").trim();
                if (!nestedText.isBlank()) {
                  extract = nestedText;
                    if (title.isBlank()) {
                        title = query;
                    }
                    if (page.isBlank()) {
                        page = nestedUrl;
                    }
                  break;
                }
              }
            }
              if (!extract.isBlank()) {
                  break;
              }
          }
        }
      }

        if (title.isBlank()) {
            title = query;
        }
        if (extract.isBlank()) {
            return LookupAttempt.failed("duckduckgo_empty_extract");
        }
        if (page.isBlank()) {
            page = googleSearchUrl(query);
        }
      return LookupAttempt.success(new LookupResult(title, extract, page));
    } catch (Exception e) {
      return LookupAttempt.failed("duckduckgo_exception");
    }
  }

  private static HttpResponse<String> sendGetWithRetry(String url) throws Exception {
    int attempts = Math.max(1, BotConfig.WEB_LOOKUP_RETRY_COUNT);
    int maxBytes = Math.max(64_000, BotConfig.WEB_LOOKUP_MAX_RESPONSE_BYTES);
    for (int i = 0; i < attempts; i++) {
      try {
        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
            .timeout(Duration.ofMillis(BotConfig.WEB_LOOKUP_TIMEOUT_MS))
            .header("Accept", "application/json")
            .header("User-Agent", USER_AGENT)
            .GET()
            .build();
        HttpResponse<String> res = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
        if (res.body() != null && res.body().getBytes(StandardCharsets.UTF_8).length > maxBytes) {
          return null;
        }
        if (res.statusCode() >= 500 || res.statusCode() == 429) {
            if (i < attempts - 1) {
                Thread.sleep(180L * (i + 1));
            }
          continue;
        }
        return res;
      } catch (Exception e) {
          if (i >= attempts - 1) {
              throw e;
          }
        Thread.sleep(180L * (i + 1));
      }
    }
    return null;
  }

  private static void maybeQueueLookupIssue(String query, String reason) {
    long now = System.currentTimeMillis();
    String key =
        (reason == null ? "lookup_failed" : reason) + "::" + query.toLowerCase(Locale.ROOT);
    long cooldownMs = Math.max(60L, BotConfig.WEB_LOOKUP_ISSUE_COOLDOWN_SECONDS) * 1000L;
    long last = ISSUE_COOLDOWN_BY_KEY.getOrDefault(key, 0L);
      if (now - last < cooldownMs) {
          return;
      }
    ISSUE_COOLDOWN_BY_KEY.put(key, now);

    String shortReason = reason == null ? "lookup_failed" : reason;
      if (shortReason.length() > 220) {
          shortReason = shortReason.substring(0, 220);
      }
    MissingDataReportService.queueOperationalIssue(
        "web_lookup",
        query,
        "Automated runtime note: provider lookup failed (" + shortReason + ")"
    );
  }

  private static String googleSearchUrl(String query) {
    return "https://www.google.com/search?q=" + URLEncoder.encode(query, StandardCharsets.UTF_8);
  }

  private static String trimSummary(String text, int maxChars) {
      if (text == null) {
          return "";
      }
    String clean = text.replaceAll("\\s+", " ").trim();
      if (clean.length() <= maxChars) {
          return clean;
      }
    int cut = Math.max(0, clean.lastIndexOf(' ', maxChars - 3));
      if (cut < 40) {
          cut = maxChars - 3;
      }
    return clean.substring(0, cut).trim() + "...";
  }

  private static String formatLookupReply(LookupResult result, String query) {
    String summary = trimSummary(result.summary, BotConfig.WEB_LOOKUP_MAX_SUMMARY_CHARS);
    String safeUrl = isSafePublicHttpsUrl(result.url) ? result.url : googleSearchUrl(query);
    return "Quick lookup on **" + result.title + "**:\n"
        + summary + "\n"
        + "Source: " + safeUrl + "\n"
        + "More results: " + googleSearchUrl(query);
  }

  // Matches bare RFC-1918 / loopback IP prefixes only — avoids false positives on
  // domain names that contain digit sequences (e.g. "action10.com").
  private static final java.util.regex.Pattern LOCAL_IP_PATTERN =
      java.util.regex.Pattern.compile(
          "(?:^|\\s|[/:])" +
              "(?:localhost" +
              "|127\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}" +
              "|0\\.0\\.0\\.0" +
              "|10\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}" +
              "|192\\.168\\.\\d{1,3}\\.\\d{1,3}" +
              "|172\\.(?:1[6-9]|2\\d|3[01])\\.\\d{1,3}\\.\\d{1,3}" +
              "|\\[::1\\]" +
              "|\\[fc[0-9a-f]{2}:" +
              ")");

  private static boolean looksLikeLocalTarget(String query) {
    String lower = query.toLowerCase(Locale.ROOT);
      if (lower.contains("internal")) {
          return true;
      }
    return LOCAL_IP_PATTERN.matcher(lower).find();
  }

  private static boolean isSafePublicHttpsUrl(String value) {
      if (value == null || value.isBlank()) {
          return false;
      }
    try {
      URI uri = URI.create(value.trim());
        if (!"https".equalsIgnoreCase(uri.getScheme())) {
            return false;
        }
      String host = uri.getHost();
        if (host == null || host.isBlank()) {
            return false;
        }
        if (host.equalsIgnoreCase("localhost") || host.endsWith(".localhost")) {
            return false;
        }
      for (InetAddress address : InetAddress.getAllByName(host)) {
        if (address.isAnyLocalAddress()
            || address.isLoopbackAddress()
            || address.isSiteLocalAddress()
            || address.isLinkLocalAddress()) {
          return false;
        }
      }
      return true;
    } catch (Exception e) {
      return false;
    }
  }

  private static class LookupAttempt {

    final LookupResult result;
    final String failureReason;

    private LookupAttempt(LookupResult result, String failureReason) {
      this.result = result;
      this.failureReason = failureReason;
    }

    static LookupAttempt success(LookupResult result) {
      return new LookupAttempt(result, null);
    }

    static LookupAttempt failed(String reason) {
      return new LookupAttempt(null, reason);
    }
  }

  private static class LookupResult {

    final String title;
    final String summary;
    final String url;

    LookupResult(String title, String summary, String url) {
      this.title = title;
      this.summary = summary;
      this.url = url;
    }
  }

  private static class CachedLookup {

    final LookupResult result;
    final long cachedAtMs;

    CachedLookup(LookupResult result, long cachedAtMs) {
      this.result = result;
      this.cachedAtMs = cachedAtMs;
    }

    boolean isExpired() {
      long ttlMs = Math.max(30L, BotConfig.WEB_LOOKUP_CACHE_SECONDS) * 1000L;
      return System.currentTimeMillis() - cachedAtMs > ttlMs;
    }
  }
}

