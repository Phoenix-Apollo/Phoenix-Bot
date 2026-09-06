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
      return "I'm Phoenix Bot, your Phoenix Industries assistant.";
    }

    if (isCapabilitiesQuestion(lower)) {
      return "I can help with Star Citizen data, server tools, and normal conversation. Try /mine, /trade, /ship, or /weapon.";
    }

    if (isBotStatusQuestion(lower)) {
      return "I'm doing well and ready to help. What's up?";
    }

    if (isGeneralConversationQuestion(lower)) {
      return "Yes — I can do normal conversation too. What would you like to talk about?";
    }

    String topicResponse = resolveTopicOverview(lower);
    if (topicResponse != null) {
      return topicResponse;
    }

    if (isWorldDominationQuestion(lower)) {
      return "Nope. I’m keeping things to Star Citizen, server tools, and conversation.";
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

  private static boolean isBotStatusQuestion(String lower) {
    return lower.contains("how are you")
        || lower.contains("how is the bot")
        || lower.contains("how's the bot")
        || lower.contains("how is phoenix bot")
        || lower.contains("how's phoenix bot")
        || lower.contains("how are you doing")
        || lower.contains("how is it going")
        || lower.contains("how's it going");
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
      return "Star Citizen is an in-development space sim MMO focused on mining, hauling, combat, salvage, trading, and exploration. I can help with the in-game details too.";
    }

    if (isMiningOverview(lower)) {
      return "Mining is about matching your ship, laser setup, and rock resistance so you get useful ore without blowing it up. Use /mine for a full breakdown.";
    }

    if (isTradeOverview(lower)) {
      return "Trade is buying low, selling high, and managing cargo risk along the way. Use /trade for routes or commodity-specific runs.";
    }

    if (isRefineryOverview(lower)) {
      return "Refinery turns raw ore into higher-value output. The main choice is usually time versus yield.";
    }

    if (isSalvageOverview(lower)) {
      return "Salvage is hull scraping and material recovery, usually for RMC and other recovered parts. I can point you to hotspots and ship guidance.";
    }

    if (isShipsOverview(lower)) {
      return "Ship selection depends on the job: cargo, combat, mining, exploration, or salvage. Use /ship <name> for stats.";
    }

    if (isWeaponsOverview(lower)) {
      return "Weapon choice comes down to DPS, range, projectile speed, and damage type. Use /weapon <name> to compare.";
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

