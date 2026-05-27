package Botcode.StarCitizen;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.*;

/**
 * Refinery method analysis and yield calculations.
 *
 * <p>Uses the {@code refinery.json} dataset loaded by {@link StarCitizenDataService}. Key
 * features:
 *
 * <ul>
 *   <li>Compare all refinery methods for a given ore and SCU amount
 *   <li>Recommend the best method based on either speed or profit
 *   <li>List available refinery stations
 * </ul>
 */
public class RefineryService {

  // ------------------------------------------------------------------
  // Result types
  // ------------------------------------------------------------------

  public static class MethodResult {

    public final String method;
    public final double yieldPct;
    public final double timeMinutes;
    public final double costModifier;
    public final double refinedScu;
    public final double estimatedValue;
    public final String description;

    public MethodResult(
        String method,
        double yieldPct,
        double timeMinutes,
        double costModifier,
        double refinedScu,
        double estimatedValue,
        String description) {
      this.method = method;
      this.yieldPct = yieldPct;
      this.timeMinutes = timeMinutes;
      this.costModifier = costModifier;
      this.refinedScu = refinedScu;
      this.estimatedValue = estimatedValue;
      this.description = description;
    }
  }

  public static class RefineJob {

    public final String ore;
    public final int rawScu;
    public final List<MethodResult> methods;
    public final MethodResult bestProfit;
    public final MethodResult bestSpeed;

    public RefineJob(String ore, int rawScu, List<MethodResult> methods) {
      this.ore = ore;
      this.rawScu = rawScu;
      this.methods = methods;

      this.bestProfit =
          methods.stream().max(Comparator.comparingDouble(m -> m.estimatedValue)).orElse(null);

      this.bestSpeed =
          methods.stream().min(Comparator.comparingDouble(m -> m.timeMinutes)).orElse(null);
    }
  }

  // ------------------------------------------------------------------
  // Public API
  // ------------------------------------------------------------------

  /**
   * Analyses all refinery methods for {@code ore} at {@code rawScu} input.
   *
   * @param ore    ore name (case-insensitive match against ore_base_values)
   * @param rawScu raw SCU of ore to refine
   * @return a {@link RefineJob} with per-method breakdowns and recommendations
   */
  public static RefineJob analyze(String ore, int rawScu) {
    JsonNode data = StarCitizenDataService.get("refinery");

    List<MethodResult> results = new ArrayList<>();

    if (data == null || data.isEmpty()) {
      return new RefineJob(ore, rawScu, results);
    }

    JsonNode methods = data.path("methods");
    JsonNode oreValues = data.path("ore_base_values");

    double baseValue = oreValues.path(ore).asDouble(0);
    // Try case-insensitive match if exact key not found
    if (baseValue == 0) {
      for (Iterator<Map.Entry<String, JsonNode>> it = oreValues.fields(); it.hasNext(); ) {
        Map.Entry<String, JsonNode> entry = it.next();
        if (entry.getKey().equalsIgnoreCase(ore)) {
          baseValue = entry.getValue().asDouble(0);
          break;
        }
      }
    }

    final double finalBaseValue = baseValue;

    methods
        .fields()
        .forEachRemaining(
            entry -> {
              String methodName = entry.getKey();
              JsonNode m = entry.getValue();

              double yieldPct = m.path("yield_bonus_pct").asDouble(0);
              double timePerScu = m.path("time_per_scu_min").asDouble(0);
              double costMod = m.path("cost_modifier").asDouble(1.0);
              String description = m.path("description").asText("");

              double refinedScu = rawScu * (1 + yieldPct / 100.0);
              double totalTimeMin = timePerScu * rawScu;
              double estimatedValue = refinedScu * finalBaseValue;

              results.add(
                  new MethodResult(
                      methodName,
                      yieldPct,
                      totalTimeMin,
                      costMod,
                      refinedScu,
                      estimatedValue,
                      description));
            });

    // Sort by yield descending by default
    results.sort(Comparator.comparingDouble((MethodResult r) -> r.yieldPct).reversed());

    return new RefineJob(ore, rawScu, results);
  }

  /**
   * Returns a formatted list of all refinery stations.
   */
  public static String listStations() {
    JsonNode stations = StarCitizenDataService.get("refinery_stations");
    if (stations == null
        || stations.isMissingNode()
        || !stations.isObject()
        || stations.isEmpty()) {
      JsonNode refinery = StarCitizenDataService.get("refinery");
      stations = refinery != null ? refinery.path("stations") : null;
    }
    if (stations == null
        || stations.isMissingNode()
        || !stations.isObject()
        || stations.isEmpty()) {
      return "*No station data loaded.*";
    }

    StringBuilder sb = new StringBuilder();
    List<Map.Entry<String, JsonNode>> sorted = new ArrayList<>();
    stations.fields().forEachRemaining(sorted::add);
    sorted.sort(Comparator.comparing(Map.Entry::getKey, String.CASE_INSENSITIVE_ORDER));

    for (Map.Entry<String, JsonNode> e : sorted) {
      String system = e.getValue().path("system").asText("");
      String body = e.getValue().path("body").asText("Unknown");
      sb.append("• **").append(e.getKey()).append("** — ").append(body);
      if (!system.isBlank()) {
        sb.append(" (").append(system).append(")");
      }
      sb.append("\n");
    }
    return sb.toString().trim();
  }

  // ------------------------------------------------------------------
  // Formatters
  // ------------------------------------------------------------------

  public static class Formatter {

    public static String brief(RefineJob job) {
      if (job.methods.isEmpty()) {
        return "*No refinery data available.*";
      }
      StringBuilder sb = new StringBuilder();
      sb.append(String.format("**Ore:** %s  |  **Input:** %d SCU\n\n", job.ore, job.rawScu));
      if (job.bestProfit != null) {
        sb.append(
            String.format(
                "🏆 **Best Yield:** %s (+%.0f%%) → %.1f SCU refined  (~%.0f aUEC)\n",
                job.bestProfit.method,
                job.bestProfit.yieldPct,
                job.bestProfit.refinedScu,
                job.bestProfit.estimatedValue));
      }
      if (job.bestSpeed != null) {
        sb.append(
            String.format(
                "⚡ **Fastest:** %s → %.0f min total\n",
                job.bestSpeed.method, job.bestSpeed.timeMinutes));
      }
      return sb.toString().trim();
    }

    public static String full(RefineJob job) {
      if (job.methods.isEmpty()) {
        return "*No refinery data available.*";
      }
      StringBuilder sb = new StringBuilder();
      sb.append(String.format("**Ore:** %s  |  **Input:** %d SCU\n\n", job.ore, job.rawScu));
      for (MethodResult m : job.methods) {
        sb.append(String.format("**%s**\n", m.method));
        sb.append(
            String.format(
                "  Yield: +%.0f%%  |  Output: %.1f SCU  |  Time: %.0f min  |  Value: ~%.0f aUEC\n",
                m.yieldPct, m.refinedScu, m.timeMinutes, m.estimatedValue));
        sb.append(String.format("  _%s_\n\n", m.description));
      }
      return sb.toString().trim();
    }
  }
}
