package Botcode.listeners.Interests;

import Botcode.AI.Personality.NLPProcessor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Derives coarse user interests from message text using a simple NLP pipeline.
 *
 * <p>Current behavior combines detected names, noun extraction, and keyword buckets.
 */
public class InterestDetector {

  // Basic keyword categories (you can expand these later)
  private static final List<String> gamingKeywords =
      List.of(
          "game",
          "gaming",
          "play",
          "fps",
          "rpg",
          "elden",
          "ring",
          "minecraft",
          "fortnite",
          "valorant");

  private static final List<String> techKeywords =
      List.of("java", "coding", "programming", "pc", "computer", "software", "hardware");

  private static final List<String> musicKeywords =
      List.of("music", "song", "band", "guitar", "rap", "metal", "rock", "pop");

  private static final List<String> sportsKeywords =
      List.of("soccer", "football", "basketball", "hockey", "f1", "racing");

  /**
   * Extracts interest tags from free text using NLP signals plus keyword buckets.
   */
  public static List<String> detectInterests(String message) {

    List<String> interests = new ArrayList<>();

    // Run NLP in sequence so later stages can reuse earlier outputs.
    // 1. Tokenize
    String[] tokens = NLPProcessor.tokenize(message.toLowerCase());

    // 2. POS tags
    String[] pos = NLPProcessor.posTags(tokens);

    // 3. Named entities (people for now)
    String[] names = NLPProcessor.findNames(tokens);

    // Add detected names as interests
    interests.addAll(Arrays.asList(names));

    // 4. Extract nouns (NN, NNP, NNS, NNPS)
    for (int i = 0; i < tokens.length; i++) {
      if (pos[i].startsWith("NN")) {
        interests.add(tokens[i]);
      }
    }

    // 5. Keyword category detection
    for (String token : tokens) {
      if (gamingKeywords.contains(token)) {
        interests.add("gaming");
      }
      if (techKeywords.contains(token)) {
        interests.add("tech");
      }
      if (musicKeywords.contains(token)) {
        interests.add("music");
      }
      if (sportsKeywords.contains(token)) {
        interests.add("sports");
      }
    }

    return interests;
  }
}

