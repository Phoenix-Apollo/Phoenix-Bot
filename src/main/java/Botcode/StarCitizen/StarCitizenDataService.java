package Botcode.StarCitizen;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.File;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;

/**
 * Central in-memory store for normalized Star Citizen datasets.
 *
 * <p>Loads runtime JSON snapshots first (from {@code data/<dataset>.json}), then falls back to
 * packaged resources under {@code /starcitizen/<dataset>.json}. This allows website-fetched updates
 * (UEX/Erkul) to become live without rebuilding the jar.
 */
public class StarCitizenDataService {

  private static final ObjectMapper mapper = new ObjectMapper();

  /**
   * Runtime snapshot folder written by StarCitizenUpdateManager.
   */
  private static final String DATA_FOLDER = "data";

  // Cache holds all loaded JSON files
  private static final Map<String, JsonNode> cache = new HashMap<>();

  // Direct references for fast access
  private static JsonNode ships;
  private static JsonNode weapons;
  private static JsonNode components;

  // ---------------------------------------------------------
  // INITIAL LOAD
  // ---------------------------------------------------------
  static {
    loadAll();
  }

  /**
   * Loads all supported datasets into the in-memory cache.
   */
  private static void loadAll() {

    // Load all JSON files into cache
    load("ships");
    load("weapons");
    load("components");
    load("commodities");
    load("trade_routes");
    load("mining");
    load("refinery");
    load("refinery_stations");
    load("salvage");
    load("locations");
    load("missions");
    load("items");
    load("armor");

    // ---------------------------------------------------------
    // ASSIGN DIRECT REFERENCES
    // ---------------------------------------------------------
    // (This was missing — without this, ships/weapons/components were always null)
    ships = cache.get("ships");
    weapons = cache.get("weapons");
    components = cache.get("components");
    applyDerivedShipStats();
  }

  // ---------------------------------------------------------
  // RELOAD A SINGLE DATASET
  // ---------------------------------------------------------

  /**
   * Reloads one dataset and refreshes direct references if needed.
   */
  public static void reload(String name) {
    load(name);

    // Keep direct dataset handles in sync after a targeted reload.
    // Update direct references when reloading
    if (name.equals("ships")) {
      ships = cache.get("ships");
    }
    if (name.equals("weapons")) {
      weapons = cache.get("weapons");
    }
    if (name.equals("components")) {
      components = cache.get("components");
    }

    if ("ships".equals(name) || "weapons".equals(name) || "components".equals(name)) {
      applyDerivedShipStats();
    }
  }

  /**
   * Derives missing ship combat and subsystem stats from default loadouts/components.
   *
   * <p>This is in-memory enrichment only (no file writes) and runs after load/reload.
   */
  private static void applyDerivedShipStats() {
    if (ships == null || !ships.isObject()) {
      return;
    }

    ships
        .fields()
        .forEachRemaining(
            entry -> {
              JsonNode ship = entry.getValue();
              if (!(ship instanceof ObjectNode shipObj)) {
                return;
              }

              ObjectNode stats = shipObj.with("stats");
              ObjectNode info = shipObj.with("info");

              double[] fp = deriveShipFirepowerFromLoadout(shipObj);
              double pilotDps = Math.max(stats.path("pilot_dps").asDouble(0), fp[0]);
              double turretDps = Math.max(stats.path("turret_dps").asDouble(0), fp[1]);
              double missileDps = Math.max(stats.path("missile_dps").asDouble(0), fp[2]);
              double pilotAlpha = Math.max(stats.path("pilot_alpha").asDouble(0), fp[3]);
              double turretAlpha = Math.max(stats.path("turret_alpha").asDouble(0), fp[4]);
              double missileAlpha = Math.max(stats.path("missile_alpha").asDouble(0), fp[5]);

              if (pilotDps > 0) {
                stats.put("pilot_dps", pilotDps);
              }
              if (turretDps > 0) {
                stats.put("turret_dps", turretDps);
              }
              if (missileDps > 0) {
                stats.put("missile_dps", missileDps);
              }
              if (pilotAlpha > 0) {
                stats.put("pilot_alpha", pilotAlpha);
              }
              if (turretAlpha > 0) {
                stats.put("turret_alpha", turretAlpha);
              }
              if (missileAlpha > 0) {
                stats.put("missile_alpha", missileAlpha);
              }

              double totalDps = pilotDps + turretDps + missileDps;
              double totalAlpha = pilotAlpha + turretAlpha + missileAlpha;
              if (totalDps > stats.path("total_dps").asDouble(0)) {
                stats.put("total_dps", totalDps);
              }
              if (totalAlpha > stats.path("total_alpha").asDouble(0)) {
                stats.put("total_alpha", totalAlpha);
              }

              double[] effectiveTotals = deriveShipEffectiveTotalsFromLoadout(shipObj);
              if (effectiveTotals[0] > 0) {
                stats.put("effective_total_dps_physical", effectiveTotals[0]);
              }
              if (effectiveTotals[1] > 0) {
                stats.put("effective_total_dps_energy", effectiveTotals[1]);
              }
              if (effectiveTotals[2] > 0) {
                stats.put("effective_total_alpha_physical", effectiveTotals[2]);
              }
              if (effectiveTotals[3] > 0) {
                stats.put("effective_total_alpha_energy", effectiveTotals[3]);
              }

              double[] derivedComponentStats =
                  deriveShipStatsFromComponents(shipObj.path("components"));
              if (info.path("shield_hp").asDouble(0) <= 0 && derivedComponentStats[0] > 0) {
                info.put("shield_hp", (int) Math.round(derivedComponentStats[0]));
              }
              if (stats.path("qt_speed").asDouble(0) <= 0 && derivedComponentStats[1] > 0) {
                stats.put("qt_speed", derivedComponentStats[1]);
              }
              if (stats.path("qt_range").asDouble(0) <= 0 && derivedComponentStats[2] > 0) {
                stats.put("qt_range", derivedComponentStats[2]);
              }
            });
  }

  /**
   * Shared loadout-derived firepower helper used across panel/chat/commands.
   */
  public static double[] deriveShipFirepowerFromLoadout(JsonNode ship) {
    double pilotDps = 0, turretDps = 0, missileDps = 0;
    double pilotAlpha = 0, turretAlpha = 0, missileAlpha = 0;

    JsonNode shipWeapons = ship.path("weapons");
    if (shipWeapons.isArray()) {
      for (JsonNode w : shipWeapons) {
        String weaponName = w.path("name").asText(w.path("item_name").asText(""));
        if (weaponName.isBlank()) {
          continue;
        }
        String resolved = resolveDatasetKey("weapons", weaponName);
        JsonNode weaponEntry = resolved != null ? getWeapon(resolved) : getWeapon(weaponName);
        JsonNode ws = weaponEntry != null ? weaponEntry.path("stats") : null;
        if (ws == null || !ws.isObject()) {
          continue;
        }
        double count = Math.max(1, w.path("count").asDouble(1));
        pilotDps += ws.path("dps").asDouble(0) * count;
        pilotAlpha += ws.path("alpha_damage").asDouble(0) * count;
      }
    }

    JsonNode shipTurrets = ship.path("turrets");
    if (shipTurrets.isArray()) {
      for (JsonNode t : shipTurrets) {
        String turretName = t.path("name").asText(t.path("item_name").asText(""));
        if (turretName.isBlank()) {
          continue;
        }
        String resolved = resolveDatasetKey("weapons", turretName);
        JsonNode turretEntry = resolved != null ? getWeapon(resolved) : getWeapon(turretName);
        JsonNode ts = turretEntry != null ? turretEntry.path("stats") : null;
        if (ts == null || !ts.isObject()) {
          continue;
        }
        double count = Math.max(1, t.path("count").asDouble(1));
        turretDps += ts.path("dps").asDouble(0) * count;
        turretAlpha += ts.path("alpha_damage").asDouble(0) * count;
      }
    }

    JsonNode shipMissiles = ship.path("missiles");
    if (shipMissiles.isArray()) {
      for (JsonNode m : shipMissiles) {
        String missileName =
            m.path("name").asText(m.path("item_name").asText(m.path("type").asText("")));
        if (missileName.isBlank()) {
          continue;
        }
        String resolved = resolveDatasetKey("weapons", missileName);
        JsonNode missileEntry = resolved != null ? getWeapon(resolved) : getWeapon(missileName);
        JsonNode ms = missileEntry != null ? missileEntry.path("stats") : null;
        if (ms == null || !ms.isObject()) {
          continue;
        }
        missileDps += ms.path("dps").asDouble(0);
        missileAlpha += ms.path("alpha_damage").asDouble(0);
      }
    }

    return new double[]{pilotDps, turretDps, missileDps, pilotAlpha, turretAlpha, missileAlpha};
  }

  /**
   * Returns total effective DPS/Alpha (physical, energy) derived from default loadouts.
   */
  public static double[] deriveShipEffectiveTotalsFromLoadout(JsonNode ship) {
    double[] base = deriveShipFirepowerFromLoadout(ship);
    double totalDps = base[0] + base[1] + base[2];
    double totalAlpha = base[3] + base[4] + base[5];

    JsonNode stats = ship != null ? ship.path("stats") : null;
    double physicalPct = stats != null ? stats.path("armor_physical_damage_modifier").asDouble(0) : 0;
    double energyPct = stats != null ? stats.path("armor_energy_damage_modifier").asDouble(0) : 0;
    double physicalScale = Math.max(0, 1.0 + (physicalPct / 100.0));
    double energyScale = Math.max(0, 1.0 + (energyPct / 100.0));

    return new double[] {
      totalDps * physicalScale,
      totalDps * energyScale,
      totalAlpha * physicalScale,
      totalAlpha * energyScale
    };
  }

  private static double[] deriveShipStatsFromComponents(JsonNode componentNode) {
    double shieldHp = 0;
    double qtSpeed = 0;
    double qtRange = 0;

    if (componentNode == null || componentNode.isMissingNode() || componentNode.isNull()) {
      return new double[]{0, 0, 0};
    }

    List<String> names = new ArrayList<>();
    collectComponentNames(componentNode, names);
    for (String componentName : names) {
      if (componentName == null || componentName.isBlank()) {
        continue;
      }
      String resolved = resolveDatasetKey("components", componentName);
      JsonNode component = resolved != null ? getComponent(resolved) : getComponent(componentName);
      if (component == null || component.isMissingNode()) {
        continue;
      }

      JsonNode cs = component.path("stats");
      JsonNode ci = component.path("info");
      String type = ci.path("type").asText("").toLowerCase();

      double compShield = cs.path("shield_hp").asDouble(0);
      if (compShield > 0 && (type.contains("shield") || shieldHp == 0)) {
        shieldHp += compShield;
      }

      double compQtSpeed = cs.path("quantum_speed").asDouble(0);
      double compQtRange = cs.path("quantum_range").asDouble(0);
      if (type.contains("quantum") || compQtSpeed > 0 || compQtRange > 0) {
        if (compQtSpeed > qtSpeed) {
          qtSpeed = compQtSpeed;
        }
        if (compQtRange > qtRange) {
          qtRange = compQtRange;
        }
      }
    }

    return new double[]{shieldHp, qtSpeed, qtRange};
  }

  private static void collectComponentNames(JsonNode node, List<String> out) {
    if (node == null || node.isMissingNode() || node.isNull()) {
      return;
    }
    if (node.isTextual()) {
      String text = node.asText("").trim();
      if (!text.isBlank()) {
        out.add(text);
      }
      return;
    }
    if (node.isArray()) {
      for (JsonNode item : node) {
        collectComponentNames(item, out);
      }
      return;
    }
    if (!node.isObject()) {
      return;
    }

    String directName = node.path("name").asText(node.path("item_name").asText(""));
    if (directName.isBlank()) {
      directName = node.path("type").asText("");
    }
    if (!directName.isBlank()) {
      out.add(directName.trim());
      return;
    }

    node.fields().forEachRemaining(field -> collectComponentNames(field.getValue(), out));
  }

  // ---------------------------------------------------------
  // LOAD A SINGLE JSON FILE
  // ---------------------------------------------------------

  /**
   * Loads one dataset, preferring runtime snapshot data/<name>.json. Falls back to classpath
   * resource /starcitizen/<name>.json.
   */
  private static void load(String name) {
    JsonNode dbJson = null;
    JsonNode snapshotJson = null;
    JsonNode resourceJson = null;

    // 0) SQLite cache first
    dbJson = StarCitizenDatasetStore.loadDataset(name);

    // 1) Runtime snapshot first (produced by update manager)
    File dataFile = new File(DATA_FOLDER, name + ".json");
    if (dataFile.exists()) {
      try {
        snapshotJson = mapper.readTree(dataFile);
        System.out.println("[DataService] Loaded " + name + " from " + dataFile.getPath());
      } catch (Exception e) {
        // Continue to packaged fallback if snapshot is unreadable/corrupt.
        System.out.println(
            "[DataService] Snapshot load failed for "
                + name
                + "; falling back to packaged resource. Reason: "
                + e.getMessage());
      }
    }

    // 2) Packaged resource fallback
    try (InputStream stream =
        StarCitizenDataService.class.getResourceAsStream("/starcitizen/" + name + ".json")) {
      if (stream != null) {
        resourceJson = mapper.readTree(stream);
        System.out.println("[DataService] Loaded " + name + " from packaged resources");
      }
    } catch (Exception e) {
      System.out.println("[DataService] Resource load failed for " + name + ": " + e.getMessage());
    }

    JsonNode resolved;
    if ("ships".equals(name)) {
      resolved = preferRicherShipData(snapshotJson, dbJson, resourceJson);
    } else if ("weapons".equals(name)) {
      resolved = preferRicherWeaponData(snapshotJson, dbJson, resourceJson);
    } else if ("components".equals(name)) {
      resolved = preferRicherComponentData(snapshotJson, dbJson, resourceJson);
    } else {
      resolved = firstUsable(dbJson, snapshotJson, resourceJson);
    }

    if (resolved != null) {
      cache.put(name, resolved);
      return;
    }

    // 3) No source found/readable: keep previous cache entry (if any) untouched.
    if (!cache.containsKey(name)) {
      System.out.println(
          "[DataService] Dataset unavailable: " + name + " (no snapshot or resource found)");
    }
  }

  private static JsonNode firstUsable(JsonNode... candidates) {
    if (candidates == null) {
      return null;
    }
    for (JsonNode candidate : candidates) {
      if (isUsable(candidate)) {
        return candidate;
      }
    }
    return null;
  }

  private static boolean isUsable(JsonNode json) {
    return json != null && !json.isNull() && !json.isMissingNode() && !json.isEmpty();
  }

  private static JsonNode preferRicherShipData(
      JsonNode snapshotJson, JsonNode dbJson, JsonNode resourceJson) {
    JsonNode preferred = firstUsable(snapshotJson, dbJson, resourceJson);
    if (!isUsable(preferred)) {
      return null;
    }

    JsonNode merged = preferred;
    if (isUsable(dbJson) && dbJson != preferred) {
      merged = StarCitizenUpdateManager.mergeLoadedShips(dbJson, merged);
    }
    if (isUsable(resourceJson) && resourceJson != preferred) {
      merged = StarCitizenUpdateManager.mergeLoadedShips(resourceJson, merged);
    }
    return StarCitizenUpdateManager.applyRawShipStats(merged);
  }

  private static JsonNode preferRicherWeaponData(
      JsonNode snapshotJson, JsonNode dbJson, JsonNode resourceJson) {
    JsonNode preferred = firstUsable(snapshotJson, dbJson, resourceJson);
    if (!isUsable(preferred)) {
      return null;
    }

    JsonNode merged = preferred;
    if (isUsable(dbJson) && dbJson != preferred) {
      merged = StarCitizenUpdateManager.mergeLoadedWeapons(dbJson, merged);
    }
    if (isUsable(resourceJson) && resourceJson != preferred) {
      merged = StarCitizenUpdateManager.mergeLoadedWeapons(resourceJson, merged);
    }
    return StarCitizenUpdateManager.applyGuideWeaponStats(merged);
  }

  private static JsonNode preferRicherComponentData(
      JsonNode snapshotJson, JsonNode dbJson, JsonNode resourceJson) {
    JsonNode preferred = firstUsable(snapshotJson, dbJson, resourceJson);
    if (!isUsable(preferred)) {
      return null;
    }

    JsonNode merged = preferred;
    if (isUsable(dbJson) && dbJson != preferred) {
      merged = StarCitizenUpdateManager.mergeLoadedComponents(dbJson, merged);
    }
    if (isUsable(resourceJson) && resourceJson != preferred) {
      merged = StarCitizenUpdateManager.mergeLoadedComponents(resourceJson, merged);
    }
    return merged;
  }

  // ---------------------------------------------------------
  // RAW ACCESSOR (rarely used)
  // ---------------------------------------------------------

  /**
   * Returns a cached dataset by file key.
   */
  public static JsonNode get(String name) {
    return cache.get(name);
  }

  /**
   * Resolves a dataset key (commodity, ship, weapon, etc.) from user input.
   */
  public static String resolveDatasetKey(String dataset, String input) {
    if (input == null || input.isBlank()) {
      return null;
    }

    JsonNode data = cache.get(dataset);
    if (data == null || !data.isObject() || data.isEmpty()) {
      return null;
    }

    String trimmed = input.trim();

    // Exact key hit
    if (data.has(trimmed)) {
      return trimmed;
    }

    // Case-insensitive exact key hit
    List<String> names = new ArrayList<>();
    data.fieldNames().forEachRemaining(names::add);
    for (String name : names) {
      if (name.equalsIgnoreCase(trimmed)) {
        return name;
      }
    }

    // Fuzzy score ranking
    String lower = trimmed.toLowerCase();
    Map<String, Integer> scored = new HashMap<>();
    for (String name : names) {
      int score = scoreMatch(lower, name.toLowerCase());
      if (score > 0) {
        scored.put(name, score);
      }
    }

    if (scored.isEmpty()) {
      return null;
    }

    List<Map.Entry<String, Integer>> sorted =
        scored.entrySet().stream().sorted((a, b) -> b.getValue() - a.getValue()).toList();

    String best = sorted.get(0).getKey();
    int bestScore = sorted.get(0).getValue();
    if (bestScore < 45) {
      return null;
    }
    return best;
  }

  /**
   * Returns top fuzzy key suggestions for a dataset.
   */
  public static List<String> suggestDatasetKeys(String dataset, String input, int limit) {
    List<String> out = new ArrayList<>();
    if (input == null || input.isBlank() || limit <= 0) {
      return out;
    }

    JsonNode data = cache.get(dataset);
    if (data == null || !data.isObject() || data.isEmpty()) {
      return out;
    }

    String lower = input.trim().toLowerCase();
    Map<String, Integer> scored = new HashMap<>();
    data.fieldNames()
        .forEachRemaining(
            name -> {
              int score = scoreMatch(lower, name.toLowerCase());
              if (score > 0) {
                scored.put(name, score);
              }
            });

    scored.entrySet().stream()
        .sorted((a, b) -> b.getValue() - a.getValue())
        .limit(limit)
        .forEach(e -> out.add(e.getKey()));
    return out;
  }

  // ---------------------------------------------------------
  // SHIP QUERY FUNCTIONS
  // ---------------------------------------------------------

  /**
   * Returns the full normalized ship object for a given ship name.
   */
  public static JsonNode getShip(String name) {
    return ships != null ? ships.path(name) : null;
  }

  /**
   * Returns the ship info subsection.
   */
  public static JsonNode getShipInfo(String name) {
    JsonNode ship = getShip(name);
    return ship != null ? ship.path("info") : null;
  }

  /**
   * Returns the ship stats subsection.
   */
  public static JsonNode getShipStats(String name) {
    JsonNode ship = getShip(name);
    return ship != null ? ship.path("stats") : null;
  }

  /**
   * Returns mounted weapon entries for the ship.
   */
  public static JsonNode getShipWeapons(String name) {
    JsonNode ship = getShip(name);
    return ship != null ? ship.path("weapons") : null;
  }

  /**
   * Returns missile configuration entries for the ship.
   */
  public static JsonNode getShipMissiles(String name) {
    JsonNode ship = getShip(name);
    return ship != null ? ship.path("missiles") : null;
  }

  /**
   * Returns missile rack configuration entries for the ship.
   */
  public static JsonNode getShipMissileRacks(String name) {
    JsonNode ship = getShip(name);
    return ship != null ? ship.path("missile_racks") : null;
  }

  /**
   * Returns turret configuration entries for the ship.
   */
  public static JsonNode getShipTurrets(String name) {
    JsonNode ship = getShip(name);
    return ship != null ? ship.path("turrets") : null;
  }

  /**
   * Returns component slots/assignments for the ship.
   */
  public static JsonNode getShipComponents(String name) {
    JsonNode ship = getShip(name);
    return ship != null ? ship.path("components") : null;
  }

  /**
   * Convenience alias for returning the complete ship object.
   */
  public static JsonNode getShipFull(String name) {
    return getShip(name);
  }

  // ---------------------------------------------------------
  // WEAPON QUERY FUNCTIONS
  // ---------------------------------------------------------

  /**
   * Returns the full normalized weapon object for a given weapon name.
   */
  public static JsonNode getWeapon(String name) {
    return weapons != null ? weapons.path(name) : null;
  }

  /**
   * Returns the weapon info subsection.
   */
  public static JsonNode getWeaponInfo(String name) {
    JsonNode weapon = getWeapon(name);
    return weapon != null ? weapon.path("info") : null;
  }

  /**
   * Returns the weapon stats subsection.
   */
  public static JsonNode getWeaponStats(String name) {
    JsonNode weapon = getWeapon(name);
    return weapon != null ? weapon.path("stats") : null;
  }

  /**
   * Returns the weapon ammo subsection.
   */
  public static JsonNode getWeaponAmmo(String name) {
    JsonNode weapon = getWeapon(name);
    return weapon != null ? weapon.path("ammo") : null;
  }

  /**
   * Returns supported fire modes for the weapon.
   */
  public static JsonNode getWeaponFireModes(String name) {
    JsonNode weapon = getWeapon(name);
    return weapon != null ? weapon.path("fire_modes") : null;
  }

  /**
   * Returns attachment compatibility information.
   */
  public static JsonNode getWeaponAttachments(String name) {
    JsonNode weapon = getWeapon(name);
    return weapon != null ? weapon.path("attachments") : null;
  }

  /**
   * Convenience alias for returning the complete weapon object.
   */
  public static JsonNode getWeaponFull(String name) {
    return getWeapon(name);
  }

  // ---------------------------------------------------------
  // COMPONENT QUERY FUNCTIONS
  // ---------------------------------------------------------

  /**
   * Returns the full normalized component object for a given component name.
   */
  public static JsonNode getComponent(String name) {
    return components != null ? components.path(name) : null;
  }

  /**
   * Returns the component info subsection.
   */
  public static JsonNode getComponentInfo(String name) {
    JsonNode comp = getComponent(name);
    return comp != null ? comp.path("info") : null;
  }

  /**
   * Returns the component stats subsection.
   */
  public static JsonNode getComponentStats(String name) {
    JsonNode comp = getComponent(name);
    return comp != null ? comp.path("stats") : null;
  }

  /**
   * Returns additional raw component attributes.
   */
  public static JsonNode getComponentAttributes(String name) {
    JsonNode comp = getComponent(name);
    return comp != null ? comp.path("attributes") : null;
  }

  /**
   * Convenience alias for returning the complete component object.
   */
  public static JsonNode getComponentFull(String name) {
    return getComponent(name);
  }

  // ---------------------------------------------------------
  // NAME RESOLVER (Balanced fuzzy matching + aliases)
  // ---------------------------------------------------------

  private static final Map<String, String> SHIP_ALIASES =
      Map.ofEntries(
          Map.entry("cutty", "Cutlass Black"),
          Map.entry("cutlass", "Cutlass Black"),
          Map.entry("cutty black", "Cutlass Black"),
          Map.entry("cutty red", "Cutlass Red"),
          Map.entry("cutty blue", "Cutlass Blue"),
          Map.entry("glad", "Gladius"),
          Map.entry("gladius", "Gladius"),
          Map.entry("hawk", "Anvil Hawk"),
          Map.entry("redee", "Redeemer"),
          Map.entry("redeemer", "Redeemer"),
          Map.entry("carr", "Carrack"),
          Map.entry("carrack", "Carrack"),
          Map.entry("cat", "Caterpillar"),
          Map.entry("caterpillar", "Caterpillar"),
          Map.entry("cater", "Caterpillar"),
          Map.entry("valk", "Valkyrie"),
          Map.entry("valky", "Valkyrie"),
          Map.entry("valkyrie", "Valkyrie"),
          Map.entry("arrow", "Aegis Arrow"),
          Map.entry("300i", "300i"),
          Map.entry("315p", "315p"),
          Map.entry("325a", "325a"),
          Map.entry("350r", "350r"),
          Map.entry("890", "890 Jump"),
          Map.entry("890 jump", "890 Jump"),
          Map.entry("890j", "890 Jump"),
          Map.entry("connie", "Constellation Andromeda"),
          Map.entry("andromeda", "Constellation Andromeda"),
          Map.entry("aquila", "Constellation Aquila"),
          Map.entry("phoenix", "Constellation Phoenix"),
          Map.entry("taurus", "Constellation Taurus"),
          Map.entry("freelancer", "Freelancer"),
          Map.entry("max", "Freelancer MAX"),
          Map.entry("freelancer max", "Freelancer MAX"),
          Map.entry("dur", "Freelancer DUR"),
          Map.entry("freelancer dur", "Freelancer DUR"),
          Map.entry("mis", "Freelancer MIS"),
          Map.entry("freelancer mis", "Freelancer MIS"),
          Map.entry("herc", "C2 Hercules"),
          Map.entry("c2", "C2 Hercules"),
          Map.entry("m2", "M2 Hercules"),
          Map.entry("a2", "A2 Hercules"),
          Map.entry("reclaimer", "Reclaimer"),
          Map.entry("vulture", "Vulture"),
          Map.entry("prospector", "Prospector"),
          Map.entry("mole", "MOLE"),
          Map.entry("hammerhead", "Hammerhead"),
          Map.entry("hh", "Hammerhead"),
          Map.entry("idris", "Idris-P"),
          Map.entry("idris-p", "Idris-P"),
          Map.entry("starfarer", "Starfarer"),
          Map.entry("gemini", "Starfarer Gemini"),
          Map.entry("terrapin", "Terrapin"),
          Map.entry("eclipse", "Eclipse"),
          Map.entry("sabre", "Sabre"),
          Map.entry("hornet", "F7C Hornet"),
          Map.entry("f7c", "F7C Hornet"),
          Map.entry("super hornet", "F7C-M Super Hornet"),
          Map.entry("avenger titan", "Avenger Titan"),
          Map.entry("titan", "Avenger Titan"),
          Map.entry("aurora mr", "Aurora MR"),
          Map.entry("aurora", "Aurora MR"),
          Map.entry("mustang alpha", "Mustang Alpha"),
          Map.entry("mustang", "Mustang Alpha"),
          Map.entry("merchantman", "Banu Merchantman"),
          Map.entry("bmm", "Banu Merchantman"),
          Map.entry("polaris", "Polaris"),
          Map.entry("perseus", "Perseus"));

  /**
   * Resolves user-entered ship names using aliases plus fuzzy scoring.
   *
   * <p>Returns null when confidence is too low or top results are ambiguous.
   */
  public static String resolveShipName(String input) {

    if (input == null || input.isBlank()) {
      return null;
    }
    input = input.toLowerCase().trim();

    JsonNode shipData = ships;
    if (shipData == null) {
      return null;
    }

    // 1. Alias match
    if (SHIP_ALIASES.containsKey(input)) {
      return SHIP_ALIASES.get(input);
    }

    // 2. Build list of ship names
    List<String> names = new ArrayList<>();
    shipData.fieldNames().forEachRemaining(names::add);

    // Score candidates so exact/prefix matches win before fuzzy distance ties.
    // 3. Score matches
    Map<String, Integer> scored = new HashMap<>();
    for (String name : names) {
      int score = scoreMatch(input, name.toLowerCase());
      if (score > 0) {
        scored.put(name, score);
      }
    }

    if (scored.isEmpty()) {
      return null;
    }

    // 4. Sort by score
    List<Map.Entry<String, Integer>> sorted =
        scored.entrySet().stream().sorted((a, b) -> b.getValue() - a.getValue()).toList();

    String best = sorted.get(0).getKey();
    int bestScore = sorted.get(0).getValue();

    // 5. Ambiguity check
    if (sorted.size() > 1) {
      int second = sorted.get(1).getValue();
      if (Math.abs(bestScore - second) < 10) {
        return null; // too close → ask user to clarify
      }
    }

    // 6. Minimum confidence
    if (bestScore < 50) {
      return null;
    }

    return best;
  }

  /**
   * Scores one candidate ship name against user input.
   */
  private static int scoreMatch(String input, String target) {

    if (input.equals(target)) {
      return 100;
    }
    if (target.startsWith(input)) {
      return 90;
    }
    if (target.contains(input)) {
      return 75;
    }

    // Fall back to edit-distance similarity when no direct substring hit exists.
    int dist = levenshtein(input, target);
    int max = Math.max(input.length(), target.length());
    int similarity = (int) ((1.0 - (double) dist / max) * 70);

    return Math.max(similarity, 0);
  }

  // Standard dynamic-programming Levenshtein implementation.

  /**
   * Computes edit distance used by fuzzy score fallback.
   */
  private static int levenshtein(String a, String b) {
    int[][] dp = new int[a.length() + 1][b.length() + 1];

    for (int i = 0; i <= a.length(); i++) {
      dp[i][0] = i;
    }
    for (int j = 0; j <= b.length(); j++) {
      dp[0][j] = j;
    }

    for (int i = 1; i <= a.length(); i++) {
      for (int j = 1; j <= b.length(); j++) {
        int cost = (a.charAt(i - 1) == b.charAt(j - 1)) ? 0 : 1;

        dp[i][j] = Math.min(Math.min(dp[i - 1][j] + 1, dp[i][j - 1] + 1), dp[i - 1][j - 1] + cost);
      }
    }

    return dp[a.length()][b.length()];
  }
}
