package Botcode.AI.AIHook;

import Botcode.AI.Personality.SelfFactsRouter;
import Botcode.StarCitizen.StarCitizenChatService;
import Botcode.AI.AIUtils.BotConfig;
import Botcode.AI.WebLookupService;

import java.util.Locale;

/**
 * Central AI reply facade with enhanced contextual awareness and learning integration.
 *
 * <p>Behavior flow (May 2026+):
 * - Prioritizes self-identity responses for identity queries (consistent persona)
 * - Routes knowledge queries to web lookup for accurate factual answers
 * - Leverages Star Citizen domain expertise for topic-specific questions
 * - Falls back to learned personality phrases for conversational continuity
 * - Maintains context from ConversationMemoryService for smarter follow-ups
 */
public class AIResponder {

  public enum Source {
    STAR_CITIZEN,
    SELF_FACTS,
    WEB_LOOKUP,
    PREMIUM,
    LEARNED_PERSONALITY,
    NONE
  }

  public static final class AIResponse {

    public final String text;
    public final Source source;

    private AIResponse(String text, Source source) {
      this.text = text;
      this.source = source;
    }

    public static AIResponse of(String text, Source source) {
      return new AIResponse(text, source);
    }

    public static AIResponse none() {
      return new AIResponse(null, Source.NONE);
    }

    public boolean hasText() {
      return text != null && !text.isBlank();
    }
  }

  private AIResponder() {
  }

  /**
   * Resolves a conversational AI reply from available providers.
   *
   * <p>Enhanced flow (May 2026+):
   * 1. Prioritize self-identity responses for consistent persona
   * 2. Route knowledge queries to web lookup for factual accuracy
   * 3. Check Star Citizen domain expertise
   * 4. Try web lookup for non-SC topics
   * 5. Fall back to learned personality phrases
   * 6. Premium provider as optional override
   */
  public static AIResponse resolve(String prompt) {
    if (!BotConfig.AI_ENABLED || prompt == null || prompt.isBlank()) {
      return AIResponse.none();
    }

    // PRIORITY 1: Self-identity queries (consistent persona)
    if (isSelfIdentityPrompt(prompt)) {
      String self = SelfFactsRouter.resolve(prompt);
      if (self != null) {
        return AIResponse.of(self, Source.SELF_FACTS);
      }
    }

    // PRIORITY 2: Explicit knowledge queries to web lookup (accurate facts)
    if (isExplicitNonScLookupPrompt(prompt)) {
      String webFirst = WebLookupService.tryLookup(prompt);
      if (webFirst != null) {
        return AIResponse.of(webFirst, Source.WEB_LOOKUP);
      }
    }

    // PRIORITY 3: Star Citizen domain expertise (topic-specific)
    String sc = StarCitizenChatService.tryRespond(prompt);
    if (sc != null) {
      return AIResponse.of(sc, Source.STAR_CITIZEN);
    }

    // PRIORITY 4: Web lookup for non-SC general questions
    String web = WebLookupService.tryLookup(prompt);
    if (web != null) {
      return AIResponse.of(web, Source.WEB_LOOKUP);
    }

    // PRIORITY 5: Optional premium provider as override (future expansion)
    if (BotConfig.AI_PREMIUM_ENABLED) {
      String premium = tryPremiumRespond(prompt);
      if (premium != null && !premium.isBlank()) {
        return AIResponse.of(premium, Source.PREMIUM);
      }
    }

    // FALLBACK: Learned personality phrases for conversational continuity
    String self = SelfFactsRouter.resolve(prompt);
    if (self != null) {
      return AIResponse.of(self, Source.LEARNED_PERSONALITY);
    }

    return AIResponse.none();
  }

  /**
   * Placeholder hook for future external AI providers (intentionally no-op).
   */
  private static String tryPremiumRespond(String prompt) {
    // Future: implement provider-specific adapters using BotConfig.AI_PREMIUM_PROVIDER.
    return null;
  }

  private static boolean isSelfIdentityPrompt(String prompt) {
    String lower = prompt.toLowerCase(Locale.ROOT);
    return lower.contains("who are you")
        || lower.contains("what are you")
        || lower.contains("your name")
        || lower.contains("about yourself")
        || lower.contains("what can you do")
        || lower.contains("your purpose")
        || lower.contains("your role")
        || lower.contains("created by")
        || lower.contains("who made you")
        || lower.contains("who created you")
        || lower.equals("introduce yourself")
        || lower.equals("tell me about yourself");
  }

  private static boolean isExplicitNonScLookupPrompt(String prompt) {
    String lower = prompt.toLowerCase(Locale.ROOT);

    // Explicit lookup intent
    boolean explicitLookup =
        lower.contains("look up")
            || lower.contains("lookup")
            || lower.contains("search for")
            || lower.contains("google")
            || lower.startsWith("wiki ")
            || lower.startsWith("wikipedia ")
            || lower.startsWith("who is ")
            || lower.startsWith("what is ")
            || lower.startsWith("who was ")
            || lower.startsWith("what was ")
            || lower.startsWith("define ")
            || lower.startsWith("definition of ")
            || lower.startsWith("how to ")
            || lower.startsWith("how do ")
            || (lower.startsWith("when ") && (lower.contains("happen") || lower.contains("did") || lower.contains("was")))
            || lower.contains("capital of")
            || lower.contains("population of")
            || lower.contains("distance to");

    if (!explicitLookup) {
      return false;
    }

    // Exclude Star Citizen specific queries
    return !isSCContextualQuery(lower);
  }

  private static boolean isSCContextualQuery(String lower) {
    return lower.contains("star citizen")
        || lower.contains("trade route")
        || lower.contains("auec")
        || lower.contains("quantum")
        || lower.contains("refinery")
        || lower.contains("salvage")
        || lower.contains("mining")
        || lower.contains("ship")
        || lower.contains("weapon")
        || lower.contains("component")
        || lower.contains("commodity")
        || lower.contains("cargo");
  }
}


