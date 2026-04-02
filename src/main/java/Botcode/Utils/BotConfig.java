package Botcode.Utils;


    /**
     * Global configuration settings for the bot.
     * This class centralizes toggles and settings that may be used
     * across multiple systems (AI, personality engines, interest detection, etc).
     */
    public class BotConfig {

        // Toggle for enabling or disabling AI responses globally.
        // When false, AIResponder will always return null.
        public static final boolean AI_ENABLED = false;

        // Placeholder for future AI provider settings.
        public static final String AI_PROVIDER = "NONE";
        public static final String AI_API_KEY = "";

        // Add future configuration values here as needed
}
