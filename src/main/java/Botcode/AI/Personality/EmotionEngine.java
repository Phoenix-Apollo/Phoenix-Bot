package Botcode.AI.Personality;

import java.util.List;
import java.util.Map;

/**
 * Detects coarse emotional tone from message keywords.
 *
 * <p>This is a lightweight rule-based layer used before personality profile selection.
 */
public class EmotionEngine {

  /**
   * Supported emotion buckets used by downstream personality logic.
   */
  public enum Emotion {
    NEUTRAL,
    HAPPY,
    FRUSTRATED,
    SAD,
    HYPED,
    ANGRY
  }

  // Keyword groups mapped to each emotion bucket.
  // Ordered: stronger signals (multi-word, unique terms) before weak single-word ones.
  private static final Map<Emotion, List<String>> emotionKeywords =
      Map.of(
          Emotion.HYPED,
          List.of(
              "hype",
              "pog",
              "poggers",
              "lets go",
              "let's go",
              "can't wait",
              "excited",
              "so ready",
              "hyped",
              "pumped",
              "stoked",
              "omg yes",
              "lets freaking go",
              "hype train"),
          Emotion.ANGRY,
          List.of(
              "pissed",
              "pissed off",
              "fuck this",
              "screw this",
              "i hate this",
              "so angry",
              "furious"),
          Emotion.FRUSTRATED,
          List.of(
              "wtf",
              "hate",
              "annoying",
              "crash",
              "lag",
              "bug",
              "broken",
              "not working",
              "doesn't work",
              "error",
              "glitch",
              "won't work",
              "keeps crashing",
              "frustrating",
              "ugh"),
          Emotion.SAD,
          List.of(
              "unlucky",
              "sad",
              "rip",
              "lost everything",
              "fml",
              "that sucks",
              "gutted",
              "devastating",
              "awful",
              "terrible"),
          Emotion.HAPPY,
          List.of(
              "gg",
              "nice",
              "awesome",
              "love this",
              "fun",
              "great",
              "lol",
              "lmao",
              "haha",
              "hahaha",
              "rofl",
              "made my day",
              "so good",
              "love it",
              "perfect",
              "excellent",
              "brilliant"));

  /**
   * Returns the first matching emotion for a message, or NEUTRAL when no keyword matches.
   */
  public static Emotion detectEmotion(String message) {
    if (message == null || message.isEmpty()) {
      return Emotion.NEUTRAL;
    }
    String lower = message.toLowerCase();

    // Scan all keyword buckets and return the first match encountered.
    for (Map.Entry<Emotion, List<String>> entry : emotionKeywords.entrySet()) {
      for (String keyword : entry.getValue()) {
        if (lower.contains(keyword)) {
          return entry.getKey();
        }
      }
    }

    return Emotion.NEUTRAL;
  }
}


