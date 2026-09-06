package Botcode.StarCitizen;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Converts raw API payloads into a stable internal JSON shape used by commands.
 *
 * <p>Each normalizer ensures missing sections are initialized and adds update timestamps so
 * downstream features can track data freshness.
 */
public class StarCitizenNormalizer {

  private static final ObjectMapper mapper = new ObjectMapper();

  // ---------------------------------------------------------
  // UEX NORMALIZERS
  // ---------------------------------------------------------

  /**
   * Normalizes commodity payloads and computes best buy/sell plus profit metadata.
   */
  public static JsonNode normalizeCommodities(JsonNode raw) {
    ObjectNode root = mapper.createObjectNode();

    JsonNode rows = unwrapDataArray(raw);
    if (rows == null || !rows.isArray()) {
      return root;
    }

    for (JsonNode commodity : rows) {
      String name = commodity.path("name").asText();

      // UEX API 2.0 price payloads expose these keys instead of nested buy/sell arrays.
      if (name.isEmpty()) {
        name = commodity.path("commodity_name").asText();
      }
      if (name.isEmpty()) {
        continue;
      }

      ObjectNode entry = root.has(name) ? (ObjectNode) root.path(name) : newCommodityEntry();

      if (commodity.path("buy").isArray() || commodity.path("sell").isArray()) {
        // Legacy schema: buy/sell arrays already present.
        entry.set(
            "buy",
            commodity.path("buy").isArray() ? commodity.path("buy") : mapper.createArrayNode());
        entry.set(
            "sell",
            commodity.path("sell").isArray() ? commodity.path("sell") : mapper.createArrayNode());
      } else {
        // Current schema: each row is a terminal quote for one commodity.
        String location =
            commodity.path("terminal_name").asText(commodity.path("location").asText(""));
        if (!location.isBlank()) {
          double buyPrice = commodity.path("price_buy").asDouble(0);
          if (buyPrice > 0) {
            ObjectNode buy = mapper.createObjectNode();
            buy.put("location", location);
            buy.put("price", buyPrice);
            entry.withArray("buy").add(buy);
          }

          double sellPrice = commodity.path("price_sell").asDouble(0);
          if (sellPrice > 0) {
            ObjectNode sell = mapper.createObjectNode();
            sell.put("location", location);
            sell.put("price", sellPrice);
            entry.withArray("sell").add(sell);
          }
        }
      }

      root.set(name, entry);
    }

    // Finalize computed fields after all rows are merged.
    root.fields()
        .forEachRemaining(
            field -> {
              ObjectNode entry = (ObjectNode) field.getValue();

              double bestBuy = Double.MAX_VALUE;
              String bestBuyLoc = null;
              for (JsonNode b : entry.withArray("buy")) {
                double price = b.path("price").asDouble(Double.MAX_VALUE);
                if (price < bestBuy) {
                  bestBuy = price;
                  bestBuyLoc = b.path("location").asText();
                }
              }

              double bestSell = 0;
              String bestSellLoc = null;
              for (JsonNode s : entry.withArray("sell")) {
                double price = s.path("price").asDouble(0);
                if (price > bestSell) {
                  bestSell = price;
                  bestSellLoc = s.path("location").asText();
                }
              }

              entry.put("best_buy", bestBuyLoc == null ? "" : bestBuyLoc);
              entry.put("best_sell", bestSellLoc == null ? "" : bestSellLoc);
              entry.put(
                  "profit_per_scu",
                  (bestBuyLoc != null && bestSellLoc != null) ? (bestSell - bestBuy) : 0);
              entry.put("last_update", System.currentTimeMillis() / 1000);
            });

    return root;
  }

  private static ObjectNode newCommodityEntry() {
    ObjectNode entry = mapper.createObjectNode();
    entry.set("buy", mapper.createArrayNode());
    entry.set("sell", mapper.createArrayNode());
    return entry;
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

  /**
   * Normalizes refinery capacity rows into station lookup entries.
   */
  public static JsonNode normalizeRefineryStations(JsonNode raw) {
    ObjectNode root = mapper.createObjectNode();
    JsonNode rows = unwrapDataArray(raw);
    if (rows == null || !rows.isArray()) {
      return root;
    }

    for (JsonNode row : rows) {
      String stationName =
          row.path("terminal_name")
              .asText(row.path("space_station_name").asText(row.path("name").asText("")));
      if (stationName.isBlank()) {
        continue;
      }

      ObjectNode station = mapper.createObjectNode();
      station.put("system", row.path("star_system_name").asText("Unknown"));

      String body = row.path("orbit_name").asText("");
      if (body.isBlank()) {
        body = row.path("planet_name").asText("");
      }
      if (body.isBlank()) {
        body = row.path("moon_name").asText("");
      }
      if (body.isBlank()) {
        body = row.path("space_station_name").asText("Unknown");
      }
      station.put("body", body);

      root.set(stationName, station);
    }

    return root;
  }

  /**
   * Normalizes trade route payloads into commodity-keyed route summaries.
   */
  public static JsonNode normalizeTradeRoutes(JsonNode raw) {
    ObjectNode root = mapper.createObjectNode();

    if (raw == null || !raw.isArray()) {
      return root;
    }

    for (JsonNode route : raw) {
      String commodity = route.path("commodity").asText();
      if (commodity.isEmpty()) {
        continue;
      }

      ObjectNode entry = mapper.createObjectNode();

      entry.put("best_buy", route.path("best_buy").asText(""));
      entry.put("best_sell", route.path("best_sell").asText(""));
      entry.put("profit_per_scu", route.path("profit_per_scu").asDouble(0));
      entry.put("distance", route.path("distance").asDouble(0));
      entry.put("risk", route.path("risk").asText("unknown"));
      entry.put("last_update", System.currentTimeMillis() / 1000);

      root.set(commodity, entry);
    }

    return root;
  }

  // ---------------------------------------------------------
  // PLACEHOLDER NORMALIZERS FOR FUTURE DATASETS
  // ---------------------------------------------------------

  /**
   * Normalizes ship payloads into info/stats/weapons/component sections.
   */
  public static JsonNode normalizeShips(JsonNode raw) {
    ObjectNode root = mapper.createObjectNode();

    JsonNode rows = unwrapDataArray(raw);
    if (rows == null || !rows.isArray()) {
      return root;
    }

    for (JsonNode ship : rows) {
      String name = ship.path("name").asText();
      if (name.isEmpty()) {
        name = ship.path("name_full").asText();
      }
      if (name.isEmpty()) {
        continue;
      }

      ObjectNode entry = mapper.createObjectNode();

      // -------------------------
      // INFO (brief summary)
      // -------------------------
      ObjectNode info = mapper.createObjectNode();
      info.put(
          "manufacturer", ship.path("manufacturer").asText(ship.path("company_name").asText("")));
      info.put("role", ship.path("role").asText(deriveVehicleRole(ship)));
      info.put(
          "size", ship.path("size").asText(deriveVehicleSize(ship.path("length").asDouble(0))));
      info.put("crew", ship.path("crew").asInt(0));
      info.put("cargo", ship.path("cargo").asInt(ship.path("scu").asInt(0)));
      info.put(
          "price_auec",
          firstPositiveDouble(
              ship,
              "price_buy",
              "price",
              "price_sale",
              "price_uec",
              "uec_price",
              "buy_price",
              "msrp"));
      info.put(
          "buy_location",
          firstNonBlank(
              ship,
              "location",
              "terminal_name",
              "shop",
              "store",
              "buy_location",
              "purchase_location"));
      info.set(
          "buy_locations",
          collectTextArray(ship, "buy_locations", "locations", "available_at", "sold_at"));
      info.put(
          "image_url",
          firstNonBlank(ship, "url_photo", "image", "image_url", "url_image", "screenshot"));
      info.put("brochure_url", firstNonBlank(ship, "url_brochure", "brochure_url"));
      info.put("store_url", firstNonBlank(ship, "url_store", "store_url"));
      info.put("scm_speed", ship.path("scm_speed").asInt(0));
      info.put("max_speed", ship.path("max_speed").asInt(0));
      info.put(
          "shield_hp",
          (int)
              Math.round(
                  firstPositiveDouble(
                      ship,
                      "shield_hp",
                      "shield_health",
                      "shield_capacity",
                      "shields",
                      "hitpoints_shield")));
      info.put(
          "hull_hp",
          (int)
              Math.round(
                  firstPositiveDouble(
                      ship, "hull_hp", "hp", "health", "hitpoints", "durability", "hit_points")));
      info.put(
          "sell_location",
          firstNonBlank(ship, "sell_location", "sold_at", "sell_at", "terminal_name"));
      info.set("sell_locations", collectTextArray(ship, "sell_locations", "sold_at", "sell_at"));
      entry.set("info", info);

      // -------------------------
      // STATS (important only)
      // -------------------------
      ObjectNode stats = mapper.createObjectNode();
      stats.put("mass", ship.path("mass").asDouble(0));
      stats.put("pitch", ship.path("pitch").asDouble(0));
      stats.put("yaw", ship.path("yaw").asDouble(0));
      stats.put("roll", ship.path("roll").asDouble(0));
      stats.put(
          "fuel_capacity",
          ship.path("fuel_capacity").asDouble(ship.path("fuel_hydrogen").asDouble(0)));
      stats.put(
          "quantum_fuel",
          ship.path("quantum_fuel").asDouble(ship.path("fuel_quantum").asDouble(0)));
      stats.put(
          "hydrogen_fuel",
          firstPositiveDouble(ship, "fuel_hydrogen", "hydrogen_fuel", "fuel_capacity"));
      stats.put("qt_speed", ship.path("qt_speed").asDouble(0));
      stats.put("qt_range", ship.path("qt_range").asDouble(0));
      stats.put(
          "pilot_dps",
          firstPositiveDouble(ship, "pilot_dps", "dps_pilot", "weapon_dps_pilot", "dps_nose"));
      stats.put(
          "turret_dps", firstPositiveDouble(ship, "turret_dps", "dps_turret", "weapon_dps_turret"));
      stats.put(
          "missile_dps",
          firstPositiveDouble(ship, "missile_dps", "dps_missile", "weapon_dps_missile"));
      stats.put(
          "total_dps",
          firstPositiveDouble(ship, "dps", "total_dps", "combat_dps", "weapon_dps_total"));
      stats.put(
          "pilot_alpha",
          firstPositiveDouble(ship, "pilot_alpha", "alpha_pilot", "weapon_alpha_pilot"));
      stats.put(
          "turret_alpha",
          firstPositiveDouble(ship, "turret_alpha", "alpha_turret", "weapon_alpha_turret"));
      stats.put(
          "missile_alpha",
          firstPositiveDouble(ship, "missile_alpha", "alpha_missile", "weapon_alpha_missile"));
      double totalAlpha = firstPositiveDouble(ship, "alpha", "total_alpha", "weapon_alpha_total");
      if (totalAlpha <= 0) {
        totalAlpha =
            stats.path("pilot_alpha").asDouble(0)
                + stats.path("turret_alpha").asDouble(0)
                + stats.path("missile_alpha").asDouble(0);
      }
      stats.put("total_alpha", totalAlpha);
      entry.set("stats", stats);

      // -------------------------
      // WEAPONS
      // -------------------------
      entry.set(
          "weapons",
          ship.path("weapons").isArray() ? ship.path("weapons") : mapper.createArrayNode());

      // -------------------------
      // MISSILES
      // -------------------------
      entry.set("missiles", firstArray(ship, "missiles", "missile_banks", "torpedoes"));

      // -------------------------
      // MISSILE RACKS
      // -------------------------
      entry.set(
          "missile_racks",
          firstArray(ship, "missile_racks", "missile_rack", "missileRacks", "torpedo_racks"));

      // -------------------------
      // TURRETS
      // -------------------------
      entry.set(
          "turrets",
          ship.path("turrets").isArray() ? ship.path("turrets") : mapper.createArrayNode());

      // -------------------------
      // COMPONENTS
      // -------------------------
      entry.set(
          "components",
          ship.path("components").isObject() ? ship.path("components") : mapper.createObjectNode());

      // -------------------------
      // TIMESTAMP
      // -------------------------
      entry.put("last_update", System.currentTimeMillis() / 1000);

      root.set(name, entry);
    }

    return root;
  }

  /**
   * Normalizes weapon payloads into info/stats/ammo/fire mode sections.
   */
  public static JsonNode normalizeWeapons(JsonNode raw) {
    ObjectNode root = mapper.createObjectNode();

    JsonNode rows = unwrapDataArray(raw);
    if (rows == null || !rows.isArray()) {
      return root;
    }

    for (JsonNode weapon : rows) {
      String name = weapon.path("name").asText();
      if (name.isEmpty()) {
        continue;
      }

      ObjectNode entry = mapper.createObjectNode();

      // -------------------------
      // INFO (brief summary)
      // -------------------------
      ObjectNode info = mapper.createObjectNode();
      info.put(
          "manufacturer",
          weapon.path("manufacturer").asText(weapon.path("company_name").asText("")));
      info.put(
          "type",
          weapon
              .path("type")
              .asText(weapon.path("category").asText(weapon.path("section").asText(""))));
      info.put("size", parseSize(weapon.path("size")));
      info.put("class", weapon.path("class").asText(weapon.path("section").asText("")));
      info.put("domain", classifyWeaponDomain(weapon));
      info.put("damage_type", weapon.path("damage_type").asText(""));
      info.put(
          "hardpoint",
          firstNonBlank(weapon, "vehicle_name", "hardpoint", "mount", "compatibility"));
      info.put(
          "buy_location",
          firstNonBlank(
              weapon,
              "location",
              "terminal_name",
              "shop",
              "store",
              "buy_location",
              "purchase_location"));
      info.set(
          "buy_locations",
          collectTextArray(weapon, "buy_locations", "locations", "available_at", "sold_at"));
      info.put("sell_location", firstNonBlank(weapon, "sell_location", "sold_at", "sell_at"));
      info.set("sell_locations", collectTextArray(weapon, "sell_locations", "sold_at", "sell_at"));
      info.put("store_url", firstNonBlank(weapon, "url_store", "store_url"));
      entry.set("info", info);

      // -------------------------
      // STATS (important only)
      // -------------------------
      ObjectNode stats = mapper.createObjectNode();
      double alphaDamage =
          firstPositiveFromPaths(
              weapon,
              "alpha_damage",
              "damage_alpha",
              "damage_per_shot",
              "damage",
              "shot_damage",
              "damage_total",
              "alpha_min",
              "alpha_max");
      int rpm =
          (int)
              Math.round(
                  firstPositiveFromPaths(
                      weapon,
                      "rpm",
                      "rate_of_fire",
                      "rof",
                      "fire_rate",
                      "rounds_per_minute",
                      "full_charge_fire_rate"));
      double dps =
          firstPositiveFromPaths(weapon, "dps", "weapon_dps", "damage_per_second", "burst_dps");
      if (dps <= 0 && alphaDamage > 0 && rpm > 0) {
        dps = alphaDamage * (rpm / 60.0);
      }
      if (alphaDamage <= 0 && dps > 0 && rpm > 0) {
        alphaDamage = dps / (rpm / 60.0);
      }

      stats.put("dps", dps);
      stats.put("burst_dps", firstPositiveFromPaths(weapon, "burst_dps", "dps_burst"));
      stats.put("alpha_damage", alphaDamage);
      stats.put(
          "alpha_min", firstPositiveFromPaths(weapon, "alpha_min", "damage_min", "min_damage"));
      stats.put(
          "alpha_max", firstPositiveFromPaths(weapon, "alpha_max", "damage_max", "max_damage"));
      stats.put("rpm", rpm);
      stats.put(
          "range",
          (int) Math.round(firstPositiveFromPaths(weapon, "range", "max_range", "distance")));
      stats.put(
          "projectile_speed",
          (int)
              Math.round(
                  firstPositiveFromPaths(
                      weapon, "projectile_speed", "speed", "ammo_speed", "bullet_speed")));
      stats.put("quality", firstPositiveFromPaths(weapon, "quality"));
      stats.put("hp", firstPositiveFromPaths(weapon, "hp", "health", "hitpoints", "durability"));

      // Extended weapon stats used by Erkul-style sheets.
      stats.put(
          "penetration_distance",
          firstPositiveFromPaths(
              weapon, "penetration_distance", "penetration.distance", "pen_dist"));
      stats.put(
          "penetration_near_radius",
          firstPositiveFromPaths(
              weapon, "penetration_near_radius", "penetration.near_radius", "pen_near_radius"));
      stats.put(
          "penetration_far_radius",
          firstPositiveFromPaths(
              weapon, "penetration_far_radius", "penetration.far_radius", "pen_far_radius"));
      stats.put(
          "power_consumption",
          firstPositiveFromPaths(
              weapon, "power_consumption", "power_draw", "power_usage", "power_required"));
      stats.put(
          "max_ammos",
          firstPositiveFromPaths(weapon, "max_ammos", "max_ammo", "ammo_max", "ammo.max"));
      stats.put(
          "ammos_regen",
          firstPositiveFromPaths(
              weapon, "ammos_regen", "ammo_regen", "ammo_regeneration", "ammo.regen"));
      stats.put(
          "overheat",
          firstPositiveFromPaths(
              weapon, "overheat", "overheat_time", "heat_capacity", "overheat_threshold"));
      stats.put(
          "total_dmg_dealt",
          firstPositiveFromPaths(weapon, "total_dmg_dealt", "total_damage_dealt", "total_damage"));
      stats.put(
          "total_dealt_time",
          firstPositiveFromPaths(weapon, "total_dealt_time", "total_damage_time", "time_to_empty"));
      stats.put(
          "full_charge_fire_rate",
          firstPositiveFromPaths(weapon, "full_charge_fire_rate", "charge_fire_rate"));
      stats.put(
          "charged_dmg_multi",
          firstPositiveFromPaths(
              weapon,
              "charged_dmg_multi",
              "charged_damage_multiplier",
              "charge_damage_multiplier"));
      stats.put("charge_time", firstPositiveFromPaths(weapon, "charge_time", "charge.duration"));
      stats.put(
          "pellets_per_shot",
          firstPositiveFromPaths(weapon, "pellets_per_shot", "pellets", "projectiles_per_shot"));
      stats.put(
          "explosion_radius",
          firstPositiveFromPaths(weapon, "explosion_radius", "aoe_radius", "blast_radius"));
      stats.put(
          "regen_cooldown",
          firstPositiveFromPaths(
              weapon, "regen_cooldown", "ammo_regen_cooldown", "regeneration_cooldown"));

      stats.put(
          "spread_first_attack",
          firstPositiveFromPaths(weapon, "spread_first_attack", "spread.first_attack"));
      stats.put("spread_attack", firstPositiveFromPaths(weapon, "spread_attack", "spread.attack"));
      stats.put("spread_min", firstPositiveFromPaths(weapon, "spread_min", "spread.min"));
      stats.put("spread_max", firstPositiveFromPaths(weapon, "spread_max", "spread.max"));
      stats.put("spread_decay", firstPositiveFromPaths(weapon, "spread_decay", "spread.decay"));

      stats.put(
          "ammo_dmg_biochemical",
          firstPositiveFromPaths(
              weapon, "ammo_dmg_biochemical", "damage.biochemical", "ammo_damage.biochemical"));
      stats.put(
          "ammo_dmg_distortion",
          firstPositiveFromPaths(
              weapon, "ammo_dmg_distortion", "damage.distortion", "ammo_damage.distortion"));
      stats.put(
          "ammo_dmg_energy",
          firstPositiveFromPaths(weapon, "ammo_dmg_energy", "damage.energy", "ammo_damage.energy"));
      stats.put(
          "ammo_dmg_physical",
          firstPositiveFromPaths(
              weapon, "ammo_dmg_physical", "damage.physical", "ammo_damage.physical"));
      stats.put(
          "ammo_dmg_stun",
          firstPositiveFromPaths(weapon, "ammo_dmg_stun", "damage.stun", "ammo_damage.stun"));
      stats.put(
          "ammo_dmg_thermal",
          firstPositiveFromPaths(
              weapon, "ammo_dmg_thermal", "damage.thermal", "ammo_damage.thermal"));

      stats.put(
          "distortion_shutdown_dmg",
          firstPositiveFromPaths(weapon, "distortion_shutdown_dmg", "distortion.shutdown_damage"));
      stats.put(
          "distortion_decay_delay",
          firstPositiveFromPaths(weapon, "distortion_decay_delay", "distortion.decay_delay"));
      stats.put(
          "distortion_decay_rate",
          firstPositiveFromPaths(weapon, "distortion_decay_rate", "distortion.decay_rate"));
      stats.put(
          "distortion_warning_ratio",
          firstPositiveFromPaths(weapon, "distortion_warning_ratio", "distortion.warning_ratio"));

      stats.put(
          "fire_only_on_full_charge",
          firstBooleanFromPaths(weapon, "fire_only_on_full_charge", "charge.fire_only_on_full"));
      stats.put(
          "fire_automatically_on_full_charge",
          firstBooleanFromPaths(
              weapon, "fire_automatically_on_full_charge", "charge.auto_fire_on_full"));

      stats.put(
          "cost_auec",
          firstPositiveFromPaths(
              weapon,
              "price_buy",
              "price",
              "price_sale",
              "price_uec",
              "uec_price",
              "buy_price",
              "msrp"));
      stats.put(
          "sell_price",
          firstPositiveFromPaths(
              weapon, "price_sell", "sell_price", "trade_price", "resell_price"));
      entry.set("stats", stats);

      // -------------------------
      // AMMO (if applicable)
      // -------------------------
      entry.set(
          "ammo", weapon.path("ammo").isObject() ? weapon.path("ammo") : mapper.createObjectNode());

      // -------------------------
      // FIRE MODES
      // -------------------------
      entry.set(
          "fire_modes",
          weapon.path("fire_modes").isArray()
              ? weapon.path("fire_modes")
              : mapper.createArrayNode());

      // -------------------------
      // ATTACHMENTS
      // -------------------------
      entry.set(
          "attachments",
          weapon.path("attachments").isArray()
              ? weapon.path("attachments")
              : mapper.createArrayNode());

      // -------------------------
      // TIMESTAMP
      // -------------------------
      entry.put("last_update", System.currentTimeMillis() / 1000);

      root.set(name, entry);
    }

    return root;
  }

  /**
   * Normalizes component payloads into info/stats/attributes sections.
   *
   * <p>Grade is normalized to a human-readable label: A, B, C, Military, Civilian, Industrial.
   * Stats cover power output, power draw, cooling rate, HP, cost, and buy/sell prices.
   */
  public static JsonNode normalizeComponents(JsonNode raw) {
    ObjectNode root = mapper.createObjectNode();

    JsonNode rows = unwrapDataArray(raw);
    if (rows == null || !rows.isArray()) {
      return root;
    }

    for (JsonNode comp : rows) {
      String name = comp.path("name").asText();
      if (name.isEmpty()) {
        continue;
      }

      ObjectNode entry = mapper.createObjectNode();

      // -------------------------
      // INFO (brief summary)
      // -------------------------
      ObjectNode info = mapper.createObjectNode();
      info.put(
          "manufacturer", firstNonBlank(comp, "manufacturer", "company_name", "manufacturer_name"));
      info.put(
          "type",
          firstNonBlank(comp, "type", "category", "section", "item_type", "sub_type", "subtype"));
      info.put(
          "size",
          parseSize(
              comp.path("size").isMissingNode() ? comp.path("item_size") : comp.path("size")));
      info.put("grade", normalizeGrade(firstNonBlank(comp, "grade", "item_grade", "tier", "rank")));
      info.put(
          "class",
          normalizeClass(
              firstNonBlank(
                  comp, "class", "item_class", "classification", "section", "category_name")));
      info.put(
          "buy_location",
          firstNonBlank(
              comp,
              "location",
              "terminal_name",
              "shop",
              "store",
              "buy_location",
              "purchase_location",
              "sold_at",
              "available_at"));
      info.set(
          "buy_locations",
          collectTextArray(comp, "buy_locations", "locations", "available_at", "sold_at"));
      info.put(
          "sell_location", firstNonBlank(comp, "sell_location", "sold_at_terminal", "sell_at"));
      info.set("sell_locations", collectTextArray(comp, "sell_locations", "sold_at", "sell_at"));
      info.put("store_url", firstNonBlank(comp, "url_store", "store_url", "url"));
      entry.set("info", info);

      // -------------------------
      // STATS
      // -------------------------
      ObjectNode stats = mapper.createObjectNode();

      // Power — output (what it generates) and draw (what it consumes)
      stats.put(
          "power_output",
          firstPositiveDouble(
              comp,
              "power_output",
              "power_generated",
              "em_output",
              "power",
              "power_capacity",
              "energy_output",
              "power_generation"));
      stats.put(
          "power_draw",
          firstPositiveDouble(
              comp,
              "power_draw",
              "power_consumption",
              "power_usage",
              "power_required",
              "power_demand",
              "draw",
              "energy_draw",
              "powerdraw"));

      // Cooling
      stats.put(
          "cooling_rate",
          firstPositiveDouble(
              comp,
              "cooling_rate",
              "cooling",
              "thermal_dissipation",
              "thermal_rate",
              "heat_dissipation",
              "cooler_rate",
              "cooling_output",
              "heat_removal"));

      // Shield HP
      stats.put(
          "shield_hp",
          firstPositiveDouble(
              comp,
              "shield_hp",
              "shield_health",
              "shield_capacity",
              "shields",
              "max_shield_hp",
              "hitpoints_shield"));

      // Component HP / Durability
      stats.put(
          "hp",
          firstPositiveDouble(
              comp,
              "hp",
              "health",
              "hitpoints",
              "durability",
              "max_health",
              "item_hp",
              "component_hp",
              "hit_points",
              "hull_hp"));

      // Quantum drive stats
      stats.put(
          "quantum_speed",
          firstPositiveDouble(
              comp, "quantum_speed", "qt_speed", "drive_speed", "qd_speed", "speed"));
      stats.put(
          "quantum_range",
          firstPositiveDouble(
              comp, "quantum_range", "qt_range", "drive_range", "qd_range", "range"));

      // Cost / trade prices
      stats.put(
          "cost_auec",
          firstPositiveDouble(
              comp,
              "price_buy",
              "price",
              "price_sale",
              "price_uec",
              "uec_price",
              "buy_price",
              "msrp",
              "cost",
              "cost_auec"));
      stats.put(
          "sell_price",
          firstPositiveDouble(comp, "price_sell", "sell_price", "trade_price", "resell_price"));

      // Quality/grade score (numeric)
      stats.put("quality", comp.path("quality").asDouble(0));

      entry.set("stats", stats);

      // -------------------------
      // ATTRIBUTES (raw extra data)
      // -------------------------
      entry.set(
          "attributes",
          comp.path("attributes").isObject() ? comp.path("attributes") : mapper.createObjectNode());

      // -------------------------
      // TIMESTAMP
      // -------------------------
      entry.put("last_update", System.currentTimeMillis() / 1000);

      root.set(name, entry);
    }

    return root;
  }

  /**
   * Normalizes raw grade strings to canonical Star Citizen grade labels. UEX may return letters
   * (A/B/C), numbers (1/2/3), or full words.
   */
  private static String normalizeGrade(String raw) {
    if (raw == null || raw.isBlank()) {
      return "";
    }
    switch (raw.trim().toUpperCase()) {
      case "A":
      case "1":
      case "GRADE A":
      case "ALPHA":
        return "A";
      case "B":
      case "2":
      case "GRADE B":
      case "BETA":
        return "B";
      case "C":
      case "3":
      case "GRADE C":
      case "GAMMA":
        return "C";
      default:
        return raw.trim();
    }
  }

  /**
   * Normalizes raw class strings to canonical Star Citizen class labels. UEX may return Civilian,
   * Military, Industrial, Systems, etc.
   */
  private static String normalizeClass(String raw) {
    if (raw == null || raw.isBlank()) {
      return "";
    }
    String upper = raw.trim().toUpperCase();
    if (upper.contains("MILITARY") || upper.equals("MIL")) {
      return "Military";
    }
    if (upper.contains("CIVILIAN") || upper.equals("CIV")) {
      return "Civilian";
    }
    if (upper.contains("INDUSTRIAL") || upper.equals("IND")) {
      return "Industrial";
    }
    if (upper.contains("STEALTH")) {
      return "Stealth";
    }
    if (upper.contains("RACING")) {
      return "Racing";
    }
    return raw.trim();
  }

  /**
   * Normalizes mining payloads into an object container.
   *
   * <p>Accepted inputs:
   *
   * <ul>
   *   <li>Existing object schema (pass-through)
   *   <li>Array payloads wrapped into {@code entries}
   * </ul>
   */
  public static JsonNode normalizeMining(JsonNode raw) {
    if (raw == null || raw.isNull()) {
      return null;
    }

    if (raw.isObject()) {
      // If API returns wrapped rows, keep a compact entries container.
      JsonNode rows = unwrapDataArray(raw);
      if (rows != null && rows.isArray()) {
        ObjectNode out = mapper.createObjectNode();
        out.set("entries", rows);
        out.put("last_update", System.currentTimeMillis() / 1000);
        return out;
      }
      ObjectNode out = ((ObjectNode) raw).deepCopy();
      out.put("last_update", System.currentTimeMillis() / 1000);
      return out;
    }

    if (raw.isArray()) {
      ObjectNode out = mapper.createObjectNode();
      out.set("entries", raw);
      out.put("last_update", System.currentTimeMillis() / 1000);
      return out;
    }

    return null;
  }

  /**
   * Normalizes refinery payloads into internal schema expected by RefineryService.
   */
  public static JsonNode normalizeRefinery(JsonNode raw) {
    if (raw == null || raw.isNull()) {
      return null;
    }

    // Already in internal schema -> pass through with timestamp refresh.
    if (raw.isObject() && raw.has("methods")) {
      ObjectNode out = ((ObjectNode) raw).deepCopy();
      if (!out.path("ore_base_values").isObject() || out.path("ore_base_values").isEmpty()) {
        out.set("ore_base_values", defaultRefineryOreValues());
      }
      out.put("last_update", System.currentTimeMillis() / 1000);
      return out;
    }

    // Best-effort map from list payloads.
    ObjectNode out = mapper.createObjectNode();
    ObjectNode methods = mapper.createObjectNode();
    out.set("methods", methods);
    out.set("stations", mapper.createObjectNode());
    out.set("ore_base_values", defaultRefineryOreValues());

    JsonNode rows = unwrapDataArray(raw);
    if (rows != null && rows.isArray()) {
      for (JsonNode item : rows) {
        String name = item.path("name").asText("");
        if (name.isEmpty()) {
          name = item.path("method").asText("");
        }
        if (name.isEmpty()) {
          continue;
        }

        ObjectNode entry = mapper.createObjectNode();
        double yield = item.path("yield_bonus_pct").asDouble(Double.NaN);
        if (Double.isNaN(yield)) {
          yield = item.path("yield").asDouble(Double.NaN);
        }
        if (Double.isNaN(yield)) {
          yield = item.path("rating_yield").asDouble(0) * 5.0;
        }

        double timePerScu = item.path("time_per_scu_min").asDouble(Double.NaN);
        if (Double.isNaN(timePerScu)) {
          timePerScu = item.path("time").asDouble(Double.NaN);
        }
        if (Double.isNaN(timePerScu)) {
          double speedRating = item.path("rating_speed").asDouble(2.0);
          timePerScu = Math.max(4.0, 14.0 - (speedRating * 2.0));
        }

        double costModifier = item.path("cost_modifier").asDouble(Double.NaN);
        if (Double.isNaN(costModifier)) {
          double costRating = item.path("rating_cost").asDouble(2.0);
          costModifier = 0.7 + (costRating * 0.15);
        }

        entry.put("yield_bonus_pct", yield);
        entry.put("time_per_scu_min", timePerScu);
        entry.put("cost_modifier", costModifier);
        entry.put("description", item.path("description").asText("Imported from live source"));
        entry.set("good_for", mapper.createArrayNode());
        methods.set(name, entry);
      }
    }

    if (methods.isEmpty()) {
      return null;
    }
    out.put("last_update", System.currentTimeMillis() / 1000);
    return out;
  }

  /**
   * Normalizes salvage payloads into internal schema expected by SalvageService.
   */
  public static JsonNode normalizeSalvage(JsonNode raw) {
    if (raw == null || raw.isNull()) {
      return null;
    }

    // Already in internal schema -> pass through with timestamp refresh.
    if (raw.isObject() && raw.has("hotspots")) {
      ObjectNode out = ((ObjectNode) raw).deepCopy();
      out.put("last_update", System.currentTimeMillis() / 1000);
      return out;
    }

    ObjectNode out = mapper.createObjectNode();
    ObjectNode hotspots = mapper.createObjectNode();
    out.set("ships", mapper.createObjectNode());
    out.set("materials", mapper.createObjectNode());
    out.set("hotspots", hotspots);
    out.set("tips", mapper.createArrayNode());

    JsonNode rows = unwrapDataArray(raw);
    if (rows != null && rows.isArray()) {
      for (JsonNode item : rows) {
        String name = item.path("name").asText(item.path("location").asText(""));
        if (name.isEmpty()) {
          continue;
        }

        ObjectNode h = mapper.createObjectNode();
        h.put("body", item.path("body").asText(item.path("location").asText("Unknown")));
        h.put("system", item.path("system").asText("Unknown"));
        h.put("risk", item.path("risk").asText("Unknown"));
        h.put("wreck_density", item.path("wreck_density").asText("Unknown"));
        h.put("notes", item.path("notes").asText("Imported from live source"));

        hotspots.set(name, h);
      }
    }

    if (hotspots.isEmpty()) {
      return null;
    }
    out.put("last_update", System.currentTimeMillis() / 1000);
    return out;
  }

  /**
   * Normalizes location payloads into name-keyed location metadata.
   */
  public static JsonNode normalizeLocations(JsonNode raw) {
    ObjectNode root = mapper.createObjectNode();
    JsonNode rows = unwrapDataArray(raw);
    if (rows == null || !rows.isArray()) {
      return root;
    }

    for (JsonNode row : rows) {
      String name = row.path("name").asText(row.path("terminal_name").asText(""));
      if (name.isBlank()) {
        continue;
      }

      ObjectNode entry = mapper.createObjectNode();
      entry.put("system", row.path("star_system_name").asText("Unknown"));
      entry.put(
          "body",
          row.path("orbit_name")
              .asText(row.path("planet_name").asText(row.path("moon_name").asText("Unknown"))));
      entry.put("type", row.path("type").asText("station"));
      entry.put("last_update", System.currentTimeMillis() / 1000);
      root.set(name, entry);
    }

    return root;
  }

  /**
   * Normalizes armor payloads into name-keyed entries and assigns armor categories (undersuits,
   * arms, legs, chests, helmets, backpacks).
   */
  public static JsonNode normalizeArmor(JsonNode raw) {
    ObjectNode root = mapper.createObjectNode();
    JsonNode rows = unwrapDataArray(raw);
    if (rows == null || !rows.isArray()) {
      return root;
    }

    for (JsonNode item : rows) {
      String name = firstNonBlank(item, "name", "item_name", "name_full", "title");
      if (name.isBlank()) {
        continue;
      }

      String typeHaystack =
          (firstNonBlank(item, "type", "category", "section", "item_type", "sub_type", "subtype")
                  + " "
                  + name)
              .toLowerCase();
      String pieces =
          firstNonBlank(item, "pieces", "slot", "slots", "body_part", "body_parts", "part");

      ObjectNode entry = mapper.createObjectNode();
      entry.put("class", firstNonBlank(item, "class", "item_class", "tier", "grade"));
      entry.put("manufacturer", firstNonBlank(item, "manufacturer", "company_name"));
      entry.put("description", firstNonBlank(item, "description", "short_description"));
      entry.put("pieces", pieces);
      entry.put("weight_class", firstNonBlank(item, "weight_class", "weight", "mass_class"));
      entry.put(
          "ballistic_resist_pct",
          (int)
              Math.round(
                  firstPositiveDouble(
                      item, "ballistic_resist_pct", "ballistic_resistance", "resistance_ballistic")));
      entry.put(
          "energy_resist_pct",
          (int)
              Math.round(
                  firstPositiveDouble(
                      item, "energy_resist_pct", "energy_resistance", "resistance_energy")));
      entry.put(
          "distortion_resist_pct",
          (int)
              Math.round(
                  firstPositiveDouble(
                      item,
                      "distortion_resist_pct",
                      "distortion_resistance",
                      "resistance_distortion")));
      entry.put("temp_resist", firstNonBlank(item, "temp_resist", "temperature_resistance"));
      entry.set(
          "buy_locations",
          collectTextArray(item, "buy_locations", "locations", "available_at", "sold_at"));
      entry.set("sell_locations", collectTextArray(item, "sell_locations", "sell_at", "sold_at"));
      entry.put("notes", firstNonBlank(item, "notes"));

      String category = deriveArmorPrimaryCategory(typeHaystack, pieces);
      entry.put("category", category);
      entry.set("categories", deriveArmorCategories(typeHaystack, pieces));
      entry.put("last_update", System.currentTimeMillis() / 1000);

      root.set(name, entry);
    }

    return root;
  }

  private static String deriveArmorPrimaryCategory(String haystack, String pieces) {
    ArrayNode categories = deriveArmorCategories(haystack, pieces);
    if (categories.isArray() && categories.size() > 0) {
      return categories.get(0).asText("armor");
    }
    return "armor";
  }

  private static ArrayNode deriveArmorCategories(String haystack, String pieces) {
    String text = ((haystack == null ? "" : haystack) + " " + (pieces == null ? "" : pieces)).toLowerCase();
    ArrayNode out = mapper.createArrayNode();
    addArmorCategoryIfMatch(out, text, "undersuits", "undersuit", "under suit");
    addArmorCategoryIfMatch(out, text, "helmets", "helmet", "head");
    addArmorCategoryIfMatch(out, text, "chests", "chest", "torso", "core");
    addArmorCategoryIfMatch(out, text, "arms", "arm", "glove", "shoulder");
    addArmorCategoryIfMatch(out, text, "legs", "leg", "thigh", "shin", "boot");
    addArmorCategoryIfMatch(out, text, "backpacks", "backpack", "pack");
    if (out.isEmpty()) {
      out.add("armor");
    }
    return out;
  }

  private static void addArmorCategoryIfMatch(
      ArrayNode out, String text, String category, String... keywords) {
    for (String keyword : keywords) {
      if (text.contains(keyword)) {
        if (!containsText(out, category)) {
          out.add(category);
        }
        return;
      }
    }
  }

  private static boolean containsText(ArrayNode array, String value) {
    for (JsonNode n : array) {
      if (value.equalsIgnoreCase(n.asText(""))) {
        return true;
      }
    }
    return false;
  }

  private static int parseSize(JsonNode sizeNode) {
    if (sizeNode == null || sizeNode.isMissingNode() || sizeNode.isNull()) {
      return 0;
    }
    if (sizeNode.isNumber()) {
      return sizeNode.asInt(0);
    }
    String s = sizeNode.asText("").trim();
    if (s.isEmpty()) {
      return 0;
    }
    String digits = s.replaceAll("[^0-9]", "");
    if (digits.isEmpty()) {
      return 0;
    }
    try {
      return Integer.parseInt(digits);
    } catch (NumberFormatException e) {
      return 0;
    }
  }

  private static String firstNonBlank(JsonNode node, String... keys) {
    if (node == null || keys == null) {
      return "";
    }
    for (String key : keys) {
      if (key == null || key.isBlank()) {
        continue;
      }
      String value = node.path(key).asText("").trim();
      if (!value.isBlank()) {
        return value;
      }
    }
    return "";
  }

  private static JsonNode firstArray(JsonNode node, String... keys) {
    if (node == null || keys == null) {
      return mapper.createArrayNode();
    }
    for (String key : keys) {
      if (key == null || key.isBlank()) {
        continue;
      }
      JsonNode value = node.path(key);
      if (value.isArray()) {
        return value;
      }
    }
    return mapper.createArrayNode();
  }

  private static double firstPositiveDouble(JsonNode node, String... keys) {
    if (node == null || keys == null) {
      return 0;
    }
    for (String key : keys) {
      if (key == null || key.isBlank()) {
        continue;
      }
      JsonNode value = node.path(key);
      if (!value.isMissingNode() && !value.isNull()) {
        double n;
        if (value.isNumber()) {
          n = value.asDouble(0);
        } else {
          String text = value.asText("").replace(",", "").trim();
          if (text.isEmpty()) {
            continue;
          }
          try {
            n = Double.parseDouble(text);
          } catch (NumberFormatException e) {
            continue;
          }
        }
        if (n > 0) {
          return n;
        }
      }
    }
    return 0;
  }

  private static double firstPositiveFromPaths(JsonNode node, String... paths) {
    if (node == null || paths == null) {
      return 0;
    }
    for (String path : paths) {
      if (path == null || path.isBlank()) {
        continue;
      }
      JsonNode current = node;
      String[] parts = path.split("\\.");
      boolean missing = false;
      for (String part : parts) {
        current = current.path(part);
        if (current.isMissingNode() || current.isNull()) {
          missing = true;
          break;
        }
      }
      if (missing) {
        continue;
      }

      double n;
      if (current.isNumber()) {
        n = current.asDouble(0);
      } else {
        String text = current.asText("").replace(",", "").trim();
        if (text.isEmpty() || text.equalsIgnoreCase("∞") || text.equalsIgnoreCase("infinity")) {
          continue;
        }
        try {
          n = Double.parseDouble(text);
        } catch (NumberFormatException e) {
          continue;
        }
      }
      if (n > 0) {
        return n;
      }
    }
    return 0;
  }

  private static boolean firstBooleanFromPaths(JsonNode node, String... paths) {
    if (node == null || paths == null) {
      return false;
    }
    for (String path : paths) {
      if (path == null || path.isBlank()) {
        continue;
      }
      JsonNode current = node;
      String[] parts = path.split("\\.");
      boolean missing = false;
      for (String part : parts) {
        current = current.path(part);
        if (current.isMissingNode() || current.isNull()) {
          missing = true;
          break;
        }
      }
      if (missing) {
        continue;
      }
      if (current.isBoolean()) {
        return current.asBoolean(false);
      }
      String text = current.asText("").trim().toLowerCase();
      if (text.equals("true") || text.equals("yes") || text.equals("1")) {
        return true;
      }
    }
    return false;
  }

  private static String deriveVehicleRole(JsonNode ship) {
    if (ship.path("is_mining").asInt(0) == 1) {
      return "Mining";
    }
    if (ship.path("is_salvage").asInt(0) == 1) {
      return "Salvage";
    }
    if (ship.path("is_cargo").asInt(0) == 1) {
      return "Cargo";
    }
    if (ship.path("is_bomber").asInt(0) == 1) {
      return "Bomber";
    }
    if (ship.path("is_exploration").asInt(0) == 1) {
      return "Exploration";
    }
    if (ship.path("is_medical").asInt(0) == 1) {
      return "Medical";
    }
    if (ship.path("is_racing").asInt(0) == 1) {
      return "Racing";
    }
    if (ship.path("is_military").asInt(0) == 1) {
      return "Military";
    }
    if (ship.path("is_starter").asInt(0) == 1) {
      return "Starter";
    }
    return ship.path("role").asText("Multi-role");
  }

  private static String deriveVehicleSize(double lengthMeters) {
    if (lengthMeters <= 0) {
      return "Unknown";
    }
    if (lengthMeters < 15) {
      return "Snub";
    }
    if (lengthMeters < 30) {
      return "Small";
    }
    if (lengthMeters < 70) {
      return "Medium";
    }
    if (lengthMeters < 150) {
      return "Large";
    }
    return "Capital";
  }

  private static String classifyWeaponDomain(JsonNode weapon) {
    String section = firstNonBlank(weapon, "section", "category", "type", "class").toLowerCase();
    if (section.contains("personal")
        || section.contains("fps")
        || section.contains("rifle")
        || section.contains("smg")
        || section.contains("shotgun")
        || section.contains("pistol")) {
      return "fps";
    }
    return "vehicle";
  }

  private static ArrayNode collectTextArray(JsonNode node, String... keys) {
    ArrayNode out = mapper.createArrayNode();
    if (node == null || keys == null) {
      return out;
    }

    for (String key : keys) {
      if (key == null || key.isBlank()) {
        continue;
      }
      JsonNode value = node.path(key);
      if (value.isArray()) {
        for (JsonNode item : value) {
          String t = item.asText("").trim();
          if (!t.isBlank() && !containsValue(out, t)) {
            out.add(t);
          }
        }
      } else {
        String text = value.asText("").trim();
        if (text.isBlank()) {
          continue;
        }
        String[] parts = text.split("\\s*[|,;]\\s*");
        for (String part : parts) {
          String t = part.trim();
          if (!t.isBlank() && !containsValue(out, t)) {
            out.add(t);
          }
        }
      }
    }
    return out;
  }

  private static boolean containsValue(ArrayNode values, String target) {
    for (JsonNode n : values) {
      if (n.asText("").equalsIgnoreCase(target)) {
        return true;
      }
    }
    return false;
  }

  private static ObjectNode defaultRefineryOreValues() {
    ObjectNode ores = mapper.createObjectNode();
    ores.put("Quantainium", 1400);
    ores.put("Bexalite", 1100);
    ores.put("Hadanite", 950);
    ores.put("Taranite", 850);
    ores.put("Agricium", 700);
    ores.put("Laranite", 650);
    ores.put("Hephaestanite", 500);
    ores.put("Gold", 350);
    ores.put("Copper", 120);
    ores.put("Titanium", 90);
    ores.put("Iron", 55);
    ores.put("Corundum", 40);
    ores.put("Quartz", 20);
    ores.put("Beryl", 15);
    return ores;
  }
}
