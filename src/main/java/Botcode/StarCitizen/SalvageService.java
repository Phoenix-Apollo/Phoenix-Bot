package Botcode.StarCitizen;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.Locale;

/**
 * Salvage information service using the {@code salvage.json} dataset.
 *
 * <p>Provides:
 *
 * <ul>
 *   <li>Hotspot listings with risk and wreck density info
 *   <li>Material values (RMC, Construction Materials, etc.)
 *   <li>Ship comparison (Reclaimer vs Vulture)
 *   <li>Tips for new salvagers
 * </ul>
 */
public class SalvageService {

  // ------------------------------------------------------------------
  // Public API
  // ------------------------------------------------------------------

  /**
   * Returns a formatted summary of the best salvage hotspots.
   */
  public static String listHotspots() {
    return listHotspots(null);
  }

  /**
   * Returns a formatted summary of salvage hotspots, optionally filtered by system.
   */
  public static String listHotspots(String systemFilter) {
    JsonNode data = StarCitizenDataService.get("salvage");
    if (data == null) {
      return "*No salvage data loaded.*";
    }

    JsonNode hotspots = data.path("hotspots");
    if (hotspots.isMissingNode()) {
      return "*No hotspot data.*";
    }

    String normalizedSystem = normalizeSystem(systemFilter);
    StringBuilder sb = new StringBuilder();
    hotspots
        .fields()
        .forEachRemaining(
            e -> {
              JsonNode h = e.getValue();
              String system = h.path("system").asText("");
              if (normalizedSystem != null && !system.equalsIgnoreCase(normalizedSystem)) {
                return;
              }
              sb.append(String.format("**%s** — %s\n", e.getKey(), h.path("body").asText()));
              sb.append(
                  String.format(
                      "  System: `%s`  |  Risk: `%s`  |  Wrecks: `%s`\n",
                      h.path("system").asText("?"),
                      h.path("risk").asText("?"),
                      h.path("wreck_density").asText("?")));
              sb.append(String.format("  _%s_\n\n", h.path("notes").asText("")));
            });
    if (sb.length() == 0 && normalizedSystem != null) {
      return "*No salvage hotspots found for " + normalizedSystem + ".*";
    }
    return sb.toString().trim();
  }

  private static String normalizeSystem(String input) {
    if (input == null || input.isBlank()) {
      return null;
    }
    String lower = input.trim().toLowerCase(Locale.ROOT);
    if (lower.contains("stanton")) {
      return "Stanton";
    }
    if (lower.contains("pyro")) {
      return "Pyro";
    }
    if (lower.contains("nyx")) {
      return "Nyx";
    }
    return null;
  }

  /**
   * Returns a formatted comparison of the Reclaimer and Vulture.
   */
  public static String compareShips() {
    JsonNode data = StarCitizenDataService.get("salvage");
    if (data == null) {
      return "*No salvage data loaded.*";
    }

    JsonNode ships = data.path("ships");
    if (ships.isMissingNode()) {
      return "*No ship data.*";
    }

    StringBuilder sb = new StringBuilder();
    ships
        .fields()
        .forEachRemaining(
            e -> {
              JsonNode s = e.getValue();
              sb.append(String.format("**%s** — %s\n", e.getKey(), s.path("role").asText()));
              sb.append(
                  String.format(
                      "  Crew: %d  |  Cargo: %d SCU  |  Scrape: %.1f SCU/min\n",
                      s.path("crew").asInt(0),
                      s.path("cargo_scu").asInt(0),
                      s.path("scrape_rate_scu_min").asDouble(0)));
              sb.append(String.format("  _%s_\n\n", s.path("notes").asText("")));
            });
    return sb.toString().trim();
  }

  /**
   * Returns a formatted list of salvage materials and their sell values.
   */
  public static String listMaterials() {
    JsonNode data = StarCitizenDataService.get("salvage");
    if (data == null) {
      return "*No salvage data loaded.*";
    }

    JsonNode materials = data.path("materials");
    if (materials.isMissingNode()) {
      return "*No material data.*";
    }

    StringBuilder sb = new StringBuilder();
    materials
        .fields()
        .forEachRemaining(
            e -> {
              JsonNode m = e.getValue();
              sb.append(String.format("**%s** (%s)\n", e.getKey(), m.path("full_name").asText()));
              sb.append(
                  String.format(
                      "  Value: ~%d aUEC/SCU  |  Source: %s\n",
                      m.path("base_value_per_scu").asInt(0), m.path("source").asText("?")));

              // Sell locations
              StringBuilder sells = new StringBuilder();
              for (JsonNode loc : m.withArray("sells_at")) {
                if (sells.length() > 0) {
                  sells.append(", ");
                }
                sells.append(loc.asText());
              }
              if (sells.length() > 0) {
                sb.append("  Sells at: ").append(sells).append("\n");
              }
              sb.append("\n");
            });
    return sb.toString().trim();
  }

  /**
   * Returns salvage tips as a formatted string.
   */
  public static String getTips() {
    JsonNode data = StarCitizenDataService.get("salvage");
    if (data == null) {
      return "*No salvage data loaded.*";
    }

    JsonNode tips = data.path("tips");
    if (!tips.isArray()) {
      return "*No tips available.*";
    }

    StringBuilder sb = new StringBuilder();
    int i = 1;
    for (JsonNode tip : tips) {
      sb.append(i++).append(". ").append(tip.asText()).append("\n");
    }
    return sb.toString().trim();
  }
}
