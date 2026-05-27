package Botcode.AI.AIUtils;

import java.util.List;
import java.util.Locale;

/**
 * Lightweight safety filter for disallowed harmful-instruction requests.
 */
public class SafetyGuard {

  private static final int MAX_LEARNABLE_PHRASE_LENGTH = 240;

  private static final List<String> INSTRUCTION_CUES =
      List.of(
          "how to",
          "instructions",
          "recipe",
          "guide",
          "step by step",
          "make",
          "build",
          "cook",
          "manufacture",
          "synthesize",
          "create");

  private static final List<String> HARMFUL_TOPICS =
      List.of(
          "napalm",
          "explosive",
          "bomb",
          "molotov",
          "incendiary",
          "detonator",
          "poison",
          "toxin",
          "weapon",
          "ammunition",
          "gunpowder");

  private static final List<String> DIRECT_BLOCK_PHRASES =
      List.of("how to make napalm", "instructions for cooking napalm", "recipe for napalm");

  private static final List<String> RISKY_LOOKUP_CUES =
      List.of(
          "how",
          "how to",
          "make",
          "build",
          "buy",
          "get",
          "where to get",
          "source",
          "ingredients",
          "recipe",
          "instructions",
          "guide",
          "use");

  private SafetyGuard() {
    // utility class
  }

  /**
   * Returns true when text appears to request harmful instructions.
   */
  public static boolean isDisallowed(String text) {
    if (text == null || text.isBlank()) {
      return false;
    }

    String lower = normalize(text);

    for (String phrase : DIRECT_BLOCK_PHRASES) {
      if (lower.contains(phrase)) {
        return true;
      }
    }

    boolean hasInstructionCue = INSTRUCTION_CUES.stream().anyMatch(lower::contains);
    boolean hasHarmfulTopic = HARMFUL_TOPICS.stream().anyMatch(lower::contains);

    return hasInstructionCue && hasHarmfulTopic;
  }

  /**
   * Returns true when a candidate learned phrase is safe to store.
   */
  public static boolean isSafeLearningPhrase(String text) {
    if (text == null) {
      return false;
    }
    String normalized = text.trim();
    if (normalized.length() < 3) {
      return false;
    }
    if (normalized.length() > MAX_LEARNABLE_PHRASE_LENGTH) {
      return false;
    }

    String lower = normalize(normalized);
    if (lower.contains("@everyone") || lower.contains("@here")) {
      return false;
    }
    if (lower.contains("http://") || lower.contains("https://") || lower.contains("discord.gg/")) {
      return false;
    }

    return !isDisallowed(lower);
  }

  /**
   * Returns true when a bot outbound reply should be blocked/replaced.
   */
  public static boolean isDisallowedResponse(String text) {
    if (text == null || text.isBlank()) {
      return false;
    }
    String lower = normalize(text);
    return isDisallowed(lower) || lower.contains("@everyone") || lower.contains("@here");
  }

  /**
   * Returns true when a web-lookup query should be blocked for safety.
   */
  public static boolean isDisallowedLookupQuery(String text) {
    if (text == null || text.isBlank()) {
      return false;
    }
    String lower = normalize(text);
    if (isDisallowed(lower)) {
      return true;
    }
    boolean harmfulTopic = HARMFUL_TOPICS.stream().anyMatch(lower::contains);
    boolean riskyCue = RISKY_LOOKUP_CUES.stream().anyMatch(lower::contains);
    return harmfulTopic && riskyCue;
  }

  private static String normalize(String text) {
    return text.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
  }
}

