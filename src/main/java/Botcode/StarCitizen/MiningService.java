package Botcode.StarCitizen;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Computes mining viability estimates from rock, ship, laser, and consumable inputs.
 *
 * <p>Values are calibrated against Star Citizen 3.23 balance data.
 */
public class MiningService {

  // ---------------------------------------------------------
  // STATIC MAPS (CONSUMABLES, SHIP BONUSES, ROCK TYPES)
  // ---------------------------------------------------------

  public static final Map<String, Consumable> CONSUMABLES =
      Map.ofEntries(
          // Active modules
          Map.entry("surge", new Consumable(0.50, 0.0, 0.0)),
          Map.entry("brandt", new Consumable(0.0, -0.20, 0.0)),
          Map.entry("stampede", new Consumable(0.0, 0.0, 0.30)),
          Map.entry("focus i", new Consumable(0.20, -0.05, 0.0)),
          Map.entry("focus ii", new Consumable(0.35, -0.05, 0.0)),
          Map.entry("focus iii", new Consumable(0.50, -0.05, 0.0)),
          Map.entry("torrent i", new Consumable(0.10, 0.0, 0.08)),
          Map.entry("torrent ii", new Consumable(0.15, 0.0, 0.12)),
          Map.entry("torrent iii", new Consumable(0.20, 0.0, 0.15)),
          Map.entry("xmt xl", new Consumable(0.25, -0.10, 0.0)),
          // Passive modules
          Map.entry("lifeline", new Consumable(0.05, -0.05, 0.05)),
          Map.entry("optimum", new Consumable(0.10, 0.0, 0.0)),
          Map.entry("torrent", new Consumable(0.15, 0.0, 0.10)),
          Map.entry("rime", new Consumable(0.0, -0.15, 0.0)),
          Map.entry("rime i", new Consumable(0.0, -0.10, 0.0)),
          Map.entry("rime ii", new Consumable(0.0, -0.15, 0.0)),
          Map.entry("rime iii", new Consumable(0.0, -0.20, 0.0)),
          Map.entry("forel", new Consumable(0.0, -0.12, 0.05)),
          Map.entry("grelin", new Consumable(0.0, 0.0, 0.25)),
          Map.entry("herc", new Consumable(0.12, 0.0, 0.0)),
          Map.entry("impulse", new Consumable(0.08, 0.0, 0.0)),
          Map.entry(
              "jütes", new Consumable(0.0, 0.0, 0.0)), // resistance reduction handled separately
          Map.entry("none", new Consumable(0.0, 0.0, 0.0)));

  // Known in-game purchase locations for key mining consumables/modules.
  // Keep this map conservative and factual; add entries as verified.
  public static final Map<String, List<String>> CONSUMABLE_BUY_LOCATIONS =
      Map.ofEntries(
          Map.entry(
              "surge",
              List.of("Refinery Shop - ARC-L2 (ArcCorp)", "Refinery Shop - HUR-L1 (Hurston)")),
          Map.entry(
              "brandt",
              List.of("Refinery Shop - ARC-L2 (ArcCorp)", "Refinery Shop - HUR-L1 (Hurston)")),
          Map.entry(
              "stampede",
              List.of("Refinery Shop - ARC-L2 (ArcCorp)", "Refinery Shop - HUR-L1 (Hurston)")),
          Map.entry(
              "xmt xl",
              List.of("Refinery Shop - ARC-L2 (ArcCorp)", "Refinery Shop - HUR-L1 (Hurston)")));

  public static final Map<String, ShipMiningProfile> SHIP_MINING =
      Map.of(
          "Prospector", new ShipMiningProfile(0.0, 0.0),
          "Mole", new ShipMiningProfile(0.10, -0.10),
          "Orion", new ShipMiningProfile(0.25, -0.20));

  public static final Map<String, RockProfile> ROCK_TYPES =
      Map.ofEntries(
          Map.entry("Quantanium", new RockProfile(0.85, 1.20)),
          Map.entry("Laranite", new RockProfile(0.60, 1.10)),
          Map.entry("Agricium", new RockProfile(0.45, 1.00)),
          Map.entry("Bexalite", new RockProfile(0.55, 1.15)),
          Map.entry("Taranite", new RockProfile(0.50, 1.05)),
          Map.entry("Beryl", new RockProfile(0.20, 0.80)),
          Map.entry("Gold", new RockProfile(0.30, 0.85)),
          Map.entry("Diamond", new RockProfile(0.40, 1.10)),
          Map.entry("Corundum", new RockProfile(0.25, 0.75)),
          Map.entry("Titanium", new RockProfile(0.15, 0.70)),
          Map.entry("Tungsten", new RockProfile(0.20, 0.75)),
          Map.entry("Aluminum", new RockProfile(0.10, 0.60)),
          Map.entry("Hephaestanite", new RockProfile(0.70, 1.15)),
          Map.entry("Inertite", new RockProfile(0.35, 0.90)));

  // Approximate baseline rock masses used for power/charge modelling.
  // This replaces the previous fixed mass so analysis changes by resource type.
  public static final Map<String, Double> ROCK_MASS =
      Map.ofEntries(
          Map.entry("Quantanium", 7600.0),
          Map.entry("Laranite", 6200.0),
          Map.entry("Agricium", 5800.0),
          Map.entry("Bexalite", 6400.0),
          Map.entry("Taranite", 6000.0),
          Map.entry("Beryl", 4100.0),
          Map.entry("Gold", 4500.0),
          Map.entry("Diamond", 5200.0),
          Map.entry("Corundum", 3900.0),
          Map.entry("Titanium", 3600.0),
          Map.entry("Tungsten", 3800.0),
          Map.entry("Aluminum", 3300.0),
          Map.entry("Hephaestanite", 7000.0),
          Map.entry("Inertite", 4700.0));

  // ---------------------------------------------------------
  // LASER PROFILES — all current mining heads
  // ---------------------------------------------------------

  public static final Map<String, LaserProfile> LASERS =
      Map.ofEntries(
          Map.entry("Hofstede S1", new LaserProfile(1500, 1.00, 0.05)),
          Map.entry("Hofstede S2", new LaserProfile(4500, 1.00, 0.05)),
          // Legacy alias so existing presets still work
          Map.entry("Hofstede", new LaserProfile(4500, 1.00, 0.05)),
          Map.entry("Helix I", new LaserProfile(1800, 0.90, 0.08)),
          Map.entry("Helix II", new LaserProfile(5400, 0.90, 0.08)),
          Map.entry("Lancet MH1", new LaserProfile(1200, 1.20, 0.03)),
          Map.entry("Lancet MH2", new LaserProfile(3600, 1.20, 0.03)),
          Map.entry("Arbor MH1", new LaserProfile(1600, 1.00, 0.06)),
          Map.entry("Arbor MH2", new LaserProfile(4800, 1.00, 0.06)),
          Map.entry("Rigler XL", new LaserProfile(6500, 0.85, 0.10)),
          Map.entry("Klein S", new LaserProfile(900, 1.30, 0.02)));

  // ---------------------------------------------------------
  // ALIAS MAPS — tolerate common misspellings / short forms
  // ---------------------------------------------------------

  private static final Map<String, String> LASER_ALIASES =
      new HashMap<>(
          Map.ofEntries(
              // Rigler variations (the one the user misspelled as "rigerc-xl")
              Map.entry("rigler", "Rigler XL"),
              Map.entry("riglerxl", "Rigler XL"),
              Map.entry("rigler xl", "Rigler XL"),
              Map.entry("riglerx", "Rigler XL"),
              Map.entry("riglexl", "Rigler XL"),
              Map.entry("rigerc", "Rigler XL"),
              Map.entry("rigercxl", "Rigler XL"),
              Map.entry("rigerc-xl", "Rigler XL"),
              Map.entry("rigerxl", "Rigler XL"),
              Map.entry("rigerx", "Rigler XL"),
              Map.entry("rigx", "Rigler XL"),
              // Hofstede
              Map.entry("hofstede", "Hofstede S2"),
              Map.entry("hofstedes1", "Hofstede S1"),
              Map.entry("hofstedes2", "Hofstede S2"),
              Map.entry("hof", "Hofstede S2"),
              Map.entry("hofs1", "Hofstede S1"),
              Map.entry("hofs2", "Hofstede S2"),
              // Helix
              Map.entry("helix", "Helix II"),
              Map.entry("helixii", "Helix II"),
              Map.entry("helix2", "Helix II"),
              Map.entry("helixi", "Helix I"),
              Map.entry("helix1", "Helix I"),
              // Lancet
              Map.entry("lancet", "Lancet MH2"),
              Map.entry("lancetmh1", "Lancet MH1"),
              Map.entry("lancetmh2", "Lancet MH2"),
              Map.entry("lancetml", "Lancet MH2"),
              // Arbor
              Map.entry("arbor", "Arbor MH2"),
              Map.entry("arbormh1", "Arbor MH1"),
              Map.entry("arbormh2", "Arbor MH2"),
              // Klein
              Map.entry("klein", "Klein S"),
              Map.entry("kleins", "Klein S")));

  private static final Map<String, String> CONSUMABLE_ALIASES =
      new HashMap<>(
          Map.ofEntries(
              // Surge
              Map.entry("surge", "surge"),
              // Brandt
              Map.entry("brandt", "brandt"),
              Map.entry("brant", "brandt"),
              Map.entry("brand", "brandt"),
              // Stampede
              Map.entry("stampede", "stampede"),
              Map.entry("stamp", "stampede"),
              // Lifeline
              Map.entry("lifeline", "lifeline"),
              Map.entry("life", "lifeline"),
              // Optimum
              Map.entry("optimum", "optimum"),
              Map.entry("optim", "optimum"),
              // Torrent (base passive)
              Map.entry("torrent", "torrent"),
              Map.entry("torr", "torrent"),
              // Torrent tiers (active)
              Map.entry("torrent i", "torrent i"),
              Map.entry("torrenti", "torrent i"),
              Map.entry("torrent1", "torrent i"),
              Map.entry("torrent ii", "torrent ii"),
              Map.entry("torrentii", "torrent ii"),
              Map.entry("torrent2", "torrent ii"),
              Map.entry("torrent iii", "torrent iii"),
              Map.entry("torrentiii", "torrent iii"),
              Map.entry("torrent3", "torrent iii"),
              // Focus tiers
              Map.entry("focus1", "focus i"),
              Map.entry("focus i", "focus i"),
              Map.entry("focusi", "focus i"),
              Map.entry("focus2", "focus ii"),
              Map.entry("focus ii", "focus ii"),
              Map.entry("focusii", "focus ii"),
              Map.entry("focus3", "focus iii"),
              Map.entry("focus iii", "focus iii"),
              Map.entry("focusiii", "focus iii"),
              // Rime (base passive)
              Map.entry("rime", "rime"),
              // Rime tiers (passive)
              Map.entry("rime i", "rime i"),
              Map.entry("rimei", "rime i"),
              Map.entry("rime1", "rime i"),
              Map.entry("rime ii", "rime ii"),
              Map.entry("rimeii", "rime ii"),
              Map.entry("rime2", "rime ii"),
              Map.entry("rime iii", "rime iii"),
              Map.entry("rimeiii", "rime iii"),
              Map.entry("rime3", "rime iii"),
              // New passive modules
              Map.entry("forel", "forel"),
              Map.entry("grelin", "grelin"),
              Map.entry("grel", "grelin"),
              Map.entry("herc", "herc"),
              Map.entry("impulse", "impulse"),
              Map.entry("imp", "impulse"),
              Map.entry("jütes", "jütes"),
              Map.entry("jutes", "jütes"),
              Map.entry("jute", "jütes"),
              // XMT XL — active combined module (user-reported, common misspellings)
              Map.entry("xmt xl", "xmt xl"),
              Map.entry("xmtxl", "xmt xl"),
              Map.entry("xmt", "xmt xl"),
              Map.entry("xmtx", "xmt xl"),
              Map.entry("xml xl", "xmt xl"),
              Map.entry("xmt-xl", "xmt xl"),
              // None
              Map.entry("none", "none"),
              Map.entry("n/a", "none"),
              Map.entry("", "none")));

  private static final Map<String, String> ROCK_ALIASES =
      new HashMap<>(
          Map.ofEntries(
              Map.entry("quant", "Quantanium"),
              Map.entry("quantanium", "Quantanium"),
              Map.entry("quantainium", "Quantanium"),
              Map.entry("quan", "Quantanium"),
              Map.entry("laranite", "Laranite"),
              Map.entry("lara", "Laranite"),
              Map.entry("agricium", "Agricium"),
              Map.entry("agri", "Agricium"),
              Map.entry("bexalite", "Bexalite"),
              Map.entry("bex", "Bexalite"),
              Map.entry("taranite", "Taranite"),
              Map.entry("tara", "Taranite"),
              Map.entry("beryl", "Beryl"),
              Map.entry("gold", "Gold"),
              Map.entry("diamond", "Diamond"),
              Map.entry("dia", "Diamond"),
              Map.entry("corundum", "Corundum"),
              Map.entry("cor", "Corundum"),
              Map.entry("titanium", "Titanium"),
              Map.entry("titan", "Titanium"),
              Map.entry("tungsten", "Tungsten"),
              Map.entry("tung", "Tungsten"),
              Map.entry("aluminum", "Aluminum"),
              Map.entry("aluminium", "Aluminum"),
              Map.entry("alum", "Aluminum"),
              Map.entry("hephaestanite", "Hephaestanite"),
              Map.entry("heph", "Hephaestanite"),
              Map.entry("inertite", "Inertite"),
              Map.entry("inert", "Inertite")));

  private static final Map<String, String> SHIP_ALIASES =
      new HashMap<>(
          Map.ofEntries(
              Map.entry("prospector", "Prospector"),
              Map.entry("prosp", "Prospector"),
              Map.entry("pros", "Prospector"),
              Map.entry("mole", "Mole"),
              Map.entry("argo mole", "Mole"),
              Map.entry("argomole", "Mole"),
              Map.entry("orion", "Orion"),
              Map.entry("rsi orion", "Orion")));

  // ---------------------------------------------------------
  // FUZZY RESOLUTION HELPERS
  // ---------------------------------------------------------

  /**
   * Resolves a free-text laser name to the canonical key in LASERS, tolerating common misspellings
   * and abbreviations (e.g. "rigerc-xl" → "Rigler XL").
   */
  public static String resolveLaserName(String input) {
    return resolve(input, LASER_ALIASES, LASERS.keySet());
  }

  /**
   * Resolves consumable input (case/typo-tolerant).
   */
  public static String resolveConsumableName(String input) {
    return resolve(input, CONSUMABLE_ALIASES, CONSUMABLES.keySet());
  }

  /**
   * Returns a friendly buy-location list for a consumable/module key.
   */
  public static List<String> getConsumableBuyLocations(String consumableName) {
    String resolved = resolveConsumableName(consumableName);
    if (resolved == null) {
      return List.of();
    }
    return CONSUMABLE_BUY_LOCATIONS.getOrDefault(resolved.toLowerCase(), List.of());
  }

  /**
   * Returns the effect string from mining.json-equivalent static definitions.
   */
  public static String getConsumableEffect(String consumableName) {
    String resolved = resolveConsumableName(consumableName);
    if (resolved == null) {
      return "";
    }
    return switch (resolved.toLowerCase()) {
      case "surge" -> "+50% power burst";
      case "stampede" -> "+30% charge rate";
      case "brandt" -> "-20% instability";
      case "focus i" -> "+20% focus power";
      case "focus ii" -> "+35% focus power";
      case "focus iii" -> "+50% focus power";
      case "torrent i" -> "+10% power, +8% charge rate";
      case "torrent ii" -> "+15% power, +12% charge rate";
      case "torrent iii" -> "+20% power, +15% charge rate";
      case "xmt xl" -> "+25% power burst, -10% instability";
      default -> "";
    };
  }

  /**
   * Resolves rock name input (case/typo-tolerant).
   */
  public static String resolveRockName(String input) {
    return resolve(input, ROCK_ALIASES, ROCK_TYPES.keySet());
  }

  public static double resolveRockMass(String rockName) {
    String resolved = resolveRockName(rockName);
    return ROCK_MASS.getOrDefault(resolved, 5000.0);
  }

  /**
   * Resolves mining ship name input (case/typo-tolerant).
   */
  public static String resolveShipName(String input) {
    return resolve(input, SHIP_ALIASES, SHIP_MINING.keySet());
  }

  /**
   * Generic resolver: strips, lower-cases, checks alias map, then falls back to case-insensitive
   * substring scan of known keys.
   *
   * @return closest canonical key, or the original input if nothing matches well enough.
   */
  private static String resolve(
      String input, Map<String, String> aliases, java.util.Set<String> keys) {
    if (input == null) {
      return "none";
    }
    String clean =
        input.trim().toLowerCase().replace("-", " ").replace("_", " ").replaceAll("\\s+", " ");

    // 1. exact alias hit
    if (aliases.containsKey(clean)) {
      return aliases.get(clean);
    }

    // 2. without spaces
    String noSpace = clean.replace(" ", "");
    if (aliases.containsKey(noSpace)) {
      return aliases.get(noSpace);
    }

    // 3. case-insensitive exact match against known keys
    for (String k : keys) {
      if (k.equalsIgnoreCase(input.trim())) {
        return k;
      }
    }

    // 4. substring match
    String lower = input.toLowerCase();
    for (String k : keys) {
      if (k.toLowerCase().contains(lower) || lower.contains(k.toLowerCase())) {
        return k;
      }
    }

    // 5. fuzzy levenshtein — pick closest key within threshold
    String best = null;
    int bestDist = Integer.MAX_VALUE;
    for (String k : keys) {
      int dist = levenshtein(clean, k.toLowerCase());
      if (dist < bestDist) {
        bestDist = dist;
        best = k;
      }
    }
    // Accept if edit distance ≤ 4 (handles short typos well)
    if (best != null && bestDist <= 4) {
      return best;
    }

    // 6. Fallback — return original so caller can display "unknown" gracefully
    return input.trim();
  }

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
        int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
        dp[i][j] = Math.min(Math.min(dp[i - 1][j] + 1, dp[i][j - 1] + 1), dp[i - 1][j - 1] + cost);
      }
    }
    return dp[a.length()][b.length()];
  }

  // ---------------------------------------------------------
  // DATA CLASSES
  // ---------------------------------------------------------

  public record Consumable(double powerBonus, double instabilityBonus, double chargeRateBonus) {

  }

  public record ShipMiningProfile(double powerBonus, double stabilityBonus) {

  }

  public record RockProfile(double instabilityMultiplier, double resistanceMultiplier) {

  }

  public record LaserProfile(double basePower, double chargeRate, double fluctuation) {

  }

  // ---------------------------------------------------------
  // MINING RESULT (FINAL FIELDS)
  // ---------------------------------------------------------

  public record MiningResult(
      double rawResistance,
      double rawInstability,
      double rawMass,
      double shipBonus,
      double consumableBonus,
      int moduleSlots,
      double moduleScalingBonus,
      double multiLaserBonus,
      double effectivePower,
      double requiredPower,
      double chargeRate,
      double fluctuation,
      double breakChance,
      boolean viable) {

  }

  // ---------------------------------------------------------
  // CALCULATION METHODS
  // ---------------------------------------------------------

  public static double calculateEffectivePower(
      double basePower, double shipBonus, double consumableBonus, double multiLaserBonus) {
    return basePower * (1 + shipBonus + consumableBonus + multiLaserBonus);
  }

  public static double calculateRequiredPower(double resistance, double instability) {
    return resistance * (1.0 + (instability * 0.25));
  }

  public static double calculateChargeRate(
      double effectivePower, double resistance, double rockMass) {
    double netPower = effectivePower - resistance;
    return netPower / (rockMass * 0.75);
  }

  public static double calculateFluctuation(double instability) {
    return instability * 0.85;
  }

  public static double calculateBreakChance(
      double instability, double overcharge, double timeInRed) {
    return (instability * 0.4) + (overcharge * 0.35) + (timeInRed * 0.25);
  }

  public static boolean isRockViable(
      double requiredPower, double effectivePower, double instability) {
    if (effectivePower < requiredPower) {
      return false;
    }
    return instability < 0.75;
  }

  // ---------------------------------------------------------
  // MAIN ENTRY POINT: analyzeRock()
  // ---------------------------------------------------------

  /**
   * Runs the full mining analysis pipeline and returns a structured result. All input strings are
   * run through the fuzzy resolver so misspellings (e.g. "rigerc-xl", "brant", "quant") are
   * automatically corrected.
   */
  public static MiningResult analyzeRock(
      String rockName, String shipName, String laserName, String consumableName, int operators) {
    return analyzeRock(
        rockName, shipName, laserName, consumableName, operators, Math.max(1, operators));
  }

  public static MiningResult analyzeRock(
      String rockName,
      String shipName,
      List<String> laserNames,
      String consumableName,
      int operators,
      int moduleSlots) {
    List<String> defaultConsumables = new ArrayList<>();
    int count =
        (laserNames == null || laserNames.isEmpty()) ? Math.max(1, operators) : laserNames.size();
    for (int i = 0; i < count; i++) {
      defaultConsumables.add(consumableName);
    }
    return analyzeRock(rockName, shipName, laserNames, defaultConsumables, operators, moduleSlots);
  }

  public static MiningResult analyzeRock(
      String rockName,
      String shipName,
      List<String> laserNames,
      List<String> consumableNames,
      int operators,
      int moduleSlots) {
    if (laserNames == null || laserNames.isEmpty()) {
      String fallbackConsumable =
          (consumableNames == null || consumableNames.isEmpty()) ? "none" : consumableNames.get(0);
      return analyzeRock(
          rockName, shipName, "Hofstede S2", fallbackConsumable, operators, moduleSlots);
    }

    String resolvedRock = resolveRockName(rockName);
    String resolvedShip = resolveShipName(shipName);
    String resolvedConsumable =
        resolveConsumableName(
            consumableNames == null || consumableNames.isEmpty() ? "none" : consumableNames.get(0));

    List<LaserProfile> selected = new ArrayList<>();
    for (String laserName : laserNames) {
      String resolved = resolveLaserName(laserName);
      selected.add(LASERS.getOrDefault(resolved, LASERS.get("Hofstede S2")));
    }

    double avgBasePower = 0;
    double avgChargeRate = 0;
    double avgFluctuation = 0;
    for (LaserProfile lp : selected) {
      avgBasePower += lp.basePower();
      avgChargeRate += lp.chargeRate();
      avgFluctuation += lp.fluctuation();
    }
    avgBasePower /= selected.size();
    avgChargeRate /= selected.size();
    avgFluctuation /= selected.size();

    RockProfile rock = ROCK_TYPES.getOrDefault(resolvedRock, new RockProfile(0.50, 1.00));
    ShipMiningProfile ship =
        SHIP_MINING.getOrDefault(resolvedShip, new ShipMiningProfile(0.0, 0.0));
    Consumable consumable =
        CONSUMABLES.getOrDefault(resolvedConsumable.toLowerCase(), new Consumable(0.0, 0.0, 0.0));

    if (consumableNames != null && !consumableNames.isEmpty()) {
      double p = 0;
      double i = 0;
      double c = 0;
      int n = 0;
      for (String cn : consumableNames) {
        String rc = resolveConsumableName(cn == null ? "none" : cn);
        Consumable ci = CONSUMABLES.getOrDefault(rc.toLowerCase(), new Consumable(0.0, 0.0, 0.0));
        p += ci.powerBonus();
        i += ci.instabilityBonus();
        c += ci.chargeRateBonus();
        n++;
      }
      if (n > 0) {
        consumable = new Consumable(p / n, i / n, c / n);
      }
    }

    int effectiveOperators = Math.max(1, operators);
    double rawResistance = rock.resistanceMultiplier();
    double rawInstability = rock.instabilityMultiplier();
    double rawMass = resolveRockMass(resolvedRock);

    double shipBonus = ship.powerBonus();
    int normalizedSlots = Math.max(0, Math.min(9, moduleSlots));
    double slotMultiplier = normalizedSlots <= 1 ? 1.0 : (1.0 + ((normalizedSlots - 1) * 0.20));
    double moduleScalingBonus = slotMultiplier - 1.0;
    double consumableBonus = consumable.powerBonus() * slotMultiplier;
    double multiLaserBonus = (effectiveOperators - 1) * 0.10;

    double effectivePower =
        calculateEffectivePower(avgBasePower, shipBonus, consumableBonus, multiLaserBonus);
    double requiredPower = calculateRequiredPower(rawResistance, rawInstability);
    double chargeRate =
        calculateChargeRate(
            effectivePower * (avgChargeRate + consumable.chargeRateBonus()),
            rawResistance,
            rawMass);
    double fluctuation = calculateFluctuation(rawInstability + avgFluctuation);
    double breakChance = calculateBreakChance(rawInstability, 0.0, 0.0);
    boolean viable = isRockViable(requiredPower, effectivePower, rawInstability);

    return new MiningResult(
        rawResistance,
        rawInstability,
        rawMass,
        shipBonus,
        consumableBonus,
        normalizedSlots,
        moduleScalingBonus,
        multiLaserBonus,
        effectivePower,
        requiredPower,
        chargeRate,
        fluctuation,
        breakChance,
        viable);
  }

  public static MiningResult analyzeRock(
      String rockName,
      String shipName,
      String laserName,
      String consumableName,
      int operators,
      int moduleSlots) {
    // Resolve user inputs (handles typos / aliases)
    String resolvedRock = resolveRockName(rockName);
    String resolvedShip = resolveShipName(shipName);
    String resolvedLaser = resolveLaserName(laserName);
    String resolvedConsumable = resolveConsumableName(consumableName);

    RockProfile rock = ROCK_TYPES.getOrDefault(resolvedRock, new RockProfile(0.50, 1.00));
    ShipMiningProfile ship =
        SHIP_MINING.getOrDefault(resolvedShip, new ShipMiningProfile(0.0, 0.0));
    Consumable consumable =
        CONSUMABLES.getOrDefault(resolvedConsumable.toLowerCase(), new Consumable(0.0, 0.0, 0.0));
    LaserProfile laser = LASERS.getOrDefault(resolvedLaser, LASERS.get("Hofstede S2"));

    double rawResistance = rock.resistanceMultiplier();
    double rawInstability = rock.instabilityMultiplier();
    double rawMass = resolveRockMass(resolvedRock);

    double shipBonus = ship.powerBonus();
    int normalizedSlots = Math.max(0, Math.min(9, moduleSlots));
    double slotMultiplier = normalizedSlots <= 1 ? 1.0 : (1.0 + ((normalizedSlots - 1) * 0.20));
    double moduleScalingBonus = slotMultiplier - 1.0;
    double consumableBonus = consumable.powerBonus() * slotMultiplier;
    double multiLaserBonus = (operators - 1) * 0.10;

    double effectivePower =
        calculateEffectivePower(laser.basePower(), shipBonus, consumableBonus, multiLaserBonus);
    double requiredPower = calculateRequiredPower(rawResistance, rawInstability);
    double chargeRate = calculateChargeRate(effectivePower, rawResistance, rawMass);
    double fluctuation = calculateFluctuation(rawInstability);
    double breakChance = calculateBreakChance(rawInstability, 0.0, 0.0);
    boolean viable = isRockViable(requiredPower, effectivePower, rawInstability);

    return new MiningResult(
        rawResistance,
        rawInstability,
        rawMass,
        shipBonus,
        consumableBonus,
        normalizedSlots,
        moduleScalingBonus,
        multiLaserBonus,
        effectivePower,
        requiredPower,
        chargeRate,
        fluctuation,
        breakChance,
        viable);
  }

  // ---------------------------------------------------------
  // FORMATTER (BRIEF / FULL / SPECIFIC)
  // ---------------------------------------------------------

  public static class MiningResultFormatter {

    public static String brief(MiningResult r) {
      return String.format(
          "**Mining Summary**\n"
              + "• Viable: %s\n"
              + "• Effective Power: %.0f\n"
              + "• Required Power: %.2f\n"
              + "• Module Slots: %d\n"
              + "• Instability: %.2f\n"
              + "• Break Chance: %.1f%%",
          r.viable() ? "✅ Yes" : "❌ No",
          r.effectivePower(),
          r.requiredPower(),
          r.moduleSlots(),
          r.rawInstability(),
          r.breakChance() * 100);
    }

    public static String full(MiningResult r) {
      return String.format(
          "**Mining Analysis (Full)**\n\n"
              + "**Rock Stats**\n"
              + "• Resistance: %.2f\n"
              + "• Instability: %.2f\n"
              + "• Mass: %.0f\n\n"
              + "**Modifiers**\n"
              + "• Ship Bonus: +%.0f%%\n"
              + "• Consumable Bonus: +%.0f%%\n"
              + "• Module Scaling: +%.0f%% (%d slots)\n"
              + "• Multi-Laser Bonus: +%.0f%%\n\n"
              + "**Computed Values**\n"
              + "• Effective Power: %.0f\n"
              + "• Required Power: %.2f\n"
              + "• Charge Rate: %.4f/s\n"
              + "• Fluctuation: %.4f\n"
              + "• Break Chance: %.1f%%\n\n"
              + "**Viable:** %s",
          r.rawResistance(),
          r.rawInstability(),
          r.rawMass(),
          r.shipBonus() * 100,
          r.consumableBonus() * 100,
          r.moduleScalingBonus() * 100,
          r.moduleSlots(),
          r.multiLaserBonus() * 100,
          r.effectivePower(),
          r.requiredPower(),
          r.chargeRate(),
          r.fluctuation(),
          r.breakChance() * 100,
          r.viable() ? "✅ Yes" : "❌ No");
    }

    public static String specific(MiningResult r, String field) {
      field = field.toLowerCase();
      return switch (field) {
        case "resistance", "rawresistance" -> "Resistance: " + r.rawResistance();
        case "instability", "rawinstability" -> "Instability: " + r.rawInstability();
        case "mass", "rawmass" -> "Mass: " + r.rawMass();
        case "shipbonus" -> "Ship Bonus: " + r.shipBonus();
        case "consumablebonus" -> "Consumable Bonus: " + r.consumableBonus();
        case "moduleslots" -> "Module Slots: " + r.moduleSlots();
        case "modulescaling", "modulescalingbonus" ->
            "Module Scaling Bonus: " + r.moduleScalingBonus();
        case "multilaserbonus" -> "Multi-Laser Bonus: " + r.multiLaserBonus();
        case "effectivepower" -> "Effective Power: " + r.effectivePower();
        case "requiredpower" -> "Required Power: " + r.requiredPower();
        case "chargerate" -> "Charge Rate: " + r.chargeRate();
        case "fluctuation" -> "Fluctuation: " + r.fluctuation();
        case "breakchance" -> String.format("Break Chance: %.1f%%", r.breakChance() * 100);
        case "viable" -> "Viable: " + (r.viable() ? "✅ Yes" : "❌ No");
        default -> "Unknown field: " + field;
      };
    }
  }
}
