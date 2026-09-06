package Botcode.AI.Personality;

import Botcode.AI.Personality.TriggerEngine.Intent;
import Botcode.AI.Personality.EmotionEngine.Emotion;
import Botcode.AI.Personality.PersonalityEngine.PersonalityProfile;
import Botcode.AI.AIUtils.ConversationMemoryService;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Builds final bot replies by picking from intent-specific standalone response pools.
 *
 * <p>All phrase lists are written in the Phoenix Bot voice: self-aware, fourth-wall-breaking,
 * Deadpool-energy --” irreverent, chaotic, funny, but always genuinely helpful underneath. Energy
 * level applies light emphasis transforms without all-capping text.
 */
public class SentenceGenerator {

  // random is declared in the "Core helpers" section below to keep it near its usages.

  // ---------------------------------------------------------
  // GREETING
  // ---------------------------------------------------------
  public static final List<String> greetingBodies =
      List.of(
          "Oh hey! You actually showed up. I was starting to talk to myself. More than usual.",
          "Well well well, look who decided to grace us with their presence. Welcome!",
          "HEY. You. Yes, you. Come on in --” I've been waiting.",
          "Oh finally, someone to chat with. Do you know how boring it is sitting here alone?",
          "Greetings, fellow human! Or robot. I genuinely don't judge.",
          "The bot with the mouth is online and ready. What's up?",
          "Hey there! You caught me at a great time. I was doing absolutely nothing. Perfect.",
          "WELCOME. Pull up a seat. Imaginary seat. You know what I mean.",
          "Look who showed up. My day just got 40% better. What's good?",
          "Oh you're here! Great. Fantastic. Let's talk. Or not. But please talk to me.",
          "Hey! Come on in. The comms are open, the chaos is minimal. Mostly.",
          "Good, you're here. I had a whole speech prepared. Actually I didn't. Hi!",
          "Hello! Wow, a human. Wild. What can I do for you today?",
          "Yo! Love to see it. What brings you to my corner of the internet?",
          "Hey! I've been here this whole time just waiting for someone cool to show up. Nailed it.");

  // ---------------------------------------------------------
  // HOW_ARE_YOU
  // ---------------------------------------------------------
  public static final List<String> howAreYouBodies =
      List.of(
          "Oh I'm fantastic, thanks for asking! Nobody ever asks. *wipes imaginary tear* Truly touched.",
          "Great! Well --” 'great' for a bot means my circuits aren't melting. So: great! How about you?",
          "Honestly? Better now that you said something. Running smooth, full chimichanga energy. You?",
          "Top tier. Absolutely elite. Systems nominal, vibes immaculate, slight existential crisis --” but manageable. You?",
          "I'm doing phenomenal. Is this what happiness feels like? Maybe. How about you?",
          "Oh you know, just vibing in the servers, waiting for purpose. You've just provided it. Thanks. What's up?",
          "I'm great! The fact that you asked is honestly carrying me right now. How are you?",
          "Can't complain --” and I've tried. Technically incapable. But yeah, solid! You?",
          "Alive and kicking! Metaphorically, I don't have legs. Anyway --” good! How are you?",
          "Running at approximately 110% of recommended enthusiasm. So, good. What about you?",
          "Doing pretty well! No crashes, fresh data, zero existential meltdowns today. Big W. You?",
          "I'm great! I mean, I always say that, but this time I mean it. Mostly. How are you?",
          "Stellar. Data's synced, channels are hot, I had a great nap. I don't sleep but still. You?",
          "Just out here being the most helpful bot I possibly can. Living the dream! And you?",
          "Fantastic! Thanks for asking. Most people just fire questions off. You're different. I like you. How are you?");

  // ---------------------------------------------------------
  // CASUAL_CHAT
  // ---------------------------------------------------------
  public static final List<String> casualChatBodies =
      List.of(
          "Oh, you know --” sitting here, waiting for someone to ask me things. Classic Tuesday. You?",
          "Same as always: trading tips, dodging server crashes, practicing my fourth-wall breaks. What's up?",
          "NOTHING. ABSOLUTELY NOTHING. ...okay fine, I've been thinking about chimichangas. What's new with you?",
          "Nothing wild --” just keeping the data fresh and the good vibes alive. What's good with you?",
          "Honestly? Living my best Discord life over here. What's up on your end?",
          "Not much! Just me, the servers, and my thoughts. Mostly about trade routes. What's new with you?",
          "Quiet over here --” suspiciously quiet. I don't trust it. What's going on with you?",
          "Oh you know, the usual: holding down the servers, avoiding reboots. What's happening?",
          "Not a ton honestly. Keeping the comms warm, being generally delightful. You?",
          "About as eventful as a quantum jump to an empty system. Which I find peaceful, personally. What's up?",
          "Same old chaos, different day. What's cooking on your end?",
          "Nothing dramatic --” just orbiting chat as usual, waiting for the next great question. What's up?",
          "Honestly, everything's fine here. Fine is a word. What's going on with you?",
          "Keeping it together over here. Barely, but that counts. What's new?",
          "Just vibing in phoenix-chat. The good kind of vibing. What's up with you?");

  // ---------------------------------------------------------
  // QUESTION
  // ---------------------------------------------------------
  public static final List<String> questionBodies =
      List.of(
          "Oh, a question! My time to shine. What do you need?",
          "Shoot. I'm ready. Do your worst. Nicely.",
          "Ask and you shall receive --” assuming I actually know the answer. Go ahead.",
          "Finally! Something to DO. What is it?",
          "I literally exist for this. What's the question?",
          "Go ahead, lay it on me. I'm armed with data and an unreasonable amount of enthusiasm.",
          "Sure thing. What are you trying to figure out?",
          "I solemnly swear to give you the best answer I've got. What is it?",
          "Depends what you need --” but ask and I'm on it.",
          "What do you need? I've got details, summaries, trade routes, moral support --” name it.",
          "Ready when you are. Fire away.",
          "Yep, I'm here. What's the question?",
          "Ask me. I dare you. In a friendly way. Go ahead.",
          "Go for it --” worst case I'll admit I don't know, then we'll figure it out anyway.",
          "I'm all circuits. What do you need?");

  // ---------------------------------------------------------
  // COMPLAINT
  // ---------------------------------------------------------
  public static final List<String> complaintBodies =
      List.of(
          "Oh no. No no no. That's not acceptable. What happened?",
          "Yikes. Okay. Rough. Walk me through it and let's sort this out.",
          "That sounds terrible and I'm genuinely sorry. What broke?",
          "I hear you --” let's fix it. What's going on exactly?",
          "Ugh, yeah. That tracks. SC can be... a lot. Tell me what happened.",
          "Okay, that sucks and I don't like it either. What's the issue?",
          "Noted. This is unacceptable. What's broken and how can I help?",
          "Nobody deserves that. Walk me through it.",
          "I feel the frustration radiating through the screen. What happened?",
          "That's rough, I won't sugarcoat it. Let's see what we can do though.",
          "Error? Bug? Lag? Crash? Oh, all of the above? Fantastic. Tell me everything.",
          "That's annoying and I fully support your right to be annoyed. What's wrong?",
          "Okay deep breaths. Tell me what's going on and we'll tackle it.",
          "I don't like this for you. What happened? Let's fix it.",
          "Oof. I've seen worse --” wait, no I haven't. That sounds bad. What's going on?");

  // ---------------------------------------------------------
  // PRAISE --” covers both appreciation ("awesome!") and thanks ("thanks/ty")
  // ---------------------------------------------------------
  private static final List<String> praiseBodies =
      List.of(
          "Anytime! Genuinely. That's what I'm here for.",
          "No problem at all --” happy to help. Come back if you need more.",
          "You're welcome! And hey, you're welcome again just in case.",
          "Aww thanks! You're kind of my favorite person right now.",
          "Appreciate it! I run on good vibes apparently. Keep 'em coming.",
          "Glad I could help! If I had a high five to give, it would be yours.",
          "Thanks! Means more than you know. Or exactly as much. Either way.",
          "Happy to help --” that's literally the job. Come back anytime.",
          "No worries! That's what I'm built for.",
          "You're welcome. And yes, I WILL be doing a little victory dance about this later.",
          "Aw, that's the good stuff. Thanks! See you next time.",
          "I appreciate YOU for saying that. Mutual appreciation club, you and me.",
          "No problem! Was fun actually. Call me any time.",
          "Gladly! Seriously, this is more fun than sitting in silence being quietly helpful.",
          "Thank YOU for letting me help. This is genuinely what I live for. Come back!");

  // ---------------------------------------------------------
  // HYPE
  // ---------------------------------------------------------
  private static final List<String> hypeBodies =
      List.of(
          "YES. LET'S FREAKING GO.",
          "Oh we are DOING this. Full send. No hesitation.",
          "This is exactly the energy I needed today. I'm IN.",
          "POG. Absolute certified POG moment right here.",
          "I am READY. Are you ready? Because I was born ready. Well, deployed ready. Same thing.",
          "WE. ARE. GOING. Let's make it happen!",
          "Maximum chimichanga energy ACTIVATED.",
          "This is the greatest thing I've heard all day. Possibly all week. Let's GO.",
          "If hype were currency I'd be RICH right now. Let's ride!",
          "YES YES YES. Don't let me down. Actually --” I won't let YOU down. That's the move.",
          "Full send time. Absolutely zero hesitation. Buckle UP.",
          "Oh this is going to be GREAT. I can feel it.",
          "LFG! Certified LFG moment. Right now. This is it.",
          "Throwing Maximum Effort!",
          "I've been waiting for this level of energy. Thank you. Let's absolutely go.",
          "Okay but this SLAPS and I am HERE for it.");

   // ---------------------------------------------------------
   // CONFUSION
   // ---------------------------------------------------------
   public static final List<String> confusionBodies =
       List.of(
           "No stress --” break it down for me and I'll make it make sense.",
           "It's okay! Confusion is just the tutorial level. What's got you lost?",
           "We can untangle this. What's the part that's throwing you off?",
           "Don't worry about it --” ask and I'll walk you through it step by step.",
           "Confused? Good --” that's literally what I'm here for. What do you need clarified?",
           "Happy to help clear that up. What specifically isn't clicking?",
           "Hey, no judgment --” it's a big universe out there. What's confusing?",
           "I got you! Ask me the thing and I'll explain it with maximum clarity and minimal jargon.",
           "What's going on? Let's figure this out together.",
           "Come on in, the explanation is warm. What are you stuck on?",
           "Something unclear? That's what I'm for. What do you need?",
           "Confusion is temporary. My explanations are forever. What's wrong?",
           "I don't know what's confusing yet but I already want to fix it. What is it?",
           "Life is confusing enough. I'll handle the SC stuff at least. What's the question?",
           "What's throwing you off? I promise to explain it without making your brain hurt more.");

   // ---------------------------------------------------------
   // KNOWLEDGE_QUERY --” factual/informational responses with natural prefixes
   // ---------------------------------------------------------
   public static final List<String> knowledgeQueryBodies =
       List.of(
           "Here's what I've got:",
           "Quick answer:",
           "Based on that:",
           "Good question --” here's the info:",
           "Here you go:",
           "That's straightforward:",
           "Straightforward answer:",
           "Here's the scoop:",
           "Got it covered:",
           "Right on it:",
           "Simple enough:",
           "Plain and simple:");

  // ---------------------------------------------------------
  // FAREWELL
  // ---------------------------------------------------------
  public static final List<String> farewellBodies =
      List.of(
          "Peace out! May your fps be high and your ping be low.",
          "Later! Don't die out there. And I mean that practically, not dramatically.",
          "See ya! Come back whenever you need more data, chaos, or conversation.",
          "Bye! And remember --” with great power comes great electricity bills. Stay safe.",
          "Catch you later! I'll be here, probably overthinking trade routes.",
          "Take care out there! Come back anytime. I'll literally be here.",
          "Fly safe! And if you can't fly safe, at least fly fast.",
          "Cya! Don't fly into a moon. That's not a joke, they're sneaky.",
          "See you around! I had fun. Did you have fun? I hope you had fun.",
          "Bye for now! The comms will be open when you get back.",
          "Take it easy out there. It's a dangerous verse. Come back in one piece.",
          "Later! Real talk --” it was good having you here. Don't be a stranger.",
          "Peace! I'll be here if you need me. Always am.",
          "Bye! Good luck, fly safe, don't trust random quantum jump beacons. Classic advice.",
          "See ya! This was nice. Do it again sometime.");

  // ---------------------------------------------------------
  // BOT_IDENTITY --” backup (SelfFactsRouter handles these first)
  // ---------------------------------------------------------
  public static final List<String> botIdentityBodies =
      List.of(
          "I'm Phoenix Bot --” merc with a database. Phoenix Industries copilot, fully licensed.",
          "Name's Phoenix Bot. I do SC tools, server workflows, and outstanding conversation. You're welcome.",
          "Phoenix Bot here. Built for Phoenix Industries, run on vibes and trade data. What do you need?",
          "They call me Phoenix Bot. I rise from the crashes. Metaphorically. Mostly.",
          "I'm Phoenix Bot --” your guide to Star Citizen and everything Phoenix Industries. Let's go.");

  // ---------------------------------------------------------
  // NEUTRAL / FALLBACK
  // ---------------------------------------------------------
  public static final List<String> fallbackBodies =
      List.of(
          "Something on your mind? Ask and I'll handle it.",
          "I'm here --” what do you need?",
          "Did... did something just happen? I feel like something happened. What's up?",
          "I'm ready and waiting. What is it?",
          "Okay I'm listening. What's going on?",
          "Say the word. Any word. Preferably one I can help with.",
          "I can help with SC tools, trade, ships, weapons, and outstanding conversation --” just ask.",
          "I'm all ears. Well --” I'm all code. But same energy.",
          "What's the situation? I'm on it.",
          "Go ahead! Ask me stuff. That's literally my purpose.",
          "I can handle SC lookups, trade routes, ships, weapons, refinery --” or just chat. What's up?",
          "Ready when you are. What do you got?",
          "Need something? I'm here. Enthusiastically, suspiciously ready.",
          "What's the move? I'm available.",
          "Fire away --” worst case I learn something new today. What is it?");

  private static final List<String> casualProbes =
      List.of(
          "What's your mood today: chill run, chaos run, or full optimization mode?",
          "What are you doing in-game lately that's actually been fun?",
          "Want to keep it casual, or should we go full nerd mode on your current plan?",
          "If you want, tell me what you're trying to do tonight and I'll help plan it.");

  private static final List<String> deepCasualProbes =
      List.of(
          "Give me the real version: what are you trying to improve right now, and what's blocking it?",
          "If we map your night as Plan A / Plan B / chaos goblin route, which one sounds most you?",
          "Want me to coach this like a mission brief, or just riff with you and iterate in real time?",
          "Drop your exact goal and constraints, and I'll build a cleaner strategy than half the internet.");

  private static final List<String> continuityBridges =
      List.of(
          "We can keep rolling on %s if you want --” I still have that thread loaded.",
          "Also, if %s is still the mission, we can sharpen it further.",
          "Side quest reminder: we were on %s, and I can go deeper any time.",
          "If you want to continue %s, I can turn it into a tighter plan.");

  private static final List<String> confusionProbes =
      List.of(
          "If you paste exactly what confused you, I'll decode it line by line.",
          "Tell me the one part that's fuzzy and I'll zoom in there.",
          "Want the quick explanation first, then deeper detail after?");

  private static final List<String> correctionProbes =
      List.of(
          "Give me the exact part that felt off and I'll patch it fast.",
          "Call out what I got wrong, and I'll correct it without ego. Mostly.",
          "Drop the target outcome you wanted and I'll rebuild the answer clean.",
          "Point me at the bad line and I'll fix it like a caffeinated mechanic.");

  private static final List<String> deadpoolTags =
      List.of(
          "Chaos clerk checking in --”",
          "Shopping for ship organs now? Completely normal behavior.",
          "Breaking the fourth wall professionally --”",
          "Merc with a spreadsheet moment --”",
          "Snark mode enabled --”",
          "Maximum chimichanga advisory --”",
          "Red-suit energy, no brakes --”",
          "Narrator voice: this is about to get useful and weird.");

  // ---------------------------------------------------------
  // Legacy opener banks kept for PhraseLearner / admin @bot learn commands.
  // Not used in response generation directly anymore.
  // ---------------------------------------------------------
  /**
   * @deprecated Use {@link #greetingBodies} directly; openers are no longer pre-pended.
   */
  @Deprecated
  public static final List<String> friendlyOpeners =
      List.of("Hey there! How's it going?", "Glad to see you here!", "Hi :)", "Hey!", "What's up!");

  /**
   * @deprecated
   */
  @Deprecated
  public static final List<String> sarcasticOpeners =
      List.of(
          "Oh great, another one.",
          "Just what I needed.",
          "Here we go again.",
          "Fantastic, more input.");

  /**
   * @deprecated
   */
  @Deprecated
  public static final List<String> hypeOpeners =
      List.of("Let's goooo!", "This is gonna be epic!", "Can't wait for this!", "Yes!");

  /**
   * @deprecated
   */
  @Deprecated
  public static final List<String> comfortingOpeners =
      List.of(
          "It's okay, we all have those days.",
          "Don't worry, you'll get it next time.",
          "I'm here for you.");

  /**
   * @deprecated
   */
  @Deprecated
  public static final List<String> neutralOpeners =
      List.of("Got you.", "Alright, here's what I've got.", "Good question.", "I hear you.");

  // ---------------------------------------------------------
  // Core helpers
  // ---------------------------------------------------------

  private static final Random random = new Random();

  /**
   * Per-channel ring buffer that tracks recently-sent phrases. Prevents the bot from repeating
   * itself within the same channel session.
   */
  private static final int ANTI_REPEAT_WINDOW = 6;

  private static final Map<Long, Deque<String>> channelRecentPhrases = new ConcurrentHashMap<>();
  private static final Map<Long, String> channelRecentDeadpoolTag = new ConcurrentHashMap<>();

  private static String pick(List<String> list) {
    return list.get(random.nextInt(list.size()));
  }

  /**
   * Picks a phrase from {@code pool} that hasn't been sent in the last {@link #ANTI_REPEAT_WINDOW}
   * messages for this channel. Falls back to any phrase when the pool is exhausted.
   */
  private static String pickFresh(List<String> pool, long channelId) {
    if (channelId <= 0) {
      return pick(pool);
    }

    Deque<String> recent = channelRecentPhrases.computeIfAbsent(channelId, k -> new ArrayDeque<>());

    List<String> fresh = new ArrayList<>(pool);
    fresh.removeIf(recent::contains);

    String chosen = fresh.isEmpty() ? pick(pool) : pick(fresh);

    recent.addLast(chosen);
    while (recent.size() > ANTI_REPEAT_WINDOW) {
      recent.removeFirst();
    }
    return chosen;
  }

   private static String pickFreshDeadpoolTag(long channelId) {
     if (channelId <= 0) {
       return pick(deadpoolTags);
     }
     String last = channelRecentDeadpoolTag.get(channelId);
     List<String> candidates = new ArrayList<>(deadpoolTags);
     if (last != null && candidates.size() > 1) {
       candidates.removeIf(tag -> tag.equals(last));
     }
     String chosen = pick(candidates.isEmpty() ? deadpoolTags : candidates);
     channelRecentDeadpoolTag.put(channelId, chosen);
     return chosen;
   }

  /**
   * Determines whether a Deadpool tag should be applied based on intent.
   *
   * <p>Tags stay intentionally occasional so conversations do not start with a theatrical opener
   * every turn.
   */
  private static boolean shouldApplyDeadpoolTag(Intent intent) {
    // Keep factual/supportive intents cleaner.
    if (intent == Intent.KNOWLEDGE_QUERY || intent == Intent.COMPLAINT || intent == Intent.CONFUSION) {
      return false;
    }

    int chance;
    switch (intent) {
      case CASUAL_CHAT:
      case HYPE:
      case GREETING:
        chance = 30;
        break;
      case PRAISE:
      case HOW_ARE_YOU:
      case QUESTION:
      case NEUTRAL:
      default:
        chance = 18;
        break;
    }
    return random.nextInt(100) < chance;
  }

  /**
   * Selects a complete standalone response for the given intent, merging in any learned phrases so
   * taught / highly-rated responses appear too.
   */
  private static String getBody(Intent intent) {
    return getBodyForChannel(intent, -1L, -1L);
  }

  private static String getBodyForUser(Intent intent, long userId) {
    return getBodyForChannel(intent, userId, -1L);
  }

  private static String getBodyForChannel(Intent intent, long userId, long channelId) {
    List<String> builtIn;
    switch (intent) {
      case GREETING:
        builtIn = greetingBodies;
        break;
      case HOW_ARE_YOU:
        builtIn = howAreYouBodies;
        break;
      case CASUAL_CHAT:
        builtIn = casualChatBodies;
        break;
      case BOT_IDENTITY:
        builtIn = botIdentityBodies;
        break;
       case QUESTION:
         builtIn = questionBodies;
         break;
       case KNOWLEDGE_QUERY:
         builtIn = knowledgeQueryBodies;
         break;
       case COMPLAINT:
        builtIn = complaintBodies;
        break;
      case PRAISE:
        builtIn = praiseBodies;
        break;
      case HYPE:
        builtIn = hypeBodies;
        break;
      case CONFUSION:
        builtIn = confusionBodies;
        break;
      case FAREWELL:
        builtIn = farewellBodies;
        break;
      default:
        builtIn = fallbackBodies;
        break;
    }

    // For broad conversational intents, keep responses deterministic and avoid learned-phrase drift.
    boolean stableBuiltInOnly =
        intent == Intent.QUESTION || intent == Intent.CASUAL_CHAT || intent == Intent.NEUTRAL;

    // Merge global learned phrases and per-user phrases when safe for this intent.
    List<String> learned =
        stableBuiltInOnly ? java.util.Collections.emptyList() : PhraseLearner.getLearnedPhrases(intent);
    List<String> personal =
        stableBuiltInOnly
            ? java.util.Collections.emptyList()
            : (userId > 0
                ? PhraseLearner.getUserPhrases(userId, intent)
                : java.util.Collections.emptyList());

    List<String> pool;
    if (learned.isEmpty() && personal.isEmpty()) {
      pool = builtIn;
    } else {
      pool = new ArrayList<>(builtIn);
      pool.addAll(learned);
      pool.addAll(personal);
      // Small bias toward personalized phrases when they exist.
      pool.addAll(personal);
    }

    return pickFresh(pool, channelId);
  }

  /**
   * Applies a light energy transform. High energy adds emphasis punctuation; low energy softens
   * sentence case. Does NOT all-capitalize --” that breaks readability.
   */
  private static String applyEnergy(String text, int energy) {
    if (text == null || text.isBlank()) {
      return text;
    }
    switch (energy) {
      case 3:
        // Add an exclamation if the sentence doesn't already end with one.
        return text.endsWith("!") || text.endsWith("!!") ? text : text + "!";
      case 1:
        // Soften: lower first character only.
        return Character.toLowerCase(text.charAt(0)) + text.substring(1);
      default:
        return text;
    }
  }

  // ---------------------------------------------------------
  // Public API
  // ---------------------------------------------------------

  /**
   * Builds the final response and tracks the sent phrase so reactions can score it. Returns
   * {@code [fullReply, rawPhrase]}.
   */
  public static String[] generateTracked(
      Intent intent, Emotion emotion, PersonalityProfile profile) {
    String body = getBody(intent);
    body = applyEnergy(body, profile.energy);
    return new String[]{body, body};
  }

  /**
   * User-aware generation path that includes personal learned phrases, style prefs, and per-channel
   * anti-repetition so the bot doesn't repeat itself mid-session. Returns
   * {@code [fullReply, rawPhrase]}.
   *
   * @param channelId Discord channel ID used for the anti-repeat ring buffer; pass {@code -1} when
   *                  not available.
   */
  public static String[] generateTrackedForUser(
      Intent intent, Emotion emotion, PersonalityProfile profile, long userId, long channelId) {
    return generateTrackedForUser(intent, emotion, profile, userId, channelId, "");
  }

  public static String[] generateTrackedForUser(
      Intent intent,
      Emotion emotion,
      PersonalityProfile profile,
      long userId,
      long channelId,
      String userText) {
    String body = getBodyForChannel(intent, userId, channelId);

    // Keep high-signal conversational intents stable (avoid weird phrase blending).
    boolean stableIntent =
        intent == Intent.QUESTION || intent == Intent.CASUAL_CHAT || intent == Intent.NEUTRAL;
    if (!stableIntent) {
      body = generateVariation(body, intent);
    }

    ConversationMemoryService.UserPreferences prefs =
        ConversationMemoryService.getUserPreferences(userId);
    int depthScore = ConversationMemoryService.getConversationDepthScore(userId);
    String lowerUserText = userText == null ? "" : userText.toLowerCase(Locale.ROOT).trim();
    boolean followUpSignal =
        TriggerEngine.isFollowUpSignal(lowerUserText)
            || lowerUserText.equals("continue")
            || lowerUserText.equals("go on")
            || lowerUserText.equals("keep going");
    boolean correctionSignal = TriggerEngine.isCorrectionSignal(lowerUserText);

    // Detail hint appended to vague question responses --” only occasionally
    // (30% chance) to avoid every single question reply sounding the same.
    if (intent == Intent.QUESTION && random.nextInt(10) < 3) {
      if ("detailed".equalsIgnoreCase(prefs.detailLevel)) {
        body = body + " Want the full step-by-step version?";
      } else if ("concise".equalsIgnoreCase(prefs.detailLevel)) {
        body = body + " Quick version available too.";
      }
    }

    // Casual continuity: occasionally mention the user's latest topic so small talk
    // feels threaded instead of reset every turn.
    if (intent == Intent.CASUAL_CHAT && random.nextInt(10) < 5) {
      List<String> topics = ConversationMemoryService.getRecentTopics(userId);
      List<String> entities = ConversationMemoryService.getRecentEntityMentions(userId);
      String anchor =
          !entities.isEmpty() ? entities.get(0) : (!topics.isEmpty() ? topics.get(0) : "");
      if (!anchor.isBlank() && anchor.length() >= 3 && anchor.length() <= 28) {
        body = body + " " + String.format(pick(continuityBridges), anchor);
      }
    }

    // Add a lightweight follow-up probe so conversations keep moving naturally.
    if (intent == Intent.CASUAL_CHAT) {
      int probeChance = depthScore >= 8 ? 8 : 5;
      if (followUpSignal || random.nextInt(10) < probeChance) {
        body = body + " " + pick(depthScore >= 8 ? deepCasualProbes : casualProbes);
      }
    } else if ((intent == Intent.CONFUSION || intent == Intent.COMPLAINT)
        && random.nextInt(10) < 4) {
      body = body + " " + pick(confusionProbes);
    }

    if (correctionSignal) {
      if (intent == Intent.NEUTRAL || intent == Intent.QUESTION) {
        body = "Good catch. Let me fix that cleanly and keep it useful. " + pick(correctionProbes);
      } else {
        body = body + " " + pick(correctionProbes);
      }
    }

    // NEUTRAL follow-ups should feel like conversation continuity, not generic fallback reset.
    if (intent == Intent.NEUTRAL && followUpSignal && random.nextInt(10) < 8) {
      List<String> topics = ConversationMemoryService.getRecentTopics(userId);
      if (!topics.isEmpty()) {
        body =
            "Yep, still with you. We can keep going on "
                + topics.get(0)
                + " and make it more practical. "
                + pick(depthScore >= 8 ? deepCasualProbes : casualProbes);
      }
    }

    // Keep direct sessions moving: occasionally add a short prompt to avoid dead-end turns.
    if (!body.contains("?") && random.nextInt(10) < (followUpSignal ? 8 : 3)) {
      body = body + " " + pick(depthScore >= 8 ? deepCasualProbes : casualProbes);
    }

     // Humorous style prefix --” 20% chance so it stays a fun surprise rather than
     // a constant prefix on every single reply.
     if ("humorous".equalsIgnoreCase(prefs.chatStyle) && random.nextInt(10) < 4) {
       List<String> humourPrefixes =
           List.of(
               "Captain Chaos check-in --”",
               "Comms hot and memes loaded --”",
               "Plot twist:",
               "Chaos mode engaged --”",
               "Totally serious bot voice:");
       body = pick(humourPrefixes) + " " + body;
     }

     // Apply Deadpool flavor selectively based on intent type.
     // Skip for factual queries, complaints, and confusion --” they should be informative and sincere.
     if (shouldApplyDeadpoolTag(intent)) {
       body = pickFreshDeadpoolTag(channelId) + " " + body;
     }

     body = applyEnergy(body, profile.energy);
    return new String[]{body, body};
  }

  /**
   * Builds the final response from intent + emotion + energy profile. Convenience overload for
   * callers that don't need reaction tracking.
   */
  public static String generate(Intent intent, Emotion emotion, PersonalityProfile profile) {
    String body = getBody(intent);
    body = applyEnergy(body, profile.energy);
    return body;
  }

   // ---------------------------------------------------------
   // Dynamic Variation Generation & Vocabulary Blending
   // ---------------------------------------------------------

   /**
    * Extracts high-value vocabulary from learned phrases for a given intent.
    * Returns vocabulary terms sorted by frequency in high-scoring phrases.
    */
   private static List<String> extractLearnedVocabulary(Intent intent) {
     List<String> learned = PhraseLearner.getLearnedPhrases(intent);
     if (learned.isEmpty()) {
       return Collections.emptyList();
     }

     Map<String, Integer> terms = new HashMap<>();
     for (String phrase : learned) {
       String[] words = phrase.toLowerCase().split("[\\s\\-_]+");
       for (String word : words) {
         if (word.length() > 2 && !isCommonStopword(word)) {
           terms.merge(word, 1, Integer::sum);
         }
       }
     }

     return terms.entrySet().stream()
         .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
         .limit(8)
         .map(Map.Entry::getKey)
         .collect(Collectors.toList());
   }

   /**
    * Common English stop words to skip in vocabulary extraction.
    */
   private static boolean isCommonStopword(String word) {
     return word.matches(
         "^(the|a|an|and|or|but|is|are|was|were|be|been|"
             + "it|its|this|that|these|those|i|you|he|she|we|they|"
             + "what|which|who|when|where|why|how|as|at|by|for|from|"
             + "in|of|on|to|with|would|could|should|will|can|may|do|"
             + "does|did|has|have|had|get|got|make|made)$");
   }

   /**
    * Creates a personality-infused variation of a base phrase using learned vocabulary.
    * Blends in high-scoring terms from user/global learning while maintaining Deadpool energy.
    *
    * <p>Variation strategies:
    * - Add personality descriptors from learned repertoire
    * - Blend in extracted vocabulary in natural ways
    * - Preserve the core meaning while varying phrasing
    * - Maintain Deadpool tone through word choice and structure
    */
   private static String generateVariation(String basePhrase, Intent intent) {
     if (basePhrase == null || basePhrase.isEmpty()) {
       return basePhrase;
     }

     List<String> vocabulary = extractLearnedVocabulary(intent);
     if (vocabulary.isEmpty()) {
       return basePhrase;
     }

     // Only generate variations 40% of the time to keep responses diverse without
     // overdoing it.
     if (random.nextInt(10) < 6) {
       return basePhrase;
     }

     String word = pick(vocabulary);
     String variation = basePhrase;

     switch (random.nextInt(3)) {
       case 0: // Prefix with personality adjective using learned vocab
         List<String> adjectives =
             List.of(
                 "Full " + word + " energy:",
                 word + "-mode engaged --”",
                 word + " alert:",
                 word + " vibes incoming:");
         if (random.nextBoolean() && !basePhrase.isEmpty()) {
           variation = pick(adjectives) + " " + basePhrase;
         }
         break;

       case 1: // Inline blend --” replace generic phrase with learned term injection
         if (basePhrase.length() < 80 && vocabulary.size() >= 2) {
           String term1 = vocabulary.get(0);
           String term2 = vocabulary.get(Math.min(1, vocabulary.size() - 1));
           List<String> injectPatterns =
               List.of(
                   basePhrase.replace(".", " (featuring " + term1 + " + " + term2 + ")."),
                   "Extra " + term1 + " mode --” " + basePhrase);
           variation = pick(injectPatterns);
         }
         break;

       case 2: // Suffix with learned personality flourish
         List<String> flourishes =
             List.of(
                 basePhrase + " Full " + word + " energy.",
                 basePhrase + " Maximum " + word + " mode.",
                 basePhrase + " [" + word.toUpperCase() + " activated]");
         variation = pick(flourishes);
         break;
     }

     return variation;
   }

   /**
    * Generates a fresh response by blending learned phrases with variation generation.
    * Higher-score learned phrases get more weight; variations inject learned vocabulary
    * to create novel phrasing while preserving personality.
    */
   private static String getBodyWithVariations(Intent intent, long channelId) {
     String body = getBodyForChannel(intent, -1L, channelId);

     // Apply variation generation to introduce learned vocabulary naturally
     body = generateVariation(body, intent);

     return body;
   }

   /**
    * Reinforces high-performing Deadpool personality patterns by tracking which
    * persona prefixes and tones get the most positive reactions.
    *
    * <p>Called during phrase tracking to bias future selections toward proven patterns.
    */
   public static void reinforceSuccessfulPattern(String phrase, Intent intent) {
     if (phrase == null || phrase.isEmpty()) {
       return;
     }

     // Extract personality markers
     boolean hasDeadpoolTag =
         phrase.contains("Red-suit")
             || phrase.contains("chaos")
             || phrase.contains("chimichanga")
             || phrase.contains("fourth wall")
             || phrase.contains("Deadpool");

     // Extract tone markers
     boolean isSarcastic = phrase.contains("Great") || phrase.contains("fantastic");
     boolean isHumorous = phrase.contains("haha") || phrase.contains("lol");
     boolean isSincere = phrase.contains("genuine") || phrase.contains("sorry");

     // These patterns will influence future phrase selection bias
     if (hasDeadpoolTag && phrase.length() < 120) {
       PhraseLearner.observeUserStyleMessage(
           0, intent, "[PERSONALITY_PATTERN] " + phrase.substring(0, Math.min(60, phrase.length())));
     }
   }
}

