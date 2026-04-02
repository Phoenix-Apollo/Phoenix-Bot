package Botcode.Personality;

import java.util.List;
import java.util.Map;

/**
 * Detects coarse emotional tone from message keywords.
 *
 * This is a lightweight rule-based layer used before personality profile selection.
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
    private static final Map<Emotion, List<String>> emotionKeywords = Map.of(
            Emotion.HAPPY, List.of("gg", "nice", "awesome", "love this", "fun", "great"),
            Emotion.FRUSTRATED, List.of("wtf", "hate", "annoying", "crash", "lag", "bug"),
            Emotion.SAD, List.of("unlucky", "sad", "rip", "lost everything", "fml"),
            Emotion.HYPED, List.of("hype", "pog", "lets go", "can't wait", "excited", "so ready"),
            Emotion.ANGRY, List.of("pissed", "fuck", "shit", "screw", "damn")
    );

    /**
     * Returns the first matching emotion for a message, or NEUTRAL when no keyword matches.
     */
    public static Emotion detectEmotion(String message) {
        // Reserved null/empty guard block kept as-is for compatibility with current flow.
        if (message == null || message.isEmpty()) {


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
