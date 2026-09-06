package Botcode.AI.AIHook;

import Botcode.AI.Personality.SelfFactsRouter;
import Botcode.StarCitizen.StarCitizenChatService;
import Botcode.AI.AIUtils.BotConfig;
import Botcode.AI.WebLookupService;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * Central AI reply facade with enhanced contextual awareness and learning integration.
 *
 * <p>Behavior flow (May 2026+):
 * - Prioritizes self-identity responses for identity queries (consistent persona)
 * - Routes knowledge queries to web lookup for accurate factual answers
 * - Leverages Star Citizen domain expertise for topic-specific questions
 * - Falls back to learned personality phrases for conversational continuity
 * - Maintains context from ConversationMemoryService for smarter follow-ups
 */
public class AIResponder {

  public enum Source {
    STAR_CITIZEN,
    SELF_FACTS,
    WEB_LOOKUP,
    PREMIUM,
    LEARNED_PERSONALITY,
    NONE
  }

  public static final class AIResponse {

    public final String text;
    public final Source source;

    private AIResponse(String text, Source source) {
      this.text = text;
      this.source = source;
    }

    public static AIResponse of(String text, Source source) {
      return new AIResponse(text, source);
    }

    public static AIResponse none() {
      return new AIResponse(null, Source.NONE);
    }

    public boolean hasText() {
      return text != null && !text.isBlank();
    }
  }

  private AIResponder() {
  }

  /**
   * Resolves a conversational AI reply from available providers.
   *
   * <p>Enhanced flow (May 2026+):
   * 1. Prioritize self-identity responses for consistent persona
   * 2. Route knowledge queries to web lookup for factual accuracy
   * 3. Check Star Citizen domain expertise
   * 4. Try web lookup for non-SC topics
   * 5. Fall back to learned personality phrases
   * 6. Premium provider as optional override
   */
  public static AIResponse resolve(String prompt) {
    if (!BotConfig.AI_ENABLED || prompt == null || prompt.isBlank()) {
      return AIResponse.none();
    }

    // PRIORITY 1: Self-identity queries (consistent persona)
    if (isSelfIdentityPrompt(prompt)) {
      String self = SelfFactsRouter.resolve(prompt);
      if (self != null) {
        return AIResponse.of(self, Source.SELF_FACTS);
      }
    }

    // PRIORITY 2: Direct status / conversational reassurance prompts.
    if (isBotStatusPrompt(prompt)) {
      String status = SelfFactsRouter.resolve(prompt);
      if (status != null) {
        return AIResponse.of(status, Source.SELF_FACTS);
      }
    }

    // PRIORITY 3: Playful/smirky conversational prompts.
    String playful = tryPlayfulPrompt(prompt);
    if (playful != null) {
      return AIResponse.of(playful, Source.LEARNED_PERSONALITY);
    }

    // PRIORITY 4: Planning follow-through (selected option / explain mode).
    String planningFollowThrough = tryPlanningFollowThrough(prompt);
    if (planningFollowThrough != null) {
      return AIResponse.of(planningFollowThrough, Source.LEARNED_PERSONALITY);
    }

    // PRIORITY 5: Conversational planning/support prompts.
    String planner = tryPlanningPrompt(prompt);
    if (planner != null) {
      return AIResponse.of(planner, Source.LEARNED_PERSONALITY);
    }

    String empathy = tryLowPlaytimePrompt(prompt);
    if (empathy != null) {
      return AIResponse.of(empathy, Source.LEARNED_PERSONALITY);
    }

    // PRIORITY 6: Targeted Star Citizen "which fighter should I pick" prompts.
    String fighterPick = tryBountyFighterPicker(prompt);
    if (fighterPick != null) {
      return AIResponse.of(fighterPick, Source.STAR_CITIZEN);
    }

    // PRIORITY 7: Star Citizen domain expertise (topic-specific only).
    if (isSCContextualQuery(prompt.toLowerCase(Locale.ROOT))) {
      String sc = StarCitizenChatService.tryRespond(prompt);
      if (sc != null) {
        return AIResponse.of(sc, Source.STAR_CITIZEN);
      }
    }

    // PRIORITY 8: Explicit knowledge queries to web lookup (accurate facts)
    if (isExplicitNonScLookupPrompt(prompt)) {
      String webFirst = WebLookupService.tryLookup(prompt);
      if (webFirst != null) {
        return AIResponse.of(webFirst, Source.WEB_LOOKUP);
      }
    }

    // PRIORITY 9: Web lookup for non-SC general questions
    String web = WebLookupService.tryLookup(prompt);
    if (web != null) {
      return AIResponse.of(web, Source.WEB_LOOKUP);
    }

    // PRIORITY 10: Force web-style answer for plain non-SC questions.
    String forcedLookup = tryForcedGeneralLookup(prompt);
    if (forcedLookup != null) {
      return AIResponse.of(forcedLookup, Source.WEB_LOOKUP);
    }

    // PRIORITY 11: Optional premium provider as override (future expansion)
    if (BotConfig.AI_PREMIUM_ENABLED) {
      String premium = tryPremiumRespond(prompt);
      if (premium != null && !premium.isBlank()) {
        return AIResponse.of(premium, Source.PREMIUM);
      }
    }

    // FALLBACK: Learned personality phrases for conversational continuity
    String self = SelfFactsRouter.resolve(prompt);
    if (self != null) {
      return AIResponse.of(self, Source.LEARNED_PERSONALITY);
    }

    return AIResponse.none();
  }

  /**
   * Placeholder hook for future external AI providers (intentionally no-op).
   */
  private static String tryPremiumRespond(String prompt) {
    // Future: implement provider-specific adapters using BotConfig.AI_PREMIUM_PROVIDER.
    return null;
  }

  private static boolean isSelfIdentityPrompt(String prompt) {
    String lower = prompt.toLowerCase(Locale.ROOT);
    return lower.contains("who are you")
        || lower.contains("what are you")
        || lower.contains("your name")
        || lower.contains("about yourself")
        || lower.contains("what can you do")
        || lower.contains("your purpose")
        || lower.contains("your role")
        || lower.contains("created by")
        || lower.contains("who made you")
        || lower.contains("who created you")
        || lower.equals("introduce yourself")
        || lower.equals("tell me about yourself");
  }

  private static boolean isBotStatusPrompt(String prompt) {
    String lower = prompt.toLowerCase(Locale.ROOT);
    return lower.contains("how are you")
        || lower.contains("how is the bot")
        || lower.contains("how's the bot")
        || lower.contains("how is phoenix bot")
        || lower.contains("how's phoenix bot")
        || lower.contains("how are you doing")
        || lower.contains("how is it going")
        || lower.contains("how's it going");
  }

  private static boolean isExplicitNonScLookupPrompt(String prompt) {
    String lower = prompt.toLowerCase(Locale.ROOT);

    // Explicit lookup intent
    boolean explicitLookup =
        lower.contains("look up")
            || lower.contains("lookup")
            || lower.contains("search for")
            || lower.contains("google")
            || lower.startsWith("wiki ")
            || lower.startsWith("wikipedia ")
            || lower.startsWith("who is ")
            || lower.startsWith("what is ")
            || lower.startsWith("who was ")
            || lower.startsWith("what was ")
            || lower.startsWith("define ")
            || lower.startsWith("definition of ")
            || lower.startsWith("how to ")
            || lower.startsWith("how do ")
            || (lower.startsWith("when ") && (lower.contains("happen") || lower.contains("did") || lower.contains("was")))
            || lower.contains("capital of")
            || lower.contains("population of")
            || lower.contains("distance to");

    if (!explicitLookup) {
      return false;
    }

    // Exclude Star Citizen specific queries
    return !isSCContextualQuery(lower);
  }

  private static boolean isSCContextualQuery(String lower) {
    return lower.contains("star citizen")
        || lower.contains("stanton")
        || lower.contains("pyro")
        || lower.contains("hurston")
        || lower.contains("arccorp")
        || lower.contains("microtech")
        || lower.contains("crusader")
        || lower.contains("lorville")
        || lower.contains("orison")
        || lower.contains("new babbage")
        || lower.contains("area18")
        || lower.contains("trade route")
        || lower.contains("auec")
        || lower.contains("quantum")
        || lower.contains("refinery")
        || lower.contains("salvage")
        || lower.contains("mining")
        || lower.contains("ship")
        || lower.contains("weapon")
        || lower.contains("component")
        || lower.contains("commodity")
        || lower.contains("cargo");
  }

  private static String tryPlanningPrompt(String prompt) {
    String lower = prompt.toLowerCase(Locale.ROOT);
    boolean wantsPlan =
        lower.contains("plan")
            || lower.contains("event")
            || lower.contains("suggest")
            || lower.contains("what kind of event")
            || lower.contains("help me plan")
            || lower.contains("what do you suggest");
    if (!wantsPlan) {
      return null;
    }

    return "Love it — let’s plan something fun.\n\n"
        + "Pick one and I’ll build the full runbook:\n"
        + "1) **Race Night** (time trials + brackets)\n"
        + "2) **Bounty Gauntlet** (team hunt rotation)\n"
        + "3) **Mining Madness** (crew split + profit challenge)\n"
        + "4) **Chaos Ops** (mixed objectives, points, winner)\n\n"
        + "If you want a quick start, I recommend **Chaos Ops**: 60–90 min, 8–20 players, low setup, high laughs.";
  }

  private static String tryPlayfulPrompt(String prompt) {
    String lower = prompt.toLowerCase(Locale.ROOT).trim();

    if (lower.contains("who let the dogs out")) {
      return "Legend says nobody knows. Real answer: it was probably one guy who said "
          + "\"it'll be fine\" right before total chaos.\n\n"
          + "If you want, I can also provide the tactical version: who to blame in 3 easy steps.";
    }

    if (lower.contains("cheese")
        && (lower.startsWith("where")
            || lower.contains("where do you find")
            || lower.contains("where is the cheese"))) {
      return "Cheese is usually in one of three places: fridge, pizza, or a suspiciously expensive "
          + "deli counter.\n\n"
          + "Practical answer: check the grocery **dairy aisle** first, then specialty cheese section for the good stuff.";
    }

    boolean catResentQuestion =
        (lower.contains("cat") || lower.contains("kitty"))
            && (lower.contains("resent")
                || lower.contains("mad at me")
                || lower.contains("hate me")
                || lower.contains("judging me"))
            && (lower.contains("food") || lower.contains("feed"));
    if (catResentQuestion) {
      return "Short answer: yes — for approximately 11 dramatic minutes.\n\n"
          + "Cat answer: you changed The Sacred Crunch Protocol without committee approval.\n"
          + "Human answer: transition slowly (75/25 → 50/50 → 25/75 over a week), watch appetite/litter box, and bribe with treats.\n"
          + "If your cat could text, it would read: *\"I will remember this.\"*";
    }

    boolean playfulSmartAleck =
        lower.contains("asking for a friend")
            || lower.contains("be honest")
            || lower.contains("am i cooked")
            || lower.contains("do i look")
            || lower.contains("is it just me");
    if (playfulSmartAleck) {
      return "I respect the chaos in that question.\n\n"
          + "Verdict: mildly suspicious, highly relatable. Give me one more detail and I’ll give you a real answer with only 12% emotional damage.";
    }

    return null;
  }

  private static String tryForcedGeneralLookup(String prompt) {
    String lower = prompt.toLowerCase(Locale.ROOT).trim();
    if (isSCContextualQuery(lower) || !looksLikeGeneralKnowledgeQuestion(lower)) {
      return null;
    }

    String forcedPrompt = "look up " + prompt;
    String lookedUp = WebLookupService.tryLookup(forcedPrompt);
    if (lookedUp != null) {
      return lookedUp;
    }

    return "I couldn’t pull a clean web result for that on this pass.\n"
        + "Fast fallback: https://www.google.com/search?q="
        + URLEncoder.encode(prompt.trim(), StandardCharsets.UTF_8);
  }

  private static boolean looksLikeGeneralKnowledgeQuestion(String lower) {
    return lower.contains("?")
        || lower.startsWith("where ")
        || lower.startsWith("who ")
        || lower.startsWith("what ")
        || lower.startsWith("when ")
        || lower.startsWith("why ")
        || lower.startsWith("how ")
        || lower.startsWith("does ")
        || lower.startsWith("do ")
        || lower.startsWith("is ")
        || lower.startsWith("are ")
        || lower.contains("find ")
        || lower.contains("where is");
  }

  private static String tryBountyFighterPicker(String prompt) {
    String lower = prompt.toLowerCase(Locale.ROOT).trim();
    boolean asksChoice =
        containsAny(lower, "which", "what", "pick", "choose", "recommend", "should i fly");
    boolean bountyContext = containsAny(lower, "bounty", "bounties", "vhRT", "ert", "combat mission");
    boolean fighterContext = containsAny(lower, "fighter", "ship");
    boolean scContext = containsAny(lower, "star citizen", "stanton", "pyro");
    if (!(asksChoice && bountyContext && fighterContext) && !(scContext && bountyContext && fighterContext)) {
      return null;
    }

    return "**For bounty missions, pick by tier:**\n"
        + "• **Beginner/cheap:** **Anvil Arrow** — nimble, forgiving, low operating cost.\n"
        + "• **All-rounder:** **Aegis Gladius** — consistent dogfighter, great for chaining contracts.\n"
        + "• **Heavy hitter:** **Aegis Vanguard Sentinel/Warden** — tougher for higher-risk targets.\n\n"
        + "If you tell me your budget + solo/duo + preferred style (speed vs tank), I’ll give one exact pick.";
  }

  private static boolean containsAny(String lower, String... terms) {
    for (String term : terms) {
      if (lower.contains(term.toLowerCase(Locale.ROOT))) {
        return true;
      }
    }
    return false;
  }

  private static String tryPlanningFollowThrough(String prompt) {
    String lower = prompt.toLowerCase(Locale.ROOT).trim();

    if (lower.equals("1") || lower.contains("race night")) {
      return "Perfect — **Race Night** runbook:\n\n"
          + "1) Pick location + track (15 min)\n"
          + "2) Qualifiers: 1 timed lap per pilot (20 min)\n"
          + "3) Bracket: top 8 or top 16 (30–45 min)\n"
          + "4) Finals + podium screenshot (10 min)\n\n"
          + "Roles: 1 host, 1 ref, 1 recorder.\n"
          + "I can draft rules + bracket format next.";
    }

    if (lower.equals("2") || lower.contains("bounty gauntlet")) {
      return "Locked in — **Bounty Gauntlet** runbook:\n\n"
          + "1) Form 2–4 squads\n"
          + "2) 3 rounds, 20 min each\n"
          + "3) Points: contract complete (3), team wipe (2), no deaths bonus (1)\n"
          + "4) Final showdown between top 2 squads\n\n"
          + "Best for 6–16 players. I can generate a scoreboard template next.";
    }

    if (lower.equals("3") || lower.contains("mining madness")) {
      return "Great pick — **Mining Madness** runbook:\n\n"
          + "1) Teams: 2–4 crews (MOLE/Prospector mix)\n"
          + "2) Timer: 45 minutes mining + return\n"
          + "3) Scoring:\n"
          + "   • Total sell value = base score\n"
          + "   • +10% bonus for zero ship losses\n"
          + "   • +5% bonus for best efficiency (value per crew member)\n"
          + "4) Debrief + MVP awards\n\n"
          + "Suggested roles: pilot, scanner, laser operator, hauler.\n"
          + "Want me to produce a ready-to-post Discord event brief?";
    }

    if (lower.equals("4") || lower.contains("chaos ops") || lower.contains("explain chaos ops")) {
      return "Here’s **Chaos Ops** in plain terms:\n\n"
          + "A mixed challenge night with short rounds and points.\n"
          + "Example 4-round format:\n"
          + "1) Fast bounty clear\n"
          + "2) Cargo sprint\n"
          + "3) Mining micro-challenge\n"
          + "4) PvP or obstacle finale\n\n"
          + "Each round awards points; highest total wins. It works great when players have different ships/skills.\n"
          + "Want a 60-minute or 90-minute version?";
    }

    if (lower.contains("what kind of event should we plan")
        || lower.equals("what kind of event")
        || lower.equals("what event")
        || lower.equals("game")) {
      return "For your group, I’d run **Mining Madness** or **Chaos Ops**.\n\n"
          + "• Pick **Mining Madness** if you want teamwork + profit race.\n"
          + "• Pick **Chaos Ops** if you want variety + high energy.\n\n"
          + "Reply with `3` for Mining Madness or `4` for Chaos Ops and I’ll give the full schedule.";
    }

    return null;
  }

  private static String tryLowPlaytimePrompt(String prompt) {
    String lower = prompt.toLowerCase(Locale.ROOT);
    if (!lower.contains("haven't had")
        && !lower.contains("havent had")
        && !lower.contains("no time")
        && !lower.contains("didn't have time")
        && !lower.contains("didnt have time")
        && !lower.contains("sadly")) {
      return null;
    }
    return "Totally fair — real life wins sometimes.\n\n"
        + "If you only have **30–45 mins**, try:\n"
        + "1) 1 fast bounty chain\n"
        + "2) 1 short cargo loop\n"
        + "3) 1 social event mini-round\n\n"
        + "I can build you a **quick-session plan** based on how many players you’ll have tonight.";
  }
}
