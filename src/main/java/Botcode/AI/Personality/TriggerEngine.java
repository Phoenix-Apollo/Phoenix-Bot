package Botcode.AI.Personality;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Detects high-level conversational intent from message text.
 *
 * <p>This module is rule-based today and can be swapped later without changing downstream
 * personality or sentence-generation flows.
 */
public class TriggerEngine {

  /**
   * Intent buckets consumed by profile and response generation systems.
   */
  public enum Intent {
    GREETING,
    FAREWELL,
    HOW_ARE_YOU,
    CASUAL_CHAT,
    BOT_IDENTITY,
    QUESTION,
    KNOWLEDGE_QUERY,
    COMPLAINT,
    PRAISE,
    HYPE,
    CONFUSION,
    NEUTRAL
  }

  // HOW_ARE_YOU patterns checked before the general keyword map.
  private static final List<String> howAreYouPatterns =
      List.of(
          "how are you",
          "how are u",
          "how r u",
          "how's it going",
          "hows it going",
          "how is it going",
          "how goes it",
          "how's it been",
          "how have you been",
          "how's everything",
          "how are things",
          "how's things",
          "hows things",
          "you doing",
          "u doing",
          "how do you feel",
          "you okay",
          "u okay",
          "you alright",
          "u alright",
          "what's up with you",
          "whats up with you");

  // CASUAL_CHAT patterns --” small talk about "what's new" etc.
  // Checked before the generic startsWith("what ") block so these get the right intent.
  private static final List<String> casualChatPatterns =
      List.of(
          "what is new",
          "what's new",
          "whats new",
          "anything new",
          "what's been going",
          "what's been happening",
          "what's happening",
          "what's good",
          "whats good",
          "what's going on",
          "whats going on",
          "been up to",
          "what have you been",
          "what are you up to",
          "wassup",
          "wussup",
          "sup bot",
          "yo sup",
          "what's up bot",
          "talk to me",
          "let's talk",
          "wanna chat",
          "want to chat",
          "bored",
          "boring",
          "im bored",
          "i'm bored",
          "entertain me",
          "lol",
          "lmao",
          "lmfao",
          "haha",
          "hahaha",
          "heh",
          "rofl",
          // Soft acknowledgements --” conversational continuations that deserve a casual reply
          // rather than the generic NEUTRAL "what can I do for you?"
          "i see",
          "makes sense",
          "that makes sense",
          "got it",
          "noted",
          "understood",
          "fair enough",
          "good point",
          "true that",
          "that's true",
          "interesting",
          "oh interesting",
          "wow",
          "no way",
          "for real",
          "nice one",
          "that's cool",
          "that's wild",
          "mind blown",
          "wild");

  // Continuation patterns --” "tell me more" and similar follow-ups map to QUESTION
  // so the bot prompts the user to specify what they want to expand on.
  private static final List<String> continuationPatterns =
      List.of(
          "tell me more",
          "tell me about",
          "elaborate",
          "go on",
          "keep going",
          "more details",
          "more info",
          "can you expand",
          "expand on that",
          "say more",
          "what else",
          "anything else you",
          "can you explain more",
          "explain that",
          "break it down",
          "break that down",
          "more on that",
          "continue",
          "keep talking",
          "go deeper",
          "deeper dive",
          "dig deeper");

  private static final List<String> followUpPatterns =
      List.of(
          "what about",
          "how about",
          "and what",
          "and also",
          "and for",
          "same for",
          "same with",
          "that one",
          "this one",
          "those",
          "can you compare",
          "compare it",
          "more on that",
          "details",
          "go deeper",
          "continue",
          "keep going",
          "go on",
          "risk it",
          "all in");

   private static final List<String> correctionPatterns =
       List.of(
           "that is wrong",
           "that's wrong",
           "you are wrong",
           "you're wrong",
           "not right",
           "incorrect",
           "wrong answer",
           "fix that",
           "correct that",
           "try again",
           "redo that",
           "not what i asked",
           "you missed",
           "learn this",
           "learn from this",
           "learn from that",
           "improve this",
           "improve that",
           "improve your answer",
           "make corrections",
           "correct yourself",
           "fix your answer",
           "update that",
           "you are not learning",
           "you're not learning",
           "your not learning",
           "not learning");

   // KNOWLEDGE_QUERY patterns --” factual queries that need informative tone, not personality fluff
   private static final List<String> knowledgeQueryPatterns =
       List.of(
           "what is the time",
           "what time is it",
           "what's the time",
           "whats the time",
           "current time",
           "time now",
           "what date is it",
           "what is today",
           "what day is it",
           "what's today",
           "definition of",
           "how do you",
           "how do i",
           "how do we",
           "how to",
           "what is a",
           "what does",
           "explain",
           "can you define",
           "capital of",
           "population of",
           "area of",
           "distance to");

   // Identity patterns are checked before generic QUESTION routing.
  private static final List<String> botIdentityPatterns =
      List.of(
          "your name",
          "what is your name",
          "whats your name",
          "who are you",
          "do you know your name",
          "what should i call you",
          "what are you called");

  // PRAISE/thanks patterns checked before general keyword map so "thanks" doesn't
  // fall into NEUTRAL.
  private static final List<String> praisePatterns =
      List.of("thank you", "thanks a lot", "thanks so much", "many thanks", "big thanks");

  // Keyword groups used for simple intent routing.
  // LinkedHashMap preserves insertion order, giving deterministic priority.
  private static final Map<Intent, List<String>> intentKeywords;

  static {
    intentKeywords = new LinkedHashMap<>();
    intentKeywords.put(
        Intent.GREETING,
        List.of(
            "hello",
            "hi",
            "hey",
            "sup",
            "yo",
            "greetings",
            "good morning",
            "good afternoon",
            "good evening",
            "morning",
            "afternoon",
            "evening",
            "howdy",
            "hiya"));
    intentKeywords.put(
        Intent.FAREWELL,
        List.of(
            "bye",
            "goodbye",
            "good night",
            "goodnight",
            "gnight",
            "gnite",
            "gn",
            "cya",
            "see ya",
            "see you",
            "later",
            "laters",
            "peace",
            "ttyl",
            "gtg",
            "take care",
            "have a good one",
            "good one",
            "bbl",
            "afk",
            "signing off",
            "log off",
            "heading out"));
    intentKeywords.put(
        Intent.PRAISE,
        List.of(
            "nice",
            "awesome",
            "great",
            "love",
            "cool",
            "perfect",
            "excellent",
            "amazing",
            "brilliant",
            "well done",
            "good job",
            "good bot",
            "thanks",
            "thank you",
            "ty",
            "thx",
            "cheers",
            "appreciate",
            "helpful",
            "you rock",
            "you're the best"));
    intentKeywords.put(
        Intent.HYPE,
        List.of(
            "hype",
            "pog",
            "poggers",
            "lets go",
            "let's go",
            "sweet",
            "can't wait",
            "so ready",
            "hyped",
            "pumped",
            "stoked",
            "hype train",
            "hype mode",
            "omg yes",
            "lets freaking go"));
    intentKeywords.put(
        Intent.COMPLAINT,
        List.of(
            "hate",
            "broken",
            "crash",
            "lag",
            "bug",
            "annoying",
            "error",
            "not working",
            "doesn't work",
            "won't work",
            "issue",
            "problem",
            "fix this",
            "broken again",
            "keeps crashing",
            "fps drop",
            "missing",
            "missing options",
            "needs more options",
            "not enough options",
            "server down",
            "glitch",
            "stuck"));
    intentKeywords.put(
        Intent.CONFUSION,
        List.of(
            "idk",
            "i'm lost",
            "huh",
            "confused",
            "doesn't make sense",
            "what do you mean",
            "not sure",
            "lost me",
            "no idea",
            "what?",
            "come again",
            "say that again",
            "unclear"));
  }

  /**
   * Returns the best-matching intent, or NEUTRAL when no keyword matches. HOW_ARE_YOU is checked
   * first so "how are you?" doesn't fall into QUESTION.
   */
  public static Intent detectIntent(String message) {
    if (message == null || message.isEmpty()) {
      return Intent.NEUTRAL;
    }

    String lower = message.toLowerCase().trim();

    // Bare "?" alone means the user is confused or echoing, not asking a question.
    if (lower.equals("?") || lower.equals("??") || lower.equals("???")) {
      return Intent.CONFUSION;
    }

    // Check HOW_ARE_YOU patterns before general keyword map.
    for (String pattern : howAreYouPatterns) {
      if (lower.contains(pattern)) {
        return Intent.HOW_ARE_YOU;
      }
    }

    // Support brief follow-up like: "I am and you?"
    if (lower.endsWith("and you?") || lower.endsWith("and u?")) {
      return Intent.HOW_ARE_YOU;
    }

    // Check CASUAL_CHAT before general QUESTION routing so "what is new" etc. aren't
    // misidentified as questions needing a factual answer.
    for (String pattern : casualChatPatterns) {
      if (lower.contains(pattern)) {
        return Intent.CASUAL_CHAT;
      }
    }

     // Check identity patterns before generic QUESTION routing.
     for (String pattern : botIdentityPatterns) {
       if (lower.contains(pattern)) {
         return Intent.BOT_IDENTITY;
       }
     }

     // Check KNOWLEDGE_QUERY patterns before generic QUESTION routing so "what is X"
     // gets the right tone (informative, not chatty).
     for (String pattern : knowledgeQueryPatterns) {
       if (lower.contains(pattern)) {
         return Intent.KNOWLEDGE_QUERY;
       }
     }

     // Check multi-word praise/thanks patterns before keyword map iteration
    // so "thank you" doesn't collapse to NEUTRAL.
    for (String pattern : praisePatterns) {
      if (lower.contains(pattern)) {
        return Intent.PRAISE;
      }
    }

    // Continuation phrases ("tell me more", "elaborate") -†’ QUESTION so the bot
    // prompts the user to specify what they want expanded.
    for (String pattern : continuationPatterns) {
      if (lower.contains(pattern)) {
        return Intent.QUESTION;
      }
    }

    if (isCorrectionSignal(lower)) {
      return Intent.COMPLAINT;
    }

    // Prioritize general question forms before keyword-map iteration.
    if (lower.contains("?")
        || lower.startsWith("how ")
        || lower.startsWith("what ")
        || lower.startsWith("where ")
        || lower.startsWith("why ")
        || lower.startsWith("when ")
        || lower.startsWith("can ")
        || lower.startsWith("could ")
        || lower.startsWith("do ")
        || lower.startsWith("is ")) {
      return Intent.QUESTION;
    }

    // Iterate each bucket in insertion order --” short-circuit on first keyword hit.
    for (Map.Entry<Intent, List<String>> entry : intentKeywords.entrySet()) {
      for (String keyword : entry.getValue()) {
        if (matchesKeyword(lower, keyword)) {
          return entry.getKey();
        }
      }
    }

    return Intent.NEUTRAL;
  }

  /**
   * Returns true when text looks like a short continuation of prior context.
   */
  public static boolean isFollowUpSignal(String message) {
    if (message == null || message.isBlank()) {
      return false;
    }
    String lower = message.toLowerCase().trim();

    if (lower.equals("and?") || lower.equals("details") || lower.equals("same?")) {
      return true;
    }

    for (String token : followUpPatterns) {
      if (lower.contains(token)) {
        return true;
      }
    }

    // Very short prompts are often contextual follow-ups.
    if (lower.length() <= 24 && (lower.startsWith("and ") || lower.startsWith("also "))) {
      return true;
    }

    return false;
  }

  public static boolean isCorrectionSignal(String message) {
    if (message == null || message.isBlank()) {
      return false;
    }
    String lower = message.toLowerCase().trim();
    for (String token : correctionPatterns) {
      if (lower.contains(token)) {
        return true;
      }
    }
    return false;
  }

  /**
   * Matches keywords safely so single words use word boundaries and don't match substrings.
   * Example: keyword "yo" should not match inside "your".
   */
  private static boolean matchesKeyword(String lowerMessage, String keyword) {
    String k = keyword.toLowerCase();
    if (k.contains(" ") || k.contains("?") || k.contains("'")) {
      return lowerMessage.contains(k);
    }
    String regex = "\\b" + Pattern.quote(k) + "\\b";
    return Pattern.compile(regex).matcher(lowerMessage).find();
  }
}


