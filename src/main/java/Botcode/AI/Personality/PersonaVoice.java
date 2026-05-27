package Botcode.AI.Personality;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;
/**
 * Centralized Deadpool-style voice shaping for outgoing chat text.
 */
public class PersonaVoice {
  // Keep persona flavor present but not on every single line.
  private static final int PREFIX_CHANCE_PERCENT = 28;

  private static final List<String> DEADPOOL_PREFIXES =
      List.of(
          "Maximum chimichanga advisory -",
          "Merc-with-a-mouth update -",
          "Red-suit narrator voice -",
          "Fourth-wall report -",
          "Mask-on chaos briefing -");
  private static final List<String> DEADPOOL_GIF_CAPTIONS =
      List.of(
          "Deadpool-grade GIF diplomacy deployed.",
          "GIF attached. Tactical nonsense confirmed.",
          "Visual evidence of chaos. Professionally reviewed.",
          "That GIF is now canon in my chaos logs.",
          "GIF mode engaged. Chimichanga-level confidence.");
  private PersonaVoice() {
  }
  /**
   * Ensures user-facing text keeps the configured persona voice.
   */
  public static String enforceDeadpoolVoice(String text) {
    if (text == null || text.isBlank()) {
      return "Maximum chimichanga advisory - say that again and I'll spin up a better answer.";
    }
    String clean = text.trim();
    if (alreadyInPersona(clean)) {
      return clean;
    }
    // Avoid constant opener spam: only add a prefix sometimes.
    if (ThreadLocalRandom.current().nextInt(100) >= PREFIX_CHANCE_PERCENT) {
      return clean;
    }
    return pick(DEADPOOL_PREFIXES) + " " + clean;
  }
  /**
   * Returns a Deadpool-style line suitable for GIF reactions/decorations.
   */
  public static String gifCaption() {
    return pick(DEADPOOL_GIF_CAPTIONS);
  }
  private static boolean alreadyInPersona(String value) {
    String lower = value.toLowerCase(Locale.ROOT);
    return lower.contains("deadpool")
        || lower.contains("chimichanga")
        || lower.contains("snark mode")
        || lower.contains("snark mode enabled")
        || lower.contains("maximum chimichanga advisory")
        || lower.contains("fourth-wall")
        || lower.contains("fourth wall")
        || lower.contains("breaking the fourth wall")
        || lower.contains("narrator voice")
        || lower.contains("merc with a spreadsheet")
        || lower.contains("merc-with-a-mouth")
        || lower.contains("merc with a mouth")
        || lower.contains("chaos clerk")
        || lower.contains("shopping for ship organs")
        || lower.contains("red-suit energy")
        || lower.contains("chaos");
  }
  private static String pick(List<String> options) {
    return options.get(ThreadLocalRandom.current().nextInt(options.size()));
  }
}