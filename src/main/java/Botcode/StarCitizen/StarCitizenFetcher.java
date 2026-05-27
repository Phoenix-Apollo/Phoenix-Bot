package Botcode.StarCitizen;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Fetches raw Star Citizen datasets from external APIs before normalization.
 *
 * <p>URL selection order for configurable datasets:
 *
 * <ol>
 *   <li>Environment override (e.g. SC_REFINERY_URL)
 *   <li>Built-in default endpoint
 * </ol>
 */
public class StarCitizenFetcher {

  private static final ObjectMapper mapper = new ObjectMapper();

  // UEX Fetcher

  /**
   * Fetches commodity market data from UEX.
   */
  public static JsonNode fetchCommodities() {
    JsonNode local = fetchLocalDataset("SC_COMMODITIES_FILE", "commodities", "commodities");
    if (local != null) {
      return local;
    }
    return fetchFromEnvOrDefault(
        "SC_COMMODITIES_URL", "https://uexcorp.space/api/commodities_prices_all", "commodities");
  }

  /**
   * Fetches trade route data from UEX.
   */
  public static JsonNode fetchTradeRoutes() {
    JsonNode local = fetchLocalDataset("SC_TRADE_ROUTES_FILE", "trade_routes", "trade_routes");
    if (local != null) {
      return local;
    }
    return fetch("https://uexcorp.space/api/commodities_routes");
  }

  // ERKUL Fetcher

  /**
   * Fetches ship data from UEX vehicles.
   */
  public static JsonNode fetchShips() {
    JsonNode base = fetchLocalDataset("SC_SHIPS_FILE", "ships", "ships");
    if (base == null) {
      base = fetchFromEnvOrDefault("SC_SHIPS_URL", "https://uexcorp.space/api/vehicles", "ships");
      base = enrichShipsWithPurchases(base);
    }

    // Optional enrichment source (defaults to Erkul server JSON feed) for fields UEX may omit.
    JsonNode enrich = fetchLocalStats("SC_SHIPS_STATS_FILE", "ships");
    if (enrich == null) {
      enrich =
          fetchFromOptionalEnvOrDefault(
              "SC_SHIPS_STATS_URL", "https://server.erkul.games/live/ships", "ships-stats");
    }
    if (enrich == null) {
      enrich =
          fetchFromHtmlStatsEnvOrDefault(
              "SC_SHIPS_STATS_HTML_URL",
              "https://www.erkul.games/live/ships",
              "ships-stats-html",
              new String[]{"ships", "vehicles"});
    }
    return mergeByName(base, enrich, "name", "name_full");
  }

  /**
   * Fetches vehicle weapon items from UEX categories.
   */
  public static JsonNode fetchWeapons() {
    JsonNode base = fetchLocalDataset("SC_WEAPONS_FILE", "weapons", "weapons");
    if (base == null) {
      // Vehicle + FPS weapon categories in UEX.
      int[] categoryIds = {18, 32, 33, 34, 35, 70, 79, 80, 90};
      base = fetchItemsByCategoryIds("SC_WEAPONS_CATEGORY_IDS", categoryIds, "weapons");
      base =
          enrichItemsWithPricesByCategories(
              base, parseCategoryIdsFromEnv("SC_WEAPONS_CATEGORY_IDS", categoryIds), "weapons");
    }

    // Optional enrichment source (defaults to Erkul server JSON feed) for DPS/HP/cost fields.
    JsonNode enrich = fetchLocalStats("SC_WEAPONS_STATS_FILE", "weapons");
    if (enrich == null) {
      enrich =
          fetchFromOptionalEnvOrDefault(
              "SC_WEAPONS_STATS_URL",
              "https://api.erkul.games/live/weapon;https://server.erkul.games/live/weapons",
              "weapons-stats");
    }
    if (enrich == null) {
      enrich =
          fetchFromHtmlStatsEnvOrDefault(
              "SC_WEAPONS_STATS_HTML_URL",
              "https://www.erkul.games/live/weapons",
              "weapons-stats-html",
              new String[]{"weapons", "items"});
    }
    return mergeByName(base, enrich, "name");
  }

  /**
   * Fetches ship component items from UEX categories, with optional Erkul enrichment for stats
   * (power, cooling, HP, cost) that UEX does not expose directly.
   */
  public static JsonNode fetchComponents() {
    JsonNode base = fetchLocalDataset("SC_COMPONENTS_FILE", "components", "components");
    if (base == null) {
      int[] categoryIds = {19, 21, 22, 23, 81, 82, 83, 84, 86, 103};
      base = fetchItemsByCategoryIds("SC_COMPONENTS_CATEGORY_IDS", categoryIds, "components");
      base =
          enrichItemsWithPricesByCategories(
              base,
              parseCategoryIdsFromEnv("SC_COMPONENTS_CATEGORY_IDS", categoryIds),
              "components");
    }

    // Optional enrichment from Erkul for per-component stats (power, thermal, hp, cost).
    JsonNode enrich = fetchLocalStats("SC_COMPONENTS_STATS_FILE", "components");
    if (enrich == null) {
      enrich =
          fetchFromOptionalEnvOrDefault(
              "SC_COMPONENTS_STATS_URL",
              "https://server.erkul.games/live/components",
              "components-stats");
    }
    return mergeByName(base, enrich, "name");
  }

  // Industry fetchers (env-overridable)

  /**
   * Mining source URL override: SC_MINING_URL.
   */
  public static JsonNode fetchMining() {
    JsonNode local = fetchLocalDataset("SC_MINING_FILE", "mining", "mining");
    if (local != null) {
      return local;
    }
    return fetchFromEnvOrDefault("SC_MINING_URL", "https://uexcorp.space/api/mining", "mining");
  }

  /**
   * Refinery source URL override: SC_REFINERY_URL.
   */
  public static JsonNode fetchRefinery() {
    JsonNode local = fetchLocalDataset("SC_REFINERY_FILE", "refinery", "refinery");
    if (local != null) {
      return local;
    }
    return fetchFromEnvOrDefault(
        "SC_REFINERY_URL", "https://uexcorp.space/api/refineries_methods", "refinery");
  }

  /**
   * Refinery station source URL override: SC_REFINERY_STATIONS_URL.
   */
  public static JsonNode fetchRefineryStations() {
    JsonNode local =
        fetchLocalDataset("SC_REFINERY_STATIONS_FILE", "refinery_stations", "refinery_stations");
    if (local != null) {
      return local;
    }
    return fetchFromEnvOrDefault(
        "SC_REFINERY_STATIONS_URL",
        "https://uexcorp.space/api/refineries_capacities",
        "refinery_stations");
  }

  /**
   * Salvage source URL override: SC_SALVAGE_URL.
   */
  public static JsonNode fetchSalvage() {
    JsonNode local = fetchLocalDataset("SC_SALVAGE_FILE", "salvage", "salvage");
    if (local != null) {
      return local;
    }
    return fetchFromEnvOrDefault("SC_SALVAGE_URL", "https://uexcorp.space/api/salvage", "salvage");
  }

  /**
   * Location source URL override: SC_LOCATIONS_URL.
   */
  public static JsonNode fetchLocations() {
    JsonNode local = fetchLocalDataset("SC_LOCATIONS_FILE", "locations", "locations");
    if (local != null) {
      return local;
    }
    return fetchFromEnvOrDefault(
        "SC_LOCATIONS_URL", "https://uexcorp.space/api/space_stations", "locations");
  }

  private static JsonNode fetchLocalDataset(String envPathVar, String datasetName, String label) {
    String envPath = System.getenv(envPathVar);
    if (envPath != null && !envPath.isBlank()) {
      JsonNode envJson = readJsonFileIfExists(envPath.trim(), label + " (env)");
      if (envJson != null) {
        return envJson;
      }
    }

    String[] defaults = {
        "data/game/" + datasetName + ".json",
        "data/raw/" + datasetName + ".json",
        "data/" + datasetName + "_raw.json"
    };
    for (String path : defaults) {
      JsonNode json = readJsonFileIfExists(path, label + " (local)");
      if (json != null) {
        return json;
      }
    }
    return null;
  }

  private static JsonNode fetchLocalStats(String envPathVar, String datasetName) {
    String envPath = System.getenv(envPathVar);
    if (envPath != null && !envPath.isBlank()) {
      JsonNode envJson = readJsonFileIfExists(envPath.trim(), datasetName + "-stats (env)");
      if (envJson != null) {
        return envJson;
      }
    }

    String[] defaults = {
        "data/game/" + datasetName + "_stats.json",
        "data/raw/" + datasetName + "_stats.json",
        "data/erkul_" + datasetName + "_stats.json"
    };
    for (String path : defaults) {
      JsonNode json = readJsonFileIfExists(path, datasetName + "-stats (local)");
      if (json != null) {
        return json;
      }
    }
    return null;
  }

  private static JsonNode readJsonFileIfExists(String path, String label) {
    try {
      File f = new File(path);
      if (!f.exists() || !f.isFile()) {
        return null;
      }
      JsonNode json = mapper.readTree(f);
      System.out.println("[Fetcher] " + label + " source: " + f.getPath() + " (local file)");
      return json;
    } catch (Exception e) {
      System.out.println("[Fetcher] Failed reading local file " + path + ": " + e.getMessage());
      return null;
    }
  }

  private static JsonNode fetchFromEnvOrDefault(
      String envName, String fallbackUrl, String dataset) {
    String env = System.getenv(envName);
    String approved = SourceOverrideService.getApprovedUrl(dataset);
    String url;
    String source;
    if (env != null && !env.isBlank()) {
      url = env.trim();
      source = "env override";
    } else if (approved != null && !approved.isBlank()) {
      url = approved;
      source = "approved user source";
    } else {
      url = fallbackUrl;
      source = "default";
    }
    System.out.println("[Fetcher] " + dataset + " source: " + url + " (" + source + ")");
    return fetch(url);
  }

  private static JsonNode fetchFromOptionalEnv(String envName, String dataset) {
    String env = System.getenv(envName);
    if (env == null || env.isBlank()) {
      return null;
    }
    String url = env.trim();
    System.out.println("[Fetcher] " + dataset + " source: " + url + " (optional env override)");
    return fetch(url);
  }

  private static JsonNode fetchFromOptionalEnvOrDefault(
      String envName, String fallbackUrl, String dataset) {
    List<String> urls = parseCandidateUrls(System.getenv(envName), fallbackUrl);
    if (urls.isEmpty()) {
      return null;
    }

    for (int i = 0; i < urls.size(); i++) {
      String url = urls.get(i);
      boolean fromEnv =
          i == 0 && System.getenv(envName) != null && !System.getenv(envName).isBlank();
      System.out.println(
          "[Fetcher] "
              + dataset
              + " source: "
              + url
              + (fromEnv ? " (optional env override)" : " (default optional)"));
      JsonNode json = fetch(url);
      if (json != null) {
        return json;
      }
    }
    return null;
  }

  private static JsonNode fetchFromHtmlStatsEnvOrDefault(
      String envName, String fallbackUrl, String dataset, String[] preferredKeys) {
    List<String> urls = parseCandidateUrls(System.getenv(envName), fallbackUrl);
    for (int i = 0; i < urls.size(); i++) {
      String url = urls.get(i);
      boolean fromEnv =
          i == 0 && System.getenv(envName) != null && !System.getenv(envName).isBlank();
      System.out.println(
          "[Fetcher] "
              + dataset
              + " source: "
              + url
              + (fromEnv ? " (html env override)" : " (html default)"));
      JsonNode extracted = fetchEmbeddedStatsArray(url, preferredKeys);
      if (extracted != null && extracted.isArray() && !extracted.isEmpty()) {
        return extracted;
      }
    }
    return null;
  }

  private static List<String> parseCandidateUrls(String envValue, String fallbackUrl) {
    List<String> urls = new ArrayList<>();
    addDelimitedUrls(urls, envValue);
    addDelimitedUrls(urls, fallbackUrl);
    return urls;
  }

  private static void addDelimitedUrls(List<String> urls, String value) {
    if (value == null || value.isBlank()) {
      return;
    }
    for (String part : value.split("[,;\\n]")) {
      String trimmed = part.trim();
      if (!trimmed.isBlank() && !urls.contains(trimmed)) {
        urls.add(trimmed);
      }
    }
  }

  private static JsonNode fetchEmbeddedStatsArray(String urlString, String[] preferredKeys) {
    try {
      URL url = new URL(urlString);
      HttpURLConnection conn = (HttpURLConnection) url.openConnection();
      conn.setRequestMethod("GET");
      conn.setRequestProperty("User-Agent", "Mozilla/5.0");
      conn.setRequestProperty("Accept", "text/html,application/json,text/plain,*/*");
      conn.setRequestProperty("Referer", "https://www.erkul.games/");
      conn.setRequestProperty("Origin", "https://www.erkul.games");
      conn.setConnectTimeout(20000);
      conn.setReadTimeout(30000);

      BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
      StringBuilder response = new StringBuilder();
      String line;
      while ((line = reader.readLine()) != null) {
        response.append(line);
      }
      reader.close();

      String body = response.toString();
      if (body.isBlank()) {
        return null;
      }

      String contentType = conn.getHeaderField("Content-Type");
      boolean html =
          (contentType != null && contentType.toLowerCase().contains("text/html"))
              || body.startsWith("<!DOCTYPE html")
              || body.startsWith("<html");
      if (!html) {
        JsonNode json = mapper.readTree(body);
        JsonNode rows = unwrapDataArray(json);
        return (rows != null && rows.isArray() && !rows.isEmpty()) ? rows : null;
      }

      JsonNode nextData = extractNextDataNode(body);
      if (nextData == null) {
        return null;
      }

      JsonNode preferred = findArrayByKeyHints(nextData, preferredKeys);
      if (preferred != null) {
        System.out.println("[Fetcher] Extracted stats from __NEXT_DATA__ using preferred keys.");
        return preferred;
      }

      JsonNode fallback = findLikelyStatArray(nextData);
      if (fallback != null) {
        System.out.println(
            "[Fetcher] Extracted stats from __NEXT_DATA__ using structural fallback.");
      }
      return fallback;
    } catch (Exception e) {
      System.out.println(
          "[Fetcher] Failed HTML stats extraction: " + urlString + " | " + e.getMessage());
      return null;
    }
  }

  private static JsonNode extractNextDataNode(String html) {
    Pattern p = Pattern.compile("(?is)<script[^>]*id=['\"]__NEXT_DATA__['\"][^>]*>(.*?)</script>");
    Matcher m = p.matcher(html);
    if (!m.find()) {
      return null;
    }
    String payload = m.group(1);
    if (payload == null || payload.isBlank()) {
      return null;
    }
    try {
      return mapper.readTree(payload);
    } catch (Exception ignored) {
      return null;
    }
  }

  private static JsonNode findArrayByKeyHints(JsonNode node, String[] keyHints) {
    if (node == null || keyHints == null || keyHints.length == 0) {
      return null;
    }
    for (String key : keyHints) {
      JsonNode found = findArrayByKey(node, key == null ? "" : key.toLowerCase(Locale.ROOT));
      if (found != null && found.isArray() && !found.isEmpty()) {
        return found;
      }
    }
    return null;
  }

  private static JsonNode findArrayByKey(JsonNode node, String keyHint) {
    if (node == null || keyHint == null || keyHint.isBlank()) {
      return null;
    }
    if (node.isObject()) {
      var fields = node.fields();
      while (fields.hasNext()) {
        Map.Entry<String, JsonNode> entry = fields.next();
        String key = entry.getKey() == null ? "" : entry.getKey().toLowerCase(Locale.ROOT);
        JsonNode value = entry.getValue();
        if (key.equals(keyHint) && value != null && value.isArray()) {
          return value;
        }
        JsonNode nested = findArrayByKey(value, keyHint);
        if (nested != null) {
          return nested;
        }
      }
      return null;
    }
    if (node.isArray()) {
      for (JsonNode item : node) {
        JsonNode nested = findArrayByKey(item, keyHint);
        if (nested != null) {
          return nested;
        }
      }
    }
    return null;
  }

  private static JsonNode findLikelyStatArray(JsonNode node) {
    if (node == null) {
      return null;
    }
    if (node.isArray()) {
      if (node.size() > 0 && looksLikeStatRow(node.get(0))) {
        return node;
      }
      for (JsonNode item : node) {
        JsonNode nested = findLikelyStatArray(item);
        if (nested != null) {
          return nested;
        }
      }
      return null;
    }
    if (node.isObject()) {
      var fields = node.fields();
      while (fields.hasNext()) {
        JsonNode nested = findLikelyStatArray(fields.next().getValue());
        if (nested != null) {
          return nested;
        }
      }
    }
    return null;
  }

  private static boolean looksLikeStatRow(JsonNode row) {
    if (row == null || !row.isObject()) {
      return false;
    }
    boolean hasName =
        !row.path("name").asText("").isBlank() || !row.path("name_full").asText("").isBlank();
    boolean hasCombat =
        row.has("dps")
            || row.has("rpm")
            || row.has("alpha_damage")
            || row.has("damage")
            || row.has("projectile_speed")
            || row.has("type");
    return hasName && hasCombat;
  }

  private static JsonNode fetchItemsByCategoryIds(
      String envName, int[] defaultIds, String dataset) {
    int[] categoryIds = parseCategoryIdsFromEnv(envName, defaultIds);
    ArrayNode merged = mapper.createArrayNode();

    for (int id : categoryIds) {
      String url = "https://uexcorp.space/api/items?id_category=" + id;
      JsonNode payload = fetch(url);
      JsonNode rows = unwrapDataArray(payload);
      if (rows != null && rows.isArray()) {
        rows.forEach(merged::add);
      }
    }

    System.out.println(
        "[Fetcher] "
            + dataset
            + " categories loaded: "
            + categoryIds.length
            + " | merged entries: "
            + merged.size());
    return merged;
  }

  private static int[] parseCategoryIdsFromEnv(String envName, int[] fallback) {
    String env = System.getenv(envName);
    if (env == null || env.isBlank()) {
      return fallback;
    }

    try {
      String[] parts = env.split(",");
      int[] ids = new int[parts.length];
      for (int i = 0; i < parts.length; i++) {
        ids[i] = Integer.parseInt(parts[i].trim());
      }
      return ids.length == 0 ? fallback : ids;
    } catch (Exception e) {
      System.out.println("[Fetcher] Invalid " + envName + " value: " + env + " (using defaults)");
      return fallback;
    }
  }

  private static JsonNode unwrapDataArray(JsonNode raw) {
    if (raw == null || raw.isNull()) {
      return null;
    }
    if (raw.isArray()) {
      return raw;
    }
    if (raw.isObject() && raw.path("data").isArray()) {
      return raw.path("data");
    }
    return raw;
  }

  private static JsonNode enrichShipsWithPurchases(JsonNode baseRaw) {
    JsonNode base = unwrapDataArray(baseRaw);
    if (base == null || !base.isArray()) {
      return baseRaw;
    }

    JsonNode purchasesPayload = fetch("https://uexcorp.space/api/vehicles_purchases_prices_all");
    JsonNode purchases = unwrapDataArray(purchasesPayload);
    if (purchases == null || !purchases.isArray()) {
      return baseRaw;
    }

    Map<String, List<JsonNode>> purchasesByVehicle = new HashMap<>();
    for (JsonNode row : purchases) {
      String name = row.path("vehicle_name").asText("").trim().toLowerCase(Locale.ROOT);
      if (name.isBlank()) {
        continue;
      }
      purchasesByVehicle.computeIfAbsent(name, k -> new ArrayList<>()).add(row);
    }

    ArrayNode enriched = mapper.createArrayNode();
    for (JsonNode row : base) {
      if (!row.isObject()) {
        enriched.add(row);
        continue;
      }
      ObjectNode out = ((ObjectNode) row).deepCopy();
      String name = out.path("name").asText("").trim().toLowerCase(Locale.ROOT);
      List<JsonNode> options = purchasesByVehicle.get(name);
      if (options != null && !options.isEmpty()) {
        applyShipPurchaseEnrichment(out, options);
      }
      enriched.add(out);
    }

    System.out.println(
        "[Fetcher] ships enriched with purchase rows: "
            + purchasesByVehicle.size()
            + " vehicle keys");
    return enriched;
  }

  private static void applyShipPurchaseEnrichment(ObjectNode out, List<JsonNode> options) {
    double bestPrice = Double.MAX_VALUE;
    String bestLocation = "";
    ArrayNode locations = mapper.createArrayNode();
    int added = 0;

    for (JsonNode option : options) {
      double price = option.path("price_buy").asDouble(0);
      String terminal = option.path("terminal_name").asText("").trim();
      if (!terminal.isBlank() && added < 12 && !containsText(locations, terminal)) {
        locations.add(terminal);
        added++;
      }
      if (price > 0 && price < bestPrice) {
        bestPrice = price;
        bestLocation = terminal;
      }
    }

    if (bestPrice < Double.MAX_VALUE && isMissingOrEmpty(out.path("price_buy"))) {
      out.put("price_buy", bestPrice);
    }
    if (!bestLocation.isBlank() && isMissingOrEmpty(out.path("buy_location"))) {
      out.put("buy_location", bestLocation);
    }
    if (!locations.isEmpty()
        && (out.path("buy_locations").isMissingNode() || out.path("buy_locations").isEmpty())) {
      out.set("buy_locations", locations);
    }
  }

  private static JsonNode fetchItemPricesByCategory(int categoryId) {
    String url = "https://uexcorp.space/api/items_prices?id_category=" + categoryId;
    return fetch(url);
  }

  private static JsonNode enrichItemsWithPricesByCategories(
      JsonNode baseRaw, int[] categoryIds, String dataset) {
    JsonNode base = unwrapDataArray(baseRaw);
    if (base == null || !base.isArray()) {
      return baseRaw;
    }

    Map<String, List<JsonNode>> pricesByItem = new HashMap<>();
    for (int categoryId : categoryIds) {
      JsonNode pricePayload = fetchItemPricesByCategory(categoryId);
      JsonNode rows = unwrapDataArray(pricePayload);
      if (rows == null || !rows.isArray()) {
        continue;
      }
      for (JsonNode row : rows) {
        String itemName = row.path("item_name").asText("").trim().toLowerCase(Locale.ROOT);
        if (itemName.isBlank()) {
          continue;
        }
        pricesByItem.computeIfAbsent(itemName, k -> new ArrayList<>()).add(row);
      }
    }

    ArrayNode enriched = mapper.createArrayNode();
    for (JsonNode row : base) {
      if (!row.isObject()) {
        enriched.add(row);
        continue;
      }
      ObjectNode out = ((ObjectNode) row).deepCopy();
      String nameKey = out.path("name").asText("").trim().toLowerCase(Locale.ROOT);
      List<JsonNode> prices = pricesByItem.get(nameKey);
      if (prices != null && !prices.isEmpty()) {
        applyPriceEnrichment(out, prices);
      }
      enriched.add(out);
    }

    System.out.println(
        "[Fetcher] "
            + dataset
            + " enriched with item prices: "
            + pricesByItem.size()
            + " item keys");
    return enriched;
  }

  private static void applyPriceEnrichment(ObjectNode out, List<JsonNode> prices) {
    double bestBuy = Double.MAX_VALUE;
    String bestLocation = "";
    ArrayNode locations = mapper.createArrayNode();
    double bestSell = 0;
    String bestSellLocation = "";
    ArrayNode sellLocations = mapper.createArrayNode();
    int added = 0;
    int sellAdded = 0;

    for (JsonNode price : prices) {
      String terminal = price.path("terminal_name").asText("").trim();
      if (terminal.isBlank()) {
        continue;
      }

      double buy = price.path("price_buy").asDouble(0);
      double sell = price.path("price_sell").asDouble(0);
      String city = price.path("city_name").asText("").trim();
      String body = price.path("planet_name").asText(price.path("orbit_name").asText(""));

      String loc = terminal;
      if (!city.isBlank()) {
        loc += " (" + city + ")";
      } else if (!body.isBlank()) {
        loc += " (" + body.trim() + ")";
      }

      if (added < 10 && !containsText(locations, loc)) {
        locations.add(loc);
        added++;
      }

      if (buy > 0 && buy < bestBuy) {
        bestBuy = buy;
        bestLocation = loc;
      }
      if (sell > 0 && sell > bestSell) {
        bestSell = sell;
        bestSellLocation = loc;
      }
      if (sell > 0 && sellAdded < 10 && !containsText(sellLocations, loc)) {
        sellLocations.add(loc);
        sellAdded++;
      }
    }

    if (!bestLocation.isBlank() && isMissingOrEmpty(out.path("buy_location"))) {
      out.put("buy_location", bestLocation);
    }
    if (bestBuy < Double.MAX_VALUE && isMissingOrEmpty(out.path("price_buy"))) {
      out.put("price_buy", bestBuy);
    }
    if (!locations.isEmpty()
        && (out.path("buy_locations").isMissingNode() || out.path("buy_locations").isEmpty())) {
      out.set("buy_locations", locations);
    }
    if (!bestSellLocation.isBlank() && isMissingOrEmpty(out.path("sell_location"))) {
      out.put("sell_location", bestSellLocation);
    }
    if (bestSell > 0 && isMissingOrEmpty(out.path("price_sell"))) {
      out.put("price_sell", bestSell);
    }
    if (!sellLocations.isEmpty()
        && (out.path("sell_locations").isMissingNode() || out.path("sell_locations").isEmpty())) {
      out.set("sell_locations", sellLocations);
    }
  }

  private static boolean containsText(ArrayNode values, String target) {
    for (JsonNode value : values) {
      if (value.asText("").equalsIgnoreCase(target)) {
        return true;
      }
    }
    return false;
  }

  /**
   * Best-effort merge by name: keeps primary rows and fills missing fields from secondary rows.
   */
  private static JsonNode mergeByName(
      JsonNode primaryRaw, JsonNode secondaryRaw, String... nameKeys) {
    JsonNode primary = unwrapDataArray(primaryRaw);
    if (primary == null || !primary.isArray()) {
      return primaryRaw;
    }

    JsonNode secondary = unwrapDataArray(secondaryRaw);
    if (secondary == null || !secondary.isArray()) {
      return primaryRaw;
    }

    Map<String, JsonNode> secondaryByName = new HashMap<>();
    for (JsonNode row : secondary) {
      String key = extractNameKey(row, nameKeys);
      if (!key.isBlank() && !secondaryByName.containsKey(key)) {
        secondaryByName.put(key, row);
      }
    }

    ArrayNode merged = mapper.createArrayNode();
    for (JsonNode row : primary) {
      if (!row.isObject()) {
        merged.add(row);
        continue;
      }

      ObjectNode out = ((ObjectNode) row).deepCopy();
      String key = extractNameKey(row, nameKeys);
      JsonNode extra = key.isBlank() ? null : secondaryByName.get(key);
      if (extra != null && extra.isObject()) {
        extra
            .fields()
            .forEachRemaining(
                f -> {
                  if (isMissingOrEmpty(out.path(f.getKey()))) {
                    out.set(f.getKey(), f.getValue());
                  }
                });
      }
      merged.add(out);
    }

    return merged;
  }

  private static String extractNameKey(JsonNode node, String... keys) {
    if (node == null || keys == null) {
      return "";
    }
    for (String key : keys) {
      if (key == null || key.isBlank()) {
        continue;
      }
      String value = node.path(key).asText("").trim();
      if (!value.isBlank()) {
        return value.toLowerCase();
      }
    }
    return "";
  }

  private static boolean isMissingOrEmpty(JsonNode node) {
    if (node == null || node.isNull() || node.isMissingNode()) {
      return true;
    }
    if (node.isTextual()) {
      return node.asText("").trim().isEmpty();
    }
    if (node.isArray() || node.isObject()) {
      return node.isEmpty();
    }
    if (node.isNumber()) {
      return Math.abs(node.asDouble(0)) < 1e-9;
    }
    return false;
  }

  // Shared fetch logic for all external endpoints.

  /**
   * Performs an HTTP GET and parses the response body as JSON.
   */
  private static JsonNode fetch(String urlString) {
    try {
      URL url = new URL(urlString);
      HttpURLConnection conn = (HttpURLConnection) url.openConnection();

      // Minimal headers to avoid provider-side request rejection.
      conn.setRequestMethod("GET");
      conn.setRequestProperty("User-Agent", "Mozilla/5.0");
      conn.setRequestProperty("Accept", "application/json,text/plain,*/*");
      conn.setRequestProperty("Referer", "https://www.erkul.games/");
      conn.setRequestProperty("Origin", "https://www.erkul.games");
      conn.setConnectTimeout(20000);
      conn.setReadTimeout(30000);

      BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));

      StringBuilder response = new StringBuilder();
      String line;

      while ((line = reader.readLine()) != null) {
        response.append(line);
      }

      reader.close();

      String body = response.toString();
      String contentType = conn.getHeaderField("Content-Type");
      if ((contentType != null && contentType.toLowerCase().contains("text/html"))
          || body.startsWith("<!DOCTYPE html")
          || body.startsWith("<html")) {
        System.out.println("[Fetcher] Non-JSON response from: " + urlString);
        if (urlString.contains("erkul.games/live")) {
          System.out.println(
              "[Fetcher] Erkul /live pages are app routes (HTML). "
                  + "Use a direct JSON export endpoint for SC_SHIPS_STATS_URL / SC_WEAPONS_STATS_URL.");
        }
        return null;
      }

      return mapper.readTree(body);

    } catch (Exception e) {
      System.out.println("[Fetcher] Failed to fetch: " + urlString);
      e.printStackTrace();
      return null;
    }
  }
}
