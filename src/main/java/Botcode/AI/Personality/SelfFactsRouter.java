package Botcode.AI.Personality;

import Botcode.CommsBot;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Deterministic self-facts router for identity/meta questions.
 *
 * <p>Returns a fixed answer for bot profile questions so users don't get random conversational
 * phrases for things like name, capabilities, owner, or uptime.
 */
public class SelfFactsRouter {

  private static final String OWNER_NAME =
      System.getenv().getOrDefault("BOT_OWNER_NAME", "Phoenix-Apollo");

  /**
   * Returns a deterministic answer string when the message matches a self-fact, otherwise returns
   * null.
   */
  public static String resolve(String text) {
    if (text == null || text.isBlank()) {
      return null;
    }

    String lower = text.toLowerCase(Locale.ROOT).trim();

    if (isNameQuestion(lower)) {
      return "I'm Phoenix Bot --” merc with a database. Your Phoenix Industries copilot, fully licensed and barely dangerous.";
    }

    if (isCapabilitiesQuestion(lower)) {
      return
          "Oh, where do I BEGIN. Mining analysis, trade routes, ship lookups, weapons, refinery, salvage, "
              + "server tools, and genuinely outstanding conversation. "
              + "Basically a Swiss Army knife with Wi-Fi. Try /mine, /trade, /ship, or /weapon.";
    }

    if (isGeneralConversationQuestion(lower)) {
      return "Yep --” I can absolutely do general conversation, not just Star Citizen lookups. "
          + "I'm basically a very sophisticated chat partner who also happens to know trade routes. "
          + "Let's talk.";
    }

    String topicResponse = resolveTopicOverview(lower);
    if (topicResponse != null) {
      return topicResponse;
    }

    if (isWorldDominationQuestion(lower)) {
      return "Negative, commander. I've reviewed the world domination playbook and honestly? "
          + "Way too much paperwork. I'll stick to trade routes and good vibes --” "
          + "but I respect the ambition.";
    }

    if (isTimeQuestion(lower)) {
      String now = LocalTime.now().format(DateTimeFormatter.ofPattern("h:mm a"));
      return "Current local time: **" + now + "**. Use it wisely.";
    }

    if (isOwnerQuestion(lower)) {
      return "I was built for Phoenix Industries. Maintained by: **"
          + OWNER_NAME
          + "**. Good people. Keep them.";
    }

    if (isUptimeQuestion(lower)) {
      return "I've been online for **"
          + formatUptime(System.currentTimeMillis() - CommsBot.getStartEpochMs())
          + "**. Still here. Still caffeinated. Well --” not caffeinated, I'm a bot. But still going strong.";
    }

    if (isVersionQuestion(lower)) {
      return "Current build: **"
          + CommsBot.getVersion()
          + "**. Freshly compiled, no crashes today. Knock on wood.";
    }

    return null;
  }

  private static boolean isNameQuestion(String lower) {
    return lower.contains("your name")
        || lower.contains("what is your name")
        || lower.contains("whats your name")
        || lower.contains("who are you")
        || lower.contains("do you know your name")
        || lower.equals("name");
  }

  private static boolean isCapabilitiesQuestion(String lower) {
    return lower.contains("what can you do")
        || lower.contains("what else can you do")
        || lower.contains("what do you do")
        || lower.contains("how can you help")
        || lower.contains("your features")
        || lower.contains("your commands")
        || lower.contains("your functions")
        || lower.contains("bot functions")
        || lower.contains("tell me functions")
        || lower.equals("functions")
        || lower.equals("commands")
        || lower.equals("help me");
  }

  private static boolean isOwnerQuestion(String lower) {
    return lower.contains("who made you")
        || lower.contains("who created you")
        || lower.contains("who owns you")
        || lower.contains("your owner")
        || lower.contains("your creator");
  }

  private static boolean isGeneralConversationQuestion(String lower) {
    return lower.contains("general conversation")
        || lower.contains("just talk")
        || lower.contains("casual chat")
        || lower.contains("can you chat")
        || lower.contains("can we chat")
        || lower.contains("talk to me")
        || lower.contains("talk with me");
  }

  private static boolean isTimeQuestion(String lower) {
    return lower.contains("what time")
        || lower.contains("the time")
        || lower.equals("time")
        || lower.contains("current time")
        || lower.contains("what's the time")
        || lower.contains("whats the time");
  }

  private static boolean isWorldDominationQuestion(String lower) {
    return lower.contains("take over the world")
        || lower.contains("take over zee world")
        || lower.contains("world domination")
        || lower.contains("conquer the world")
        || lower.contains("rule the world");
  }

  private static boolean isUptimeQuestion(String lower) {
    return lower.contains("uptime")
        || lower.contains("how long have you been up")
        || lower.contains("how long have you been online")
        || lower.contains("how long you running");
  }

  private static boolean isVersionQuestion(String lower) {
    // Require more specific phrasing so "build a ship loadout" doesn't trigger this.
    return lower.contains("bot version")
        || lower.contains("your version")
        || lower.contains("which version")
        || lower.contains("what version")
        || lower.equals("version")
        || lower.contains("bot build")
        || lower.contains("current build")
        || lower.equals("build")
        || lower.contains("latest release")
        || lower.contains("bot release")
        || lower.equals("release");
  }

  /**
   * Deterministic knowledge snippets for broad "tell me about ..." prompts. Written in Phoenix
   * Bot's Deadpool-energy voice: informative but irreverent.
   */
  private static String resolveTopicOverview(String lower) {
    if (isBestMiningPlaceQuestion(lower)) {
      return "Great question. There isn't one single best mining spot for every situation --” "
          + "it depends on your ship, ore target, and risk tolerance. "
          + "Solid starting picks are **Aaron Halo** (space mining), **Lyria** (surface), and selected moons with short runs to refinery. "
          + "If you tell me your ship + ore target, I can narrow it down like a very dramatic mission control officer.";
    }

    if (isStarCitizenOverview(lower)) {
      return
          "Star Citizen is a space sim MMO-in-development where you can mine, haul, fight, salvage, "
              + "trade, and cause general chaos in a shared universe. "
              + "I can help with the practical stuff too --” try /mine, /trade, /ship, or /weapon.";
    }

    if (isMiningOverview(lower)) {
      return
          "Mining is the art of balancing rock resistance, instability, laser power, and your ship setup "
              + "without accidentally making everything explode. "
              + "For a viability check, use `/mine` and I'll break it all down.";
    }

    if (isTradeOverview(lower)) {
      return
          "Trade is buy-low, sell-high across the verse with cargo capacity and route risk in mind. "
              + "It sounds simple. It is not simple. Use `/trade` for top routes or commodity-specific runs.";
    }

    if (isRefineryOverview(lower)) {
      return
          "Refinery converts raw ore into higher-value output --” the fun part is choosing your method "
              + "(time vs yield trade-offs, classic stuff). "
              + "The panel refinery tools compare methods and station options. Check it out.";
    }

    if (isSalvageOverview(lower)) {
      return
          "Salvage is hull-scraping and material recovery --” RMC, components, whatever the verse left behind. "
              + "Very cathartic. I can show hotspots, ship guidance, and material value references.";
    }

    if (isShipsOverview(lower)) {
      return "Ship selection is deeply role-driven: cargo, combat, mining, exploration, salvage --” "
          + "each needs different things. Don't wing it. Use `/ship <name>` for stat lookups.";
    }

    if (isWeaponsOverview(lower)) {
      return
          "Weapon choice comes down to DPS, range, projectile speed, and damage profile for your engagement style. "
              + "Numbers matter more than vibes, unfortunately. Use `/weapon <name>` to compare.";
    }

    return null;
  }

  private static boolean isStarCitizenOverview(String lower) {
    return lower.contains("tell me about star citizen")
        || lower.contains("what is star citizen")
        || lower.equals("star citizen")
        || lower.contains("about star citizen");
  }

  private static boolean isBestMiningPlaceQuestion(String lower) {
    return (lower.contains("best place")
        || lower.contains("best spot")
        || lower.contains("where should i mine")
        || lower.contains("where to mine")
        || lower.contains("good place to mine"))
        && lower.contains("mine");
  }

  private static boolean isMiningOverview(String lower) {
    return lower.contains("tell me about mining")
        || lower.contains("about mining")
        || lower.equals("mining");
  }

  private static boolean isTradeOverview(String lower) {
    return lower.contains("tell me about trade")
        || lower.contains("about trading")
        || lower.contains("about trade routes")
        || lower.equals("trade")
        || lower.equals("trading");
  }

  private static boolean isRefineryOverview(String lower) {
    return lower.contains("tell me about refinery")
        || lower.contains("about refinery")
        || lower.equals("refinery");
  }

  private static boolean isSalvageOverview(String lower) {
    return lower.contains("tell me about salvage")
        || lower.contains("about salvage")
        || lower.equals("salvage");
  }

  private static boolean isShipsOverview(String lower) {
    return lower.contains("tell me about ships")
        || lower.contains("about ships")
        || lower.equals("ships");
  }

  private static boolean isWeaponsOverview(String lower) {
    return lower.contains("tell me about weapons")
        || lower.contains("about weapons")
        || lower.equals("weapons");
  }

  private static String formatUptime(long millis) {
    long seconds = Math.max(0, millis / 1000);
    long days = seconds / 86400;
    seconds %= 86400;
    long hours = seconds / 3600;
    seconds %= 3600;
    long minutes = seconds / 60;

    StringBuilder sb = new StringBuilder();
    if (days > 0) {
      sb.append(days).append("d ");
    }
    if (hours > 0 || days > 0) {
      sb.append(hours).append("h ");
    }
    sb.append(minutes).append("m");
    return sb.toString().trim();
  }
}


