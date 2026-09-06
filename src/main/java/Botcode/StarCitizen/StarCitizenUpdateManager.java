package Botcode.StarCitizen;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.File;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Orchestrates fetch -> normalize -> save -> reload updates for Star Citizen datasets.
 */
public class StarCitizenUpdateManager {

  public enum UpdateOutcome {
    UPDATED,
    UNCHANGED,
    FAILED
  }

  public static class UpdateResult {

    public final UpdateOutcome outcome;
    public final String message;

    public UpdateResult(UpdateOutcome outcome, String message) {
      this.outcome = outcome;
      this.message = message == null ? "" : message;
    }

    public boolean isSuccess() {
      return outcome != UpdateOutcome.FAILED;
    }
  }

  private static final ObjectMapper mapper = new ObjectMapper();
  private static final String DATA_FOLDER = "data/";
  private static final long DEFAULT_REFRESH_INTERVAL_MINUTES = 30L * 24L * 60L;

  /**
   * Updates a specific dataset (commodities, trade_routes, ships, etc.)
   */
  public static boolean update(String dataset) {
    return updateDetailed(dataset).isSuccess();
  }

  public static boolean update(String dataset, boolean force) {
    return updateDetailed(dataset, force).isSuccess();
  }

  /**
   * Updates a dataset and returns a detailed outcome for GUI/admin diagnostics.
   */
  public static UpdateResult updateDetailed(String dataset) {
    return updateDetailed(dataset, false);
  }

  public static UpdateResult updateDetailed(String dataset, boolean force) {
    try {
      if (!force && shouldSkipLiveFetch(dataset)) {
        if ("ships".equals(dataset)) {
          return syncShipsFromRawGuideToStorage();
        }
        return new UpdateResult(
            UpdateOutcome.UNCHANGED, "local snapshot still fresh; skipped live fetch");
      }

      // Step 1: pull raw data from the matching upstream source.
      JsonNode raw = fetch(dataset);
      if (raw == null) {
        System.out.println("[UpdateManager] Fetch failed for: " + dataset);
        if (canKeepExisting(dataset)) {
          return new UpdateResult(
              UpdateOutcome.UNCHANGED, "source unavailable; kept existing snapshot");
        }
        return new UpdateResult(UpdateOutcome.FAILED, "fetch failed");
      }

      // Step 2: convert upstream data into the bot's normalized schema.
      JsonNode normalized = normalize(dataset, raw);
      if (normalized == null) {
        System.out.println("[UpdateManager] Normalize failed for: " + dataset);
        if (canKeepExisting(dataset)) {
          return new UpdateResult(
              UpdateOutcome.UNCHANGED, "live payload unsupported; kept existing snapshot");
        }
        return new UpdateResult(UpdateOutcome.FAILED, "normalize failed");
      }

      // Prevent clobbering good snapshots with empty/invalid payloads.
      if (normalized.isEmpty()) {
        System.out.println(
            "[UpdateManager] Normalize produced empty payload for: "
                + dataset
                + " (skipping save)");
        if (canKeepExisting(dataset)) {
          return new UpdateResult(
              UpdateOutcome.UNCHANGED, "empty live payload; kept existing snapshot");
        }
        return new UpdateResult(UpdateOutcome.FAILED, "empty payload");
      }

      // Defensive merge: keep known-good ship fields when live source is sparse.
      if ("ships".equals(dataset)) {
        normalized = mergeShipsWithExisting(StarCitizenDataService.get("ships"), normalized);
        normalized = mergeShipsWithRawGuideStats(normalized);
      }
      if ("weapons".equals(dataset)) {
        normalized = mergeWeaponsWithExisting(StarCitizenDataService.get("weapons"), normalized);
        normalized = mergeWeaponsWithGuideStats(normalized);
        int unresolved = countWeaponsWithMissingCoreStats(normalized);
        System.out.println("[UpdateManager] Weapons missing core stats after merge: " + unresolved);
      }
      if ("components".equals(dataset)) {
        normalized =
            mergeComponentsWithExisting(StarCitizenDataService.get("components"), normalized);
      }

      // Step 3: persist to disk and refresh in-memory references.
      save(dataset, normalized);

      // Reload into memory
      StarCitizenDataService.reload(dataset);

      System.out.println("[UpdateManager] Updated dataset: " + dataset);
      return new UpdateResult(UpdateOutcome.UPDATED, "updated from live source");

    } catch (Exception e) {
      System.out.println("[UpdateManager] Error updating dataset: " + dataset);
      e.printStackTrace();
      if (canKeepExisting(dataset)) {
        return new UpdateResult(
            UpdateOutcome.UNCHANGED, "exception during update; kept existing snapshot");
      }
      return new UpdateResult(UpdateOutcome.FAILED, "exception during update");
    }
  }

  private static boolean canKeepExisting(String dataset) {
    // Keep any known-good snapshot instead of failing hard when upstream is missing/blocked.
    JsonNode existing = StarCitizenDataService.get(dataset);
    return existing != null
        && !existing.isNull()
        && !existing.isMissingNode()
        && !existing.isEmpty();
  }

  private static boolean shouldSkipLiveFetch(String dataset) {
    if (dataset == null || dataset.isBlank()) {
      return false;
    }
    if (isTruthy(System.getenv("SC_FORCE_REFRESH"))) {
      return false;
    }

    long refreshMinutes =
        parseLongEnv("SC_REFRESH_INTERVAL_MINUTES", DEFAULT_REFRESH_INTERVAL_MINUTES);
    if (refreshMinutes <= 0) {
      return false;
    }

    long newestMs = 0;
    File snapshot = new File(DATA_FOLDER + dataset + ".json");
    if (snapshot.exists() && snapshot.isFile()) {
      newestMs = Math.max(newestMs, snapshot.lastModified());
    }
    newestMs = Math.max(newestMs, StarCitizenDatasetStore.getLastRefreshMs(dataset));
    if (newestMs <= 0) {
      return false;
    }

    long ageMs = System.currentTimeMillis() - newestMs;
    long maxAgeMs = refreshMinutes * 60L * 1000L;
    if (ageMs >= 0 && ageMs < maxAgeMs) {
      long ageMin = ageMs / (60L * 1000L);
      System.out.println(
          "[UpdateManager] Skipping live fetch for "
              + dataset
              + " (snapshot age "
              + ageMin
              + "m < "
              + refreshMinutes
              + "m)");
      return true;
    }
    return false;
  }

  private static long parseLongEnv(String envName, long fallback) {
    String value = System.getenv(envName);
    if (value == null || value.isBlank()) {
      return fallback;
    }
    try {
      return Long.parseLong(value.trim());
    } catch (Exception e) {
      return fallback;
    }
  }

  private static boolean isTruthy(String value) {
    if (value == null) {
      return false;
    }
    String v = value.trim().toLowerCase();
    return v.equals("1") || v.equals("true") || v.equals("yes") || v.equals("on");
  }

  /**
   * Fetches raw data from the correct source based on dataset name.
   */
  private static JsonNode fetch(String dataset) {
    switch (dataset) {

      // -------------------------
      // UEX DATASETS
      // -------------------------
      case "commodities":
        return StarCitizenFetcher.fetchCommodities();

      case "trade_routes":
        return StarCitizenFetcher.fetchTradeRoutes();

      case "mining":
        return StarCitizenFetcher.fetchMining();

      case "refinery":
        return StarCitizenFetcher.fetchRefinery();

      case "refinery_stations":
        return StarCitizenFetcher.fetchRefineryStations();

      case "salvage":
        return StarCitizenFetcher.fetchSalvage();

      case "locations":
        return StarCitizenFetcher.fetchLocations();

      // -------------------------
      // ERKUL DATASETS (placeholders)
      // -------------------------
      case "ships":
        return StarCitizenFetcher.fetchShips();

      case "weapons":
        return StarCitizenFetcher.fetchWeapons();

      case "components":
        return StarCitizenFetcher.fetchComponents();

      // -------------------------
      // FUTURE DATASETS (SC.Tools, static, etc.)
      // -------------------------
      case "missions":
      case "items":
      case "armor":
        System.out.println("[UpdateManager] Fetcher not implemented for: " + dataset);
        return null;

      default:
        System.out.println("[UpdateManager] Unknown dataset: " + dataset);
        return null;
    }
  }

  /**
   * Normalizes raw data into your internal JSON format.
   */
  private static JsonNode normalize(String dataset, JsonNode raw) {
    switch (dataset) {

      // -------------------------
      // UEX NORMALIZERS
      // -------------------------
      case "commodities":
        return StarCitizenNormalizer.normalizeCommodities(raw);

      case "trade_routes":
        return StarCitizenNormalizer.normalizeTradeRoutes(raw);

      // -------------------------
      // ERKUL NORMALIZERS (placeholders)
      // -------------------------
      case "ships":
        return StarCitizenNormalizer.normalizeShips(raw);

      case "weapons":
        return StarCitizenNormalizer.normalizeWeapons(raw);

      case "components":
        return StarCitizenNormalizer.normalizeComponents(raw);

      case "mining":
        return StarCitizenNormalizer.normalizeMining(raw);

      case "refinery":
        return StarCitizenNormalizer.normalizeRefinery(raw);

      case "refinery_stations":
        return StarCitizenNormalizer.normalizeRefineryStations(raw);

      case "salvage":
        return StarCitizenNormalizer.normalizeSalvage(raw);

      case "locations":
        return StarCitizenNormalizer.normalizeLocations(raw);

      // -------------------------
      // FUTURE NORMALIZERS
      // -------------------------
      case "missions":
      case "items":
      case "armor":
        System.out.println("[UpdateManager] Normalizer not implemented for: " + dataset);
        return null;

      default:
        System.out.println("[UpdateManager] Unknown dataset: " + dataset);
        return null;
    }
  }

  /**
   * Saves normalized JSON to /data/<dataset>.json
   */
  private static void save(String dataset, JsonNode json) {
    try {
      // Ensure output directory exists before writing dataset snapshots.
      File folder = new File(DATA_FOLDER);
      if (!folder.exists()) {
        folder.mkdirs();
      }

      try (Writer writer =
          new OutputStreamWriter(
              Files.newOutputStream(new File(DATA_FOLDER + dataset + ".json").toPath()),
              StandardCharsets.UTF_8)) {
        writer.write(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(json));
      }

      StarCitizenDatasetStore.saveDataset(dataset, json, "update-manager", "ok");

    } catch (Exception e) {
      System.out.println("[UpdateManager] Failed to save dataset: " + dataset);
      e.printStackTrace();
    }
  }

  private static JsonNode mergeShipsWithExisting(JsonNode existing, JsonNode incoming) {
    if (incoming == null || !incoming.isObject()) {
      return incoming;
    }
    if (existing == null || !existing.isObject() || existing.isEmpty()) {
      return incoming;
    }

    ObjectNode merged = ((ObjectNode) incoming).deepCopy();
    existing
        .fields()
        .forEachRemaining(
            entry -> {
              String shipName = entry.getKey();
              JsonNode oldShip = entry.getValue();
              JsonNode newShip = merged.path(shipName);

              if (newShip == null || newShip.isMissingNode() || newShip.isNull()) {
                merged.set(shipName, oldShip);
                return;
              }
              if (!newShip.isObject() || !oldShip.isObject()) {
                return;
              }

              ObjectNode shipOut = ((ObjectNode) newShip).deepCopy();
              mergeInfo(shipOut.with("info"), oldShip.path("info"));
              mergeStats(shipOut.with("stats"), oldShip.path("stats"));

              if (shipOut.path("weapons").isArray()
                  && shipOut.path("weapons").isEmpty()
                  && oldShip.path("weapons").isArray()
                  && !oldShip.path("weapons").isEmpty()) {
                shipOut.set("weapons", oldShip.path("weapons"));
              }
              if (shipOut.path("missiles").isArray()
                  && shipOut.path("missiles").isEmpty()
                  && oldShip.path("missiles").isArray()
                  && !oldShip.path("missiles").isEmpty()) {
                shipOut.set("missiles", oldShip.path("missiles"));
              }
              if (shipOut.path("missile_racks").isArray()
                  && shipOut.path("missile_racks").isEmpty()
                  && oldShip.path("missile_racks").isArray()
                  && !oldShip.path("missile_racks").isEmpty()) {
                shipOut.set("missile_racks", oldShip.path("missile_racks"));
              }
              if (shipOut.path("turrets").isArray()
                  && shipOut.path("turrets").isEmpty()
                  && oldShip.path("turrets").isArray()
                  && !oldShip.path("turrets").isEmpty()) {
                shipOut.set("turrets", oldShip.path("turrets"));
              }
              if (shipOut.path("components").isObject()
                  && shipOut.path("components").isEmpty()
                  && oldShip.path("components").isObject()
                  && !oldShip.path("components").isEmpty()) {
                shipOut.set("components", oldShip.path("components"));
              }

              merged.set(shipName, shipOut);
            });
    return merged;
  }

  private static void mergeInfo(ObjectNode target, JsonNode source) {
    if (source == null || !source.isObject()) {
      return;
    }
    String[] textFields = {
        "manufacturer", "role", "size", "buy_location", "image_url", "brochure_url", "store_url"
    };
    String[] numberFields = {
        "crew", "cargo", "price_auec", "scm_speed", "max_speed", "shield_hp", "hull_hp"
    };

    for (String f : textFields) {
      if (target.path(f).asText("").isBlank() && !source.path(f).asText("").isBlank()) {
        target.set(f, source.path(f));
      }
    }
    for (String f : numberFields) {
      if (target.path(f).asDouble(0) <= 0 && source.path(f).asDouble(0) > 0) {
        target.set(f, source.path(f));
      }
    }
    if ((target.path("buy_locations").isMissingNode() || target.path("buy_locations").isEmpty())
        && source.path("buy_locations").isArray()
        && !source.path("buy_locations").isEmpty()) {
      target.set("buy_locations", source.path("buy_locations"));
    }
  }

  private static void mergeStats(ObjectNode target, JsonNode source) {
    if (source == null || !source.isObject()) {
      return;
    }
    String[] numberFields = {
        "mass",
        "pitch",
        "yaw",
        "roll",
        "fuel_capacity",
        "quantum_fuel",
        "hydrogen_fuel",
        "qt_speed",
        "qt_range",
        "pilot_dps",
        "turret_dps",
        "missile_dps",
        "total_dps",
        "pilot_alpha",
        "turret_alpha",
        "missile_alpha",
        "total_alpha"
    };
    for (String f : numberFields) {
      if (target.path(f).asDouble(0) <= 0 && source.path(f).asDouble(0) > 0) {
        target.set(f, source.path(f));
      }
    }
  }

  static JsonNode mergeLoadedShips(JsonNode existing, JsonNode incoming) {
    return mergeShipsWithRawGuideStats(mergeShipsWithExisting(existing, incoming));
  }

  static JsonNode applyRawShipStats(JsonNode incoming) {
    return mergeShipsWithRawGuideStats(incoming);
  }

  public static UpdateResult syncShipsFromRawGuideToStorage() {
    try {
      JsonNode existing = StarCitizenDataService.get("ships");
      if (existing == null || !existing.isObject() || existing.isEmpty()) {
        return new UpdateResult(UpdateOutcome.UNCHANGED, "ships dataset not loaded");
      }
      JsonNode merged = mergeShipsWithRawGuideStats(existing);
      save("ships", merged);
      StarCitizenDataService.reload("ships");
      return new UpdateResult(UpdateOutcome.UPDATED, "local raw ship guide data merged");
    } catch (Exception e) {
      return new UpdateResult(UpdateOutcome.FAILED, "local raw ship guide merge failed");
    }
  }

  private static JsonNode mergeShipsWithRawGuideStats(JsonNode incoming) {
    if (incoming == null || !incoming.isObject() || incoming.isEmpty()) {
      return incoming;
    }

    List<RawShipRow> rows = parseRawShipRows();
    if (rows.isEmpty()) {
      return incoming;
    }

    ObjectNode merged = ((ObjectNode) incoming).deepCopy();
    Map<String, String> byCanonical = new HashMap<>();
    Map<String, String> byAlias = new HashMap<>();
    merged
        .fieldNames()
        .forEachRemaining(
            name -> {
              byCanonical.put(canonicalShipKey(name), name);
              byAlias.put(aliasShipKey(name), name);
            });

    int applied = 0;
    for (RawShipRow row : rows) {
      if (row == null || row.name.isBlank()) {
        continue;
      }
      String target = byCanonical.get(canonicalShipKey(row.name));
      if (target == null) {
        target = byAlias.get(aliasShipKey(row.name));
      }
      if (target == null) {
        continue;
      }

      ObjectNode ship = (ObjectNode) merged.path(target);
      if (ship == null || ship.isMissingNode() || !ship.isObject()) {
        continue;
      }

      ObjectNode info = ship.with("info");
      ObjectNode stats = ship.with("stats");

      patchMissingText(info, "manufacturer", row.manufacturer);
      patchMissingText(info, "type", row.type);
      patchMissingText(info, "career", row.career);
      patchMissingText(info, "role", row.role);
      patchMissingText(info, "size", row.sizeLabel);
      patchMissingText(info, "dimensions", row.dimensions);
      patchMissingText(info, "claim_time", row.claimTime);
      patchMissingText(info, "expedite_time", row.expediteTime);
      patchMissingNumber(info, "crew", row.crew);
      patchMissingNumber(info, "cargo", row.cargo);
      patchMissingNumber(info, "price_auec", row.priceAuec);
      patchMissingNumber(info, "expedition_fee", row.expeditionFee);
      patchMissingNumber(info, "scm_speed", row.scmSpeed);
      patchMissingNumber(info, "max_speed", row.navMaxSpeed);
      patchMissingNumber(info, "hull_hp", row.hp);
      patchMissingNumber(info, "shield_hp", row.armorHp);
      patchMissingText(info, "shield_face_type", row.shieldFaceType);

      patchMissingNumber(stats, "mass", row.mass);
      patchMissingNumber(stats, "pitch", row.pitch);
      patchMissingNumber(stats, "yaw", row.yaw);
      patchMissingNumber(stats, "roll", row.roll);
      patchMissingNumber(stats, "scm_boost_forward", row.scmBoostForward);
      patchMissingNumber(stats, "scm_boost_backward", row.scmBoostBackward);
      patchMissingNumber(stats, "hydrogen_fuel", row.hydrogenCapacity);
      patchMissingNumber(stats, "quantum_fuel", row.qtFuelCapacity);
      patchMissingNumber(stats, "cm_decoy", row.cmDecoy);
      patchMissingNumber(stats, "cm_noise", row.cmNoise);
      patchMissingNumber(stats, "pitch_boost", row.pitchBoost);
      patchMissingNumber(stats, "yaw_boost", row.yawBoost);
      patchMissingNumber(stats, "roll_boost", row.rollBoost);

      // Raw manual import values are authoritative for these new armor/deflection fields.
      patchPresentNumber(stats, "deflection_physical", row.deflectionPhysical);
      patchPresentNumber(stats, "deflection_energy", row.deflectionEnergy);
      patchPresentNumber(stats, "armor_physical_damage_modifier", row.armorPhysicalDmgModifier);
      patchPresentNumber(stats, "armor_energy_damage_modifier", row.armorEnergyDmgModifier);
      patchPresentNumber(stats, "armor_em_signal_modifier", row.armorEmSignalModifier);
      patchPresentNumber(stats, "armor_ir_signal_modifier", row.armorIrSignalModifier);
      patchPresentNumber(stats, "armor_cs_signal_modifier", row.armorCsSignalModifier);
      stats.put("raw_ship_data_imported", true);
      applied++;
    }

    System.out.println(
        "[UpdateManager] Raw ship armor rows parsed=" + rows.size() + " | applied=" + applied);
    return merged;
  }

  private static JsonNode mergeWeaponsWithExisting(JsonNode existing, JsonNode incoming) {
    if (incoming == null || !incoming.isObject()) {
      return incoming;
    }
    if (existing == null || !existing.isObject() || existing.isEmpty()) {
      return incoming;
    }

    ObjectNode merged = ((ObjectNode) incoming).deepCopy();
    existing
        .fields()
        .forEachRemaining(
            entry -> {
              String weaponName = entry.getKey();
              JsonNode oldWeapon = entry.getValue();
              JsonNode newWeapon = merged.path(weaponName);

              if (newWeapon == null || newWeapon.isMissingNode() || newWeapon.isNull()) {
                merged.set(weaponName, oldWeapon);
                return;
              }
              if (!newWeapon.isObject() || !oldWeapon.isObject()) {
                return;
              }

              ObjectNode weaponOut = ((ObjectNode) newWeapon).deepCopy();
              mergeWeaponInfo(weaponOut.with("info"), oldWeapon.path("info"));
              mergeWeaponStats(weaponOut.with("stats"), oldWeapon.path("stats"));

              if (weaponOut.path("ammo").isObject()
                  && weaponOut.path("ammo").isEmpty()
                  && oldWeapon.path("ammo").isObject()
                  && !oldWeapon.path("ammo").isEmpty()) {
                weaponOut.set("ammo", oldWeapon.path("ammo"));
              }
              if (weaponOut.path("fire_modes").isArray()
                  && weaponOut.path("fire_modes").isEmpty()
                  && oldWeapon.path("fire_modes").isArray()
                  && !oldWeapon.path("fire_modes").isEmpty()) {
                weaponOut.set("fire_modes", oldWeapon.path("fire_modes"));
              }
              if (weaponOut.path("attachments").isArray()
                  && weaponOut.path("attachments").isEmpty()
                  && oldWeapon.path("attachments").isArray()
                  && !oldWeapon.path("attachments").isEmpty()) {
                weaponOut.set("attachments", oldWeapon.path("attachments"));
              }

              merged.set(weaponName, weaponOut);
            });
    return merged;
  }

  static JsonNode mergeLoadedWeapons(JsonNode existing, JsonNode incoming) {
    return mergeWeaponsWithExisting(existing, incoming);
  }

  private static JsonNode mergeComponentsWithExisting(JsonNode existing, JsonNode incoming) {
    if (incoming == null || !incoming.isObject()) {
      return incoming;
    }
    if (existing == null || !existing.isObject() || existing.isEmpty()) {
      return incoming;
    }

    ObjectNode merged = ((ObjectNode) incoming).deepCopy();
    existing
        .fields()
        .forEachRemaining(
            entry -> {
              String componentName = entry.getKey();
              JsonNode oldComp = entry.getValue();
              JsonNode newComp = merged.path(componentName);

              if (newComp == null || newComp.isMissingNode() || newComp.isNull()) {
                merged.set(componentName, oldComp);
                return;
              }
              if (!newComp.isObject() || !oldComp.isObject()) {
                return;
              }

              ObjectNode out = ((ObjectNode) newComp).deepCopy();
              mergeComponentInfo(out.with("info"), oldComp.path("info"));
              mergeComponentStats(out.with("stats"), oldComp.path("stats"));
              if (out.path("attributes").isObject()
                  && out.path("attributes").isEmpty()
                  && oldComp.path("attributes").isObject()
                  && !oldComp.path("attributes").isEmpty()) {
                out.set("attributes", oldComp.path("attributes"));
              }
              merged.set(componentName, out);
            });
    return merged;
  }

  static JsonNode mergeLoadedComponents(JsonNode existing, JsonNode incoming) {
    return mergeComponentsWithExisting(existing, incoming);
  }

  private static void mergeComponentInfo(ObjectNode target, JsonNode source) {
    if (source == null || !source.isObject()) {
      return;
    }
    String[] textFields = {
        "manufacturer", "type", "grade", "class", "buy_location", "sell_location", "store_url"
    };
    for (String f : textFields) {
      if (target.path(f).asText("").isBlank() && !source.path(f).asText("").isBlank()) {
        target.set(f, source.path(f));
      }
    }
    if (target.path("size").asInt(0) <= 0 && source.path("size").asInt(0) > 0) {
      target.set("size", source.path("size"));
    }
    if ((target.path("buy_locations").isMissingNode() || target.path("buy_locations").isEmpty())
        && source.path("buy_locations").isArray()
        && !source.path("buy_locations").isEmpty()) {
      target.set("buy_locations", source.path("buy_locations"));
    }
    if ((target.path("sell_locations").isMissingNode() || target.path("sell_locations").isEmpty())
        && source.path("sell_locations").isArray()
        && !source.path("sell_locations").isEmpty()) {
      target.set("sell_locations", source.path("sell_locations"));
    }
  }

  private static void mergeComponentStats(ObjectNode target, JsonNode source) {
    if (source == null || !source.isObject()) {
      return;
    }
    String[] numberFields = {
        "power_output",
        "power_draw",
        "cooling_rate",
        "shield_hp",
        "hp",
        "quantum_speed",
        "quantum_range",
        "cost_auec",
        "sell_price",
        "quality",
        "shield_regen_max",
        "shield_regen_time_full",
        "shield_regen_delay_damaged",
        "shield_regen_delay_downed",
        "em_max",
        "distortion_shutdown_dmg",
        "distortion_decay_delay",
        "distortion_decay_rate",
        "distortion_warning_ratio"
    };
    for (String f : numberFields) {
      if (target.path(f).asDouble(0) <= 0 && source.path(f).asDouble(0) > 0) {
        target.set(f, source.path(f));
      }
    }

    String[] signedFields = {
        "physical_resistance_min", "physical_resistance_max",
        "energy_resistance_min", "energy_resistance_max",
        "distortion_resistance_min", "distortion_resistance_max",
        "physical_absorption_min", "physical_absorption_max",
        "energy_absorption_min", "energy_absorption_max",
        "distortion_absorption_min", "distortion_absorption_max"
    };
    for (String f : signedFields) {
      if (Math.abs(target.path(f).asDouble(0)) <= 1e-9
          && Math.abs(source.path(f).asDouble(0)) > 1e-9) {
        target.set(f, source.path(f));
      }
    }
  }

  private static void mergeWeaponInfo(ObjectNode target, JsonNode source) {
    if (source == null || !source.isObject()) {
      return;
    }
    String[] textFields = {
        "manufacturer",
        "type",
        "class",
        "domain",
        "damage_type",
        "hardpoint",
        "buy_location",
        "sell_location",
        "store_url"
    };
    for (String f : textFields) {
      if (target.path(f).asText("").isBlank() && !source.path(f).asText("").isBlank()) {
        target.set(f, source.path(f));
      }
    }
    if (target.path("size").asInt(0) <= 0 && source.path("size").asInt(0) > 0) {
      target.set("size", source.path("size"));
    }
    if ((target.path("buy_locations").isMissingNode() || target.path("buy_locations").isEmpty())
        && source.path("buy_locations").isArray()
        && !source.path("buy_locations").isEmpty()) {
      target.set("buy_locations", source.path("buy_locations"));
    }
    if ((target.path("sell_locations").isMissingNode() || target.path("sell_locations").isEmpty())
        && source.path("sell_locations").isArray()
        && !source.path("sell_locations").isEmpty()) {
      target.set("sell_locations", source.path("sell_locations"));
    }
  }

  private static void mergeWeaponStats(ObjectNode target, JsonNode source) {
    if (source == null || !source.isObject()) {
      return;
    }
    String[] numberFields = {
        "dps",
        "burst_dps",
        "alpha_damage",
        "alpha_min",
        "alpha_max",
        "rpm",
        "range",
        "projectile_speed",
        "quality",
        "hp",
        "cost_auec",
        "sell_price",
        "penetration_distance",
        "penetration_near_radius",
        "penetration_far_radius",
        "power_consumption",
        "max_ammos",
        "ammos_regen",
        "overheat",
        "total_dmg_dealt",
        "total_dealt_time",
        "full_charge_fire_rate",
        "charged_dmg_multi",
        "charge_time",
        "pellets_per_shot",
        "explosion_radius",
        "regen_cooldown",
        "spread_first_attack",
        "spread_attack",
        "spread_min",
        "spread_max",
        "spread_decay",
        "ammo_dmg_biochemical",
        "ammo_dmg_distortion",
        "ammo_dmg_energy",
        "ammo_dmg_physical",
        "ammo_dmg_stun",
        "ammo_dmg_thermal",
        "distortion_shutdown_dmg",
        "distortion_decay_delay",
        "distortion_decay_rate",
        "distortion_warning_ratio"
    };
    for (String f : numberFields) {
      if (target.path(f).asDouble(0) <= 0 && source.path(f).asDouble(0) > 0) {
        target.set(f, source.path(f));
      }
    }

    String[] booleanFields = {"fire_only_on_full_charge", "fire_automatically_on_full_charge"};
    for (String f : booleanFields) {
      if (!target.path(f).asBoolean(false) && source.path(f).asBoolean(false)) {
        target.set(f, source.path(f));
      }
    }
  }

  private static int countWeaponsWithMissingCoreStats(JsonNode weapons) {
    if (weapons == null || !weapons.isObject() || weapons.isEmpty()) {
      return 0;
    }
    int missing = 0;
    var fields = weapons.fields();
    while (fields.hasNext()) {
      JsonNode stats = fields.next().getValue().path("stats");
      double dps = stats.path("dps").asDouble(0);
      double alpha = stats.path("alpha_damage").asDouble(0);
      double rpm = stats.path("rpm").asDouble(0);
      double range = stats.path("range").asDouble(0);
      if (dps <= 0 && alpha <= 0 && rpm <= 0 && range <= 0) {
        missing++;
      }
    }
    return missing;
  }

  static JsonNode applyGuideWeaponStats(JsonNode incoming) {
    return mergeWeaponsWithGuideStats(incoming);
  }

  private static JsonNode mergeWeaponsWithGuideStats(JsonNode incoming) {
    if (incoming == null || !incoming.isObject() || incoming.isEmpty()) {
      return incoming;
    }

    ObjectNode merged = ((ObjectNode) incoming).deepCopy();
    Map<String, String> incomingByCanonical = new HashMap<>();
    merged
        .fieldNames()
        .forEachRemaining(name -> incomingByCanonical.put(canonicalWeaponKey(name), name));

    int applied = 0;
    int parsed = 0;

    File guide = new File("USER_TEXT_GUIDE.md");
    if (!guide.exists() || !guide.isFile()) {
      return incoming;
    }

    try {
      for (String line : Files.readAllLines(guide.toPath(), StandardCharsets.UTF_8)) {
        if (line == null || line.isBlank() || !line.contains("\t")) {
          continue;
        }
        String[] cols = line.split("\t", -1);
        if (cols.length < 8) {
          continue;
        }

        String name = clean(cols[0]);
        String manufacturer = clean(cols[1]);
        String type = clean(cols[2]);
        Integer size = toInt(cols, 3);
        if (!hasLetters(name)
            || manufacturer.isBlank()
            || type.isBlank()
            || size == null
            || size <= 0) {
          continue;
        }
        parsed++;

        String targetKey = incomingByCanonical.get(canonicalWeaponKey(name));
        if (targetKey == null || targetKey.isBlank()) {
          continue;
        }

        ObjectNode target = (ObjectNode) merged.path(targetKey);
        if (target == null || target.isMissingNode() || !target.isObject()) {
          continue;
        }

        ObjectNode info = target.with("info");
        ObjectNode stats = target.with("stats");

        if (info.path("manufacturer").asText("").isBlank()) {
          info.put("manufacturer", manufacturer);
        }
        if (info.path("type").asText("").isBlank()) {
          info.put("type", type);
        }
        if (info.path("size").asInt(0) <= 0) {
          info.put("size", size);
        }

        patchMissingStat(stats, "dps", toDouble(cols, 4));
        patchMissingStat(stats, "alpha_damage", toDouble(cols, 5));
        patchMissingStat(stats, "burst_dps", toDouble(cols, 6));
        patchMissingStat(stats, "rpm", toDouble(cols, 11));
        patchMissingStat(stats, "projectile_speed", toDouble(cols, 14));
        patchMissingStat(stats, "total_dmg_dealt", toDouble(cols, 15));
        patchMissingStat(stats, "total_dealt_time", toDouble(cols, 16));
        patchMissingStat(stats, "range", toDouble(cols, 18));
        patchMissingStat(stats, "pellets_per_shot", toDouble(cols, 24));
        patchMissingStat(stats, "ammo_dmg_energy", toDouble(cols, 27));
        patchMissingStat(stats, "ammo_dmg_physical", toDouble(cols, 28));
        patchMissingStat(stats, "ammo_dmg_distortion", toDouble(cols, 29));
        patchMissingStat(stats, "ammo_dmg_thermal", toDouble(cols, 30));
        patchMissingStat(stats, "spread_first_attack", toDouble(cols, 33));
        patchMissingStat(stats, "spread_attack", toDouble(cols, 34));
        patchMissingStat(stats, "spread_min", toDouble(cols, 35));
        patchMissingStat(stats, "spread_max", toDouble(cols, 36));
        patchMissingStat(stats, "spread_decay", toDouble(cols, 37));
        patchMissingStat(stats, "cost_auec", tailPrice(cols));
        patchMissingStat(stats, "quality", lastValue(cols));

        stats.put("guide_imported", true);
        applied++;
      }
    } catch (Exception e) {
      System.out.println("[UpdateManager] Guide weapon stats parse failed: " + e.getMessage());
      return incoming;
    }

    System.out.println(
        "[UpdateManager] Guide weapon rows parsed=" + parsed + " | applied=" + applied);
    return merged;
  }

  private static void patchMissingStat(ObjectNode stats, String key, Double candidate) {
    if (stats == null || key == null || key.isBlank() || candidate == null || candidate <= 0) {
      return;
    }
    if (stats.path(key).asDouble(0) <= 0) {
      stats.put(key, candidate);
    }
  }

  private static String canonicalWeaponKey(String value) {
    if (value == null) {
      return "";
    }
    return value
        .toLowerCase(Locale.ROOT)
        .replace('\u2019', '\'')
        .replaceAll("[^a-z0-9]+", " ")
        .replaceAll("\\s+", " ")
        .trim();
  }

  private static String canonicalShipKey(String value) {
    if (value == null) {
      return "";
    }
    return value
        .toLowerCase(Locale.ROOT)
        .replace('\u2019', '\'')
        .replaceAll("[^a-z0-9]+", " ")
        .replaceAll("\\s+", " ")
        .trim();
  }

  private static String aliasShipKey(String value) {
    String base = canonicalShipKey(value);
    if (base.isBlank()) {
      return base;
    }
    return base
        .replaceAll("\\bmk\\s+[ivx]+\\b", " ")
        .replaceAll("\\blimited\\b", " ")
        .replaceAll("\\bedition\\b", " ")
        .replaceAll("\\s+", " ")
        .trim();
  }

  private static void patchMissingText(ObjectNode target, String key, String value) {
    if (target == null || key == null || key.isBlank() || value == null || value.isBlank()) {
      return;
    }
    if (target.path(key).asText("").isBlank()) {
      target.put(key, value);
    }
  }

  private static void patchMissingNumber(ObjectNode target, String key, Double value) {
    if (target == null || key == null || key.isBlank() || value == null) {
      return;
    }
    if (target.path(key).asDouble(0) <= 0 && value > 0) {
      target.put(key, value);
    }
  }

  private static void patchPresentNumber(ObjectNode target, String key, Double value) {
    if (target == null || key == null || key.isBlank() || value == null) {
      return;
    }
    target.put(key, value);
  }

  private static List<RawShipRow> parseRawShipRows() {
    File raw = new File("src/main/java/Botcode/Raw Ship Data");
    if (!raw.exists() || !raw.isFile()) {
      return List.of();
    }

    List<RawShipRow> out = new ArrayList<>();
    try {
      List<String> lines = Files.readAllLines(raw.toPath(), StandardCharsets.UTF_8);
      boolean inShipSection = true;
      for (int i = 0; i < lines.size(); i++) {
        String line = lines.get(i);
        if (line == null) {
          continue;
        }
        if (line.contains("Raw Weapon Data:")) {
          inShipSection = false;
        }
        if (!inShipSection) {
          break;
        }

        String[] cols = line.split("\\t", -1);
        if (cols.length < 36) {
          continue;
        }

        String name = clean(getCol(cols, 2));
        if (name.isBlank() || !hasLetters(name)) {
          continue;
        }

        RawShipRow row = new RawShipRow();
        row.name = name;
        row.manufacturer = clean(getCol(cols, 3));
        row.type = clean(getCol(cols, 4));
        row.career = clean(getCol(cols, 5));
        row.role = clean(getCol(cols, 6));
        row.sizeLabel = clean(getCol(cols, 7));
        row.crew = parseNumberAllowZero(getCol(cols, 8));
        row.dimensions = clean(getCol(cols, 9));
        row.mass = parseNumberAllowZero(getCol(cols, 10));
        row.cargo = parseNumberAllowZero(getCol(cols, 11));
        row.hp = parseNumberAllowZero(getCol(cols, 12));
        row.armorHp = parseNumberAllowZero(getCol(cols, 13));
        row.deflectionPhysical = parseNumberAllowZero(getCol(cols, 14));
        row.deflectionEnergy = parseNumberAllowZero(getCol(cols, 15));
        row.scmSpeed = parseNumberAllowZero(getCol(cols, 16));
        row.scmBoostForward = parseNumberAllowZero(getCol(cols, 17));
        row.scmBoostBackward = parseNumberAllowZero(getCol(cols, 18));
        row.navMaxSpeed = parseNumberAllowZero(getCol(cols, 19));
        row.pitch = parseNumberAllowZero(getCol(cols, 20));
        row.yaw = parseNumberAllowZero(getCol(cols, 21));
        row.roll = parseNumberAllowZero(getCol(cols, 22));
        row.pitchBoost = parseNumberAllowZero(getCol(cols, 23));
        row.yawBoost = parseNumberAllowZero(getCol(cols, 24));
        row.rollBoost = parseNumberAllowZero(getCol(cols, 25));
        row.hydrogenCapacity = parseNumberAllowZero(getCol(cols, 26));
        row.qtFuelCapacity = parseNumberAllowZero(getCol(cols, 27));
        row.shieldFaceType = clean(getCol(cols, 28));
        row.cmDecoy = parseNumberAllowZero(getCol(cols, 29));
        row.cmNoise = parseNumberAllowZero(getCol(cols, 30));
        row.armorPhysicalDmgModifier = parsePercentAllowZero(getCol(cols, 31));
        row.armorEnergyDmgModifier = parsePercentAllowZero(getCol(cols, 32));
        row.armorEmSignalModifier = parsePercentAllowZero(getCol(cols, 33));
        row.armorIrSignalModifier = parsePercentAllowZero(getCol(cols, 34));
        row.armorCsSignalModifier = parsePercentAllowZero(getCol(cols, 35));
        row.expeditionFee = parseNumberAllowZero(getCol(cols, 36));
        row.claimTime = clean(getCol(cols, 37));
        row.expediteTime = clean(getCol(cols, 38));

        String next = i + 1 < lines.size() ? clean(lines.get(i + 1)) : "";
        if (looksLikeStandalonePrice(next)) {
          row.priceAuec = parseNumberAllowZero(next);
          i++;
        }
        out.add(row);
      }
    } catch (Exception e) {
      System.out.println("[UpdateManager] Raw ship data parse failed: " + e.getMessage());
      return List.of();
    }

    return out;
  }

  private static String getCol(String[] cols, int idx) {
    if (cols == null || idx < 0 || idx >= cols.length) {
      return "";
    }
    return cols[idx];
  }

  private static boolean looksLikeStandalonePrice(String text) {
    if (text == null || text.isBlank()) {
      return false;
    }
    return text.matches("^[0-9][0-9\\s,]*$");
  }

  private static Double parsePercentAllowZero(String raw) {
    if (raw == null) {
      return null;
    }
    return parseNumberAllowZero(raw.replace("%", ""));
  }

  private static Double parseNumberAllowZero(String raw) {
    String text = clean(raw);
    if (text.isBlank()) {
      return null;
    }
    if (text.equals("∞") || text.equalsIgnoreCase("inf") || text.equalsIgnoreCase("infinity")) {
      return null;
    }
    text = text.replace(",", "").replace(" ", "");
    try {
      return Double.parseDouble(text);
    } catch (Exception e) {
      return null;
    }
  }

  private static final class RawShipRow {
    String name = "";
    String manufacturer = "";
    String type = "";
    String career = "";
    String role = "";
    String sizeLabel = "";
    String dimensions = "";
    String claimTime = "";
    String expediteTime = "";
    Double crew;
    Double mass;
    Double cargo;
    Double hp;
    Double armorHp;
    Double deflectionPhysical;
    Double deflectionEnergy;
    Double scmSpeed;
    Double scmBoostForward;
    Double scmBoostBackward;
    Double navMaxSpeed;
    Double pitch;
    Double yaw;
    Double roll;
    Double pitchBoost;
    Double yawBoost;
    Double rollBoost;
    Double hydrogenCapacity;
    Double qtFuelCapacity;
    String shieldFaceType = "";
    Double cmDecoy;
    Double cmNoise;
    Double armorPhysicalDmgModifier;
    Double armorEnergyDmgModifier;
    Double armorEmSignalModifier;
    Double armorIrSignalModifier;
    Double armorCsSignalModifier;
    Double expeditionFee;
    Double priceAuec;
  }

  private static String clean(String value) {
    if (value == null) {
      return "";
    }
    return value.replace('\u00A0', ' ').trim();
  }

  private static boolean hasLetters(String value) {
    return value != null && value.matches(".*[A-Za-z].*");
  }

  private static Integer toInt(String[] cols, int idx) {
    Double d = toDouble(cols, idx);
    return d == null ? null : (int) Math.round(d);
  }

  private static Double lastValue(String[] cols) {
    if (cols == null || cols.length == 0) {
      return null;
    }
    return parseNumber(cols[cols.length - 1]);
  }

  private static Double tailPrice(String[] cols) {
    if (cols == null || cols.length < 5) {
      return null;
    }
    int idx = Math.max(0, cols.length - 5);
    return parseNumber(cols[idx]);
  }

  private static Double toDouble(String[] cols, int idx) {
    if (cols == null || idx < 0 || idx >= cols.length) {
      return null;
    }
    return parseNumber(cols[idx]);
  }

  private static Double parseNumber(String raw) {
    String text = clean(raw);
    if (text.isBlank()) {
      return null;
    }
    if (text.equals("∞") || text.equalsIgnoreCase("inf") || text.equalsIgnoreCase("infinity")) {
      return null;
    }
    text = text.replace(",", "").replace(" ", "");
    try {
      double value = Double.parseDouble(text);
      return value > 0 ? value : null;
    } catch (Exception e) {
      return null;
    }
  }
}
