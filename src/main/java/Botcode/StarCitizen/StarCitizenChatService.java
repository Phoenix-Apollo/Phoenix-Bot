package Botcode.StarCitizen;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Conversational lookup layer that exposes GUI-style Star Citizen information in chat.
 *
 * <p>Used by the message listener so users can ask for the same data without clicking panel
 * buttons.
 */
public class StarCitizenChatService {

  public static class ShipEmbedData {

    public final String shipName;
    public final String info;
    public final String stats;
    public final String loadout;
    public final String imageUrl;

    public ShipEmbedData(
        String shipName, String info, String stats, String loadout, String imageUrl) {
      this.shipName = shipName;
      this.info = info;
      this.stats = stats;
      this.loadout = loadout;
      this.imageUrl = imageUrl;
    }
  }

  private static final Pattern SCU_PATTERN =
      Pattern.compile("(\\d{1,5})\\s*scu", Pattern.CASE_INSENSITIVE);
  private static final Pattern QUOTED_PATTERN = Pattern.compile("\"([^\"]{2,120})\"");
  private static final Pattern SIZE_PATTERN =
      Pattern.compile("(?:size|s)\\s*(\\d)", Pattern.CASE_INSENSITIVE);
  private static final Pattern BUY_PATTERN =
      Pattern.compile(
          "(?:where\\s+can\\s+i\\s+buy|where\\s+do\\s+i\\s+buy|buy)\\s+(.+?)(?:\\?|$)",
          Pattern.CASE_INSENSITIVE);
  private static final Pattern SELL_PATTERN =
      Pattern.compile(
          "(?:where\\s+can\\s+i\\s+sell|where\\s+do\\s+i\\s+sell|sell)\\s+(.+?)(?:\\?|$)",
          Pattern.CASE_INSENSITIVE);
  private static final Pattern FROM_PATTERN =
      Pattern.compile(
          "from\\s+(.+?)(?:\\s+need|\\s+requires|\\s+require|\\s+with|\\?|$)",
          Pattern.CASE_INSENSITIVE);
  private static final List<String> GENERAL_SNARK =
      List.of(
          "Alright, here's the useful bit before anyone crashes into a moon.",
          "Sure, let's do actual data instead of pure chaos for six seconds.",
          "Because reading the mobiGlas yourself was apparently too mainstream.",
          "Fine, here's the answer with only a mild amount of attitude.",
          "You're welcome in advance, captain paperwork.");
  private static final List<String> ORIGIN_SNARK =
      List.of(
          "Ah yes, Origin Jumpworks — for when a spaceship must also loudly announce your tax bracket.",
          "Origin ships: because some pilots want performance, and others want a flying luxury brochure.",
          "You said Origin, so naturally we're entering the champagne-in-zero-g tier of ship design.",
          "Origin makes ships for people who think cargo space is less important than mood lighting.");
  private static final List<String> JUMP_890_SNARK =
      List.of(
          "The 890 Jump: less a ship, more a tax write-off with engines.",
          "Ah yes, the 890 Jump — a space yacht for people who looked at a capital ship and said 'make it richer.'",
          "The 890 Jump exists to answer the question: 'what if rich people brought a hotel into orbit?'",
          "The 890 Jump is what happens when luxury wins a fistfight against practicality.",
          "Behold the 890 Jump: because apparently some citizens need a penthouse that can quantum travel.");
  private static final List<String> DRAKE_SNARK =
      List.of(
          "Drake ships: because maintenance is just a state of mind.",
          "Ah yes, Drake — where safety regulations go to die gloriously.",
          "Drake builds ships for people who think exposed wiring adds character.");
  private static final List<String> AEGIS_SNARK =
      List.of(
          "Aegis: military chic with just a hint of classified paperwork.",
          "Aegis ships feel like someone weaponized a government contract.",
          "Aegis — for when your ship should look like it files incident reports in triplicate.");
  private static final List<String> RSI_SNARK =
      List.of(
          "RSI: the respectable answer people give before buying something absurd anyway.",
          "Roberts Space Industries — where every brochure sounds like it was written by a senator.",
          "RSI ships always feel like they were approved by six committees and one admiral.");
  private static final List<String> MISC_SNARK =
      List.of(
          "MISC: industrial efficiency with the charisma of a cargo manifest.",
          "MISC ships are what happen when practicality wins and nobody throws a party about it.",
          "MISC — for pilots who get weirdly excited about throughput and fuel economy.");
  private static final List<String> CRUSADER_SNARK =
      List.of(
          "Crusader ships look like clouds got into shipbuilding and somehow it worked.",
          "Crusader: smooth curves, big engines, and just enough bulk to surprise you in a hangar.",
          "Crusader ships are the aerodynamic lie we all choose to believe in space.");
  private static final List<String> ANVIL_SNARK =
      List.of(
          "Anvil ships: because someone took 'reliable violence' and made it a product line.",
          "Anvil builds ships like they expect you to survive bad decisions. Bold of them.",
          "Anvil Aerospace — sturdy, practical, and only slightly obsessed with solving problems via guns.");

  public static String tryRespond(String text) {
    if (text == null || text.isBlank()) {
      return null;
    }

    String lower = text.toLowerCase(Locale.ROOT);

    String money = tryMoneyMaking(text, lower);
    if (money != null) {
      return money;
    }

    String trade = tryTrade(text, lower);
    if (trade != null) {
      return trade;
    }

    String refinery = tryRefinery(text, lower);
    if (refinery != null) {
      return refinery;
    }

    String miningSpots = tryMiningSpots(lower, text);
    if (miningSpots != null) {
      return miningSpots;
    }

    String salvage = trySalvage(lower);
    if (salvage != null) {
      return salvage;
    }

    String buy = tryBuyQuestion(text, lower);
    if (buy != null) {
      return buy;
    }

    String sell = trySellQuestion(text, lower);
    if (sell != null) {
      return sell;
    }

    String shipWeapons = tryShipWeaponsQuery(text, lower);
    if (shipWeapons != null) {
      return shipWeapons;
    }

    String shipList = tryShipCategoryList(text, lower);
    if (shipList != null) {
      return shipList;
    }

    String armorList = tryArmorCategoryList(text, lower);
    if (armorList != null) {
      return armorList;
    }

    String weaponList = tryWeaponCategoryList(text, lower);
    if (weaponList != null) {
      return weaponList;
    }

    String missionList = tryMissionCategoryList(text, lower);
    if (missionList != null) {
      return missionList;
    }

    String locationList = tryLocationCategoryList(text, lower);
    if (locationList != null) {
      return locationList;
    }

    String componentCategory = detectComponentCategory(lower);
    if (componentCategory != null) {
      String componentCategoryReply = tryComponentCategoryOrLookup(text, lower, componentCategory);
      if (componentCategoryReply != null) {
        return componentCategoryReply;
      }
    }

    if (containsAny(lower, "commodity", "commodities", "profit/scu", "profit per scu")) {
      String answer = tryCommodity(text, lower);
      if (answer != null) {
        return answer;
      }
    }

    if (containsAny(
        lower,
        "ship",
        "ships",
        "cargo",
        "crew",
        "scm speed",
        "max speed",
        "hull hp",
        "show me",
        "tell me about",
        "look up",
        "lookup",
        "info on",
        "stats on",
        "specs")) {
      String answer = tryShip(text, lower);
      if (answer != null) {
        return answer;
      }
    }

    if (containsAny(
        lower,
        "weapon",
        "weapons",
        "dps",
        "rpm",
        "alpha damage",
        "projectile speed",
        "hardpoint",
        "show me",
        "tell me about",
        "look up",
        "lookup")) {
      String answer = tryWeapon(text, lower);
      if (answer != null) {
        return answer;
      }
    }

    if (containsAny(
        lower,
        "component",
        "components",
        "power plant",
        "quantum drive",
        "cooler",
        "shield generator",
        "shield",
        "engine",
        "thruster")) {
      String answer = tryComponent(text, lower);
      if (answer != null) {
        return answer;
      }
    }

    if (containsAny(
        lower, "armor", "armour", "ballistic resist", "energy resist", "distortion resist")) {
      String answer = tryArmor(text, lower);
      if (answer != null) {
        return answer;
      }
    }

    if (containsAny(
        lower,
        "mission",
        "missions",
        "issuer",
        "giver",
        "reputation",
        "rep required",
        "where to find")) {
      String answer = tryMission(text, lower);
      if (answer != null) {
        return answer;
      }
    }

    if (containsAny(
        lower,
        "location",
        "locations",
        "where is",
        "where's",
        "station",
        "moon",
        "planet",
        "lagrange")) {
      String answer = tryLocation(text, lower);
      if (answer != null) {
        return answer;
      }
    }

    String directLookup = tryDirectEntityLookup(text, lower);
    if (directLookup != null) {
      return directLookup;
    }

    // Final catch-all: user typed a bare entity name (e.g. "300i", "Caterpillar", "CF-227")
    String freeform = tryFreeformEntityLookup(text, lower);
    if (freeform != null) {
      return freeform;
    }

    return null;
  }

  /**
   * Handles money-making prompts with overall-default and explicit-specific guidance.
   */
  private static String tryMoneyMaking(String text, String lower) {
    if (!containsAny(
        lower,
        "make money",
        "making money",
        "earn money",
        "best way to make money",
        "money in game",
        "best way to earn",
        "fast money",
        "good money")) {
      return null;
    }

    // Keep the default broad answer "overall best" unless user explicitly asks
    // for a specific career path.
    boolean asksSpecific =
        containsAny(lower, "specifically", "specifically for", "only", "just", "focused on", "for ")
            && containsAny(
            lower,
            "mining",
            "trade",
            "hauling",
            "cargo",
            "salvage",
            "bounty",
            "combat",
            "missions");

    if (asksSpecific) {
      if (containsAny(lower, "mining")) {
        return withSnark(
            "refinery",
            null,
            "**Money plan (Mining-specific):**\n"
                + "• Focus higher-value rocks and control instability first\n"
                + "• Refine before selling whenever timing allows\n"
                + "• Run short, repeatable loops over risky hero routes\n\n"
                + "If you share your ship + risk tolerance, I'll give you a tuned mining plan.");
      }
      if (containsAny(lower, "trade", "hauling", "cargo")) {
        return withSnark(
            "trade",
            null,
            "**Money plan (Trade-specific):**\n"
                + "• Use reliable top routes for your SCU instead of chasing one-off spikes\n"
                + "• Prioritize turnaround speed and route consistency\n"
                + "• Manage risk: stable profit beats one giant loss\n\n"
                + "Tell me your SCU and I'll suggest practical routes.");
      }
      if (containsAny(lower, "salvage")) {
        return withSnark(
            "salvage",
            null,
            "**Money plan (Salvage-specific):**\n"
                + "• Keep your loop simple: recover, process, sell quickly\n"
                + "• Favor consistent turnaround over long-haul greed runs\n"
                + "• Tune route safety around your ship and crew situation\n\n"
                + "I can break this down for solo or crewed salvage if you want.");
      }
      if (containsAny(lower, "bounty", "combat", "missions")) {
        return withSnark(
            "mission",
            null,
            "**Money plan (Combat/Missions-specific):**\n"
                + "• Pick contracts matching your current ship/loadout tier\n"
                + "• Chain short missions to reduce downtime\n"
                + "• Survivability > max payout when you're grinding efficiently\n\n"
                + "Tell me your ship and I'll suggest a mission progression style.");
      }
    }

    String body =
        "**Overall best way to make money (unless you want a specific path):**\n"
            + "• **Trade / hauling** is usually the most consistent all-around earner\n"
            + "• **Mining** can beat it on upside with a good refine/sell loop\n"
            + "• **Salvage** — steady value with low planning overhead once your loop is dialed\n"
            + "• **Combat missions/bounties** — great if your ship and loadout are combat-ready\n\n"
            + "If you want a specific path (mining/trade/salvage/combat), say that and I'll switch to targeted advice.";
    return withSnark("trade", null, body);
  }

  /**
   * Handles broad "where is the best place to mine" style prompts.
   */
  private static String tryMiningSpots(String lower, String text) {
    if (!MiningSpotRecommender.isMiningSpotQuestion(lower)) {
      return null;
    }
    String body = MiningSpotRecommender.recommend(text);
    return withSnark("mining_spots", null, body);
  }

  private static String tryBuyQuestion(String text, String lower) {
    Matcher matcher = BUY_PATTERN.matcher(text);
    if (!matcher.find()) {
      return null;
    }
    String candidate = matcher.group(1).trim();
    if (candidate.isBlank()) {
      return null;
    }

    BuyQueryParts buyQuery = splitBuyQuery(candidate);
    String itemCandidate = normalizeBuyCandidate(buyQuery.item());
    String locationFilter = resolveSystemFilter(buyQuery.location());

    String componentKey = StarCitizenDataService.resolveDatasetKey("components", itemCandidate);
    if (componentKey != null) {
      return formatComponent(componentKey, locationFilter);
    }

    String weaponKey = StarCitizenDataService.resolveDatasetKey("weapons", itemCandidate);
    if (weaponKey == null && isOrdnanceQuery(itemCandidate)) {
      weaponKey = resolveOrdnanceWeaponKey(itemCandidate);
    }
    if (weaponKey != null) {
      return tryWeapon(
          "weapon " + weaponKey,
          "weapon " + weaponKey.toLowerCase(Locale.ROOT),
          locationFilter);
    }

    String shipKey = StarCitizenDataService.resolveDatasetKey("ships", itemCandidate);
    if (shipKey != null) {
      return tryShip(
          "ship " + shipKey, "ship " + shipKey.toLowerCase(Locale.ROOT), locationFilter);
    }

    String miningReply = tryMiningConsumableBuy(itemCandidate);
    if (miningReply != null) {
      return miningReply;
    }

    String armorKey = StarCitizenDataService.resolveDatasetKey("armor", itemCandidate);
    if (armorKey != null) {
      return tryArmor(
          "armor " + armorKey, "armor " + armorKey.toLowerCase(Locale.ROOT), locationFilter);
    }

    String commodityKey = StarCitizenDataService.resolveDatasetKey("commodities", itemCandidate);
    if (commodityKey != null) {
      return tryCommodity(
          "commodity " + commodityKey,
          "commodity " + commodityKey.toLowerCase(Locale.ROOT),
          locationFilter);
    }

    return suggestionReplyForUnknown(
        locationFilter == null ? "buyable item" : "buyable item in " + capitalize(locationFilter),
        itemCandidate,
        StarCitizenDataService.suggestDatasetKeys("components", itemCandidate, 3),
        StarCitizenDataService.suggestDatasetKeys("weapons", itemCandidate, 3),
        StarCitizenDataService.suggestDatasetKeys("ships", itemCandidate, 3),
        StarCitizenDataService.suggestDatasetKeys("armor", itemCandidate, 3),
        StarCitizenDataService.suggestDatasetKeys("commodities", itemCandidate, 3));
  }

  private static String trySellQuestion(String text, String lower) {
    Matcher matcher = SELL_PATTERN.matcher(text);
    if (!matcher.find()) {
      return null;
    }
    String candidate = matcher.group(1).trim();
    if (candidate.isBlank()) {
      return null;
    }

    BuyQueryParts buyQuery = splitBuyQuery(candidate);
    String itemCandidate = normalizeBuyCandidate(buyQuery.item());
    String locationFilter = resolveSystemFilter(buyQuery.location());

    String componentKey = StarCitizenDataService.resolveDatasetKey("components", itemCandidate);
    if (componentKey != null) {
      return formatComponentSale(componentKey, locationFilter);
    }

    String weaponKey = StarCitizenDataService.resolveDatasetKey("weapons", itemCandidate);
    if (weaponKey == null && isOrdnanceQuery(itemCandidate)) {
      weaponKey = resolveOrdnanceWeaponKey(itemCandidate);
    }
    if (weaponKey != null) {
      return tryWeaponSale(
          "weapon " + weaponKey,
          "weapon " + weaponKey.toLowerCase(Locale.ROOT),
          locationFilter);
    }

    String shipKey = StarCitizenDataService.resolveDatasetKey("ships", itemCandidate);
    if (shipKey != null) {
      return tryShipSale(
          "ship " + shipKey, "ship " + shipKey.toLowerCase(Locale.ROOT), locationFilter);
    }

    String armorKey = StarCitizenDataService.resolveDatasetKey("armor", itemCandidate);
    if (armorKey != null) {
      return tryArmorSale(
          "armor " + armorKey, "armor " + armorKey.toLowerCase(Locale.ROOT), locationFilter);
    }

    String commodityKey = StarCitizenDataService.resolveDatasetKey("commodities", itemCandidate);
    if (commodityKey != null) {
      return tryCommoditySale(
          "commodity " + commodityKey,
          "commodity " + commodityKey.toLowerCase(Locale.ROOT),
          locationFilter);
    }

    return suggestionReplyForUnknown(
        locationFilter == null ? "sellable item" : "sellable item in " + capitalize(locationFilter),
        itemCandidate,
        StarCitizenDataService.suggestDatasetKeys("components", itemCandidate, 3),
        StarCitizenDataService.suggestDatasetKeys("weapons", itemCandidate, 3),
        StarCitizenDataService.suggestDatasetKeys("ships", itemCandidate, 3),
        StarCitizenDataService.suggestDatasetKeys("armor", itemCandidate, 3),
        StarCitizenDataService.suggestDatasetKeys("commodities", itemCandidate, 3));
  }

  private static String tryMiningConsumableBuy(String candidate) {
    if (candidate == null || candidate.isBlank()) {
      return null;
    }
    String resolved = MiningService.resolveConsumableName(candidate);
    if (resolved == null
        || resolved.equalsIgnoreCase(candidate.trim())
        && !MiningService.CONSUMABLES.containsKey(resolved.toLowerCase(Locale.ROOT))) {
      return null;
    }
    if (!MiningService.CONSUMABLES.containsKey(resolved.toLowerCase(Locale.ROOT))) {
      return null;
    }

    List<String> locations = MiningService.getConsumableBuyLocations(resolved);
    String effect = MiningService.getConsumableEffect(resolved);

    StringBuilder sb = new StringBuilder();
    sb.append("**Mining Consumable — ").append(capitalize(resolved)).append("**\n");
    if (!effect.isBlank()) {
      sb.append("Effect: ").append(effect).append("\n");
    }
    sb.append("Buy locations: ");
    if (locations.isEmpty()) {
      sb.append("—");
    } else {
      sb.append(String.join(", ", locations));
    }
    return withSnark("component", resolved, sb.toString());
  }

  private static String tryShipCategoryList(String text, String lower) {
    if (!containsAny(lower, "ship", "ships")) {
      return null;
    }
    // Do not treat "ship weapons" style requests as ship-list requests.
    if (containsAny(lower, "weapon", "weapons", "hardpoint", "turret")) {
      return null;
    }
    if (!isPluralBrowseRequest(lower)) {
      return null;
    }

    JsonNode ships = StarCitizenDataService.get("ships");
    if (ships == null || !ships.isObject()) {
      return null;
    }

    Integer size = parseSizeQuery(text);
    boolean hasManufacturerFilter =
        containsAny(
            lower, "origin", "drake", "anvil", "aegis", "rsi", "roberts", "misc", "crusader");
    boolean hasRoleFilter =
        containsAny(
            lower, "cargo", "exploration", "mining", "salvage", "starter", "military", "fighter");
    boolean listAll =
        containsAny(lower, "all ships", "show me all ships", "list ships", "show ships")
            || (!hasManufacturerFilter
            && !hasRoleFilter
            && size == null
            && containsAny(lower, "ship", "ships", "show me", "list"));
    List<String> names = new ArrayList<>();
    ships
        .fields()
        .forEachRemaining(
            entry -> {
              JsonNode info = entry.getValue().path("info");
              String manufacturer = info.path("manufacturer").asText("").toLowerCase(Locale.ROOT);
              String role = info.path("role").asText("").toLowerCase(Locale.ROOT);
              String shipSize = info.path("size").asText("").toLowerCase(Locale.ROOT);

              boolean match =
                  listAll
                      || (lower.contains("origin")
                      ? manufacturer.contains("origin")
                      : lower.contains("drake")
                        ? manufacturer.contains("drake")
                          : lower.contains("anvil")
                            ? manufacturer.contains("anvil")
                              : lower.contains("aegis")
                                ? manufacturer.contains("aegis")
                                  : lower.contains("rsi") || lower.contains("roberts")
                                    ? manufacturer.contains("roberts")
                                      || manufacturer.contains("rsi")
                                      : lower.contains("misc")
                                        ? manufacturer.contains("misc")
                                          : lower.contains("crusader")
                                            ? manufacturer.contains("crusader")
                                              : lower.contains("cargo")
                                                ? role.contains("cargo")
                                                  : lower.contains("exploration")
                                                    ? role.contains("exploration")
                                                      : lower.contains("mining")
                                                        ? role.contains("mining")
                                                          : lower.contains("salvage")
                                                            ? role.contains("salvage")
                                                              : lower.contains("starter")
                                                                ? role.contains("starter")
                                                                  : lower.contains("military")
                                                                      || lower.contains(
                                                                      "fighter")
                                                                    ? role.contains(
                                                                      "military")
                                                                      || role.contains(
                                                                      "fighter")
                                                                      : false);

              if (size != null) {
                String expected =
                    switch (size) {
                      case 1 -> "snub";
                      case 2 -> "small";
                      case 3 -> "medium";
                      case 4 -> "large";
                      default -> "capital";
                    };
                match = match || shipSize.contains(expected);
              }

              if (match) {
                names.add(entry.getKey());
              }
            });

    if (names.isEmpty()) {
      return null;
    }
    names.sort(String.CASE_INSENSITIVE_ORDER);
    return withSnark("ship", null, "**Matching Ships**\n" + bulletList(names, 15));
  }

  private static String tryArmorCategoryList(String text, String lower) {
    if (!containsAny(lower, "armor", "armour")) {
      return null;
    }
    if (!isPluralBrowseRequest(lower)
        && !containsAny(lower, "all armor", "all armour", "armor types", "armour types")) {
      return null;
    }

    JsonNode armor = StarCitizenDataService.get("armor");
    if (armor == null || !armor.isObject()) {
      return null;
    }

    String classFilter =
        lower.contains("light")
            ? "light"
            : lower.contains("medium") ? "medium" : lower.contains("heavy") ? "heavy" : null;

    List<String> names = new ArrayList<>();
    armor
        .fields()
        .forEachRemaining(
            entry -> {
              if (classFilter == null) {
                names.add(entry.getKey());
                return;
              }
              String wc = entry.getValue().path("weight_class").asText("").toLowerCase(Locale.ROOT);
              String cls = entry.getValue().path("class").asText("").toLowerCase(Locale.ROOT);
              if (wc.contains(classFilter) || cls.contains(classFilter)) {
                names.add(entry.getKey());
              }
            });

    if (names.isEmpty()) {
      return null;
    }
    names.sort(String.CASE_INSENSITIVE_ORDER);
    String title =
        classFilter == null
            ? "**Armor Types**\n"
            : "**" + capitalize(classFilter) + " Armor Types**\n";
    return withSnark("armor", null, title + bulletList(names, 20));
  }

  private static String tryWeaponCategoryList(String text, String lower) {
    if (!containsAny(lower, "weapon", "weapons", "fps", "first person")) {
      return null;
    }
    if (!isPluralBrowseRequest(lower) && parseSizeQuery(text) == null) {
      return null;
    }

    JsonNode weapons = StarCitizenDataService.get("weapons");
    if (weapons == null || !weapons.isObject()) {
      return null;
    }

    Integer size = parseSizeQuery(text);
    String domainHint = detectWeaponDomain(lower);
    List<String> names = new ArrayList<>();
    weapons
        .fields()
        .forEachRemaining(
            entry -> {
              JsonNode info = entry.getValue().path("info");
              JsonNode stats = entry.getValue().path("stats");
              String domain = info.path("domain").asText("").toLowerCase(Locale.ROOT);
              String type = info.path("type").asText("").toLowerCase(Locale.ROOT);
              String cls = info.path("class").asText("").toLowerCase(Locale.ROOT);
              String damage = info.path("damage_type").asText("").toLowerCase(Locale.ROOT);
              String hay =
                  (entry.getKey() + " " + type + " " + cls + " " + damage + " " + domain)
                      .toLowerCase(Locale.ROOT);

              if (domainHint != null) {
                boolean fpsMatch = domain.contains("fps") || hay.contains("personal");
                if ("fps".equals(domainHint) && !fpsMatch) {
                  return;
                }
                if ("vehicle".equals(domainHint) && fpsMatch) {
                  return;
                }
              }

              boolean match =
                  lower.contains("laser")
                      ? hay.contains("laser")
                      : lower.contains("ballistic")
                        ? hay.contains("ballistic")
                          : lower.contains("cannon")
                            ? hay.contains("cannon")
                              : lower.contains("gatling")
                                ? hay.contains("gatling")
                                  : lower.contains("distortion")
                                    ? hay.contains("distortion")
                                      : true;

              if (size != null) {
                match = match && info.path("size").asInt(0) == size;
              }

              if (match) {
                String tag = (domain.contains("fps") || hay.contains("personal")) ? " [FPS]" : "";
                names.add(entry.getKey() + tag + sortSuffix(stats.path("dps").asDouble(0), " DPS"));
              }
            });

    if (names.isEmpty()) {
      return null;
    }
    names.sort(String.CASE_INSENSITIVE_ORDER);
    String title =
        "fps".equals(domainHint)
            ? "**Matching FPS Weapons**\n"
            : "vehicle".equals(domainHint)
              ? "**Matching Vehicle Weapons**\n"
                : "**Matching Weapons**\n";
    return withSnark("weapon", null, title + bulletList(names, 20));
  }

  private static String tryMissionCategoryList(String text, String lower) {
    if (!containsAny(lower, "mission", "missions")) {
      return null;
    }
    if (!isPluralBrowseRequest(lower) && !lower.contains(" from ") && !lower.contains(" rep")) {
      return null;
    }

    JsonNode missions = StarCitizenDataService.get("missions");
    if (missions == null || !missions.isObject()) {
      return null;
    }

    String giverFilter = extractFromPhrase(text);
    String typeFilter = detectMissionType(lower);
    boolean repFocus = lower.contains("rep");

    List<String> matches = new ArrayList<>();
    missions
        .fields()
        .forEachRemaining(
            entry -> {
              JsonNode mission = entry.getValue();
              String giver = mission.path("giver").asText("");
              String type = mission.path("type").asText("");

              boolean match = true;
              if (giverFilter != null && !giverFilter.isBlank()) {
                match =
                    giver.toLowerCase(Locale.ROOT).contains(giverFilter.toLowerCase(Locale.ROOT));
              }
              if (match && typeFilter != null) {
                match = type.toLowerCase(Locale.ROOT).contains(typeFilter.toLowerCase(Locale.ROOT));
              }
              if (!match) {
                return;
              }

              String rep = textOrDash(mission, "reputation_required", "rep_required");
              if ("—".equals(rep)) {
                rep =
                    deriveRepRequirement(
                        textOrDash(mission, "tier"),
                        textOrDash(mission, "difficulty"),
                        entry.getKey());
              }
              String line =
                  repFocus
                      ? entry.getKey()
                        + " — Rep: "
                        + rep
                        + " | From: "
                        + textOrDash(mission, "giver")
                      : entry.getKey() + " — " + textOrDash(mission, "giver");
              matches.add(line);
            });

    if (matches.isEmpty()) {
      if (giverFilter != null && !giverFilter.isBlank()) {
        return suggestionReplyForUnknown(
            "mission giver", giverFilter, collectMissionGiverSuggestions(giverFilter));
      }
      return null;
    }

    matches.sort(String.CASE_INSENSITIVE_ORDER);
    String title =
        giverFilter != null && !giverFilter.isBlank()
            ? "**Missions from " + giverFilter + "**\n"
            : typeFilter != null ? "**" + typeFilter + " Missions**\n" : "**Matching Missions**\n";
    return withSnark("mission", null, title + bulletList(matches, 12));
  }

  private static String tryLocationCategoryList(String text, String lower) {
    if (!containsAny(lower, "location", "locations", "station", "stations", "outpost",
        "outposts")) {
      return null;
    }
    if (!isPluralBrowseRequest(lower)) {
      return null;
    }

    JsonNode locations = StarCitizenDataService.get("locations");
    if (locations == null || !locations.isObject()) {
      return null;
    }

    String system =
        lower.contains("pyro")
            ? "pyro"
            : lower.contains("stanton")
              ? "stanton"
                : lower.contains("nyx") || lower.contains("nix")
                    ? "nyx"
                    : lower.contains("terra") ? "terra" : null;
    String typeHint =
        lower.contains("station") ? "station" : lower.contains("outpost") ? "outpost" : null;

    List<String> matches = new ArrayList<>();
    locations
        .fields()
        .forEachRemaining(
            entry -> {
              JsonNode loc = entry.getValue();
              String locSystem = loc.path("system").asText("").toLowerCase(Locale.ROOT);
              String locType = loc.path("type").asText("").toLowerCase(Locale.ROOT);
              if (system != null && !locSystem.contains(system)) {
                return;
              }
              if (typeHint != null && !locType.contains(typeHint)) {
                return;
              }
              matches.add(
                  entry.getKey()
                      + " — "
                      + textOrDash(loc, "body")
                      + " ("
                      + textOrDash(loc, "system")
                      + ")");
            });

    if (matches.isEmpty()) {
      return null;
    }
    matches.sort(String.CASE_INSENSITIVE_ORDER);
    return withSnark("location", null, "**Matching Locations**\n" + bulletList(matches, 15));
  }

  private static String tryTrade(String text, String lower) {
    if (!containsAny(lower, "trade", "trade route", "trade routes")) {
      return null;
    }

    int cargo = parseCargoScu(text);
    if (cargo <= 0) {
      cargo = 96;
    }

    String commodity = findMentionedDatasetKey("commodities", lower);
    List<TradeService.TradeRoute> routes =
        TradeService.topRoutes(cargo, commodity, commodity == null ? 5 : 3);
    if (routes.isEmpty()) {
      return "I couldn't find any trade routes for that setup right now.";
    }
    return withSnark("trade", commodity, TradeService.Formatter.format(routes, cargo));
  }

  private static String tryRefinery(String text, String lower) {
    if (!lower.contains("refinery")) {
      return null;
    }

    if (containsAny(lower, "station", "stations", "where can i refine", "where refine")) {
      return RefineryService.listStations();
    }

    JsonNode refinery = StarCitizenDataService.get("refinery");
    JsonNode oreValues = refinery != null ? refinery.path("ore_base_values") : null;
    String ore =
        oreValues != null && oreValues.isObject() ? findMentionedKeyInNode(oreValues, lower) : null;
    if (ore == null) {
      String extracted = extractAfterKeyword(text, "refinery", "ore", "refine");
      ore = resolveNodeKey(oreValues, extracted);
    }
    if (ore == null) {
      return "Ask me like `refinery Quantainium 32 SCU` or `refinery stations`.";
    }

    int rawScu = parseCargoScu(text);
    if (rawScu <= 0) {
      rawScu = 32;
    }
    RefineryService.RefineJob job = RefineryService.analyze(ore, rawScu);
    return withSnark("refinery", ore, RefineryService.Formatter.full(job));
  }

  private static String trySalvage(String lower) {
    if (!lower.contains("salvage")) {
      return null;
    }
    if (containsAny(lower, "hotspot", "hotspots", "where")) {
      return withSnark(
          "salvage", "hotspots", SalvageService.listHotspots(detectSystemFilter(lower)));
    }
    if (containsAny(lower, "ship", "ships", "vulture", "reclaimer")) {
      return withSnark("salvage", "ships", SalvageService.compareShips());
    }
    if (containsAny(lower, "material", "materials", "rmc", "construction material")) {
      return withSnark("salvage", "materials", SalvageService.listMaterials());
    }
    if (containsAny(lower, "tip", "tips", "help")) {
      return withSnark("salvage", "tips", SalvageService.getTips());
    }
    return "I can give salvage **hotspots**, **ships**, **materials**, or **tips** — just ask for one of those.";
  }

  private static String tryCommodity(String text, String lower) {
    return tryCommodity(text, lower, null);
  }

  private static String tryCommodity(String text, String lower, String locationFilter) {
    String key = findMentionedDatasetKey("commodities", lower);
    String candidate = extractAfterKeyword(text, "commodity", "commodities");
    if (key == null) {
      key = StarCitizenDataService.resolveDatasetKey("commodities", candidate);
    }
    if (key == null) {
      return suggestionReplyForUnknown(
          "commodity",
          candidate,
          StarCitizenDataService.suggestDatasetKeys("commodities", candidate, 5));
    }

    JsonNode entry = StarCitizenDataService.get("commodities").path(key);
    StringBuilder sb = new StringBuilder();
    sb.append("**Commodity — ").append(key).append("**\n");
    sb.append("Best buy: ").append(bestCommodityDisplay(entry, true)).append("\n");
    sb.append("Best sell: ").append(bestCommodityDisplay(entry, false)).append("\n");
    sb.append("Profit/SCU: ")
        .append(fmtNumber(entry.path("profit_per_scu").asDouble(0)))
        .append(" aUEC\n\n");
    String buyList = topCommodityLocations(entry.path("buy"), true);
    String sellList = topCommodityLocations(entry.path("sell"), false);
    if (!buyList.isBlank()) {
      sb.append("**Buy locations**\n").append(buyList).append("\n\n");
    }
    appendLocationAwareBuyInfo(sb, entry.path("buy_locations"), locationFilter, 5);
    if (!sellList.isBlank()) {
      sb.append("**Sell locations**\n").append(sellList);
    }
    return withSnark("commodity", key, sb.toString().trim());
  }

  private static String tryShip(String text, String lower) {
    return tryShip(text, lower, null);
  }

  private static String tryShip(String text, String lower, String locationFilter) {
    if (isStarCitizenOverviewPrompt(lower)) {
      return "Star Citizen is a shared-universe space sim where you can mine, trade, salvage, fight, "
          + "haul cargo, and explore. I can help with ship, weapon, mining, trade, refinery, "
          + "or location questions.";
    }

    String key = resolveShipKeyFromPrompt(text);
    if (key == null && lower.contains("origin") && containsAny(lower, "ship", "ships", "origin")) {
      return tryOriginShipsOverview();
    }
    String candidate = extractAfterKeyword(text, "ship", "ships");
    if (key == null && lower.contains("origin")) {
      return tryOriginShipsOverview();
    }
    if (key == null) {
      return suggestionReplyForUnknown(
          "ship", candidate, StarCitizenDataService.suggestDatasetKeys("ships", candidate, 5));
    }

    JsonNode ship = StarCitizenDataService.getShip(key);
    if (ship == null || ship.isMissingNode()) {
      return null;
    }
    JsonNode info = ship.path("info");
    JsonNode stats = ship.path("stats");
    double[] derivedFirepower = StarCitizenDataService.deriveShipFirepowerFromLoadout(ship);
    double[] effectiveTotals = StarCitizenDataService.deriveShipEffectiveTotalsFromLoadout(ship);

    StringBuilder sb = new StringBuilder();
    sb.append("**Ship — ").append(key).append("**\n");
    sb.append("Manufacturer: ").append(textOrDash(info, "manufacturer")).append("\n");
    sb.append("Role: ").append(textOrDash(info, "role")).append("\n");
    sb.append("Size: ").append(textOrDash(info, "size")).append("\n");
    sb.append("Crew: ").append(intOrDash(info.path("crew"))).append("\n");
    sb.append("Cargo: ")
        .append(info.path("cargo").asInt(0) > 0 ? info.path("cargo").asInt(0) + " SCU" : "—")
        .append("\n");
    sb.append("Buy at: ").append(textOrDash(info, "buy_location")).append("\n");
    sb.append("Buy locations: ")
        .append(formatLocationList(info.path("buy_locations"), 5))
        .append("\n");
    appendLocationAwareBuyInfo(sb, info.path("buy_locations"), locationFilter, 5);
    sb.append("Sell at: ").append(textOrDash(info, "sell_location")).append("\n");
    sb.append("Sell locations: ")
        .append(formatLocationList(info.path("sell_locations"), 5))
        .append("\n");
    sb.append("Price: ")
        .append(
            info.path("price_auec").asDouble(0) > 0
                ? fmtNumber(info.path("price_auec").asDouble(0)) + " aUEC"
                : "—")
        .append("\n");
    sb.append("SCM / Max: ")
        .append(speedPair(info.path("scm_speed").asInt(0), info.path("max_speed").asInt(0), "m/s"))
        .append("\n");
    sb.append("Shield / Hull: ")
        .append(speedPair(info.path("shield_hp").asInt(0), info.path("hull_hp").asInt(0), "HP"))
        .append("\n");
    double pilotDps = Math.max(stats.path("pilot_dps").asDouble(0), derivedFirepower[0]);
    double turretDps = Math.max(stats.path("turret_dps").asDouble(0), derivedFirepower[1]);
    double missileDps = Math.max(stats.path("missile_dps").asDouble(0), derivedFirepower[2]);
    double pilotAlpha = Math.max(stats.path("pilot_alpha").asDouble(0), derivedFirepower[3]);
    double turretAlpha = Math.max(stats.path("turret_alpha").asDouble(0), derivedFirepower[4]);
    double missileAlpha = Math.max(stats.path("missile_alpha").asDouble(0), derivedFirepower[5]);
    double totalDps =
        Math.max(stats.path("total_dps").asDouble(0), pilotDps + turretDps + missileDps);
    double totalAlpha =
        Math.max(stats.path("total_alpha").asDouble(0), pilotAlpha + turretAlpha + missileAlpha);
    sb.append("Pilot / Turret / Missile DPS: ")
        .append(fmtNumber(pilotDps))
        .append(" / ")
        .append(fmtNumber(turretDps))
        .append(" / ")
        .append(fmtNumber(missileDps))
        .append("\n");
    sb.append("Total DPS: ").append(fmtNumber(totalDps)).append("\n");
    sb.append("Pilot / Turret / Missile Alpha: ")
        .append(fmtNumber(pilotAlpha))
        .append(" / ")
        .append(fmtNumber(turretAlpha))
        .append(" / ")
        .append(fmtNumber(missileAlpha))
        .append("\n");
    sb.append("Total Alpha: ").append(fmtNumber(totalAlpha)).append("\n");
    sb.append("Armor Mod (Physical / Energy): ")
        .append(fmtSignedPercent(stats.path("armor_physical_damage_modifier").asDouble(0)))
        .append(" / ")
        .append(fmtSignedPercent(stats.path("armor_energy_damage_modifier").asDouble(0)))
        .append("\n");
    sb.append("Deflection (Physical / Energy): ")
        .append(fmtNumber(stats.path("deflection_physical").asDouble(0)))
        .append(" / ")
        .append(fmtNumber(stats.path("deflection_energy").asDouble(0)))
        .append("\n");
    sb.append("Default Loadout Effective DPS (Phys / Energy): ")
        .append(fmtNumber(effectiveTotals[0]))
        .append(" / ")
        .append(fmtNumber(effectiveTotals[1]))
        .append("\n");
    sb.append("Default Loadout Effective Alpha (Phys / Energy): ")
        .append(fmtNumber(effectiveTotals[2]))
        .append(" / ")
        .append(fmtNumber(effectiveTotals[3]))
        .append("\n");
    sb.append("Handling (Pitch/Yaw/Roll): ")
        .append(
            speedPair(
                (int) stats.path("pitch").asDouble(0), (int) stats.path("yaw").asDouble(0), ""))
        .append(" / ")
        .append(
            stats.path("roll").asDouble(0) > 0 ? fmtNumber(stats.path("roll").asDouble(0)) : "-")
        .append("\n");
    sb.append("Fuel (Hydrogen / Quantum): ")
        .append(
            speedPair(
                (int) stats.path("hydrogen_fuel").asDouble(0),
                (int) stats.path("quantum_fuel").asDouble(0),
                ""))
        .append("\n");
    sb.append("Mass: ")
        .append(
            stats.path("mass").asDouble(0) > 0 ? fmtNumber(stats.path("mass").asDouble(0)) : "-")
        .append("\n");

    // Default loadout — show actual weapon/turret/missile names
    List<String> gunNames = collectLoadoutNames(ship.path("weapons"));
    List<String> turretNames = collectLoadoutNames(ship.path("turrets"));
    List<String> missileNames = collectLoadoutNames(ship.path("missiles"));
    if (!gunNames.isEmpty()) {
      sb.append("Guns: ").append(String.join(", ", gunNames)).append("\n");
    }
    if (!turretNames.isEmpty()) {
      sb.append("Turrets: ").append(String.join(", ", turretNames)).append("\n");
    }
    if (!missileNames.isEmpty()) {
      sb.append("Missiles: ").append(String.join(", ", missileNames)).append("\n");
    }

    String image = textOrDash(info, "image_url");
    if (!"—".equals(image)) {
      sb.append("Image: ").append(image).append("\n");
    }
    if (isSparseShipStatRecord(info, stats)) {
      sb.append(
          "\n_Heads up: this source currently provides full catalog/basic purchase data, but some combat/loadout stats are unavailable in the live feed._");
    }
    return withShipSnark(key, info, sb.toString().trim());
  }

  private static boolean isStarCitizenOverviewPrompt(String lower) {
    if (lower == null || lower.isBlank()) {
      return false;
    }
    return lower.equals("star citizen")
        || lower.contains("what is star citizen")
        || lower.contains("whats star citizen")
        || lower.contains("tell me about star citizen")
        || lower.contains("about star citizen");
  }

  public static String resolveShipKeyFromPrompt(String text) {
    if (text == null || text.isBlank()) {
      return null;
    }
    String lower = text.toLowerCase(Locale.ROOT);

    String key = findMentionedDatasetKey("ships", lower);
    if (key != null) {
      return key;
    }

    String candidate =
        extractAfterKeyword(
            text,
            "ship",
            "ships",
            "show me",
            "tell me about",
            "lookup",
            "look up",
            "stats for",
            "info for");
    key = StarCitizenDataService.resolveDatasetKey("ships", candidate);
    if (key != null) {
      return key;
    }

    key = StarCitizenDataService.resolveShipName(candidate);
    if (key != null) {
      return key;
    }

    key = StarCitizenDataService.resolveDatasetKey("ships", text);
    if (key != null) {
      return key;
    }

    return StarCitizenDataService.resolveShipName(text);
  }

  public static ShipEmbedData getShipEmbedDataForPrompt(String text) {
    String key = resolveShipKeyFromPrompt(text);
    if (key == null) {
      return null;
    }

    JsonNode ship = StarCitizenDataService.getShip(key);
    if (ship == null || ship.isMissingNode()) {
      return null;
    }
    JsonNode info = ship.path("info");
    JsonNode stats = ship.path("stats");
    double[] derivedFirepower = StarCitizenDataService.deriveShipFirepowerFromLoadout(ship);
    double[] effectiveTotals = StarCitizenDataService.deriveShipEffectiveTotalsFromLoadout(ship);

    StringBuilder infoBlock = new StringBuilder();
    infoBlock.append("**Manufacturer:** ").append(textOrDash(info, "manufacturer")).append("\n");
    infoBlock.append("**Role:** ").append(textOrDash(info, "role")).append("\n");
    infoBlock.append("**Size:** ").append(textOrDash(info, "size")).append("\n");
    infoBlock.append("**Crew:** ").append(intOrDash(info.path("crew"))).append("\n");
    infoBlock
        .append("**Cargo:** ")
        .append(info.path("cargo").asInt(0) > 0 ? info.path("cargo").asInt(0) + " SCU" : "-")
        .append("\n");
    infoBlock.append("**Buy At:** ").append(textOrDash(info, "buy_location")).append("\n");
    infoBlock
        .append("**Buy Locations:** ")
        .append(formatLocationList(info.path("buy_locations"), 5))
        .append("\n");
    infoBlock.append("**Sell At:** ").append(textOrDash(info, "sell_location")).append("\n");
    infoBlock
        .append("**Sell Locations:** ")
        .append(formatLocationList(info.path("sell_locations"), 5))
        .append("\n");
    infoBlock
        .append("**Price:** ")
        .append(
            info.path("price_auec").asDouble(0) > 0
                ? fmtNumber(info.path("price_auec").asDouble(0)) + " aUEC"
                : "-");

    StringBuilder statsBlock = new StringBuilder();
    double pilotDps = Math.max(stats.path("pilot_dps").asDouble(0), derivedFirepower[0]);
    double turretDps = Math.max(stats.path("turret_dps").asDouble(0), derivedFirepower[1]);
    double missileDps = Math.max(stats.path("missile_dps").asDouble(0), derivedFirepower[2]);
    double pilotAlpha = Math.max(stats.path("pilot_alpha").asDouble(0), derivedFirepower[3]);
    double turretAlpha = Math.max(stats.path("turret_alpha").asDouble(0), derivedFirepower[4]);
    double missileAlpha = Math.max(stats.path("missile_alpha").asDouble(0), derivedFirepower[5]);
    double totalAlpha =
        Math.max(stats.path("total_alpha").asDouble(0), pilotAlpha + turretAlpha + missileAlpha);
    statsBlock
        .append("**SCM / Max:** ")
        .append(speedPair(info.path("scm_speed").asInt(0), info.path("max_speed").asInt(0), "m/s"))
        .append("\n");
    statsBlock
        .append("**Shield / Hull:** ")
        .append(speedPair(info.path("shield_hp").asInt(0), info.path("hull_hp").asInt(0), "HP"))
        .append("\n");
    statsBlock
        .append("**Pilot / Turret / Missile DPS:** ")
        .append(fmtNumber(pilotDps))
        .append(" / ")
        .append(fmtNumber(turretDps))
        .append(" / ")
        .append(fmtNumber(missileDps))
        .append("\n");
    statsBlock
        .append("**Pilot / Turret / Missile Alpha:** ")
        .append(fmtNumber(pilotAlpha))
        .append(" / ")
        .append(fmtNumber(turretAlpha))
        .append(" / ")
        .append(fmtNumber(missileAlpha))
        .append("\n");
    statsBlock.append("**Total Alpha:** ").append(fmtNumber(totalAlpha)).append("\n");
    statsBlock
        .append("**Armor Mod (Physical / Energy):** ")
        .append(fmtSignedPercent(stats.path("armor_physical_damage_modifier").asDouble(0)))
        .append(" / ")
        .append(fmtSignedPercent(stats.path("armor_energy_damage_modifier").asDouble(0)))
        .append("\n");
    statsBlock
        .append("**Deflection (Physical / Energy):** ")
        .append(fmtNumber(stats.path("deflection_physical").asDouble(0)))
        .append(" / ")
        .append(fmtNumber(stats.path("deflection_energy").asDouble(0)))
        .append("\n");
    statsBlock
        .append("**Default Loadout Effective DPS (Phys / Energy):** ")
        .append(fmtNumber(effectiveTotals[0]))
        .append(" / ")
        .append(fmtNumber(effectiveTotals[1]))
        .append("\n");
    statsBlock
        .append("**Default Loadout Effective Alpha (Phys / Energy):** ")
        .append(fmtNumber(effectiveTotals[2]))
        .append(" / ")
        .append(fmtNumber(effectiveTotals[3]))
        .append("\n");
    statsBlock
        .append("**Pitch / Yaw / Roll:** ")
        .append(fmtNumber(stats.path("pitch").asDouble(0)))
        .append(" / ")
        .append(fmtNumber(stats.path("yaw").asDouble(0)))
        .append(" / ")
        .append(fmtNumber(stats.path("roll").asDouble(0)))
        .append("\n");
    statsBlock
        .append("**Hydrogen / Quantum Fuel:** ")
        .append(fmtNumber(stats.path("hydrogen_fuel").asDouble(0)))
        .append(" / ")
        .append(fmtNumber(stats.path("quantum_fuel").asDouble(0)))
        .append("\n");
    statsBlock
        .append("**Mass:** ")
        .append(
            stats.path("mass").asDouble(0) > 0 ? fmtNumber(stats.path("mass").asDouble(0)) : "-");

    String loadout = "";
    List<String> gunNames = collectLoadoutNames(ship.path("weapons"));
    List<String> turretNames = collectLoadoutNames(ship.path("turrets"));
    List<String> missileNames = collectLoadoutNames(ship.path("missiles"));
    StringBuilder lb = new StringBuilder();
    if (!gunNames.isEmpty()) {
      lb.append("**Guns:** ").append(String.join(", ", gunNames)).append("\n");
    }
    if (!turretNames.isEmpty()) {
      lb.append("**Turrets:** ").append(String.join(", ", turretNames)).append("\n");
    }
    if (!missileNames.isEmpty()) {
      lb.append("**Missiles:** ").append(String.join(", ", missileNames)).append("\n");
    }
    loadout = lb.toString().trim();
    String image = textOrDash(info, "image_url");
    if ("—".equals(image)) {
      image = "";
    }
    return new ShipEmbedData(key, infoBlock.toString(), statsBlock.toString(), loadout, image);
  }

  private static String tryWeapon(String text, String lower) {
    return tryWeapon(text, lower, null);
  }

  private static String tryWeapon(String text, String lower, String locationFilter) {
    String key = findMentionedDatasetKey("weapons", lower);
    String candidate = extractAfterKeyword(text, "weapon", "weapons");
    if (key == null) {
      key = StarCitizenDataService.resolveDatasetKey("weapons", candidate);
    }
    if (key == null) {
      key = StarCitizenDataService.resolveDatasetKey("weapons", text);
    }
    if (key == null) {
      return suggestionReplyForUnknown(
          "weapon", candidate, StarCitizenDataService.suggestDatasetKeys("weapons", candidate, 5));
    }

    JsonNode weapon = StarCitizenDataService.getWeapon(key);
    if (weapon == null || weapon.isMissingNode()) {
      return null;
    }
    JsonNode info = weapon.path("info");
    JsonNode stats = weapon.path("stats");

    StringBuilder sb = new StringBuilder();
    sb.append("**Weapon — ").append(key).append("**\n");
    sb.append("Manufacturer: ").append(textOrDash(info, "manufacturer")).append("\n");
    sb.append("Type/Class: ")
        .append(textOrDash(info, "type"))
        .append(" / ")
        .append(textOrDash(info, "class"))
        .append("\n");
    sb.append("Domain: ")
        .append("fps".equalsIgnoreCase(textOrDash(info, "domain")) ? "FPS" : "Vehicle")
        .append("\n");
    sb.append("Size: ")
        .append(info.path("size").asInt(0) > 0 ? "S" + info.path("size").asInt(0) : "—")
        .append("\n");
    sb.append("Damage type: ").append(textOrDash(info, "damage_type")).append("\n");
    sb.append("Hardpoint: ").append(textOrDash(info, "hardpoint")).append("\n");
    sb.append("Buy at: ").append(textOrDash(info, "buy_location")).append("\n");
    sb.append("Buy locations: ")
        .append(formatLocationList(info.path("buy_locations"), 4))
        .append("\n");
    if (locationFilter != null && !locationFilter.isBlank()) {
      List<String> filteredBuyLocations = filterLocationsForSystem(info.path("buy_locations"), locationFilter);
      sb.append("Buy in ")
          .append(capitalize(locationFilter))
          .append(": ")
          .append(filteredBuyLocations.isEmpty() ? "none found" : String.join(", ", filteredBuyLocations))
          .append("\n");
      if (filteredBuyLocations.isEmpty()) {
        sb.append("Closest listed buy locations: ")
            .append(formatLocationList(info.path("buy_locations"), 4))
            .append("\n");
      }
    }
    sb.append("Sell at: ").append(textOrDash(info, "sell_location")).append("\n");
    sb.append("Sell locations: ")
        .append(formatLocationList(info.path("sell_locations"), 4))
        .append("\n");
    sb.append("DPS / Alpha: ")
        .append(
            speedPair(
                (int) stats.path("dps").asDouble(0),
                (int) stats.path("alpha_damage").asDouble(0),
                ""))
        .append("\n");
    sb.append("HP: ").append(numberOrDash(stats.path("hp"))).append("\n");
    sb.append("RPM / Range: ")
        .append(speedPair(stats.path("rpm").asInt(0), stats.path("range").asInt(0), ""))
        .append("\n");
    sb.append("Projectile speed: ")
        .append(
            stats.path("projectile_speed").asInt(0) > 0
                ? stats.path("projectile_speed").asInt(0) + " m/s"
                : "—")
        .append("\n");
    sb.append("Cost / Sell: ")
        .append(
            speedPair(
                (int) stats.path("cost_auec").asDouble(0),
                (int) stats.path("sell_price").asDouble(0),
                "aUEC"));
    return withSnark("weapon", key, sb.toString().trim());
  }

  private static String tryComponentCategoryOrLookup(String text, String lower, String category) {
    String key = tryFindSpecificComponent(text, lower);
    if (key != null) {
      return formatComponent(key);
    }

    Integer size = parseSizeQuery(text);
    boolean best = lower.contains("best") || lower.contains("top");
    List<String> matches = componentNamesForCategory(category, size, best);
    if (matches.isEmpty()) {
      return null;
    }
    String title =
        "**Components — "
            + prettyCategory(category)
            + (size != null ? " (Size " + size + ")" : "")
            + "**\n";
    return withSnark("component_category", category, title + bulletList(matches, 15));
  }

  private static String tryComponent(String text, String lower) {
    String key = tryFindSpecificComponent(text, lower);
    if (key == null) {
      String candidate =
          extractAfterKeyword(
              text,
              "component",
              "components",
              "power plant",
              "quantum drive",
              "cooler",
              "shield generator",
              "engine",
              "thruster");
      return suggestionReplyForUnknown(
          "component",
          candidate,
          StarCitizenDataService.suggestDatasetKeys("components", candidate, 5));
    }
    return formatComponent(key);
  }

  private static String tryArmor(String text, String lower) {
    return tryArmor(text, lower, null);
  }

  private static String tryArmor(String text, String lower, String locationFilter) {
    String key = findMentionedDatasetKey("armor", lower);
    String candidate = extractAfterKeyword(text, "armor", "armour");
    if (key == null) {
      key = StarCitizenDataService.resolveDatasetKey("armor", candidate);
    }
    if (key == null) {
      return suggestionReplyForUnknown(
          "armor", candidate, StarCitizenDataService.suggestDatasetKeys("armor", candidate, 5));
    }

    JsonNode armor = StarCitizenDataService.get("armor").path(key);
    if (armor == null || armor.isMissingNode()) {
      return null;
    }

    StringBuilder sb = new StringBuilder();
    sb.append("**Armor — ").append(key).append("**\n");
    sb.append("Class / Weight: ")
        .append(textOrDash(armor, "class"))
        .append(" / ")
        .append(textOrDash(armor, "weight_class"))
        .append("\n");
    sb.append("Manufacturer: ").append(textOrDash(armor, "manufacturer")).append("\n");
    sb.append("Pieces: ").append(textOrDash(armor, "pieces")).append("\n");
    sb.append("Ballistic / Energy / Distortion: ")
        .append(percentOrDash(armor.path("ballistic_resist_pct")))
        .append(" / ")
        .append(percentOrDash(armor.path("energy_resist_pct")))
        .append(" / ")
        .append(percentOrDash(armor.path("distortion_resist_pct")))
        .append("\n");
    sb.append("Temp resist: ").append(textOrDash(armor, "temp_resist")).append("\n");
    sb.append("Buy locations: ").append(textOrDash(armor, "buy_locations")).append("\n\n");
    sb.append(textOrDash(armor, "description"));
    return withSnark("armor", key, sb.toString().trim());
  }

  private static String tryLocation(String text, String lower) {
    String key = findMentionedDatasetKey("locations", lower);
    String candidate = extractAfterKeyword(text, "location", "locations", "where is", "where's");
    if (key == null) {
      key = StarCitizenDataService.resolveDatasetKey("locations", candidate);
    }
    if (key == null) {
      return suggestionReplyForUnknown(
          "location",
          candidate,
          StarCitizenDataService.suggestDatasetKeys("locations", candidate, 5));
    }

    JsonNode loc = StarCitizenDataService.get("locations").path(key);
    if (loc == null || loc.isMissingNode()) {
      return null;
    }

    StringBuilder sb = new StringBuilder();
    sb.append("**Location — ").append(key).append("**\n");
    sb.append("System: ").append(textOrDash(loc, "system")).append("\n");
    sb.append("Body: ").append(textOrDash(loc, "body")).append("\n");
    sb.append("Type: ").append(textOrDash(loc, "type")).append("\n");
    appendOptionalLine(sb, loc, "services", "Services");
    appendOptionalLine(sb, loc, "notes", "Notes");
    return withSnark("location", key, sb.toString().trim());
  }

  private static String tryMission(String text, String lower) {
    String key = findMentionedDatasetKey("missions", lower);
    String candidate = extractAfterKeyword(text, "mission", "missions");
    if (key == null) {
      key = StarCitizenDataService.resolveDatasetKey("missions", candidate);
    }
    if (key == null) {
      return suggestionReplyForUnknown(
          "mission",
          candidate,
          StarCitizenDataService.suggestDatasetKeys("missions", candidate, 5));
    }

    JsonNode mission = StarCitizenDataService.get("missions").path(key);
    if (mission == null || mission.isMissingNode()) {
      return null;
    }

    String type = textOrDash(mission, "type");
    String giver = textOrDash(mission, "giver", "issuer");
    String location = textOrDash(mission, "location", "where_to_find");
    String repWith = textOrDash(mission, "reputation_with", "rep_with");
    if ("—".equals(repWith)) {
      repWith = deriveRepFaction(type, giver, key);
    }
    String repRequired = textOrDash(mission, "reputation_required", "rep_required");
    if ("—".equals(repRequired)) {
      repRequired =
          deriveRepRequirement(textOrDash(mission, "tier"), textOrDash(mission, "difficulty"), key);
    }

    StringBuilder sb = new StringBuilder();
    sb.append("**Mission — ").append(key).append("**\n");
    sb.append("Type / Tier: ")
        .append(type)
        .append(" / ")
        .append(textOrDash(mission, "tier"))
        .append("\n");
    sb.append("Category / Difficulty: ")
        .append(textOrDash(mission, "category"))
        .append(" / ")
        .append(textOrDash(mission, "difficulty"))
        .append("\n");
    sb.append("From: ").append(giver).append("\n");
    sb.append("Where to find: ").append(location).append("\n");
    sb.append("Reputation with: ").append(repWith).append("\n");
    sb.append("Rep required: ").append(repRequired).append("\n");
    sb.append("Ship required: ").append(textOrDash(mission, "ship_required")).append("\n");
    double min = mission.path("payout_min_auec").asDouble(0);
    double max = mission.path("payout_max_auec").asDouble(0);
    sb.append("Payout: ")
        .append(min > 0 || max > 0 ? fmtNumber(min) + " - " + fmtNumber(max) + " aUEC" : "—")
        .append("\n\n");
    sb.append(textOrDash(mission, "description"))
        .append("\n\nTips: ")
        .append(textOrDash(mission, "tips"));
    return withSnark("mission", key, sb.toString().trim());
  }

  private static String tryFindSpecificComponent(String text, String lower) {
    String key = findMentionedDatasetKey("components", lower);
    if (key != null) {
      return key;
    }
    key =
        StarCitizenDataService.resolveDatasetKey(
            "components",
            extractAfterKeyword(
                text,
                "component",
                "components",
                "power plant",
                "quantum drive",
                "cooler",
                "shield generator",
                "engine",
                "thruster"));
    if (key != null) {
      return key;
    }
    return null;
  }

  private static String formatComponent(String key) {
    return formatComponent(key, null);
  }

  private static String formatComponent(String key, String locationFilter) {
    JsonNode component = StarCitizenDataService.get("components").path(key);
    if (component == null || component.isMissingNode()) {
      return null;
    }
    JsonNode info = component.path("info");
    JsonNode stats = component.path("stats");

    StringBuilder sb = new StringBuilder();
    sb.append("**Component — ").append(key).append("**\n");
    sb.append("Manufacturer: ").append(textOrDash(info, "manufacturer")).append("\n");
    sb.append("Type / Class: ")
        .append(textOrDash(info, "type"))
        .append(" / ")
        .append(textOrDash(info, "class"))
        .append("\n");
    sb.append("Size / Grade: ")
        .append(info.path("size").asInt(0) > 0 ? "S" + info.path("size").asInt(0) : "—")
        .append(" / ")
        .append(textOrDash(info, "grade"))
        .append("\n");
    sb.append("Buy at: ").append(textOrDash(info, "buy_location")).append("\n");
    sb.append("Buy locations: ")
        .append(formatLocationList(info.path("buy_locations"), 5))
        .append("\n");
    appendLocationAwareBuyInfo(sb, info.path("buy_locations"), locationFilter, 5);
    sb.append("Sell at: ").append(textOrDash(info, "sell_location")).append("\n");
    sb.append("Sell locations: ")
        .append(formatLocationList(info.path("sell_locations"), 5))
        .append("\n");
    sb.append("Power output: ").append(numberOrDash(stats.path("power_output"))).append("\n");
    sb.append("Power draw: ").append(numberOrDash(stats.path("power_draw"))).append("\n");
    sb.append("Cooling rate: ").append(numberOrDash(stats.path("cooling_rate"))).append("\n");
    sb.append("Shield HP: ")
        .append(
            stats.path("shield_hp").asDouble(0) > 0
                ? fmtNumber(stats.path("shield_hp").asDouble(0)) + " HP"
                : "—")
        .append("\n");
    sb.append("HP: ").append(numberOrDash(stats.path("hp"))).append("\n");
    sb.append("QT speed / range: ")
        .append(numberOrDash(stats.path("quantum_speed")))
        .append(" / ")
        .append(
            stats.path("quantum_range").asDouble(0) > 0
                ? fmtNumber(stats.path("quantum_range").asDouble(0)) + " AU"
                : "—")
        .append("\n");
    sb.append("Shield regen max: ")
        .append(numberOrDash(stats.path("shield_regen_max")))
        .append("\n");
    sb.append("Shield regen full: ")
        .append(
            stats.path("shield_regen_time_full").asDouble(0) > 0
                ? fmtNumber(stats.path("shield_regen_time_full").asDouble(0)) + " s"
                : "—")
        .append("\n");
    sb.append("Physical resistance (min/max): ")
        .append(minMaxPctOrDash(stats, "physical_resistance_min", "physical_resistance_max"))
        .append("\n");
    sb.append("Energy resistance (min/max): ")
        .append(minMaxPctOrDash(stats, "energy_resistance_min", "energy_resistance_max"))
        .append("\n");
    sb.append("Distortion resistance (min/max): ")
        .append(minMaxPctOrDash(stats, "distortion_resistance_min", "distortion_resistance_max"))
        .append("\n");
    sb.append("Physical absorption (min/max): ")
        .append(minMaxPctOrDash(stats, "physical_absorption_min", "physical_absorption_max"))
        .append("\n");
    sb.append("Energy absorption (min/max): ")
        .append(minMaxPctOrDash(stats, "energy_absorption_min", "energy_absorption_max"))
        .append("\n");
    sb.append("Distortion absorption (min/max): ")
        .append(minMaxPctOrDash(stats, "distortion_absorption_min", "distortion_absorption_max"))
        .append("\n");
    sb.append("EM max: ").append(numberOrDash(stats.path("em_max"))).append("\n");
    sb.append("Distortion shutdown / decay: ")
        .append(numberOrDash(stats.path("distortion_shutdown_dmg")))
        .append(" / ")
        .append(numberOrDash(stats.path("distortion_decay_rate")))
        .append("\n");
    sb.append("Cost / Sell: ")
        .append(
            speedPair(
                (int) stats.path("cost_auec").asDouble(0),
                (int) stats.path("sell_price").asDouble(0),
                "aUEC"));
    return withSnark("component", key, sb.toString().trim());
  }

  private static String tryShipWeaponsQuery(String text, String lower) {
    if (!containsAny(
        lower,
        "ship weapon",
        "ship weapons",
        "ship loadout",
        "loadout",
        "hardpoints",
        "hardpoint")) {
      return null;
    }

    String key = resolveShipKeyFromPrompt(text);
    if (key == null) {
      String candidate =
          extractAfterKeyword(
              text,
              "ship weapons",
              "ship weapon",
              "ship loadout",
              "loadout",
              "hardpoints",
              "hardpoint");
      return suggestionReplyForUnknown(
          "ship", candidate, StarCitizenDataService.suggestDatasetKeys("ships", candidate, 5));
    }

    JsonNode ship = StarCitizenDataService.getShip(key);
    if (ship == null || ship.isMissingNode()) {
      return null;
    }

    java.util.Map<String, List<String>> categories =
        categorizeShipWeaponNames(ship.path("weapons"));
    String lasers = summarizeList(categories.get("lasers"), 10);
    String ballistics = summarizeList(categories.get("ballistics"), 10);
    String cannons = summarizeList(categories.get("cannons"), 10);
    String missiles = summarizeArray(ship.path("missiles"), 10);
    String missileRacks = summarizeArray(ship.path("missile_racks"), 10);
    String turrets = summarizeArray(ship.path("turrets"), 10);
    StringBuilder sb = new StringBuilder();
    sb.append("**Ship Loadout — ").append(key).append("**\n");
    sb.append("Lasers: ").append(lasers).append("\n");
    sb.append("Ballistics: ").append(ballistics).append("\n");
    sb.append("Cannons: ").append(cannons).append("\n");
    sb.append("Missiles: ").append(missiles).append("\n");
    sb.append("Missile Racks: ").append(missileRacks).append("\n");
    sb.append("Turrets: ").append(turrets);
    return withSnark("ship", key, sb.toString());
  }

  private static java.util.Map<String, List<String>> categorizeShipWeaponNames(JsonNode weapons) {
    java.util.Map<String, List<String>> out = new java.util.HashMap<>();
    out.put("lasers", new ArrayList<>());
    out.put("ballistics", new ArrayList<>());
    out.put("cannons", new ArrayList<>());
    if (weapons == null || !weapons.isArray()) {
      return out;
    }

    for (JsonNode weaponNode : weapons) {
      String name =
          weaponNode.isTextual() ? weaponNode.asText("") : weaponNode.path("name").asText("");
      if (name.isBlank()) {
        name = weaponNode.path("item_name").asText("");
      }
      if (name.isBlank()) {
        continue;
      }

      JsonNode weapon = StarCitizenDataService.getWeapon(name);
      String type =
          weapon == null
              ? ""
              : weapon.path("info").path("type").asText("").toLowerCase(Locale.ROOT);
      String cls =
          weapon == null
              ? ""
              : weapon.path("info").path("class").asText("").toLowerCase(Locale.ROOT);
      String damage =
          weapon == null
              ? ""
              : weapon.path("info").path("damage_type").asText("").toLowerCase(Locale.ROOT);
      String hay = (name + " " + type + " " + cls + " " + damage).toLowerCase(Locale.ROOT);

      if (hay.contains("cannon")) {
        out.get("cannons").add(name);
      } else if (hay.contains("ballistic") || hay.contains("gatling")) {
        out.get("ballistics").add(name);
      } else if (hay.contains("laser") || hay.contains("repeater")) {
        out.get("lasers").add(name);
      } else {
        out.get("lasers").add(name);
      }
    }
    return out;
  }

  private static String summarizeList(List<String> items, int limit) {
    if (items == null || items.isEmpty()) {
      return "—";
    }
    if (items.size() <= limit) {
      return String.join(", ", items);
    }
    return String.join(", ", items.subList(0, limit))
        + " (and "
        + (items.size() - limit)
        + " more)";
  }

  private static String summarizeArray(JsonNode arr, int limit) {
    if (arr == null || !arr.isArray() || arr.isEmpty()) {
      return "—";
    }
    List<String> items = new ArrayList<>();
    for (JsonNode n : arr) {
      String value = n.isTextual() ? n.asText("") : n.path("name").asText("");
      if (value.isBlank()) {
        value = n.path("item_name").asText("");
      }
      if (value.isBlank()) {
        value = n.toString();
      }
      if (!value.isBlank()) {
        items.add(value.trim());
      }
    }
    if (items.isEmpty()) {
      return "—";
    }
    if (items.size() <= limit) {
      return String.join(", ", items);
    }
    return String.join(", ", items.subList(0, limit))
        + " (and "
        + (items.size() - limit)
        + " more)";
  }

  private static String detectComponentCategory(String lower) {
    if (containsAny(lower, "shield generator", "shield generators", "shields")) {
      return "shields";
    }
    if (containsAny(lower, "power plant", "power plants")) {
      return "power";
    }
    if (containsAny(lower, "cooler", "coolers", "cooling")) {
      return "coolers";
    }
    if (containsAny(lower, "quantum drive", "quantum drives", "qdrive", "q drive", " qt ")
        || lower.equals("qt")
        || lower.startsWith("qt ")
        || lower.endsWith(" qt")) {
      return "quantum";
    }
    if (containsAny(lower, "engine", "engines", "thruster", "thrusters")) {
      return "engines";
    }
    if (containsAny(lower, "missile", "missiles", "torpedo", "torpedoes")) {
      return "missiles";
    }
    if (containsAny(lower, "component", "components")) {
      return "all";
    }
    return null;
  }

  private static String detectWeaponDomain(String lower) {
    if (containsAny(
        lower,
        "fps",
        "first person",
        "personal weapon",
        "personal weapons",
        "rifle",
        "smg",
        "shotgun",
        "sniper",
        "pistol")) {
      return "fps";
    }
    if (containsAny(
        lower, "ship weapon", "vehicle weapon", "turret", "missile", "torpedo", "hardpoint")) {
      return "vehicle";
    }
    return null;
  }

  private static List<String> componentNamesForCategory(
      String category, Integer sizeFilter, boolean bestFirst) {
    JsonNode components = StarCitizenDataService.get("components");
    List<ComponentScore> names = new ArrayList<>();
    if (components == null || !components.isObject()) {
      return new ArrayList<>();
    }
    components
        .fields()
        .forEachRemaining(
            entry -> {
              JsonNode info = entry.getValue().path("info");
              JsonNode stats = entry.getValue().path("stats");
              String hay =
                  (entry.getKey()
                      + " "
                      + info.path("type").asText("")
                      + " "
                      + info.path("class").asText(""))
                      .toLowerCase(Locale.ROOT);
              if (!matchesComponentCategory(category, hay)) {
                return;
              }
              if (sizeFilter != null && info.path("size").asInt(0) != sizeFilter) {
                return;
              }

              double score =
                  switch (category) {
                    case "quantum" -> Math.max(
                        stats.path("quantum_range").asDouble(0),
                        stats.path("quantum_speed").asDouble(0));
                    case "shields" -> stats.path("shield_hp").asDouble(0);
                    case "power" -> stats.path("power_output").asDouble(0);
                    case "coolers" -> stats.path("cooling_rate").asDouble(0);
                    default -> 0;
                  };
              names.add(new ComponentScore(entry.getKey(), score));
            });
    names.sort(
        (a, b) -> {
          if (bestFirst) {
            int cmp = Double.compare(b.score, a.score);
            if (cmp != 0) {
              return cmp;
            }
          }
          return String.CASE_INSENSITIVE_ORDER.compare(a.name, b.name);
        });

    List<String> out = new ArrayList<>();
    for (ComponentScore c : names) {
      out.add(
          bestFirst && c.score > 0
              ? c.name + sortSuffix(c.score, category.equals("quantum") ? " score" : "")
              : c.name);
    }
    return out;
  }

  private static Integer parseSizeQuery(String text) {
    if (text == null || text.isBlank()) {
      return null;
    }
    Matcher matcher = SIZE_PATTERN.matcher(text);
    if (!matcher.find()) {
      return null;
    }
    try {
      return Integer.parseInt(matcher.group(1));
    } catch (NumberFormatException e) {
      return null;
    }
  }

  private static boolean isPluralBrowseRequest(String lower) {
    return containsAny(
        lower,
        "ships",
        "weapons",
        "missions",
        "locations",
        "components",
        "list",
        "show me",
        "tell me about",
        "what are",
        "best ",
        "top ");
  }

  private static String extractFromPhrase(String text) {
    if (text == null || text.isBlank()) {
      return null;
    }
    Matcher matcher = FROM_PATTERN.matcher(text);
    if (!matcher.find()) {
      return null;
    }
    String value = matcher.group(1).trim();
    return value.isBlank() ? null : value;
  }

  private static String detectMissionType(String lower) {
    if (lower.contains("bounty")) {
      return "Bounty";
    }
    if (lower.contains("delivery")) {
      return "Delivery";
    }
    if (lower.contains("cargo")) {
      return "Cargo";
    }
    if (lower.contains("mining")) {
      return "Mining";
    }
    if (lower.contains("ground combat")
        || lower.contains("ugf")
        || lower.contains("outpost assault")) {
      return "Ground Combat";
    }
    if (lower.contains("escort")) {
      return "Escort";
    }
    if (lower.contains("patrol")) {
      return "Patrol";
    }
    if (lower.contains("recon") || lower.contains("scan")) {
      return "Recon";
    }
    if (lower.contains("salvage")) {
      return "Salvage";
    }
    return null;
  }

  private static String detectSystemFilter(String lower) {
    if (lower == null || lower.isBlank()) {
      return null;
    }
    if (lower.contains("pyro")) {
      return "Pyro";
    }
    if (lower.contains("stanton")) {
      return "Stanton";
    }
    if (lower.contains("nyx") || lower.contains("nix")) {
      return "Nyx";
    }
    return null;
  }

  private static BuyQueryParts splitBuyQuery(String candidate) {
    if (candidate == null) {
      return new BuyQueryParts("", null);
    }
    String trimmed = candidate.trim();
    Matcher matcher = Pattern.compile("(?i)^(.*?)(?:\\s+(?:in|at|on|from|within|inside)\\s+)(.+)$")
        .matcher(trimmed);
    if (!matcher.matches()) {
      return new BuyQueryParts(trimmed, null);
    }
    String item = matcher.group(1).trim();
    String location = matcher.group(2).trim();
    return new BuyQueryParts(item.isBlank() ? trimmed : item, location.isBlank() ? null : location);
  }

  private static String normalizeBuyCandidate(String candidate) {
    if (candidate == null) {
      return "";
    }
    String cleaned = candidate.trim();
    cleaned = cleaned.replaceFirst("(?i)^size\\s*\\d+\\s+", "");
    cleaned = cleaned.replaceFirst("(?i)^s\\s*\\d+\\s+", "");
    return cleaned.trim();
  }

  private static String resolveSystemFilter(String locationText) {
    if (locationText == null || locationText.isBlank()) {
      return null;
    }
    String lower = locationText.toLowerCase(Locale.ROOT);
    if (lower.contains("pyro")) {
      return "pyro";
    }
    if (lower.contains("stanton")) {
      return "stanton";
    }
    if (lower.contains("nyx") || lower.contains("nix")) {
      return "nyx";
    }
    return null;
  }

  private static void appendLocationAwareBuyInfo(
      StringBuilder sb, JsonNode buyLocationsNode, String locationFilter, int limit) {
    if (sb == null || buyLocationsNode == null || locationFilter == null || locationFilter.isBlank()) {
      return;
    }

    List<String> filteredBuyLocations = filterLocationsForSystem(buyLocationsNode, locationFilter);
    sb.append("Buy in ")
        .append(capitalize(locationFilter))
        .append(": ")
        .append(filteredBuyLocations.isEmpty() ? "none found" : String.join(", ", filteredBuyLocations))
        .append("\n");
    if (filteredBuyLocations.isEmpty()) {
      sb.append("Closest listed buy locations: ")
          .append(formatLocationList(buyLocationsNode, limit))
          .append("\n");
    }
  }

  private static void appendLocationAwareSellInfo(
      StringBuilder sb, JsonNode sellLocationsNode, String locationFilter, int limit) {
    if (sb == null || sellLocationsNode == null || locationFilter == null || locationFilter.isBlank()) {
      return;
    }

    List<String> filteredSellLocations = filterLocationsForSystem(sellLocationsNode, locationFilter);
    sb.append("Sell in ")
        .append(capitalize(locationFilter))
        .append(": ")
        .append(filteredSellLocations.isEmpty() ? "none found" : String.join(", ", filteredSellLocations))
        .append("\n");
    if (filteredSellLocations.isEmpty()) {
      sb.append("Closest listed sell locations: ")
          .append(formatLocationList(sellLocationsNode, limit))
          .append("\n");
    }
  }

  private static String formatComponentSale(String key, String locationFilter) {
    String base = formatComponent(key, locationFilter);
    if (base == null) {
      return null;
    }
    JsonNode component = StarCitizenDataService.get("components").path(key);
    StringBuilder sb = new StringBuilder(base);
    appendLocationAwareSellInfo(sb, component.path("sell_locations"), locationFilter, 5);
    return withSnark("component", key, sb.toString().trim());
  }

  private static String tryWeaponSale(String text, String lower, String locationFilter) {
    String base = tryWeapon(text, lower, locationFilter);
    if (base == null) {
      return null;
    }
    String key = resolveShipKeyFromPrompt(text);
    if (key == null) {
      key = findMentionedDatasetKey("weapons", lower);
    }
    if (key == null) {
      return base;
    }
    JsonNode weapon = StarCitizenDataService.getWeapon(key);
    if (weapon == null || weapon.isMissingNode()) {
      return base;
    }
    StringBuilder sb = new StringBuilder(base);
    appendLocationAwareSellInfo(sb, weapon.path("info").path("sell_locations"), locationFilter, 4);
    return sb.toString().trim();
  }

  private static String tryShipSale(String text, String lower, String locationFilter) {
    String base = tryShip(text, lower, locationFilter);
    if (base == null) {
      return null;
    }
    String key = resolveShipKeyFromPrompt(text);
    if (key == null) {
      return base;
    }
    JsonNode ship = StarCitizenDataService.getShip(key);
    if (ship == null || ship.isMissingNode()) {
      return base;
    }
    StringBuilder sb = new StringBuilder(base);
    appendLocationAwareSellInfo(sb, ship.path("info").path("sell_locations"), locationFilter, 5);
    return sb.toString().trim();
  }

  private static String tryArmorSale(String text, String lower, String locationFilter) {
    String base = tryArmor(text, lower, locationFilter);
    if (base == null) {
      return null;
    }
    String key = findMentionedDatasetKey("armor", lower);
    if (key == null) {
      key = StarCitizenDataService.resolveDatasetKey("armor", extractAfterKeyword(text, "armor", "armour"));
    }
    if (key == null) {
      return base;
    }
    JsonNode armor = StarCitizenDataService.get("armor").path(key);
    if (armor == null || armor.isMissingNode()) {
      return base;
    }
    StringBuilder sb = new StringBuilder(base);
    appendLocationAwareSellInfo(sb, armor.path("sell_locations"), locationFilter, 5);
    return sb.toString().trim();
  }

  private static String tryCommoditySale(String text, String lower, String locationFilter) {
    String base = tryCommodity(text, lower, locationFilter);
    if (base == null) {
      return null;
    }
    String key = findMentionedDatasetKey("commodities", lower);
    if (key == null) {
      key = StarCitizenDataService.resolveDatasetKey("commodities", extractAfterKeyword(text, "commodity", "commodities"));
    }
    if (key == null) {
      return base;
    }
    JsonNode entry = StarCitizenDataService.get("commodities").path(key);
    if (entry == null || entry.isMissingNode()) {
      return base;
    }
    StringBuilder sb = new StringBuilder(base);
    appendLocationAwareSellInfo(sb, entry.path("sell_locations"), locationFilter, 5);
    return sb.toString().trim();
  }

  private static boolean isOrdnanceQuery(String candidate) {
    String lower = candidate == null ? "" : candidate.toLowerCase(Locale.ROOT);
    return containsAny(lower, "missile", "missiles", "torpedo", "torpedoes", "bomb", "bombs", "rocket", "rockets");
  }

  private static String toRomanNumeral(int value) {
    if (value <= 0) {
      return "";
    }
    return switch (value) {
      case 1 -> "I";
      case 2 -> "II";
      case 3 -> "III";
      case 4 -> "IV";
      case 5 -> "V";
      case 6 -> "VI";
      case 7 -> "VII";
      case 8 -> "VIII";
      case 9 -> "IX";
      case 10 -> "X";
      case 11 -> "XI";
      case 12 -> "XII";
      default -> String.valueOf(value);
    };
  }

  private static String resolveOrdnanceWeaponKey(String candidate) {
    JsonNode weapons = StarCitizenDataService.get("weapons");
    if (weapons == null || !weapons.isObject() || candidate == null || candidate.isBlank()) {
      return null;
    }

    String lower = candidate.toLowerCase(Locale.ROOT);
    Integer size = parseSizeQuery(candidate);
    String stripped = normalizeBuyCandidate(candidate).toLowerCase(Locale.ROOT);
    String roman = size == null ? "" : toRomanNumeral(size);

    String best = null;
    int bestScore = -1;
    for (Iterator<String> it = weapons.fieldNames(); it.hasNext(); ) {
      String key = it.next();
      JsonNode weapon = weapons.path(key);
      JsonNode info = weapon.path("info");
      String type = info.path("type").asText("").toLowerCase(Locale.ROOT);
      String cls = info.path("class").asText("").toLowerCase(Locale.ROOT);
      String domain = info.path("domain").asText("").toLowerCase(Locale.ROOT);
      String hay = (key + " " + type + " " + cls + " " + domain).toLowerCase(Locale.ROOT);
      if (!isOrdnanceQuery(hay)) {
        continue;
      }

      int score = 0;
      if (hay.contains("torpedo") && lower.contains("torpedo")) {
        score += 40;
      }
      if (hay.contains("missile") && lower.contains("missile")) {
        score += 35;
      }
      if (hay.contains("bomb") && lower.contains("bomb")) {
        score += 30;
      }
      if (!stripped.isBlank() && hay.contains(stripped)) {
        score += 20;
      }
      if (size != null) {
        String lowerKey = key.toLowerCase(Locale.ROOT);
        if ((!roman.isBlank() && lowerKey.contains(" " + roman.toLowerCase(Locale.ROOT) + " "))
            || lowerKey.endsWith(" " + roman.toLowerCase(Locale.ROOT))
            || lowerKey.contains("size " + size)
            || lowerKey.contains(String.valueOf(size))) {
          score += 15;
        }
      }

      if (score > bestScore) {
        best = key;
        bestScore = score;
      }
    }

    return bestScore >= 20 ? best : null;
  }

  private static List<String> filterLocationsForSystem(JsonNode locationsNode, String systemFilter) {
    List<String> out = new ArrayList<>();
    if (locationsNode == null || locationsNode.isMissingNode() || locationsNode.isNull()) {
      return out;
    }
    String target = systemFilter == null ? "" : systemFilter.toLowerCase(Locale.ROOT);
    if (target.isBlank()) {
      return out;
    }

    if (locationsNode.isArray()) {
      for (JsonNode node : locationsNode) {
        String text = node.asText("").trim();
        if (!text.isBlank() && target.equals(inferSystemFromLocationText(text)) && !out.contains(text)) {
          out.add(text);
        }
      }
    } else {
      String text = locationsNode.asText("").trim();
      if (!text.isBlank() && target.equals(inferSystemFromLocationText(text))) {
        out.add(text);
      }
    }
    return out;
  }

  private static String inferSystemFromLocationText(String location) {
    String lower = location == null ? "" : location.toLowerCase(Locale.ROOT);
    if (lower.contains("pyro")) {
      return "pyro";
    }
    if (lower.contains("nyx") || lower.contains("levski") || lower.contains("delamar")) {
      return "nyx";
    }
    return "stanton";
  }

  private record BuyQueryParts(String item, String location) {}

  @SafeVarargs
  private static String suggestionReplyForUnknown(
      String label, String candidate, List<String>... suggestionLists) {
    if (candidate == null || candidate.isBlank()) {
      return null;
    }
    List<String> merged = new ArrayList<>();
    for (List<String> list : suggestionLists) {
      if (list == null) {
        continue;
      }
      for (String item : list) {
        if (item != null && !item.isBlank() && !merged.contains(item)) {
          merged.add(item);
        }
      }
    }
    if (merged.isEmpty()) {
      return null;
    }
    return "I couldn't find an exact "
        + label
        + " match for **"
        + candidate.trim()
        + "**. Closest matches: "
        + String.join(", ", merged);
  }

  private static List<String> collectMissionGiverSuggestions(String giverFilter) {
    List<String> out = new ArrayList<>();
    JsonNode missions = StarCitizenDataService.get("missions");
    if (missions == null || !missions.isObject()) {
      return out;
    }
    String lower = giverFilter == null ? "" : giverFilter.toLowerCase(Locale.ROOT);
    missions
        .fields()
        .forEachRemaining(
            entry -> {
              String giver = entry.getValue().path("giver").asText("");
              if (!giver.isBlank()
                  && giver
                  .toLowerCase(Locale.ROOT)
                  .contains(
                      lower.substring(
                          0, Math.min(lower.length(), Math.max(1, lower.length()))))) {
                if (!out.contains(giver)) {
                  out.add(giver);
                }
              }
            });
    out.sort(String.CASE_INSENSITIVE_ORDER);
    return out.size() > 5 ? out.subList(0, 5) : out;
  }

  private static String sortSuffix(double value, String unit) {
    if (value <= 0) {
      return "";
    }
    return " (" + fmtNumber(value) + unit + ")";
  }

  private static class ComponentScore {

    final String name;
    final double score;

    ComponentScore(String name, double score) {
      this.name = name;
      this.score = score;
    }
  }

  private static boolean matchesComponentCategory(String category, String hay) {
    return switch (category) {
      case "all" -> true;
      case "shields" -> hay.contains("shield");
      case "power" -> hay.contains("power") || hay.contains("plant");
      case "coolers" -> hay.contains("cooler") || hay.contains("cooling");
      case "quantum" -> hay.contains("quantum") || hay.contains("qdrive") || hay.contains("jump");
      case "engines" -> hay.contains("engine") || hay.contains("thruster");
      case "missiles" -> hay.contains("missile") || hay.contains("torpedo") || hay.contains("bomb");
      default -> false;
    };
  }

  private static String findMentionedDatasetKey(String dataset, String lowerText) {
    JsonNode node = StarCitizenDataService.get(dataset);
    return findMentionedKeyInNode(node, lowerText);
  }

  private static String findMentionedKeyInNode(JsonNode node, String lowerText) {
    if (node == null || !node.isObject() || lowerText == null || lowerText.isBlank()) {
      return null;
    }
    String best = null;
    int bestLen = -1;
    for (Iterator<String> it = node.fieldNames(); it.hasNext(); ) {
      String key = it.next();
      String lowerKey = key.toLowerCase(Locale.ROOT);
      if (lowerText.contains(lowerKey) && lowerKey.length() > bestLen) {
        best = key;
        bestLen = lowerKey.length();
      }
    }
    return best;
  }

  private static String resolveNodeKey(JsonNode node, String candidate) {
    if (node == null || !node.isObject() || candidate == null || candidate.isBlank()) {
      return null;
    }
    String lower = candidate.toLowerCase(Locale.ROOT).trim();
    String best = null;
    int bestScore = -1;
    for (Iterator<String> it = node.fieldNames(); it.hasNext(); ) {
      String key = it.next();
      String lowerKey = key.toLowerCase(Locale.ROOT);
      int score = 0;
      if (lowerKey.equals(lower)) {
        score = 100;
      } else if (lowerKey.startsWith(lower) || lower.startsWith(lowerKey)) {
        score = 85;
      } else if (lowerKey.contains(lower) || lower.contains(lowerKey)) {
        score = 70;
      }
      if (score > bestScore) {
        best = key;
        bestScore = score;
      }
    }
    return bestScore >= 70 ? best : null;
  }

  private static String extractAfterKeyword(String text, String... keywords) {
    if (text == null) {
      return "";
    }
    Matcher quoted = QUOTED_PATTERN.matcher(text);
    if (quoted.find()) {
      return quoted.group(1).trim();
    }

    String lower = text.toLowerCase(Locale.ROOT);
    for (String keyword : keywords) {
      if (keyword == null || keyword.isBlank()) {
        continue;
      }
      int idx = indexOfKeywordPhrase(lower, keyword.toLowerCase(Locale.ROOT));
      if (idx >= 0) {
        String out = text.substring(idx + keyword.length()).trim();
        out =
            out.replaceFirst(
                "^(for|about|on|of|named|called|info for|stats for|lookup|show|me|the|a|an)\\s+",
                "");
        out = out.replaceFirst("(?i)\\s+(please|thanks|thank you).*$", "");
        return out.trim();
      }
    }
    String out = text.trim();
    out = out.replaceFirst("(?i)^\\s*(show|tell|give|list|lookup)\\s+", "");
    out = out.replaceFirst("(?i)^\\s*(me|all)\\s+", "");
    out =
        out.replaceFirst(
            "(?i)^\\s*(ship|ships|armor|armour|weapon|weapons|component|components|mission|missions|location|locations)\\s+",
            "");
    out = out.replaceFirst("(?i)^\\s*(for|about|on|of|the|a|an)\\s+", "");
    return out.trim();
  }

  private static String tryDirectEntityLookup(String text, String lower) {
    if (text == null || text.isBlank()) {
      return null;
    }
    if (!containsAny(
        lower,
        "show me",
        "tell me about",
        "lookup",
        "look up",
        "stats",
        "info",
        "what is",
        "what's")) {
      return null;
    }

    String candidate =
        extractAfterKeyword(
            text,
            "show me",
            "tell me about",
            "lookup",
            "look up",
            "stats for",
            "info for",
            "what is",
            "what's");
    if (candidate == null || candidate.isBlank()) {
      return null;
    }

    String shipKey = StarCitizenDataService.resolveShipName(candidate);
    if (shipKey == null) {
      shipKey = StarCitizenDataService.resolveDatasetKey("ships", candidate);
    }
    if (shipKey != null) {
      return tryShip("ship " + shipKey, "ship " + shipKey.toLowerCase(Locale.ROOT));
    }

    String weaponKey = StarCitizenDataService.resolveDatasetKey("weapons", candidate);
    if (weaponKey != null) {
      return tryWeapon("weapon " + weaponKey, "weapon " + weaponKey.toLowerCase(Locale.ROOT));
    }

    String componentKey = StarCitizenDataService.resolveDatasetKey("components", candidate);
    if (componentKey != null) {
      return formatComponent(componentKey);
    }

    String armorKey = StarCitizenDataService.resolveDatasetKey("armor", candidate);
    if (armorKey != null) {
      return tryArmor("armor " + armorKey, "armor " + armorKey.toLowerCase(Locale.ROOT));
    }

    return null;
  }

  private static String formatLocationList(JsonNode value, int limit) {
    if (value == null || value.isMissingNode() || value.isNull()) {
      return "—";
    }
    List<String> rows = new ArrayList<>();
    if (value.isArray()) {
      for (JsonNode node : value) {
        String text = node.asText("").trim();
        if (!text.isBlank() && !rows.contains(text)) {
          rows.add(text);
        }
      }
    } else {
      String raw = value.asText("").trim();
      if (!raw.isBlank()) {
        String[] parts = raw.split("\\s*[|,;]\\s*");
        for (String part : parts) {
          String text = part.trim();
          if (!text.isBlank() && !rows.contains(text)) {
            rows.add(text);
          }
        }
      }
    }
    if (rows.isEmpty()) {
      return "—";
    }
    if (rows.size() <= limit) {
      return String.join(", ", rows);
    }
    return String.join(", ", rows.subList(0, limit)) + " (and " + (rows.size() - limit) + " more)";
  }

  private static boolean isSparseShipStatRecord(JsonNode info, JsonNode stats) {
    int present = 0;
    if (info.path("price_auec").asDouble(0) > 0) {
      present++;
    }
    if (!textOrDash(info, "buy_location").equals("—")) {
      present++;
    }
    if (info.path("shield_hp").asInt(0) > 0) {
      present++;
    }
    if (info.path("hull_hp").asInt(0) > 0) {
      present++;
    }
    if (stats.path("pilot_dps").asDouble(0) > 0) {
      present++;
    }
    if (stats.path("total_dps").asDouble(0) > 0) {
      present++;
    }
    return present <= 2;
  }

  private static int indexOfKeywordPhrase(String lowerText, String lowerKeyword) {
    if (lowerText == null || lowerKeyword == null || lowerKeyword.isBlank()) {
      return -1;
    }
    Pattern p =
        Pattern.compile("\\b" + Pattern.quote(lowerKeyword) + "\\b", Pattern.CASE_INSENSITIVE);
    Matcher m = p.matcher(lowerText);
    return m.find() ? m.start() : -1;
  }

  private static String capitalize(String input) {
    if (input == null || input.isBlank()) {
      return "";
    }
    return Character.toUpperCase(input.charAt(0)) + input.substring(1).toLowerCase(Locale.ROOT);
  }

  private static int parseCargoScu(String text) {
    if (text == null) {
      return 0;
    }
    Matcher m = SCU_PATTERN.matcher(text);
    if (m.find()) {
      try {
        return Integer.parseInt(m.group(1));
      } catch (NumberFormatException ignored) {
      }
    }
    return 0;
  }

  private static String tryOriginShipsOverview() {
    JsonNode ships = StarCitizenDataService.get("ships");
    if (ships == null || !ships.isObject() || ships.isEmpty()) {
      return null;
    }

    List<String> originShips = new ArrayList<>();
    ships
        .fields()
        .forEachRemaining(
            entry -> {
              String manufacturer = entry.getValue().path("info").path("manufacturer").asText("");
              if (manufacturer.toLowerCase(Locale.ROOT).contains("origin")) {
                originShips.add(entry.getKey());
              }
            });
    if (originShips.isEmpty()) {
      return null;
    }

    originShips.sort(String.CASE_INSENSITIVE_ORDER);
    String body =
        "**Origin Ships**\n"
            + "Manufacturer: Origin Jumpworks\n"
            + bulletList(originShips, 12)
            + "\n\nAsk for a specific one like `ship 890 jump` if you want the full luxury brochure.";
    return pick(ORIGIN_SNARK) + "\n\n" + body;
  }

  private static String withShipSnark(String key, JsonNode info, String body) {
    String manufacturer = info == null ? "" : info.path("manufacturer").asText("");
    String lowerKey = key == null ? "" : key.toLowerCase(Locale.ROOT);
    String lowerMfg = manufacturer.toLowerCase(Locale.ROOT);

    if (lowerKey.contains("890 jump")) {
      return pick(JUMP_890_SNARK) + "\n\n" + body;
    }
    if (lowerMfg.contains("origin")) {
      return pick(ORIGIN_SNARK) + "\n\n" + body;
    }
    if (lowerMfg.contains("drake")) {
      return pick(DRAKE_SNARK) + "\n\n" + body;
    }
    if (lowerMfg.contains("aegis")) {
      return pick(AEGIS_SNARK) + "\n\n" + body;
    }
    if (lowerMfg.contains("roberts") || lowerMfg.contains("rsi")) {
      return pick(RSI_SNARK) + "\n\n" + body;
    }
    if (lowerMfg.contains("misc")) {
      return pick(MISC_SNARK) + "\n\n" + body;
    }
    if (lowerMfg.contains("crusader")) {
      return pick(CRUSADER_SNARK) + "\n\n" + body;
    }
    if (lowerMfg.contains("anvil")) {
      return pick(ANVIL_SNARK) + "\n\n" + body;
    }
    return withSnark("ship", key, body);
  }

  private static String withSnark(String domain, String subject, String body) {
    if (body == null || body.isBlank()) {
      return body;
    }
    String intro =
        switch (domain) {
          case "commodity" -> "Time to play accountant in space. Try not to lick the cargo.";
          case "trade" -> "Nothing says adventure like spreadsheets with thrusters.";
          case "weapon" -> "Here's the pew-pew math, because subtle diplomacy is overrated.";
          case "component" -> "Behold: the deeply glamorous world of internal ship parts.";
          case "component_category" -> "Shopping for ship organs now? Completely normal behavior.";
          case "armor" -> "Fashion, but with ballistic resistance. Finally, a useful wardrobe.";
          case "mission" -> "Contracts, payouts, and bureaucracy — the true endgame.";
          case "location" -> "A place in space. Revolutionary concept, I know.";
          case "refinery" -> "Rocks, timers, and profit margins. Living the dream.";
          case "mining_spots" ->
              "Ah yes, the eternal question: where to mine without crying. Here's the data-backed version.";
          case "salvage" -> "Let's go rummage through space garbage like professionals.";
          case "ship" -> pick(GENERAL_SNARK);
          default -> pick(GENERAL_SNARK);
        };
    return intro + "\n\n" + body;
  }

  private static String pick(List<String> values) {
    if (values == null || values.isEmpty()) {
      return "";
    }
    int index =
        Math.floorMod((int) (System.nanoTime() ^ System.currentTimeMillis()), values.size());
    return values.get(index);
  }

  private static boolean containsAny(String lower, String... tokens) {
    for (String token : tokens) {
      if (lower.contains(token)) {
        return true;
      }
    }
    return false;
  }

  private static String bestCommodityDisplay(JsonNode entry, boolean buy) {
    JsonNode arr = entry.path(buy ? "buy" : "sell");
    if (!arr.isArray() || arr.isEmpty()) {
      return "—";
    }
    double bestPrice = buy ? Double.MAX_VALUE : 0;
    String bestLoc = null;
    for (JsonNode item : arr) {
      double price = item.path("price").asDouble(0);
      String loc = item.path("location").asText("");
      if (loc.isBlank()) {
        continue;
      }
      if (buy ? (price > 0 && price < bestPrice) : price > bestPrice) {
        bestPrice = price;
        bestLoc = loc;
      }
    }
    return bestLoc == null ? "—" : bestLoc + " @ " + fmtNumber(bestPrice) + " aUEC";
  }

  private static String topCommodityLocations(JsonNode arr, boolean buy) {
    if (arr == null || !arr.isArray() || arr.isEmpty()) {
      return "";
    }
    List<JsonNode> rows = new ArrayList<>();
    arr.forEach(rows::add);
    rows.sort(Comparator.comparingDouble(n -> n.path("price").asDouble(0)));
    if (!buy) {
      rows.sort((a, b) -> Double.compare(b.path("price").asDouble(0), a.path("price").asDouble(0)));
    }
    StringBuilder sb = new StringBuilder();
    int shown = 0;
    for (JsonNode row : rows) {
      String loc = row.path("location").asText("");
      double price = row.path("price").asDouble(0);
      if (loc.isBlank() || price <= 0) {
        continue;
      }
      sb.append("• ").append(loc).append(" — ").append(fmtNumber(price)).append(" aUEC\n");
      if (++shown >= 5) {
        break;
      }
    }
    return sb.toString().trim();
  }

  private static String bulletList(List<String> items, int limit) {
    if (items == null || items.isEmpty()) {
      return "*No results.*";
    }
    StringBuilder sb = new StringBuilder();
    int shown = Math.min(limit, items.size());
    for (int i = 0; i < shown; i++) {
      sb.append("• ").append(items.get(i)).append("\n");
    }
    if (items.size() > shown) {
      sb.append("*...and ").append(items.size() - shown).append(" more*");
    }
    return sb.toString().trim();
  }

  private static String prettyCategory(String category) {
    return switch (category) {
      case "shields" -> "Shields";
      case "power" -> "Power Plants";
      case "coolers" -> "Coolers";
      case "quantum" -> "Quantum Drives";
      case "engines" -> "Engines / Thrusters";
      case "missiles" -> "Missiles";
      default -> "All Components";
    };
  }

  private static void appendOptionalLine(
      StringBuilder sb, JsonNode node, String key, String label) {
    String value = node.path(key).asText("").trim();
    if (!value.isBlank()) {
      sb.append(label).append(": ").append(value).append("\n");
    }
  }

  private static String textOrDash(JsonNode node, String... keys) {
    if (node == null || keys == null) {
      return "—";
    }
    for (String key : keys) {
      String value = node.path(key).asText("").trim();
      if (!value.isBlank()) {
        return value;
      }
    }
    return "—";
  }

  private static String deriveRepFaction(String type, String giver, String missionName) {
    String source = (type + " " + giver + " " + missionName).toLowerCase(Locale.ROOT);
    if (source.contains("bounty") || source.contains("advocacy")) {
      return "Bounty Hunters Guild / local security";
    }
    if (source.contains("mercenary") || source.contains("security")) {
      return "Mercenary Guild / local security";
    }
    if (source.contains("cargo") || source.contains("delivery") || source.contains("trade")) {
      return "Delivery / Trade contracts";
    }
    if (source.contains("mining")) {
      return "Mining Guild / industrial contacts";
    }
    if (source.contains("salvage")) {
      return "Salvage Guild / salvage operators";
    }
    return "Contract issuer reputation";
  }

  private static String deriveRepRequirement(String tier, String difficulty, String missionName) {
    String level = (tier + " " + difficulty + " " + missionName).toLowerCase(Locale.ROOT);
    if (level.contains("very hard") || level.contains("elite")) {
      return "High";
    }
    if (level.contains("hard")) {
      return "Medium-High";
    }
    if (level.contains("medium")) {
      return "Medium";
    }
    if (level.contains("easy") || level.contains("entry")) {
      return "Low / none";
    }
    return "Varies by issuer";
  }

  private static String percentOrDash(JsonNode value) {
    return value != null && value.asInt(0) > 0 ? value.asInt(0) + "%" : "—";
  }

  private static String intOrDash(JsonNode value) {
    return value != null && value.asInt(0) > 0 ? Integer.toString(value.asInt(0)) : "—";
  }

  private static String numberOrDash(JsonNode value) {
    return value != null && value.asDouble(0) > 0 ? fmtNumber(value.asDouble(0)) : "—";
  }

  private static String signedNumberOrDash(JsonNode value) {
    if (value == null) {
      return "—";
    }
    double n = value.asDouble(0);
    return Math.abs(n) < 1e-9 ? "—" : fmtNumber(n);
  }

  private static String minMaxPctOrDash(JsonNode node, String minKey, String maxKey) {
    if (node == null || node.isMissingNode()) {
      return "—";
    }
    double min = node.path(minKey).asDouble(0);
    double max = node.path(maxKey).asDouble(0);
    if (Math.abs(min) < 1e-9 && Math.abs(max) < 1e-9) {
      return "—";
    }
    return signedNumberOrDash(node.path(minKey))
        + "% / "
        + signedNumberOrDash(node.path(maxKey))
        + "%";
  }

  private static String speedPair(int a, int b, String unit) {
    String left = a > 0 ? fmtNumber(a) + (unit.isBlank() ? "" : " " + unit) : "—";
    String right = b > 0 ? fmtNumber(b) + (unit.isBlank() ? "" : " " + unit) : "—";
    return left + " / " + right;
  }

  private static String fmtNumber(double n) {
    if (Math.abs(n - Math.rint(n)) < 1e-9) {
      return String.format("%,.0f", n);
    }
    return String.format("%,.2f", n).replaceAll("\\.?0+$", "");
  }

  private static String fmtSignedPercent(double value) {
    if (Math.abs(value) < 1e-9) {
      return "0%";
    }
    String body =
        Math.abs(value - Math.rint(value)) < 1e-9
            ? String.format("%,.0f", Math.abs(value))
            : String.format("%,.2f", Math.abs(value)).replaceAll("\\.?0+$", "");
    return (value > 0 ? "+" : "-") + body + "%";
  }

  /**
   * Collects display names from a ship loadout array (weapons / turrets / missiles).
   */
  private static List<String> collectLoadoutNames(JsonNode arr) {
    List<String> names = new ArrayList<>();
    if (arr == null || !arr.isArray()) {
      return names;
    }
    for (JsonNode n : arr) {
      String name =
          n.isTextual()
              ? n.asText("").trim()
              : n.path("name").asText(n.path("item_name").asText("")).trim();
      if (!name.isBlank() && !names.contains(name)) {
        names.add(name);
      }
    }
    return names;
  }

  /**
   * Last-resort lookup: if the user typed a short bare entity name (e.g. "300i", "Caterpillar",
   * "CF-227 Badger") with no command keywords, try to resolve it directly as a ship, weapon,
   * component, or armor entry.
   */
  private static String tryFreeformEntityLookup(String text, String lower) {
    if (text == null) {
      return null;
    }
    String candidate = text.trim();
    // Only attempt for short inputs unlikely to be sentences
    if (candidate.length() > 64 || candidate.split("\\s+").length > 6) {
      return null;
    }

    // Ship
    String shipKey = StarCitizenDataService.resolveShipName(candidate);
    if (shipKey == null) {
      shipKey = StarCitizenDataService.resolveDatasetKey("ships", candidate);
    }
    if (shipKey != null) {
      return tryShip("ship " + shipKey, "ship " + shipKey.toLowerCase(Locale.ROOT));
    }

    // Weapon
    String weaponKey = StarCitizenDataService.resolveDatasetKey("weapons", candidate);
    if (weaponKey != null) {
      return tryWeapon("weapon " + weaponKey, "weapon " + weaponKey.toLowerCase(Locale.ROOT));
    }

    // Component
    String componentKey = StarCitizenDataService.resolveDatasetKey("components", candidate);
    if (componentKey != null) {
      return formatComponent(componentKey);
    }

    // Armor
    String armorKey = StarCitizenDataService.resolveDatasetKey("armor", candidate);
    if (armorKey != null) {
      return tryArmor("armor " + armorKey, "armor " + armorKey.toLowerCase(Locale.ROOT));
    }

    return null;
  }
}
