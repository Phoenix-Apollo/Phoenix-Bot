package Botcode.StarCitizen;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

/**
 * Data-backed conversational recommender for broad "best place to mine" questions.
 *
 * <p>Uses live datasets already loaded by {@link StarCitizenDataService}: refinery_stations,
 * locations, and mining rocks metadata.
 */
public class MiningSpotRecommender {

  private enum Mode {
    BALANCED,
    SAFE,
    PROFIT
  }

  private record Spot(String name, String system, String body, double score) {

  }

  private record OreHint(String name, double score) {

  }

  private record SurfaceHotspot(
      String name, String system, String body, String focus, double baseScore) {

  }

  private static final List<SurfaceHotspot> SURFACE_HOTSPOTS =
      List.of(
          new SurfaceHotspot("Lyria", "Stanton", "ArcCorp", "High-value ore sweep routes", 4.8),
          new SurfaceHotspot(
              "Aberdeen", "Stanton", "Hurston", "Aggressive profit runs (hazardous)", 4.5),
          new SurfaceHotspot("Arial", "Stanton", "Hurston", "Strong mixed-value ore routes", 4.2),
          new SurfaceHotspot("Wala", "Stanton", "ArcCorp", "Lower-risk steady surface loops", 4.0),
          new SurfaceHotspot(
              "Daymar", "Stanton", "Crusader", "Beginner-friendly stable loops", 3.9),
          new SurfaceHotspot("Ita", "Stanton", "ArcCorp", "Balanced reliability runs", 3.8),
          new SurfaceHotspot(
              "Pyro 4",
              "Pyro",
              "Pyro",
              "Premier Pyro hotspot for high-risk, high-yield mining",
              4.9),
          new SurfaceHotspot(
              "Pyro 3",
              "Pyro",
              "Pyro",
              "Strong volatile ore routes near active conflict lanes",
              4.4),
          new SurfaceHotspot(
              "Bloom", "Pyro", "Pyro", "Steady solo-friendly loops for survival-focused runs", 3.9),
          new SurfaceHotspot(
              "Levski Perimeter",
              "Nyx",
              "Delamar",
              "Reliable Nyx starter hotspot near support services",
              4.1),
          new SurfaceHotspot(
              "Delamar Highlands",
              "Nyx",
              "Delamar",
              "Balanced Nyx routes with medium-risk returns",
              4.0),
          new SurfaceHotspot(
              "Nyx Fringe Belt",
              "Nyx",
              "Nyx",
              "Remote high-upside routes for organized crews",
              3.8));

  private MiningSpotRecommender() {
  }

  public static String recommend(String text) {
    String lower = text == null ? "" : text.toLowerCase(Locale.ROOT);
    String systemPref = detectSystemPreference(lower);
    Mode mode = detectMode(lower);

    JsonNode stations = StarCitizenDataService.get("refinery_stations");
    if (stations == null || !stations.isObject() || stations.isEmpty()) {
      return "I can do mining spot recommendations, but refinery station data is unavailable right now.";
    }

    List<Spot> ranked = rankStations(stations, systemPref, mode);
    if (ranked.isEmpty()) {
      return "I looked through refinery station data and came up empty. That's impressive and mildly concerning.";
    }

    List<OreHint> oreHints = rankOres(mode);
    List<SurfaceHotspot> surface = rankSurfaceHotspots(systemPref, mode);

    StringBuilder sb = new StringBuilder();
    sb.append("**Mining Spot Recommender (data-backed)**\n");
    sb.append("Mode: ")
        .append(
            switch (mode) {
              case SAFE -> "safe + steady";
              case PROFIT -> "high-profit bias";
              default -> "balanced";
            });
    if (systemPref != null) {
      sb.append(" | System preference: ").append(capitalize(systemPref));
    }
    sb.append("\n\n");

    sb.append("Top staging picks from current refinery/location data:\n");
    int count = Math.min(3, ranked.size());
    for (int i = 0; i < count; i++) {
      Spot s = ranked.get(i);
      sb.append("• **")
          .append(s.name())
          .append("** — ")
          .append(s.body())
          .append(" (")
          .append(s.system())
          .append(")\n");
    }

    if (!surface.isEmpty()) {
      sb.append("\nSurface mining hotspot picks:\n");
      int hotspotCount = Math.min(3, surface.size());
      for (int i = 0; i < hotspotCount; i++) {
        SurfaceHotspot s = surface.get(i);
        sb.append("• **")
            .append(s.name())
            .append("** — ")
            .append(s.body())
            .append(" | ")
            .append(s.focus())
            .append("\n");
      }
    }

    if (!oreHints.isEmpty()) {
      sb.append("\nSuggested ore focus right now:\n");
      int oreCount = Math.min(3, oreHints.size());
      for (int i = 0; i < oreCount; i++) {
        sb.append("• ").append(oreHints.get(i).name()).append("\n");
      }
    }

    sb.append(
        "\nIf you want, give me your ship + risk tolerance and I'll refine this into a tighter route plan.");
    return sb.toString();
  }

  public static boolean isMiningSpotQuestion(String lower) {
    if (lower == null || lower.isBlank()) {
      return false;
    }

    boolean miningIntent = lower.contains("mine") || lower.contains("mining");
    boolean spotIntent =
        lower.contains("best place")
            || lower.contains("best spot")
            || lower.contains("best location")
            || lower.contains("best locations")
            || lower.contains("where should i mine")
            || lower.contains("where to mine")
            || lower.contains("good place to mine")
            || lower.contains("best mining location")
            || lower.contains("mining spot")
            || lower.contains("mining hotspots")
            || lower.contains("mining hotspot")
            || lower.contains("surface mining")
            || lower.contains("surface spots")
            || lower.contains("surface hotspots")
            || lower.contains("hotspots");

    return miningIntent && spotIntent;
  }

  private static List<SurfaceHotspot> rankSurfaceHotspots(String systemPref, Mode mode) {
    List<SurfaceHotspot> ranked = new ArrayList<>();
    for (SurfaceHotspot hotspot : SURFACE_HOTSPOTS) {
      double score = hotspot.baseScore();
      String system = hotspot.system().toLowerCase(Locale.ROOT);
      String name = hotspot.name().toLowerCase(Locale.ROOT);

      if (systemPref != null) {
        score += system.contains(systemPref.toLowerCase(Locale.ROOT)) ? 1.3 : -0.7;
      }

      if (mode == Mode.SAFE) {
        if (name.contains("daymar") || name.contains("wala") || name.contains("ita")) {
          score += 1.2;
        }
        if (name.contains("aberdeen")) {
          score -= 0.8;
        }
      } else if (mode == Mode.PROFIT) {
        if (name.contains("lyria") || name.contains("aberdeen") || name.contains("arial")) {
          score += 1.2;
        }
      }

      ranked.add(
          new SurfaceHotspot(
              hotspot.name(), hotspot.system(), hotspot.body(), hotspot.focus(), score));
    }

    ranked.sort(
        Comparator.comparingDouble(SurfaceHotspot::baseScore)
            .reversed()
            .thenComparing(SurfaceHotspot::name, String.CASE_INSENSITIVE_ORDER));
    return ranked;
  }

  private static String detectSystemPreference(String lower) {
    if (lower.contains("stanton")) {
      return "stanton";
    }
    if (lower.contains("pyro")) {
      return "pyro";
    }
    if (lower.contains("nyx")) {
      return "nyx";
    }
    return null;
  }

  private static Mode detectMode(String lower) {
    if (containsAny(lower, "safe", "safer", "beginner", "new player", "low risk", "steady")) {
      return Mode.SAFE;
    }
    if (containsAny(
        lower,
        "best profit",
        "max profit",
        "high risk",
        "sweaty",
        "money",
        "quant",
        "risk it all",
        "all in",
        "yolo",
        "full send",
        "send it",
        "risk it")) {
      return Mode.PROFIT;
    }
    return Mode.BALANCED;
  }

  private static List<Spot> rankStations(JsonNode stations, String systemPref, Mode mode) {
    List<Spot> out = new ArrayList<>();
    Iterator<String> names = stations.fieldNames();
    while (names.hasNext()) {
      String stationName = names.next();
      JsonNode node = stations.path(stationName);
      String system = node.path("system").asText("Unknown");
      String body = node.path("body").asText("Unknown");

      double score = 0;
      String lowerName = stationName.toLowerCase(Locale.ROOT);
      String lowerBody = body.toLowerCase(Locale.ROOT);
      String lowerSystem = system.toLowerCase(Locale.ROOT);

      if (systemPref != null) {
        score += lowerSystem.equals(systemPref) ? 5.0 : -2.5;
      }

      if (mode == Mode.SAFE && lowerSystem.equals("stanton")) {
        score += 2.0;
      }
      if (mode == Mode.PROFIT && lowerSystem.equals("pyro")) {
        score += 2.0;
      }

      if (lowerName.contains("l1") || lowerName.contains("l2")) {
        score += 2.0;
      } else if (lowerName.contains("l3") || lowerName.contains("l4") || lowerName.contains("l5")) {
        score += 1.0;
      }

      if (lowerBody.contains("lagrange")) {
        score += 1.5;
      }
      if (lowerName.contains("gateway")) {
        score -= 0.5;
      }

      out.add(new Spot(stationName, system, body, score));
    }

    out.sort(
        Comparator.comparingDouble(Spot::score)
            .reversed()
            .thenComparing(Spot::name, String.CASE_INSENSITIVE_ORDER));
    return out;
  }

  private static List<OreHint> rankOres(Mode mode) {
    JsonNode mining = StarCitizenDataService.get("mining");
    if (mining == null || mining.isMissingNode()) {
      return List.of();
    }
    JsonNode rocks = mining.path("rocks");
    if (!rocks.isObject() || rocks.isEmpty()) {
      return List.of();
    }

    List<OreHint> hints = new ArrayList<>();
    Iterator<String> names = rocks.fieldNames();
    while (names.hasNext()) {
      String name = names.next();
      JsonNode rock = rocks.path(name);

      double instability = rock.path("instability").asDouble(0.5);
      double resistance = rock.path("resistance").asDouble(1.0);
      String valueTier = rock.path("value").asText("medium").toLowerCase(Locale.ROOT);
      double valueScore =
          switch (valueTier) {
            case "very high" -> 5.0;
            case "high" -> 4.0;
            case "medium-high" -> 3.2;
            case "medium" -> 2.6;
            case "low-medium" -> 2.0;
            case "low" -> 1.5;
            default -> 1.0;
          };

      double score =
          switch (mode) {
            case SAFE -> (4.0 - (instability + resistance)) + (valueScore * 0.3);
            case PROFIT -> (valueScore * 1.2) - (instability * 0.15);
            case BALANCED -> (valueScore * 0.8) - ((instability + resistance) * 0.25);
          };
      hints.add(new OreHint(name, score));
    }

    hints.sort(Comparator.comparingDouble(OreHint::score).reversed());
    return hints;
  }

  private static boolean containsAny(String lower, String... keys) {
    for (String key : keys) {
      if (lower.contains(key)) {
        return true;
      }
    }
    return false;
  }

  private static String capitalize(String value) {
    if (value == null || value.isBlank()) {
      return value;
    }
    return Character.toUpperCase(value.charAt(0)) + value.substring(1).toLowerCase(Locale.ROOT);
  }
}
