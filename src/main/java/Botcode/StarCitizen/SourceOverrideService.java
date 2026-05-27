package Botcode.StarCitizen;

import Botcode.AI.AIUtils.BotConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Iterator;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Manages user-submitted data source overrides with validation and admin approval.
 */
public class SourceOverrideService {

  /**
   * Guards read/write access to the on-disk overrides file.
   */
  private static final Object LOCK = new Object();

  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final String FILE_PATH = "data/source_overrides.json";

  private SourceOverrideService() {
  }

  /**
   * Result returned to users after attempting source submission.
   */
  public record SubmitResult(boolean accepted, String message, String ticketId) {

  }

  /**
   * Validates and queues a source URL for admin approval.
   *
   * @param datasetInput dataset name or alias from user input
   * @param url          candidate source URL
   * @param userId       submitter user id
   * @param userTag      submitter tag for audit display
   * @return structured acceptance result with optional ticket id
   */
  public static SubmitResult submitSource(
      String datasetInput, String url, long userId, String userTag) {
    String dataset = normalizeDataset(datasetInput);
    if (dataset == null) {
      return new SubmitResult(
          false,
          "Unknown dataset. Try: commodities, trade_routes, ships, weapons,"
              + " components, mining, refinery, refinery_stations, salvage,"
              + " locations",
          null);
    }

    String cleanedUrl = validateUrl(url);
    if (cleanedUrl == null) {
      return new SubmitResult(
          false, "Source URL must be a safe HTTPS endpoint" + " (no localhost/private IP).", null);
    }

    ValidationResult vr = validateSourcePayload(dataset, cleanedUrl);
    if (!vr.ok) {
      return new SubmitResult(false, "Source check failed: " + vr.reason, null);
    }

    String ticket = UUID.randomUUID().toString().substring(0, 8);
    synchronized (LOCK) {
      ObjectNode root = loadRoot();
      ArrayNode pending = ensurePending(root);
      ObjectNode row = MAPPER.createObjectNode();
      row.put("ticket", ticket);
      row.put("dataset", dataset);
      row.put("url", cleanedUrl);
      row.put("submitted_by_id", userId);
      row.put("submitted_by_tag", userTag == null ? "unknown" : userTag);
      row.put("submitted_at", Instant.now().toString());
      row.put("validation_note", vr.reason);
      pending.add(row);
      saveRoot(root);
    }

    return new SubmitResult(
        true, "Source submitted and validated. Awaiting admin approval.", ticket);
  }

  /**
   * Approves the newest pending source submission for a dataset.
   *
   * @param datasetInput dataset name or alias
   * @param adminTag     admin display tag used in the response
   * @return user-facing status message
   */
  public static String approveLatest(String datasetInput, String adminTag) {
    String dataset = normalizeDataset(datasetInput);
    if (dataset == null) {
      return "Unknown dataset.";
    }

    synchronized (LOCK) {
      ObjectNode root = loadRoot();
      ArrayNode pending = ensurePending(root);
      ObjectNode latest = null;
      int latestIdx = -1;
      for (int i = pending.size() - 1; i >= 0; i--) {
        JsonNode p = pending.get(i);
        if (dataset.equals(p.path("dataset").asText(""))) {
          if (p.isObject()) {
            latest = (ObjectNode) p;
            latestIdx = i;
            break;
          }
        }
      }
      if (latest == null) {
        return "No pending source for dataset `" + dataset + "`.";
      }

      String url = latest.path("url").asText("");
      ValidationResult vr = validateSourcePayload(dataset, url);
      if (!vr.ok) {
        return "Approval blocked. Source re-check failed: " + vr.reason;
      }

      ObjectNode approved = ensureApproved(root);
      approved.put(dataset, url);
      pending.remove(latestIdx);
      saveRoot(root);
      return "Approved source for `"
          + dataset
          + "`: "
          + url
          + " (by "
          + (adminTag == null ? "admin" : adminTag)
          + ")";
    }
  }

  /**
   * Returns the currently approved source URL for a dataset.
   *
   * @param dataset canonical dataset key
   * @return approved URL, or {@code null} when none exists
   */
  public static String getApprovedUrl(String dataset) {
    synchronized (LOCK) {
      ObjectNode root = loadRoot();
      JsonNode approved = root.path("approved");
      if (!approved.isObject()) {
        return null;
      }
      String url = approved.path(dataset).asText("").trim();
      return url.isBlank() ? null : url;
    }
  }

  /**
   * Builds a markdown summary of approved and pending source overrides.
   *
   * @return formatted status text for command replies
   */
  public static String statusSummary() {
    synchronized (LOCK) {
      ObjectNode root = loadRoot();
      JsonNode approved = root.path("approved");
      JsonNode pending = root.path("pending");

      StringBuilder sb = new StringBuilder("**Source Overrides**\n");
      sb.append("Approved:\n");
      if (approved.isObject() && approved.size() > 0) {
        Iterator<String> fields = approved.fieldNames();
        while (fields.hasNext()) {
          String key = fields.next();
          sb.append("• ")
              .append(key)
              .append(" -> ")
              .append(approved.path(key).asText("-"))
              .append("\n");
        }
      } else {
        sb.append("• none\n");
      }

      sb.append("\nPending:\n");
      if (pending.isArray() && pending.size() > 0) {
        for (JsonNode p : pending) {
          sb.append("• [")
              .append(p.path("ticket").asText("-"))
              .append("] ")
              .append(p.path("dataset").asText("?"))
              .append(" -> ")
              .append(p.path("url").asText("?"))
              .append("\n");
        }
      } else {
        sb.append("• none\n");
      }
      return sb.toString().trim();
    }
  }

  /**
   * Runs a preflight fetch + normalize pass to verify a source payload is usable.
   */
  private static ValidationResult validateSourcePayload(String dataset, String url) {
    try {
      JsonNode payload = fetchJson(url);
      if (payload == null || payload.isNull()) {
        return new ValidationResult(false, "empty response");
      }

      JsonNode normalized = normalizeForDataset(dataset, payload);
      if (normalized == null || normalized.isNull() || normalized.isEmpty()) {
        return new ValidationResult(false, "normalizer produced no usable data");
      }

      if ("ships".equals(dataset)) {
        int count = normalized.isObject() ? normalized.size() : 0;
        if (count < 50) {
          return new ValidationResult(false, "ship payload too small (" + count + " entries)");
        }
      }

      return new ValidationResult(true, "preflight normalized successfully");
    } catch (Exception e) {
      return new ValidationResult(false, e.getMessage());
    }
  }

  /**
   * Applies dataset-specific normalization so raw payload shape does not matter.
   */
  private static JsonNode normalizeForDataset(String dataset, JsonNode payload) {
    return switch (dataset) {
      case "commodities" -> StarCitizenNormalizer.normalizeCommodities(payload);
      case "trade_routes" -> StarCitizenNormalizer.normalizeTradeRoutes(payload);
      case "ships" -> StarCitizenNormalizer.normalizeShips(payload);
      case "weapons" -> StarCitizenNormalizer.normalizeWeapons(payload);
      case "components" -> StarCitizenNormalizer.normalizeComponents(payload);
      case "mining" -> StarCitizenNormalizer.normalizeMining(payload);
      case "refinery" -> StarCitizenNormalizer.normalizeRefinery(payload);
      case "refinery_stations" -> StarCitizenNormalizer.normalizeRefineryStations(payload);
      case "salvage" -> StarCitizenNormalizer.normalizeSalvage(payload);
      case "locations" -> StarCitizenNormalizer.normalizeLocations(payload);
      default -> null;
    };
  }

  /**
   * Downloads JSON from a validated URL with strict content type and size checks.
   */
  private static JsonNode fetchJson(String urlString) throws Exception {
    URL url = new URL(urlString);
    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
    try {
      conn.setInstanceFollowRedirects(false);
      conn.setRequestMethod("GET");
      conn.setRequestProperty("User-Agent", "PhoenixBot/1.0");
      conn.setRequestProperty("Accept", "application/json");
      conn.setConnectTimeout(12000);
      conn.setReadTimeout(18000);

      int code = conn.getResponseCode();
      if (code < 200 || code >= 300) {
        throw new IOException("http_" + code);
      }

      String contentType = conn.getContentType();
      if (contentType == null || !isJsonLikeContentType(contentType)) {
        throw new IOException("unexpected_content_type");
      }

      int maxBytes = Math.max(16_384, BotConfig.SOURCE_OVERRIDE_MAX_DOWNLOAD_BYTES);
      try (InputStream in = conn.getInputStream()) {
        byte[] body = readBytesLimited(in, maxBytes);
        String text = new String(body, StandardCharsets.UTF_8);
        return MAPPER.readTree(text);
      }
    } finally {
      conn.disconnect();
    }
  }

  /**
   * Validates external source URLs and blocks private-network targets.
   */
  private static String validateUrl(String input) {
    if (input == null || input.isBlank()) {
      return null;
    }
    try {
      URI uri = URI.create(input.trim()).normalize();
      if (!"https".equalsIgnoreCase(uri.getScheme())) {
        return null;
      }
      String host = uri.getHost();
      if (host == null || host.isBlank()) {
        return null;
      }
      String h = host.toLowerCase(Locale.ROOT);
      if (h.equals("localhost") || h.endsWith(".localhost")) {
        return null;
      }
      int port = uri.getPort();
      if (BotConfig.SOURCE_OVERRIDE_REQUIRE_STANDARD_HTTPS_PORT && port != -1 && port != 443) {
        return null;
      }
      if (!isAllowedHost(h, BotConfig.SOURCE_OVERRIDE_ALLOWED_HOSTS)) {
        return null;
      }
      if (!hasSafeResolvedAddress(h)) {
        return null;
      }
      return uri.toASCIIString();
    } catch (Exception e) {
      return null;
    }
  }

  /**
   * Checks host against optional allow-list entries (including subdomains).
   */
  private static boolean isAllowedHost(String host, Set<String> allowedHosts) {
    if (allowedHosts == null || allowedHosts.isEmpty()) {
      return true;
    }
    for (String allowed : allowedHosts) {
      String normalized = allowed == null ? "" : allowed.trim().toLowerCase(Locale.ROOT);
      if (normalized.isBlank()) {
        continue;
      }
      if (host.equals(normalized) || host.endsWith("." + normalized)) {
        return true;
      }
    }
    return false;
  }

  /**
   * Resolves host DNS and rejects any local/private/multicast destination.
   */
  private static boolean hasSafeResolvedAddress(String host) {
    try {
      InetAddress[] addresses = InetAddress.getAllByName(host);
      if (addresses == null || addresses.length == 0) {
        return false;
      }
      for (InetAddress address : addresses) {
        if (isBlockedAddress(address)) {
          return false;
        }
      }
      return true;
    } catch (Exception e) {
      return false;
    }
  }

  /**
   * Returns true when an address is not safe for external data fetching.
   */
  private static boolean isBlockedAddress(InetAddress address) {
    return address.isAnyLocalAddress()
        || address.isLoopbackAddress()
        || address.isLinkLocalAddress()
        || address.isSiteLocalAddress()
        || address.isMulticastAddress();
  }

  /**
   * Accepts standard JSON content types and vendor JSON suffix types.
   */
  private static boolean isJsonLikeContentType(String value) {
    String lower = value.toLowerCase(Locale.ROOT);
    return lower.contains("application/json") || lower.contains("+json");
  }

  /**
   * Reads a stream into memory while enforcing a maximum payload size.
   */
  private static byte[] readBytesLimited(InputStream input, int maxBytes) throws IOException {
    byte[] buffer = new byte[8192];
    int total = 0;
    java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
    while (true) {
      int read = input.read(buffer);
      if (read < 0) {
        break;
      }
      total += read;
      if (total > maxBytes) {
        throw new IOException("payload_too_large");
      }
      out.write(buffer, 0, read);
    }
    return out.toByteArray();
  }

  /**
   * Maps user-provided dataset aliases to canonical dataset keys.
   */
  private static String normalizeDataset(String datasetInput) {
    if (datasetInput == null || datasetInput.isBlank()) {
      return null;
    }
    String d = datasetInput.trim().toLowerCase(Locale.ROOT);
    return switch (d) {
      case "commodity", "commodities" -> "commodities";
      case "trade", "trade_route", "trade_routes", "routes" -> "trade_routes";
      case "ship", "ships" -> "ships";
      case "weapon", "weapons" -> "weapons";
      case "component", "components" -> "components";
      case "mining" -> "mining";
      case "refinery" -> "refinery";
      case "refinery_stations", "refinerystations", "stations" -> "refinery_stations";
      case "salvage" -> "salvage";
      case "location", "locations" -> "locations";
      default -> null;
    };
  }

  /**
   * Loads persisted override state from disk, or returns defaults when unavailable.
   */
  private static ObjectNode loadRoot() {
    try {
      File f = new File(FILE_PATH);
      if (!f.exists()) {
        return defaultRoot();
      }
      JsonNode n = MAPPER.readTree(f);
      if (n != null && n.isObject()) {
        return (ObjectNode) n;
      }
    } catch (Exception ignored) {
    }
    return defaultRoot();
  }

  /**
   * Creates the default root structure used for source override persistence.
   */
  private static ObjectNode defaultRoot() {
    ObjectNode root = MAPPER.createObjectNode();
    root.set("approved", MAPPER.createObjectNode());
    root.set("pending", MAPPER.createArrayNode());
    return root;
  }

  /**
   * Ensures the approved map exists and returns it.
   */
  private static ObjectNode ensureApproved(ObjectNode root) {
    JsonNode n = root.path("approved");
    if (n.isObject()) {
      return (ObjectNode) n;
    }
    ObjectNode obj = MAPPER.createObjectNode();
    root.set("approved", obj);
    return obj;
  }

  /**
   * Ensures the pending submissions array exists and returns it.
   */
  private static ArrayNode ensurePending(ObjectNode root) {
    JsonNode n = root.path("pending");
    if (n.isArray()) {
      return (ArrayNode) n;
    }
    ArrayNode arr = MAPPER.createArrayNode();
    root.set("pending", arr);
    return arr;
  }

  /**
   * Persists the full override state to disk.
   */
  private static void saveRoot(ObjectNode root) {
    try {
      File f = new File(FILE_PATH);
      if (f.getParentFile() != null) {
        f.getParentFile().mkdirs();
      }
      try (Writer w =
          new OutputStreamWriter(Files.newOutputStream(f.toPath()), StandardCharsets.UTF_8)) {
        w.write(MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(root));
      }
    } catch (Exception e) {
      System.out.println("[SourceOverrideService] Failed to save overrides: " + e.getMessage());
    }
  }

  /**
   * Internal validation outcome used during submit/approve preflight checks.
   */
  private record ValidationResult(boolean ok, String reason) {

  }
}
