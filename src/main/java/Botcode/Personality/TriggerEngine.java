package Botcode.Personality;

import java.util.List;
import java.util.Map;

/**
 * Detects high-level conversational intent from message text.
 *
 * This module is rule-based today and can be swapped later without changing
 * downstream personality or sentence-generation flows.
 */
public class TriggerEngine {

    /**
     * Intent buckets consumed by profile and response generation systems.
     */
    public enum Intent {
        GREETING,
        FAREWELL,
        QUESTION,
        COMPLAINT,
        PRAISE,
        HYPE,
        CONFUSION,
        NEUTRAL
    }

    // Keyword groups used for simple intent routing.
    private static final Map<Intent, List<String>> intentKeywords = Map.of(
        Intent.GREETING, List.of("hello", "hi", "hey","sup", "yo", "greetings"),
        Intent.FAREWELL, List.of("bye", "goodnght", "cya", "gn", "see ya", "peace"),
        Intent.QUESTION, List.of("?","how do i", "what is", "where does", "why does", "can I"),
        Intent.COMPLAINT, List.of("ntf", "hate","dislike", "disgust","broken", "crash", "lag", "bug", "annoying"),
        Intent.PRAISE, List.of("nice", "awesome", "great", "love", "cool"),
        Intent.HYPE, List.of("hype","pog", "lets go", "sweet", "can't wait", "so ready"),
        Intent.CONFUSION, List.of("idk", "what", "I'm lost", "huh", "confused", "doesn't make sense")
    );

    /**
     * Returns the best-matching intent, or NEUTRAL when no keyword matches.
     */
    public static Intent detectIntent(String message) {
        if (message == null || message.isEmpty()) {
            return Intent.NEUTRAL;
        }

        String lower = message.toLowerCase();

        // Iterate each bucket and short-circuit on first keyword hit.
        for (Map.Entry<Intent, List<String>> entry : intentKeywords.entrySet()) {
            for (String keyword : entry.getValue()) {
                if (lower.contains(keyword)) {
                    return entry.getKey();
                }
            }
        }

        return Intent.NEUTRAL;
    }
}
