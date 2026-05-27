package Botcode.AI.Personality;

import Botcode.AI.Personality.TriggerEngine.Intent;
import Botcode.AI.Personality.EmotionEngine.Emotion;

/**
 * Chooses the bot speaking profile (tone + energy) from intent and emotion.
 */
public class PersonalityEngine {

  /**
   * Tone labels used by sentence generation to choose opener/body style.
   */
  public enum Tone {
    FRIENDLY,
    SARCASTIC,
    NEUTRAL,
    HYPE,
    COMFORTING
  }

  /**
   * Response profile selected for the current message.
   */
  public static class PersonalityProfile {

    public final Tone tone;
    public final int energy; // 1 = low, 2 = medium, 3 = high

    public PersonalityProfile(Tone tone, int energy) {
      this.tone = tone;
      this.energy = energy;
    }
  }

  /**
   * Selects response profile with emotion-first precedence.
   *
   * <p>If emotion is neutral, intent controls the fallback profile.
   */
  public static PersonalityProfile decideProfile(Intent intent, Emotion emotion) {

    // Emotion overrides intent when strong
    switch (emotion) {
      case FRUSTRATED:
        return new PersonalityProfile(Tone.SARCASTIC, 2);

      case SAD:
        return new PersonalityProfile(Tone.COMFORTING, 1);

      case HAPPY:
        return new PersonalityProfile(Tone.FRIENDLY, 3);

      case HYPED:
        return new PersonalityProfile(Tone.HYPE, 3);
      case ANGRY:
        return new PersonalityProfile(Tone.SARCASTIC, 3);

      default:
        // NEUTRAL emotion + fall through to intent logic
        break;
    }

     // Intent-based tone selection
     switch (intent) {
       case GREETING:
         return new PersonalityProfile(Tone.FRIENDLY, 2);

       case CASUAL_CHAT:
         return new PersonalityProfile(Tone.FRIENDLY, 2);

       case QUESTION:
         return new PersonalityProfile(Tone.FRIENDLY, 2);

       case KNOWLEDGE_QUERY:
         // Factual queries get neutral, informative tone with low energy
         return new PersonalityProfile(Tone.NEUTRAL, 1);

       case BOT_IDENTITY:
         return new PersonalityProfile(Tone.FRIENDLY, 2);

       case COMPLAINT:
         // Supportive tone for complaints --” mocking users with problems is counter-productive.
         return new PersonalityProfile(Tone.COMFORTING, 2);

       case PRAISE:
         return new PersonalityProfile(Tone.FRIENDLY, 3);

       case HYPE:
         return new PersonalityProfile(Tone.HYPE, 3);

       case CONFUSION:
         return new PersonalityProfile(Tone.COMFORTING, 1);

       case FAREWELL:
         return new PersonalityProfile(Tone.FRIENDLY, 2);

       default:
         return new PersonalityProfile(Tone.NEUTRAL, 2);
     }
  }
}


