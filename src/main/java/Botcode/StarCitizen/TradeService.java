package Botcode.StarCitizen;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.*;

/**
 * Commodity trade route analysis using the normalized commodities dataset.
 *
 * <p>Finds the most profitable buy→sell routes based on cargo capacity and optionally filters to a
 * specific commodity.
 */
public class TradeService {

  // ------------------------------------------------------------------
  // Result types
  // ------------------------------------------------------------------

  public static class TradeRoute {

    public final String commodity;
    public final String buyLocation;
    public final String sellLocation;
    public final double buyPrice;
    public final double sellPrice;
    public final double profitPerScu;
    public final double totalProfit;

    public TradeRoute(
        String commodity,
        String buyLocation,
        String sellLocation,
        double buyPrice,
        double sellPrice,
        double profitPerScu,
        int cargoScu) {
      this.commodity = commodity;
      this.buyLocation = buyLocation;
      this.sellLocation = sellLocation;
      this.buyPrice = buyPrice;
      this.sellPrice = sellPrice;
      this.profitPerScu = profitPerScu;
      this.totalProfit = profitPerScu * cargoScu;
    }
  }

  // ------------------------------------------------------------------
  // Public API
  // ------------------------------------------------------------------

  /**
   * Returns the top {@code limit} trade routes sorted by total profit for the given cargo size.
   *
   * @param cargoScu        ship cargo in SCU
   * @param commodityFilter optional commodity name filter — {@code null} or blank for all
   * @param limit           maximum number of routes to return
   */
  public static List<TradeRoute> topRoutes(int cargoScu, String commodityFilter, int limit) {
    JsonNode commodities = StarCitizenDataService.get("commodities");
    List<TradeRoute> routes = new ArrayList<>();

    if (commodities == null || commodities.isEmpty()) {
      return routes;
    }

    String resolvedFilter = null;
    if (commodityFilter != null && !commodityFilter.isBlank()) {
      resolvedFilter = StarCitizenDataService.resolveDatasetKey("commodities", commodityFilter);
      if (resolvedFilter == null) {
        resolvedFilter = commodityFilter;
      }
    }
    final String finalResolvedFilter = resolvedFilter;

    commodities
        .fields()
        .forEachRemaining(
            entry -> {
              String name = entry.getKey();
              JsonNode data = entry.getValue();

              // Apply optional commodity filter
              if (finalResolvedFilter != null
                  && !finalResolvedFilter.isBlank()
                  && !name.equalsIgnoreCase(finalResolvedFilter)) {
                return;
              }

              // Find best buy price (lowest)
              double bestBuyPrice = Double.MAX_VALUE;
              String bestBuyLoc = null;
              for (JsonNode b : data.withArray("buy")) {
                double price = b.path("price").asDouble(Double.MAX_VALUE);
                if (price < bestBuyPrice) {
                  bestBuyPrice = price;
                  bestBuyLoc = b.path("location").asText();
                }
              }

              // Find best sell price (highest)
              double bestSellPrice = 0;
              String bestSellLoc = null;
              for (JsonNode s : data.withArray("sell")) {
                double price = s.path("price").asDouble(0);
                if (price > bestSellPrice) {
                  bestSellPrice = price;
                  bestSellLoc = s.path("location").asText();
                }
              }

              if (bestBuyLoc == null || bestSellLoc == null) {
                return;
              }

              double profit = bestSellPrice - bestBuyPrice;
              if (profit <= 0) {
                return;
              }

              routes.add(
                  new TradeRoute(
                      name,
                      bestBuyLoc,
                      bestSellLoc,
                      bestBuyPrice,
                      bestSellPrice,
                      profit,
                      cargoScu));
            });

    // Sort by total profit descending
    routes.sort(Comparator.comparingDouble((TradeRoute r) -> r.totalProfit).reversed());

    return routes.subList(0, Math.min(limit, routes.size()));
  }

  // ------------------------------------------------------------------
  // Formatters
  // ------------------------------------------------------------------

  public static class Formatter {

    public static String format(List<TradeRoute> routes, int cargoScu) {
      if (routes.isEmpty()) {
        return "*No trade routes found.*";
      }

      StringBuilder sb = new StringBuilder();
      sb.append(String.format("**Top routes for %,d SCU cargo:**\n\n", cargoScu));

      int rank = 1;
      for (TradeRoute r : routes) {
        sb.append(
            String.format(
                "**%d. %s**\n  Buy: %s @ %,.0f aUEC\n  Sell: %s @ %,.0f aUEC\n  Profit: **%,.0f aUEC/SCU** → **~%,.0f aUEC/run**\n\n",
                rank++,
                r.commodity,
                r.buyLocation,
                r.buyPrice,
                r.sellLocation,
                r.sellPrice,
                r.profitPerScu,
                r.totalProfit));
      }
      return sb.toString().trim();
    }
  }
}
