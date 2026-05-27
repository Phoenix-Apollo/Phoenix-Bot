package Botcode.StarCitizen;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.File;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Records user-submitted missing-data reports and attempts a live refresh + re-check.
 */
public class MissingDataReportService {

  private static final String DATA_FILE = "data/missing_reports.json";
  private static final String MANUAL_QUEUE_JSON_FILE = "data/manual_update_queue.json";
  private static final String MANUAL_QUEUE_MD_FILE = "data/manual_update_queue.md";
  private static final ObjectMapper mapper =
      new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

  // User-facing alias map so report commands accept common shorthand terms.
  private static final Map<String, String> DATASET_ALIASES = new LinkedHashMap<>();

  static {
    DATASET_ALIASES.put("commodity", "commodities");
    DATASET_ALIASES.put("commodities", "commodities");
    DATASET_ALIASES.put("trade", "trade_routes");
    DATASET_ALIASES.put("trades", "trade_routes");
    DATASET_ALIASES.put("trade_routes", "trade_routes");
    DATASET_ALIASES.put("route", "trade_routes");
    DATASET_ALIASES.put("routes", "trade_routes");
    DATASET_ALIASES.put("mining", "mining");
    DATASET_ALIASES.put("module", "mining");
    DATASET_ALIASES.put("modules", "mining");
    DATASET_ALIASES.put("ship", "ships");
    DATASET_ALIASES.put("ships", "ships");
    DATASET_ALIASES.put("missile", "ships");
    DATASET_ALIASES.put("missiles", "ships");
    DATASET_ALIASES.put("missile rack", "ships");
    DATASET_ALIASES.put("missile racks", "ships");
    DATASET_ALIASES.put("missile_rack", "ships");
    DATASET_ALIASES.put("missile_racks", "ships");
    DATASET_ALIASES.put("weapon", "weapons");
    DATASET_ALIASES.put("weapons", "weapons");
    DATASET_ALIASES.put("component", "components");
    DATASET_ALIASES.put("components", "components");
    DATASET_ALIASES.put("refinery", "refinery");
    DATASET_ALIASES.put("refinery_stations", "refinery_stations");
    DATASET_ALIASES.put("refinery stations", "refinery_stations");
    DATASET_ALIASES.put("salvage", "salvage");
    DATASET_ALIASES.put("location", "locations");
    DATASET_ALIASES.put("locations", "locations");
    DATASET_ALIASES.put("mission", "missions");
    DATASET_ALIASES.put("missions", "missions");
    DATASET_ALIASES.put("item", "items");
    DATASET_ALIASES.put("items", "items");
    DATASET_ALIASES.put("armor", "armor");
  }

  // --- Public API ---

  /**
   * Full result contract for a missing-data report and remediation attempt.
   */
  public record ReportResult(
      String reportId,
      String dataset,
      String requestedItem,
      boolean alreadyPresent,
      boolean refreshAttempted,
      boolean refreshSucceeded,
      boolean foundAfterRefresh,
      String resolvedName,
      List<String> suggestions,
      boolean queuedForManualReview,
      String manualQueuePath) {

  }

  /**
   * Saves a report and checks whether the missing item can be resolved after a live refresh.
   */
  public static ReportResult reportAndAttemptFix(
      String datasetInput,
      String requestedItem,
      String notes,
      long userId,
      String userTag,
      long guildId,
      long channelId) {
    String dataset = normalizeDataset(datasetInput);
    if (dataset == null) {
      throw new IllegalArgumentException("Unsupported dataset: " + datasetInput);
    }
    if (requestedItem == null || requestedItem.isBlank()) {
      throw new IllegalArgumentException("Missing item name.");
    }

    String trimmedItem = requestedItem.trim();
    String reportId =
        appendReport(dataset, trimmedItem, notes, userId, userTag, guildId, channelId);

    String resolvedBefore = resolveFromDataset(dataset, trimmedItem);
    if (resolvedBefore != null) {
      return new ReportResult(
          reportId,
          dataset,
          trimmedItem,
          true,
          false,
          false,
          true,
          resolvedBefore,
          List.of(),
          false,
          "");
    }

    boolean refreshAttempted = supportsLiveRefresh(dataset);
    boolean refreshSucceeded = false;
    if (refreshAttempted) {
      refreshSucceeded = StarCitizenUpdateManager.update(dataset);
    }

    String resolvedAfter = resolveFromDataset(dataset, trimmedItem);
    boolean found = resolvedAfter != null;

    List<String> suggestions =
        found ? List.of() : StarCitizenDataService.suggestDatasetKeys(dataset, trimmedItem, 5);

    boolean queuedForManualReview = false;
    if (!found) {
      queuedForManualReview =
          appendManualQueueEntry(
              dataset,
              trimmedItem,
              notes,
              reportId,
              refreshAttempted,
              refreshSucceeded,
              suggestions);
    }

    return new ReportResult(
        reportId,
        dataset,
        trimmedItem,
        false,
        refreshAttempted,
        refreshSucceeded,
        found,
        found ? resolvedAfter : null,
        suggestions,
        queuedForManualReview,
        queuedForManualReview ? MANUAL_QUEUE_MD_FILE : "");
  }

  /**
   * Normalizes user-entered dataset names to canonical internal keys.
   *
   * @param raw dataset input from command text
   * @return canonical dataset key, or {@code null} when unsupported
   */
  public static String normalizeDataset(String raw) {
    if (raw == null) {
      return null;
    }
    String key = raw.trim().toLowerCase().replace('-', '_');
    return DATASET_ALIASES.get(key);
  }

  /**
   * Returns a short help string listing supported report dataset aliases.
   */
  public static String supportedDatasetsHelp() {
    return "commodity, trade, mining, ship/missile_rack, weapon, component,"
        + " refinery, salvage, location, mission, item, armor";
  }

  /**
   * Returns the markdown path used for the manual-review queue output.
   */
  public static String manualQueuePath() {
    return MANUAL_QUEUE_MD_FILE;
  }

  /**
   * Queues a runtime/system issue into the same manual-update queue used for missing data.
   */
  public static boolean queueOperationalIssue(String area, String subject, String notes) {
    String normalizedArea = area == null ? "operations" : area.trim().toLowerCase(Locale.ROOT);
    normalizedArea = normalizedArea.replaceAll("[^a-z0-9_-]+", "_");
    if (normalizedArea.isBlank()) {
      normalizedArea = "operations";
    }

    String normalizedSubject = subject == null ? "unspecified" : subject.trim();
    if (normalizedSubject.isBlank()) {
      normalizedSubject = "unspecified";
    }

    String reportId =
        "OPS-" + System.currentTimeMillis() + "-" + ThreadLocalRandom.current().nextInt(100, 1000);

    return appendManualQueueEntry(
        "ops_" + normalizedArea,
        normalizedSubject,
        notes == null ? "" : notes.trim(),
        reportId,
        false,
        false,
        List.of());
  }

  // --- Resolution and refresh helpers ---

  /**
   * Attempts to resolve an item name from the currently-loaded dataset state.
   */
  private static String resolveFromDataset(String dataset, String query) {
    JsonNode data = StarCitizenDataService.get(dataset);
    if (data == null || data.isMissingNode()) {
      return null;
    }
    if (!data.isObject()) {
      return null;
    }
    return StarCitizenDataService.resolveDatasetKey(dataset, query);
  }

  /**
   * Indicates whether a dataset supports automatic live-refresh retries.
   */
  private static boolean supportsLiveRefresh(String dataset) {
    return dataset.equals("commodities")
        || dataset.equals("trade_routes")
        || dataset.equals("mining")
        || dataset.equals("ships")
        || dataset.equals("weapons")
        || dataset.equals("components")
        || dataset.equals("refinery")
        || dataset.equals("refinery_stations")
        || dataset.equals("salvage")
        || dataset.equals("locations");
  }

  // --- Persistence helpers ---

  /**
   * Appends an immutable report entry to the missing-reports history file.
   */
  private static synchronized String appendReport(
      String dataset,
      String item,
      String notes,
      long userId,
      String userTag,
      long guildId,
      long channelId) {
    try {
      File file = new File(DATA_FILE);
      if (file.getParentFile() != null) {
        file.getParentFile().mkdirs();
      }

      ArrayNode reports = mapper.createArrayNode();
      if (file.exists()) {
        try {
          JsonNode existing = mapper.readTree(file);
          if (existing != null && existing.isArray()) {
            existing.forEach(reports::add);
          }
        } catch (IOException ignored) {
          // If existing file is unreadable, continue and overwrite with a fresh array.
        }
      }

      String reportId =
          "MR-" + System.currentTimeMillis() + "-" + ThreadLocalRandom.current().nextInt(100, 1000);

      ObjectNode row = mapper.createObjectNode();
      row.put("report_id", reportId);
      row.put("timestamp", Instant.now().toString());
      row.put("dataset", dataset);
      row.put("requested_item", item);
      row.put("notes", notes == null ? "" : notes.trim());
      row.put("user_id", userId);
      row.put("user_tag", userTag == null ? "unknown" : userTag);
      row.put("guild_id", guildId);
      row.put("channel_id", channelId);

      reports.add(row);
      mapper.writeValue(file, reports);
      return reportId;

    } catch (Exception e) {
      throw new RuntimeException("Failed to persist missing-data report: " + e.getMessage(), e);
    }
  }

  /**
   * Upserts an unresolved item into the manual-review queue artifacts.
   */
  private static synchronized boolean appendManualQueueEntry(
      String dataset,
      String item,
      String notes,
      String reportId,
      boolean refreshAttempted,
      boolean refreshSucceeded,
      List<String> suggestions) {
    try {
      File jsonFile = new File(MANUAL_QUEUE_JSON_FILE);
      if (jsonFile.getParentFile() != null) {
        jsonFile.getParentFile().mkdirs();
      }

      ArrayNode queue = mapper.createArrayNode();
      if (jsonFile.exists()) {
        try {
          JsonNode existing = mapper.readTree(jsonFile);
          if (existing != null && existing.isArray()) {
            existing.forEach(queue::add);
          }
        } catch (IOException ignored) {
          // If existing file is unreadable, continue with a fresh queue.
        }
      }

      ObjectNode current = null;
      String itemLower = item.trim().toLowerCase();
      for (JsonNode row : queue) {
        if (!row.isObject()) {
          continue;
        }
        String rowDataset = row.path("dataset").asText("");
        String rowItem = row.path("requested_item").asText("").trim().toLowerCase();
        if (rowDataset.equals(dataset) && rowItem.equals(itemLower)) {
          current = (ObjectNode) row;
          break;
        }
      }

      if (current == null) {
        current = mapper.createObjectNode();
        current.put("dataset", dataset);
        current.put("requested_item", item.trim());
        current.put("first_seen", Instant.now().toString());
        current.put("first_report_id", reportId);
        current.put("times_reported", 0);
        current.set("notes", mapper.createArrayNode());
        queue.add(current);
      }

      current.put("last_seen", Instant.now().toString());
      current.put("last_report_id", reportId);
      current.put("times_reported", current.path("times_reported").asInt(0) + 1);
      current.put("refresh_attempted", refreshAttempted);
      current.put("last_refresh_succeeded", refreshSucceeded);

      ArrayNode suggestionsNode = mapper.createArrayNode();
      if (suggestions != null) {
        for (String suggestion : suggestions) {
          if (suggestion != null && !suggestion.isBlank()) {
            suggestionsNode.add(suggestion.trim());
          }
        }
      }
      current.set("latest_suggestions", suggestionsNode);

      if (notes != null && !notes.isBlank()) {
        ArrayNode notesNode = current.withArray("notes");
        String trimmed = notes.trim();
        boolean exists = false;
        for (JsonNode n : notesNode) {
          if (trimmed.equalsIgnoreCase(n.asText(""))) {
            exists = true;
            break;
          }
        }
        if (!exists) {
          notesNode.add(trimmed);
        }
      }

      mapper.writeValue(jsonFile, queue);
      writeManualQueueMarkdown(queue);
      return true;
    } catch (Exception e) {
      return false;
    }
  }

  /**
   * Regenerates the markdown view of unresolved manual queue entries.
   */
  private static void writeManualQueueMarkdown(ArrayNode queue) throws IOException {
    File mdFile = new File(MANUAL_QUEUE_MD_FILE);
    if (mdFile.getParentFile() != null) {
      mdFile.getParentFile().mkdirs();
    }

    StringBuilder md = new StringBuilder();
    md.append("# Manual Update Queue\n\n");
    md.append("This file lists unresolved missing-data reports that need manual review.\n\n");
    md.append("Generated: ").append(Instant.now()).append("\n\n");

    if (queue.isEmpty()) {
      md.append("- No unresolved items.\n");
    } else {
      for (int i = queue.size() - 1; i >= 0; i--) {
        JsonNode row = queue.get(i);
        String dataset = row.path("dataset").asText("unknown");
        String item = row.path("requested_item").asText("unknown");
        int times = row.path("times_reported").asInt(1);
        String lastSeen = row.path("last_seen").asText("unknown");
        boolean attempted = row.path("refresh_attempted").asBoolean(false);
        boolean refreshed = row.path("last_refresh_succeeded").asBoolean(false);

        md.append("## ").append(dataset).append(" :: ").append(item).append("\n");
        md.append("- Reports: ").append(times).append("\n");
        md.append("- Last seen: ").append(lastSeen).append("\n");
        md.append("- Live refresh: ")
            .append(
                !attempted
                    ? "not supported"
                    : (refreshed ? "attempted but still missing" : "attempt failed"))
            .append("\n");

        JsonNode suggestions = row.path("latest_suggestions");
        if (suggestions.isArray() && !suggestions.isEmpty()) {
          List<String> list = new ArrayList<>();
          for (JsonNode suggestion : suggestions) {
            String text = suggestion.asText("").trim();
            if (!text.isBlank()) {
              list.add(text);
            }
          }
          if (!list.isEmpty()) {
            md.append("- Closest matches: ").append(String.join(", ", list)).append("\n");
          }
        }

        JsonNode notes = row.path("notes");
        if (notes.isArray() && !notes.isEmpty()) {
          List<String> list = new ArrayList<>();
          for (JsonNode note : notes) {
            String text = note.asText("").trim();
            if (!text.isBlank()) {
              list.add(text);
            }
          }
          if (!list.isEmpty()) {
            md.append("- Notes: ").append(String.join(" | ", list)).append("\n");
          }
        }
        md.append("\n");
      }
    }

    try (Writer writer =
        new OutputStreamWriter(Files.newOutputStream(mdFile.toPath()), StandardCharsets.UTF_8)) {
      writer.write(md.toString());
    }
  }

  /**
   * Returns a compact list of recent report IDs (newest first).
   */
  public static List<String> recentReportIds(int limit) {
    List<String> ids = new ArrayList<>();
    File file = new File(DATA_FILE);
    if (!file.exists() || limit <= 0) {
      return ids;
    }

    try {
      JsonNode raw = mapper.readTree(file);
      if (raw == null || !raw.isArray() || raw.isEmpty()) {
        return ids;
      }

      for (int i = raw.size() - 1; i >= 0 && ids.size() < limit; i--) {
        String id = raw.get(i).path("report_id").asText("");
        if (!id.isBlank()) {
          ids.add(id);
        }
      }
    } catch (Exception ignored) {
      // Non-critical helper.
    }
    return ids;
  }
}
