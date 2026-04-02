package Botcode.Personality;

import Botcode.Personality.TriggerEngine.Intent;
import Botcode.Personality.EmotionEngine.Emotion;
import Botcode.Personality.PersonalityEngine.Tone;
import Botcode.Personality.PersonalityEngine.PersonalityProfile;

import java.util.List;
import java.util.Random;

/**
 * Builds final bot replies by combining tone-based openers with intent-specific
 * message bodies, then applying a simple energy transform from the profile.
 */
public class SentenceGenerator {

    private static final Random random = new Random();

    // Phrase banks for different tones
    private static final List<String> friendlyOpeners = List.of(
            "Hey there! How's it going?",
            "Glad to see you here!",
            "This is so much fun!",
            "Hi :)",
            "Hey ",
            "Love this game, right?"
    );

    public static final List<String> sarcasticOpeners = List.of(
            "Oh great, another one.",
            "Just what I needed.",
            "Wow, so original.",
            "Here we go again.",
            "Fantastic, more input.",
            "Can't wait to hear this.",
            "Funny"
    );

    public static final List<String> hypeOpeners = List.of(
            "Let's goooo!",
            "This is gonna be epic!",
            "Can't wait for this!",
            "So ready for this!",
            "This is hype!",
            "Poggers!",
            "Yes!",
            "Sweet!"
    );

    public static final List<String> comfortingOpeners = List.of(
            "It's okay, we all have those days.",
            "Don't worry, you'll get it next time.",
            "Everyone struggles sometimes.",
            "I'm here for you.",
            "You'll bounce back from this!",
            "It's just a game, no big deal."
    );

    public static final List<String> neutralOpeners = List.of(
            "Noted.",
            "Okay.",
            "Got it.",
            "Understood.",
            "Alright.",
            "Thanks for sharing."
    );

    // Core response based on intent
    public static final List<String> greetingBodies = List.of(
            "Welcome to Phoenix Industries!",
            "Hope you have a great time here!",
            "Feel free to ask if you need anything.",
            "Glad you joined us, hope you brought pizza!",
            "How's it going?",
            "Hope you're having a good day.",
            "Nice to see you.",
            "What's up?"
    );

    public static final List<String> questionBodies = List.of(
            "That's a great question! Let me think...",
            "I'm not sure, but I'll find out for you.",
            "Hmm, that's interesting. I'll look into it.",
            "Let me check on that and get back to you.",
            "Arlight, here's the deal:"
    );

    public static final List<String> complaintBodies = List.of(
            "Sorry to hear that. Let's see if we can fix it.",
            "That sounds frustrating. I'll report it to the team.",
            "I understand how that can be annoying. Thanks for letting us know.",
            "We appreciate your feedback and will work on improving that.",
            "Yeah that sucks.",
            "Oof, that's rough.",
            "I get why you're annoyed.",
            "Yikes, not ideal."
    );

    private static final List<String> praiseBodies = List.of(
            "Thanks so much! We really appreciate it.",
            "Glad you like it! We're working hard to make it even better.",
            "Awesome, we're happy to hear that!",
            "Your support means a lot to us, thank you!",
            "Right? It's pretty great.",
            "We think it's pretty cool too!",
            "Love to hear it!",
            "That's awesome!",
            "Nice job!",
            "Huge Wim!"
    );

    private static final List<String> hypeBodies = List.of(
            "This is gonna be amazing!",
            "Can't wait for this to drop!",
            "So hyped for this!",
            "This is going to be epic!",
            "Poggers, let's go!",
            "Yes, this is what I'm talking about!",
            "This is gonna be insane!",
            "I'm pumped!",
            "Energy levels rising!",
            "Let's make it huge!"
    );

    public static final List<String> confusionBodies = List.of(
            "Let’s sort this out.",
            "No worries, I got you.",
            "Let's break it down.",
            "We’ll figure this out."
    );

    public static final List<String> farewellBodies = List.of(
            "Goodbye! Hope to see you again soon!",
            "Take care! It was great having you here.",
            "See you later! Don't forget to come back!",
            "Farewell! Wishing you all the best.",
            "Catch you later! Thanks for stopping by.",
            "Peace out! Stay awesome!",
            "Catch you later!",
            "See ya!",
            "Take care!",
            "Bye for now!"
    );

    public static final List<String> fallbackBodies = List.of(
            "Interesting.",
            "I see.",
            "Thanks for sharing that.",
            "Got it.",
            "Okay.",
            "Noted.",
            "Alright.",
            "Gotcha.",
            "Okay then.",
            "Noted."
    );

    // Utility to pick a random phrase from a list
    /**
     * Returns a random element from the provided phrase list.
     */
    private static String pick(List<String> list) {
        return list.get(random.nextInt(list.size()));
    }

    // Select opener from the persona tone determined earlier in the pipeline.
    /**
     * Picks a tone-specific opener phrase.
     */
    private static String getOpener(Tone tone) {
        switch (tone) { case FRIENDLY: return pick(friendlyOpeners);
            case SARCASTIC: return pick(sarcasticOpeners);
            case HYPE: return pick(hypeOpeners);
            case COMFORTING: return pick(comfortingOpeners);
            default: return pick(neutralOpeners);
        }
    }

    // Select body text from detected intent bucket.
    /**
     * Picks an intent-specific body phrase.
     */
    private static String getBody(Intent intent) {
        switch (intent) {
            case GREETING: return pick(greetingBodies);
            case QUESTION: return pick(questionBodies);
            case COMPLAINT: return pick(complaintBodies);
            case PRAISE: return pick(praiseBodies);
            case HYPE: return pick(hypeBodies);
            case CONFUSION: return pick(confusionBodies);
            case FAREWELL: return pick(farewellBodies);
            default: return pick(fallbackBodies);
        }
    }

    // Apply lightweight intensity styling without changing phrase selection.
    /**
     * Applies a simple energy transform to the selected body text.
     */
    private static String applyEnergy(String text, int energy) {
        switch (energy) {
            case 3: return text.toUpperCase() + "!!!";
            case 1: return text.toLowerCase();
            default: return text;
        }
    }

    /**
     * Builds the final response from tone opener + intent body + energy styling.
     */
    public static String generate(Intent intent, Emotion emotion, PersonalityProfile profile) {
        String opener = getOpener(profile.tone);
        String body = getBody(intent);
        body = applyEnergy(body, profile.energy);
        return opener + " " + body;
    }


}
